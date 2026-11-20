#include <jni.h>
#include <android/log.h>
#include <algorithm>
#include <atomic>
#include <cstring>
#include <cstdint>
#include <limits>
#include <mutex>
#include <vector>

#include "commix_telemetry.h"
#include "scopes_engine.h"
#include "image_pipeline.h"
#include "computational_photo.h"
#include "stabilization_engine.h"
#include "color_science.h"
#include "keyframe_engine.h"
#include "best_shot_engine.h"

#define LOG_TAG "CommixNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static commix::KeyframeEngine g_keyframe_engine;
static std::mutex g_keyframe_mutex;
static commix::BestShotEngine g_best_shot_engine;
static std::mutex g_best_shot_mutex;

namespace {

inline bool validateDirectBufferSize(JNIEnv* env, jobject buf, size_t requiredBytes, void** outPtr) {
    if (!buf) return false;
    void* ptr = env->GetDirectBufferAddress(buf);
    jlong cap = env->GetDirectBufferCapacity(buf);
    if (!ptr || cap < 0 || static_cast<size_t>(cap) < requiredBytes) return false;
    *outPtr = ptr;
    return true;
}

inline bool safeMultiplyBytes(int w, int h, int c, size_t elemSize, size_t& outBytes) {
    if (w <= 0 || h <= 0 || c <= 0 || elemSize == 0) return false;
    const uint64_t pixels = static_cast<uint64_t>(w) * static_cast<uint64_t>(h);
    if (pixels > std::numeric_limits<size_t>::max() / static_cast<uint64_t>(c)) return false;
    const uint64_t elems = pixels * static_cast<uint64_t>(c);
    if (elems > std::numeric_limits<size_t>::max() / elemSize) return false;
    outBytes = static_cast<size_t>(elems * elemSize);
    return true;
}

} // namespace

// =============================================================================
// 1. Telemetry & Scopes Bridges
// =============================================================================

static void native_write_audio(JNIEnv*, jclass, jfloat norm, jfloat db, jfloat leftDb, jfloat rightDb) {
    commix_write_audio(norm, db, leftDb, rightDb);
}

static void native_write_imu(JNIEnv*, jclass, jfloat pitch, jfloat roll, jfloat yaw) {
    commix_write_imu(pitch, roll, yaw);
}

static void native_write_lens(JNIEnv*, jclass, jfloat afDist, jint afState) {
    commix_write_lens(afDist, afState);
}

static void native_compute_scopes_direct(
    JNIEnv* env,
    jclass,
    jobject yBuf,
    jobject uBuf,
    jobject vBuf,
    jint width,
    jint height,
    jint yStride,
    jint uvStride,
    jint uvPixelStride,
    jint step
) {
    if (!yBuf || !uBuf || !vBuf || width <= 0 || height <= 0 || step < 1) return;
    if (yStride < width || !commix::chroma_stride_holds_row(width, uvStride, uvPixelStride)) return;

    const auto* yPtr = static_cast<const uint8_t*>(env->GetDirectBufferAddress(yBuf));
    const auto* uPtr = static_cast<const uint8_t*>(env->GetDirectBufferAddress(uBuf));
    const auto* vPtr = static_cast<const uint8_t*>(env->GetDirectBufferAddress(vBuf));
    if (!yPtr || !uPtr || !vPtr) return;

    const jlong yCap = env->GetDirectBufferCapacity(yBuf);
    const jlong uCap = env->GetDirectBufferCapacity(uBuf);
    const jlong vCap = env->GetDirectBufferCapacity(vBuf);

    const size_t yRequired = commix::luma_plane_bytes(width, height, yStride);
    const size_t uvRequired = commix::chroma_plane_bytes(width, height, uvStride, uvPixelStride);
    if (yRequired == 0 || uvRequired == 0) return;

    if (yCap < 0 || static_cast<size_t>(yCap) < yRequired ||
        uCap < 0 || static_cast<size_t>(uCap) < uvRequired ||
        vCap < 0 || static_cast<size_t>(vCap) < uvRequired) {
        LOGE("compute_scopes rejected: buffer capacity smaller than plane requirements");
        return;
    }

    int32_t luma[commix::HIST_BINS];
    int32_t rBins[commix::HIST_BINS];
    int32_t gBins[commix::HIST_BINS];
    int32_t bBins[commix::HIST_BINS];
    float waveAvg[commix::WAVE_COLS];
    float waveMin[commix::WAVE_COLS];
    float waveMax[commix::WAVE_COLS];
    float paradeR[commix::PARADE_N];
    float paradeG[commix::PARADE_N];
    float paradeB[commix::PARADE_N];
    float vscope[commix::VSCOPE_SAMPLES * 2];

    commix::ScopeOut out;
    out.luma = luma;
    out.rBins = rBins;
    out.gBins = gBins;
    out.bBins = bBins;
    out.waveAvg = waveAvg;
    out.waveMin = waveMin;
    out.waveMax = waveMax;
    out.paradeR = paradeR;
    out.paradeG = paradeG;
    out.paradeB = paradeB;
    out.vscope = vscope;

    commix::compute_scopes(yPtr, uPtr, vPtr, width, height, yStride, uvStride, uvPixelStride, step, out);

    TelemetrySharedBuffer* tb = commix_get_telemetry_buffer();
    commix_telemetry_write_begin();
    std::memcpy(tb->luma_bins, luma, sizeof(luma));
    std::memcpy(tb->r_bins, rBins, sizeof(rBins));
    std::memcpy(tb->g_bins, gBins, sizeof(gBins));
    std::memcpy(tb->b_bins, bBins, sizeof(bBins));
    std::memcpy(tb->wave_cols_avg, waveAvg, sizeof(waveAvg));
    std::memcpy(tb->wave_cols_min, waveMin, sizeof(waveMin));
    std::memcpy(tb->wave_cols_max, waveMax, sizeof(waveMax));
    std::memcpy(tb->parade_r, paradeR, sizeof(paradeR));
    std::memcpy(tb->parade_g, paradeG, sizeof(paradeG));
    std::memcpy(tb->parade_b, paradeB, sizeof(paradeB));
    std::memcpy(tb->vscope_samples, vscope, sizeof(vscope));
    tb->spot_ire = out.spotIre;
    tb->mean_u = out.meanU;
    tb->mean_v = out.meanV;
    tb->hi_clip_pct = out.hiClipPct;
    tb->lo_clip_pct = out.loClipPct;
    tb->focus_peak_score = out.focusPeakScore;
    tb->vscope_count = out.vscopeCount;
    tb->has_scopes = 1;
    commix_telemetry_write_end();
}

static jboolean native_read_telemetry(
    JNIEnv* env,
    jclass,
    jfloatArray outFloats,
    jintArray outLuma,
    jfloatArray outWave,
    jfloatArray outVscope
) {
    TelemetrySharedBuffer* tb = commix_get_telemetry_buffer();

    // Bounded lock-free seqlock retry loop (max 4 attempts) to survive brief writer overlaps
    // without blocking when a writer holds an odd sequence.
    TelemetrySharedBuffer snap{};
    bool snapshotValid = false;
    for (int attempt = 0; attempt < 4; ++attempt) {
        const uint32_t b1 = tb->seq_begin.load(std::memory_order_acquire);
        const uint32_t e1 = tb->seq_end.load(std::memory_order_acquire);
        if ((b1 & 1u) || (b1 != e1)) continue;
        std::atomic_thread_fence(std::memory_order_seq_cst);

        std::memcpy(static_cast<void*>(&snap), static_cast<const void*>(tb), sizeof(TelemetrySharedBuffer));

        std::atomic_thread_fence(std::memory_order_seq_cst);
        const uint32_t b2 = tb->seq_begin.load(std::memory_order_acquire);
        const uint32_t e2 = tb->seq_end.load(std::memory_order_acquire);
        if (b1 == b2 && b1 == e2) {
            snapshotValid = true;
            break;
        }
    }
    if (!snapshotValid) return JNI_FALSE;

    if (outFloats != nullptr) {
        const jsize len = env->GetArrayLength(outFloats);
        if (len >= 11) {
            jfloat vals[14];
            vals[0] = snap.audio_norm;
            vals[1] = snap.audio_db;
            vals[2] = snap.audio_left_db;
            vals[3] = snap.audio_right_db;
            vals[4] = snap.gyro_pitch;
            vals[5] = snap.gyro_roll;
            vals[6] = snap.gyro_yaw;
            vals[7] = snap.spot_ire;
            vals[8] = snap.hi_clip_pct;
            vals[9] = snap.lo_clip_pct;
            vals[10] = static_cast<jfloat>(snap.vscope_count);
            vals[11] = snap.af_distance;
            vals[12] = static_cast<jfloat>(snap.af_state);
            vals[13] = snap.focus_peak_score;
            const jsize copyLen = (len >= 14) ? 14 : ((len >= 13) ? 13 : 11);
            env->SetFloatArrayRegion(outFloats, 0, copyLen, vals);
        }
    }

    if (outLuma != nullptr && env->GetArrayLength(outLuma) >= COMMIX_HIST_BINS) {
        env->SetIntArrayRegion(outLuma, 0, COMMIX_HIST_BINS, snap.luma_bins);
    }

    if (outWave != nullptr && env->GetArrayLength(outWave) >= COMMIX_WAVE_COLS) {
        env->SetFloatArrayRegion(outWave, 0, COMMIX_WAVE_COLS, snap.wave_cols_avg);
    }

    if (outVscope != nullptr && env->GetArrayLength(outVscope) >= COMMIX_VSCOPE_MAX_SAMPLES * 2) {
        const jint clampedCount = std::clamp(snap.vscope_count, 0, COMMIX_VSCOPE_MAX_SAMPLES);
        if (clampedCount > 0) {
            env->SetFloatArrayRegion(outVscope, 0, clampedCount * 2, snap.vscope_samples);
        }
    }

    return JNI_TRUE;
}

static jboolean native_read_parade(
    JNIEnv* env,
    jclass,
    jfloatArray outParadeR,
    jfloatArray outParadeG,
    jfloatArray outParadeB
) {
    TelemetrySharedBuffer* tb = commix_get_telemetry_buffer();

    float pR[COMMIX_PARADE_N];
    float pG[COMMIX_PARADE_N];
    float pB[COMMIX_PARADE_N];
    bool snapshotValid = false;

    for (int attempt = 0; attempt < 4; ++attempt) {
        const uint32_t b1 = tb->seq_begin.load(std::memory_order_acquire);
        const uint32_t e1 = tb->seq_end.load(std::memory_order_acquire);
        if ((b1 & 1u) || (b1 != e1)) continue;
        std::atomic_thread_fence(std::memory_order_seq_cst);

        std::memcpy(pR, tb->parade_r, sizeof(pR));
        std::memcpy(pG, tb->parade_g, sizeof(pG));
        std::memcpy(pB, tb->parade_b, sizeof(pB));

        std::atomic_thread_fence(std::memory_order_seq_cst);
        const uint32_t b2 = tb->seq_begin.load(std::memory_order_acquire);
        const uint32_t e2 = tb->seq_end.load(std::memory_order_acquire);
        if (b1 == b2 && b1 == e2) {
            snapshotValid = true;
            break;
        }
    }
    if (!snapshotValid) return JNI_FALSE;

    if (outParadeR != nullptr && env->GetArrayLength(outParadeR) >= COMMIX_PARADE_N) {
        env->SetFloatArrayRegion(outParadeR, 0, COMMIX_PARADE_N, pR);
    }
    if (outParadeG != nullptr && env->GetArrayLength(outParadeG) >= COMMIX_PARADE_N) {
        env->SetFloatArrayRegion(outParadeG, 0, COMMIX_PARADE_N, pG);
    }
    if (outParadeB != nullptr && env->GetArrayLength(outParadeB) >= COMMIX_PARADE_N) {
        env->SetFloatArrayRegion(outParadeB, 0, COMMIX_PARADE_N, pB);
    }
    return JNI_TRUE;
}

// =============================================================================
// 2. Native Engine Bridges
// =============================================================================

static jboolean native_process_zero_direct(
    JNIEnv* env,
    jclass,
    jobject pixelBuf,
    jint width,
    jint height,
    jint channels,
    jint curveIdx,
    jint grainIdx,
    jfloat grainIntensity
) {
    if (!pixelBuf || width <= 0 || height <= 0 || channels < 3 || channels > 4) return JNI_FALSE;
    size_t required = 0;
    void* ptr = nullptr;
    if (!safeMultiplyBytes(width, height, channels, 1, required) ||
        !validateDirectBufferSize(env, pixelBuf, required, &ptr)) {
        return JNI_FALSE;
    }

    auto curve = static_cast<commix::ToneCurvePreset>(std::clamp(static_cast<int>(curveIdx), 0, 3));
    auto grain = static_cast<commix::GrainPreset>(std::clamp(static_cast<int>(grainIdx), 0, 3));

    return commix::ImagePipeline::processZero(
        static_cast<uint8_t*>(ptr), width, height, channels, curve, grain, grainIntensity, 1.0f
    ) ? JNI_TRUE : JNI_FALSE;
}

static jboolean native_process_zero_intensity_direct(
    JNIEnv* env,
    jclass,
    jobject pixelBuf,
    jint width,
    jint height,
    jint channels,
    jint curveIdx,
    jint grainIdx,
    jfloat grainIntensity,
    jfloat toneIntensity
) {
    if (!pixelBuf || width <= 0 || height <= 0 || channels < 3 || channels > 4) return JNI_FALSE;
    size_t required = 0;
    void* ptr = nullptr;
    if (!safeMultiplyBytes(width, height, channels, 1, required) ||
        !validateDirectBufferSize(env, pixelBuf, required, &ptr)) {
        return JNI_FALSE;
    }

    auto curve = static_cast<commix::ToneCurvePreset>(std::clamp(static_cast<int>(curveIdx), 0, 3));
    auto grain = static_cast<commix::GrainPreset>(std::clamp(static_cast<int>(grainIdx), 0, 3));

    return commix::ImagePipeline::processZero(
        static_cast<uint8_t*>(ptr), width, height, channels, curve, grain, grainIntensity, toneIntensity
    ) ? JNI_TRUE : JNI_FALSE;
}

static void native_apply_cdl_direct(
    JNIEnv* env,
    jclass,
    jobject pixelBuf,
    jint width,
    jint height,
    jint channels,
    jfloat sR, jfloat sG, jfloat sB,
    jfloat oR, jfloat oG, jfloat oB,
    jfloat pR, jfloat pG, jfloat pB,
    jfloat sat
) {
    if (!pixelBuf || width <= 0 || height <= 0 || channels < 3 || channels > 4) return;
    size_t required = 0;
    void* ptr = nullptr;
    if (!safeMultiplyBytes(width, height, channels, 1, required) ||
        !validateDirectBufferSize(env, pixelBuf, required, &ptr)) {
        return;
    }

    commix::AscCdlParams cdl{sR, sG, sB, oR, oG, oB, pR, pG, pB, sat};
    commix::ColorScienceEngine::applyCdl(static_cast<uint8_t*>(ptr), width, height, channels, cdl, 1.0f);
}

static void native_apply_cdl_blended_direct(
    JNIEnv* env,
    jclass,
    jobject pixelBuf,
    jint width,
    jint height,
    jint channels,
    jfloat sR, jfloat sG, jfloat sB,
    jfloat oR, jfloat oG, jfloat oB,
    jfloat pR, jfloat pG, jfloat pB,
    jfloat sat,
    jfloat blend
) {
    if (!pixelBuf || width <= 0 || height <= 0 || channels < 3 || channels > 4) return;
    size_t required = 0;
    void* ptr = nullptr;
    if (!safeMultiplyBytes(width, height, channels, 1, required) ||
        !validateDirectBufferSize(env, pixelBuf, required, &ptr)) {
        return;
    }

    commix::AscCdlParams cdl{sR, sG, sB, oR, oG, oB, pR, pG, pB, sat};
    commix::ColorScienceEngine::applyCdl(static_cast<uint8_t*>(ptr), width, height, channels, cdl, blend);
}

static void native_apply_transfer_curve_direct(
    JNIEnv* env,
    jclass,
    jobject pixelBuf,
    jint width,
    jint height,
    jint channels,
    jint curveIdx,
    jfloat blend
) {
    if (!pixelBuf || width <= 0 || height <= 0 || channels < 3 || channels > 4) return;
    size_t required = 0;
    void* ptr = nullptr;
    if (!safeMultiplyBytes(width, height, channels, 1, required) ||
        !validateDirectBufferSize(env, pixelBuf, required, &ptr)) {
        return;
    }

    const int clampedIdx = std::clamp(static_cast<int>(curveIdx), 0, 8);
    auto curve = static_cast<commix::TransferCurveType>(clampedIdx);
    commix::ColorScienceEngine::applyTransferCurve(
        static_cast<uint8_t*>(ptr), width, height, channels, curve, blend
    );
}

static void native_apply_unsharp_mask_direct(
    JNIEnv* env,
    jclass,
    jobject pixelBuf,
    jint width,
    jint height,
    jint channels,
    jfloat amount,
    jfloat radius
) {
    if (!pixelBuf || width < 3 || height < 3 || channels < 3 || channels > 4) return;
    size_t required = 0;
    void* ptr = nullptr;
    if (!safeMultiplyBytes(width, height, channels, 1, required) ||
        !validateDirectBufferSize(env, pixelBuf, required, &ptr)) {
        return;
    }
    commix::ImagePipeline::applyUnsharpMask(static_cast<uint8_t*>(ptr), width, height, channels, amount, radius);
}

static jboolean native_aether_master_enhance_direct(
    JNIEnv* env,
    jclass,
    jobject pixelBuf,
    jint width,
    jint height,
    jint channels,
    jfloat denoiseStrength,
    jfloat clarityStrength,
    jfloat shadowLift,
    jfloat highlightRollOff
) {
    if (!pixelBuf || width < 3 || height < 3 || channels < 3 || channels > 4) return JNI_FALSE;
    size_t required = 0;
    void* ptr = nullptr;
    if (!safeMultiplyBytes(width, height, channels, 1, required) ||
        !validateDirectBufferSize(env, pixelBuf, required, &ptr)) {
        return JNI_FALSE;
    }
    return commix::ImagePipeline::aetherMasterEnhance(
        static_cast<uint8_t*>(ptr), width, height, channels,
        denoiseStrength, clarityStrength, shadowLift, highlightRollOff
    ) ? JNI_TRUE : JNI_FALSE;
}

static jboolean native_apply_virtual_gain_direct(
    JNIEnv* env,
    jclass,
    jobject pixelBuf,
    jint width,
    jint height,
    jint channels,
    jfloat gainLinear
) {
    if (!pixelBuf || width <= 0 || height <= 0 || channels < 3 || channels > 4) return JNI_FALSE;
    size_t required = 0;
    void* ptr = nullptr;
    if (!safeMultiplyBytes(width, height, channels, 1, required) ||
        !validateDirectBufferSize(env, pixelBuf, required, &ptr)) {
        return JNI_FALSE;
    }
    return commix::ImagePipeline::applyVirtualGainAndTone(
        static_cast<uint8_t*>(ptr), width, height, channels, gainLinear
    ) ? JNI_TRUE : JNI_FALSE;
}

static jboolean native_fuse_hdr_exposure_direct(
    JNIEnv* env,
    jclass,
    jobjectArray frameBufs,
    jint frameCount,
    jint width,
    jint height,
    jint channels,
    jobject outBuf,
    jfloat highlightCompression,
    jfloat shadowRecovery
) {
    if (!frameBufs || !outBuf || frameCount <= 0 || width <= 0 || height <= 0 || channels < 3 || channels > 4) {
        return JNI_FALSE;
    }
    const jsize arrayLen = env->GetArrayLength(frameBufs);
    if (frameCount > arrayLen || frameCount > commix::ComputationalPhotoEngine::MAX_STACK_FRAMES) {
        return JNI_FALSE;
    }
    size_t required = 0;
    void* outPtr = nullptr;
    if (!safeMultiplyBytes(width, height, channels, 1, required) ||
        !validateDirectBufferSize(env, outBuf, required, &outPtr)) {
        return JNI_FALSE;
    }
    std::vector<const uint8_t*> ptrs;
    ptrs.reserve(static_cast<size_t>(frameCount));
    for (jint i = 0; i < frameCount; ++i) {
        jobject fObj = env->GetObjectArrayElement(frameBufs, i);
        if (!fObj) return JNI_FALSE;
        void* fPtr = nullptr;
        const bool ok = validateDirectBufferSize(env, fObj, required, &fPtr);
        env->DeleteLocalRef(fObj);
        if (!ok) return JNI_FALSE;
        ptrs.push_back(static_cast<const uint8_t*>(fPtr));
    }
    return commix::ComputationalPhotoEngine::fuseHdrExposure(
        ptrs.data(), frameCount, width, height, channels,
        static_cast<uint8_t*>(outPtr), highlightCompression, shadowRecovery
    ) ? JNI_TRUE : JNI_FALSE;
}

static jboolean native_stack_frames_direct(
    JNIEnv* env,
    jclass,
    jobjectArray frameBufs,
    jint frameCount,
    jint width,
    jint height,
    jint channels,
    jobject outBuf,
    jboolean deepShadowBoost
) {
    if (!frameBufs || !outBuf || frameCount <= 0 || width <= 0 || height <= 0 || channels < 3 || channels > 4) {
        return JNI_FALSE;
    }

    const jsize arrayLen = env->GetArrayLength(frameBufs);
    if (frameCount > arrayLen || frameCount > commix::ComputationalPhotoEngine::MAX_STACK_FRAMES) {
        LOGE("stack_frames rejected: frameCount=%d arrayLen=%d max=%d",
             static_cast<int>(frameCount), static_cast<int>(arrayLen),
             commix::ComputationalPhotoEngine::MAX_STACK_FRAMES);
        return JNI_FALSE;
    }

    size_t required = 0;
    void* outPtr = nullptr;
    if (!safeMultiplyBytes(width, height, channels, 1, required) ||
        !validateDirectBufferSize(env, outBuf, required, &outPtr)) {
        return JNI_FALSE;
    }

    std::vector<const uint8_t*> ptrs;
    ptrs.reserve(static_cast<size_t>(frameCount));
    for (jint i = 0; i < frameCount; ++i) {
        jobject fObj = env->GetObjectArrayElement(frameBufs, i);
        if (!fObj) return JNI_FALSE;
        void* fPtr = nullptr;
        const bool ok = validateDirectBufferSize(env, fObj, required, &fPtr);
        env->DeleteLocalRef(fObj);
        if (!ok) return JNI_FALSE;
        ptrs.push_back(static_cast<const uint8_t*>(fPtr));
    }

    return commix::ComputationalPhotoEngine::stackAstroFrames(
        ptrs.data(), frameCount, width, height, channels, static_cast<uint8_t*>(outPtr), deepShadowBoost == JNI_TRUE
    ) ? JNI_TRUE : JNI_FALSE;
}

static jboolean native_accumulate_long_exposure_direct(
    JNIEnv* env,
    jclass,
    jobject frameBuf,
    jobject accumFloatBuf,
    jint width,
    jint height,
    jint channels,
    jint frameIndex,
    jint styleIdx,
    jfloat alpha
) {
    if (!frameBuf || !accumFloatBuf || width <= 0 || height <= 0 || channels < 3 || channels > 4 || frameIndex < 0) {
        return JNI_FALSE;
    }
    size_t frameBytes = 0;
    size_t accumBytes = 0;
    void* framePtr = nullptr;
    void* accumPtr = nullptr;
    if (!safeMultiplyBytes(width, height, channels, 1, frameBytes) ||
        !safeMultiplyBytes(width, height, channels, sizeof(float), accumBytes) ||
        !validateDirectBufferSize(env, frameBuf, frameBytes, &framePtr) ||
        !validateDirectBufferSize(env, accumFloatBuf, accumBytes, &accumPtr)) {
        return JNI_FALSE;
    }

    auto style = static_cast<commix::LongExposureStyle>(std::clamp(static_cast<int>(styleIdx), 0, 3));
    return commix::ComputationalPhotoEngine::accumulateLongExposure(
        static_cast<const uint8_t*>(framePtr),
        static_cast<float*>(accumPtr),
        width, height, channels, frameIndex, style, alpha
    ) ? JNI_TRUE : JNI_FALSE;
}

static jboolean native_finalize_long_exposure_direct(
    JNIEnv* env,
    jclass,
    jobject accumFloatBuf,
    jobject outBuf,
    jint pixelCount,
    jint channels
) {
    if (!accumFloatBuf || !outBuf || pixelCount <= 0 || channels < 3 || channels > 4) {
        return JNI_FALSE;
    }
    size_t accumBytes = 0;
    size_t outBytes = 0;
    void* accumPtr = nullptr;
    void* outPtr = nullptr;
    if (!safeMultiplyBytes(pixelCount, 1, channels, sizeof(float), accumBytes) ||
        !safeMultiplyBytes(pixelCount, 1, channels, 1, outBytes) ||
        !validateDirectBufferSize(env, accumFloatBuf, accumBytes, &accumPtr) ||
        !validateDirectBufferSize(env, outBuf, outBytes, &outPtr)) {
        return JNI_FALSE;
    }

    commix::ComputationalPhotoEngine::finalizeLongExposure(
        static_cast<const float*>(accumPtr),
        static_cast<uint8_t*>(outPtr),
        pixelCount, channels
    );
    return JNI_TRUE;
}

static jboolean native_blend_multi_exposure_direct(
    JNIEnv* env,
    jclass,
    jobject baseBuf,
    jobject blendBuf,
    jobject outBuf,
    jint width,
    jint height,
    jint channels,
    jint modeIdx
) {
    if (!baseBuf || !blendBuf || !outBuf || width <= 0 || height <= 0 || channels < 3 || channels > 4) {
        return JNI_FALSE;
    }
    size_t required = 0;
    void* basePtr = nullptr;
    void* blendPtr = nullptr;
    void* outPtr = nullptr;
    if (!safeMultiplyBytes(width, height, channels, 1, required) ||
        !validateDirectBufferSize(env, baseBuf, required, &basePtr) ||
        !validateDirectBufferSize(env, blendBuf, required, &blendPtr) ||
        !validateDirectBufferSize(env, outBuf, required, &outPtr)) {
        return JNI_FALSE;
    }

    auto mode = static_cast<commix::MultiExposureBlendMode>(std::clamp(static_cast<int>(modeIdx), 0, 3));
    return commix::ComputationalPhotoEngine::blendMultiExposure(
        static_cast<const uint8_t*>(basePtr),
        static_cast<const uint8_t*>(blendPtr),
        static_cast<uint8_t*>(outPtr),
        width, height, channels, mode
    ) ? JNI_TRUE : JNI_FALSE;
}

static jboolean native_stack_focus_slices_direct(
    JNIEnv* env,
    jclass,
    jobjectArray sliceBufs,
    jint sliceCount,
    jint width,
    jint height,
    jint channels,
    jobject outBuf
) {
    if (!sliceBufs || !outBuf || sliceCount <= 0 || width < 3 || height < 3 || channels < 3 || channels > 4) {
        return JNI_FALSE;
    }
    const jsize arrayLen = env->GetArrayLength(sliceBufs);
    if (sliceCount > arrayLen || sliceCount > commix::ComputationalPhotoEngine::MAX_STACK_FRAMES) {
        return JNI_FALSE;
    }

    size_t required = 0;
    void* outPtr = nullptr;
    if (!safeMultiplyBytes(width, height, channels, 1, required) ||
        !validateDirectBufferSize(env, outBuf, required, &outPtr)) {
        return JNI_FALSE;
    }

    std::vector<const uint8_t*> ptrs;
    ptrs.reserve(static_cast<size_t>(sliceCount));
    for (jint i = 0; i < sliceCount; ++i) {
        jobject sObj = env->GetObjectArrayElement(sliceBufs, i);
        if (!sObj) return JNI_FALSE;
        void* sPtr = nullptr;
        const bool ok = validateDirectBufferSize(env, sObj, required, &sPtr);
        env->DeleteLocalRef(sObj);
        if (!ok) return JNI_FALSE;
        ptrs.push_back(static_cast<const uint8_t*>(sPtr));
    }

    return commix::ComputationalPhotoEngine::stackFocusSlices(
        ptrs.data(), sliceCount, width, height, channels, static_cast<uint8_t*>(outPtr)
    ) ? JNI_TRUE : JNI_FALSE;
}

static jboolean native_correct_lens_aberration_direct(
    JNIEnv* env,
    jclass,
    jobject inputBuf,
    jobject outputBuf,
    jint width,
    jint height,
    jint channels,
    jfloat k1,
    jfloat k2
) {
    if (!inputBuf || !outputBuf || width <= 0 || height <= 0 || channels < 3 || channels > 4) {
        return JNI_FALSE;
    }
    size_t required = 0;
    void* inPtr = nullptr;
    void* outPtr = nullptr;
    if (!safeMultiplyBytes(width, height, channels, 1, required) ||
        !validateDirectBufferSize(env, inputBuf, required, &inPtr) ||
        !validateDirectBufferSize(env, outputBuf, required, &outPtr)) {
        return JNI_FALSE;
    }

    return commix::ComputationalPhotoEngine::correctLensAberration(
        static_cast<const uint8_t*>(inPtr),
        static_cast<uint8_t*>(outPtr),
        width, height, channels, k1, k2
    ) ? JNI_TRUE : JNI_FALSE;
}

static jfloat native_calculate_hyperfocal_distance(
    JNIEnv*, jclass, jfloat focalLengthMm, jfloat fNumber, jfloat cocMm
) {
    return commix::ComputationalPhotoEngine::calculateHyperfocalDistance(focalLengthMm, fNumber, cocMm);
}

static void native_push_imu_sample(
    JNIEnv*, jclass,
    jlong timestampNs,
    jfloat gx, jfloat gy, jfloat gz,
    jfloat ax, jfloat ay, jfloat az
) {
    commix::StabilizationEngine::instance().pushImuSample(
        static_cast<int64_t>(timestampNs), gx, gy, gz, ax, ay, az
    );
}

static void native_update_kalman(
    JNIEnv* env, jclass,
    jfloat pitch, jfloat roll, jfloat yaw, jfloat dt,
    jfloatArray outEuler
) {
    float outP = pitch, outR = roll, outY = yaw;
    commix::StabilizationEngine::instance().updateKalman(pitch, roll, yaw, dt, outP, outR, outY);
    if (outEuler != nullptr && env->GetArrayLength(outEuler) >= 3) {
        jfloat res[3] = {outP, outR, outY};
        env->SetFloatArrayRegion(outEuler, 0, 3, res);
    }
}

static void native_start_imu_logging(JNIEnv*, jclass) {
    commix::StabilizationEngine::instance().startLogging();
}

static jboolean native_stop_and_export_gyroflow(
    JNIEnv* env,
    jclass,
    jstring outputPath,
    jstring cameraName,
    jdouble frameRate
) {
    if (!outputPath) return JNI_FALSE;
    const char* pathStr = env->GetStringUTFChars(outputPath, nullptr);
    if (!pathStr) return JNI_FALSE;

    const char* camStr = nullptr;
    if (cameraName != nullptr) {
        camStr = env->GetStringUTFChars(cameraName, nullptr);
        if (!camStr) {
            env->ReleaseStringUTFChars(outputPath, pathStr);
            return JNI_FALSE;
        }
    }

    bool ok = commix::StabilizationEngine::instance().stopAndExportGyroflow(pathStr, camStr, frameRate);
    env->ReleaseStringUTFChars(outputPath, pathStr);
    if (camStr != nullptr) {
        env->ReleaseStringUTFChars(cameraName, camStr);
    }
    return ok ? JNI_TRUE : JNI_FALSE;
}

static void native_compute_l1_path(JNIEnv* env, jclass, jfloatArray inputArr, jfloatArray outputArr, jfloat lambda) {
    if (!inputArr || !outputArr) return;
    const jsize len = env->GetArrayLength(inputArr);
    if (len <= 0 || env->GetArrayLength(outputArr) < len) return;

    std::vector<float> inVec(static_cast<size_t>(len));
    std::vector<float> outVec(static_cast<size_t>(len));
    env->GetFloatArrayRegion(inputArr, 0, len, inVec.data());

    commix::StabilizationEngine::computeL1Path(inVec.data(), outVec.data(), static_cast<int>(len), lambda);
    env->SetFloatArrayRegion(outputArr, 0, len, outVec.data());
}

static void native_push_best_shot_velocity(JNIEnv*, jclass, jfloat gx, jfloat gy, jfloat gz) {
    std::lock_guard<std::mutex> lock(g_best_shot_mutex);
    g_best_shot_engine.pushVelocity(gx, gy, gz);
}

static jfloat native_evaluate_best_shot(JNIEnv*, jclass, jfloat spotIre, jfloat hiClip, jfloat loClip) {
    TelemetrySharedBuffer* tb = commix_get_telemetry_buffer();
    int32_t histSnap[COMMIX_HIST_BINS];
    bool hasHist = false;
    {
        const uint32_t b1 = tb->seq_begin.load(std::memory_order_acquire);
        const uint32_t e1 = tb->seq_end.load(std::memory_order_acquire);
        if ((b1 & 1u) == 0u && b1 == e1 && tb->has_scopes) {
            std::atomic_thread_fence(std::memory_order_seq_cst);
            std::memcpy(histSnap, tb->luma_bins, sizeof(histSnap));
            std::atomic_thread_fence(std::memory_order_seq_cst);
            const uint32_t b2 = tb->seq_begin.load(std::memory_order_acquire);
            const uint32_t e2 = tb->seq_end.load(std::memory_order_acquire);
            hasHist = (b1 == b2 && b1 == e2);
        }
    }

    std::lock_guard<std::mutex> lock(g_best_shot_mutex);
    auto score = g_best_shot_engine.evaluate(hasHist ? histSnap : nullptr, spotIre, hiClip, loClip);
    return score.shouldTriggerShoot ? (score.totalScore + 1000.0f) : score.totalScore;
}

static void native_keyframe_reset(JNIEnv*, jclass) {
    std::lock_guard<std::mutex> lock(g_keyframe_mutex);
    g_keyframe_engine.reset();
}

static void native_keyframe_add_point(
    JNIEnv*, jclass, jint trackId, jfloat timeSec, jfloat value, jint curveIdx
) {
    std::lock_guard<std::mutex> lock(g_keyframe_mutex);
    const auto curve = static_cast<commix::KeyframeCurve>(std::clamp(static_cast<int>(curveIdx), 0, 4));
    const commix::KeyframePoint pt{timeSec, value, curve};
    switch (trackId) {
        case 0: g_keyframe_engine.trackZoom.points.push_back(pt); g_keyframe_engine.trackZoom.sortPoints(); break;
        case 1: g_keyframe_engine.trackIso.points.push_back(pt); g_keyframe_engine.trackIso.sortPoints(); break;
        case 2: g_keyframe_engine.trackShutter.points.push_back(pt); g_keyframe_engine.trackShutter.sortPoints(); break;
        case 3: g_keyframe_engine.trackEv.points.push_back(pt); g_keyframe_engine.trackEv.sortPoints(); break;
        case 4: g_keyframe_engine.trackWb.points.push_back(pt); g_keyframe_engine.trackWb.sortPoints(); break;
        case 5: g_keyframe_engine.trackFocus.points.push_back(pt); g_keyframe_engine.trackFocus.sortPoints(); break;
        default: break;
    }
}

static void native_keyframe_start(JNIEnv*, jclass, jfloat durationSec) {
    std::lock_guard<std::mutex> lock(g_keyframe_mutex);
    g_keyframe_engine.durationSec = durationSec > 0.1f ? durationSec : 10.0f;
    g_keyframe_engine.start();
}

static void native_keyframe_stop(JNIEnv*, jclass) {
    std::lock_guard<std::mutex> lock(g_keyframe_mutex);
    g_keyframe_engine.stop();
}

static jboolean native_keyframe_tick(JNIEnv* env, jclass, jfloat dtSec, jfloatArray outValues) {
    if (!outValues || env->GetArrayLength(outValues) < 6) return JNI_FALSE;
    commix::KeyframeBatchValues vals{};
    bool playing = false;
    {
        std::lock_guard<std::mutex> lock(g_keyframe_mutex);
        playing = g_keyframe_engine.tick(dtSec, vals);
    }

    jfloat res[6] = {
        vals.zoom,
        static_cast<jfloat>(vals.iso),
        static_cast<jfloat>(vals.shutterNs),
        vals.ev,
        static_cast<jfloat>(vals.wbKelvin),
        vals.focusDiopters
    };
    env->SetFloatArrayRegion(outValues, 0, 6, res);
    return playing ? JNI_TRUE : JNI_FALSE;
}

// =============================================================================
// JNI Method Registration Table
// =============================================================================

static const JNINativeMethod g_telemetry_methods[] = {
    {"nativeWriteAudio", "(FFFF)V", reinterpret_cast<void*>(native_write_audio)},
    {"nativeWriteImu", "(FFF)V", reinterpret_cast<void*>(native_write_imu)},
    {"nativeWriteLens", "(FI)V", reinterpret_cast<void*>(native_write_lens)},
    {"nativeComputeScopesDirect", "(Ljava/nio/ByteBuffer;Ljava/nio/ByteBuffer;Ljava/nio/ByteBuffer;IIIIII)V", reinterpret_cast<void*>(native_compute_scopes_direct)},
    {"nativeReadTelemetry", "([F[I[F[F)Z", reinterpret_cast<void*>(native_read_telemetry)},
    {"nativeReadParade", "([F[F[F)Z", reinterpret_cast<void*>(native_read_parade)}
};

static const JNINativeMethod g_engine_methods[] = {
    {"nativeProcessZeroDirect", "(Ljava/nio/ByteBuffer;IIIIIF)Z", reinterpret_cast<void*>(native_process_zero_direct)},
    {"nativeProcessZeroIntensityDirect", "(Ljava/nio/ByteBuffer;IIIIIFF)Z", reinterpret_cast<void*>(native_process_zero_intensity_direct)},
    {"nativeApplyCdlDirect", "(Ljava/nio/ByteBuffer;IIIFFFFFFFFFF)V", reinterpret_cast<void*>(native_apply_cdl_direct)},
    {"nativeApplyCdlBlendedDirect", "(Ljava/nio/ByteBuffer;IIIFFFFFFFFFFF)V", reinterpret_cast<void*>(native_apply_cdl_blended_direct)},
    {"nativeApplyTransferCurveDirect", "(Ljava/nio/ByteBuffer;IIIIF)V", reinterpret_cast<void*>(native_apply_transfer_curve_direct)},
    {"nativeApplyUnsharpMaskDirect", "(Ljava/nio/ByteBuffer;IIIFF)V", reinterpret_cast<void*>(native_apply_unsharp_mask_direct)},
    {"nativeAetherMasterEnhanceDirect", "(Ljava/nio/ByteBuffer;IIIFFFF)Z", reinterpret_cast<void*>(native_aether_master_enhance_direct)},
    {"nativeApplyVirtualGainDirect", "(Ljava/nio/ByteBuffer;IIIF)Z", reinterpret_cast<void*>(native_apply_virtual_gain_direct)},
    {"nativeFuseHdrExposureDirect", "([Ljava/nio/ByteBuffer;IIIILjava/nio/ByteBuffer;FF)Z", reinterpret_cast<void*>(native_fuse_hdr_exposure_direct)},
    {"nativeStackFramesDirect", "([Ljava/nio/ByteBuffer;IIIILjava/nio/ByteBuffer;Z)Z", reinterpret_cast<void*>(native_stack_frames_direct)},
    {"nativeAccumulateLongExposureDirect", "(Ljava/nio/ByteBuffer;Ljava/nio/ByteBuffer;IIIIIF)Z", reinterpret_cast<void*>(native_accumulate_long_exposure_direct)},
    {"nativeFinalizeLongExposureDirect", "(Ljava/nio/ByteBuffer;Ljava/nio/ByteBuffer;II)Z", reinterpret_cast<void*>(native_finalize_long_exposure_direct)},
    {"nativeBlendMultiExposureDirect", "(Ljava/nio/ByteBuffer;Ljava/nio/ByteBuffer;Ljava/nio/ByteBuffer;IIII)Z", reinterpret_cast<void*>(native_blend_multi_exposure_direct)},
    {"nativeStackFocusSlicesDirect", "([Ljava/nio/ByteBuffer;IIIILjava/nio/ByteBuffer;)Z", reinterpret_cast<void*>(native_stack_focus_slices_direct)},
    {"nativeCorrectLensAberrationDirect", "(Ljava/nio/ByteBuffer;Ljava/nio/ByteBuffer;IIIFF)Z", reinterpret_cast<void*>(native_correct_lens_aberration_direct)},
    {"nativeCalculateHyperfocalDistance", "(FFF)F", reinterpret_cast<void*>(native_calculate_hyperfocal_distance)},
    {"nativePushImuSample", "(JFFFFFF)V", reinterpret_cast<void*>(native_push_imu_sample)},
    {"nativeUpdateKalman", "(FFFF[F)V", reinterpret_cast<void*>(native_update_kalman)},
    {"nativeStartImuLogging", "()V", reinterpret_cast<void*>(native_start_imu_logging)},
    {"nativeStopAndExportGyroflow", "(Ljava/lang/String;Ljava/lang/String;D)Z", reinterpret_cast<void*>(native_stop_and_export_gyroflow)},
    {"nativeComputeL1Path", "([F[FF)V", reinterpret_cast<void*>(native_compute_l1_path)},
    {"nativePushBestShotVelocity", "(FFF)V", reinterpret_cast<void*>(native_push_best_shot_velocity)},
    {"nativeEvaluateBestShot", "(FFF)F", reinterpret_cast<void*>(native_evaluate_best_shot)},
    {"nativeKeyframeReset", "()V", reinterpret_cast<void*>(native_keyframe_reset)},
    {"nativeKeyframeAddPoint", "(IFFI)V", reinterpret_cast<void*>(native_keyframe_add_point)},
    {"nativeKeyframeStart", "(F)V", reinterpret_cast<void*>(native_keyframe_start)},
    {"nativeKeyframeStop", "()V", reinterpret_cast<void*>(native_keyframe_stop)},
    {"nativeKeyframeTick", "(F[F)Z", reinterpret_cast<void*>(native_keyframe_tick)}
};

JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void*) {
    JNIEnv* env = nullptr;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }

    jclass telemetryCls = env->FindClass("com/txor/commix/bridge/NativeTelemetryBridge");
    if (!telemetryCls) {
        LOGE("JNI_OnLoad failed: NativeTelemetryBridge class not found");
        return JNI_ERR;
    }
    if (env->RegisterNatives(telemetryCls, g_telemetry_methods, sizeof(g_telemetry_methods) / sizeof(g_telemetry_methods[0])) != JNI_OK) {
        LOGE("JNI_OnLoad failed: RegisterNatives failed for NativeTelemetryBridge");
        env->DeleteLocalRef(telemetryCls);
        return JNI_ERR;
    }
    env->DeleteLocalRef(telemetryCls);

    jclass engineCls = env->FindClass("com/txor/commix/bridge/NativeEngineBridge");
    if (!engineCls) {
        LOGE("JNI_OnLoad failed: NativeEngineBridge class not found");
        return JNI_ERR;
    }
    if (env->RegisterNatives(engineCls, g_engine_methods, sizeof(g_engine_methods) / sizeof(g_engine_methods[0])) != JNI_OK) {
        LOGE("JNI_OnLoad failed: RegisterNatives failed for NativeEngineBridge");
        env->DeleteLocalRef(engineCls);
        return JNI_ERR;
    }
    env->DeleteLocalRef(engineCls);

    commix::ImagePipeline::initToneLuts();
    LOGI("Commix C++20 Aether Engine initialized (%zu telemetry + %zu engine natives registered).",
         sizeof(g_telemetry_methods) / sizeof(g_telemetry_methods[0]),
         sizeof(g_engine_methods) / sizeof(g_engine_methods[0]));
    return JNI_VERSION_1_6;
}
