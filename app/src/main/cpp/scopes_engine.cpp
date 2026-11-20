#include "scopes_engine.h"
#include <algorithm>
#include <cmath>
#include <cstring>

#if defined(__ARM_NEON) || defined(__aarch64__)
#include <arm_neon.h>
#endif

namespace commix {

namespace {

inline int clamp255(int v) noexcept {
    return v < 0 ? 0 : (v > 255 ? 255 : v);
}

#if defined(__ARM_NEON) || defined(__aarch64__)
// Genuine ARM64 NEON 8-pixel YUV420 -> RGB batch converter (BT.601 full-range)
inline void yuv8ToRgb8Neon(
    uint8x8_t yv,
    uint8x8_t uv,
    uint8x8_t vv,
    uint8_t outR[8],
    uint8_t outG[8],
    uint8_t outB[8]
) noexcept {
    const int16x8_t y16 = vreinterpretq_s16_u16(vmovl_u8(yv));
    const int16x8_t c128 = vdupq_n_s16(128);
    const int16x8_t u16 = vsubq_s16(vreinterpretq_s16_u16(vmovl_u8(uv)), c128);
    const int16x8_t v16 = vsubq_s16(vreinterpretq_s16_u16(vmovl_u8(vv)), c128);

    // Fixed-point BT.601 coefficients (scaled by 256):
    // R = Y + (359 * V) >> 8
    // G = Y - (88 * U + 183 * V) >> 8
    // B = Y + (454 * U) >> 8
    const int32x4_t v_lo = vmovl_s16(vget_low_s16(v16));
    const int32x4_t v_hi = vmovl_s16(vget_high_s16(v16));
    const int32x4_t u_lo = vmovl_s16(vget_low_s16(u16));
    const int32x4_t u_hi = vmovl_s16(vget_high_s16(u16));

    const int16x4_t rOff_lo = vshrn_n_s32(vmulq_n_s32(v_lo, 359), 8);
    const int16x4_t rOff_hi = vshrn_n_s32(vmulq_n_s32(v_hi, 359), 8);
    const int16x8_t r16 = vaddq_s16(y16, vcombine_s16(rOff_lo, rOff_hi));

    const int32x4_t gTerm_lo = vaddq_s32(vmulq_n_s32(u_lo, 88), vmulq_n_s32(v_lo, 183));
    const int32x4_t gTerm_hi = vaddq_s32(vmulq_n_s32(u_hi, 88), vmulq_n_s32(v_hi, 183));
    const int16x8_t g16 = vsubq_s16(y16, vcombine_s16(vshrn_n_s32(gTerm_lo, 8), vshrn_n_s32(gTerm_hi, 8)));

    const int16x4_t bOff_lo = vshrn_n_s32(vmulq_n_s32(u_lo, 454), 8);
    const int16x4_t bOff_hi = vshrn_n_s32(vmulq_n_s32(u_hi, 454), 8);
    const int16x8_t b16 = vaddq_s16(y16, vcombine_s16(bOff_lo, bOff_hi));

    vst1_u8(outR, vqmovun_s16(r16));
    vst1_u8(outG, vqmovun_s16(g16));
    vst1_u8(outB, vqmovun_s16(b16));
}
#endif

} // namespace

void compute_scopes(
    const uint8_t* __restrict__ y,
    const uint8_t* __restrict__ u,
    const uint8_t* __restrict__ v,
    int width,
    int height,
    int yStride,
    int uvStride,
    int uvPixelStride,
    int step,
    ScopeOut& out
) noexcept {
    if (!y || !u || !v || width <= 0 || height <= 0 || step < 1) return;
    if (!out.luma || !out.rBins || !out.gBins || !out.bBins ||
        !out.waveAvg || !out.waveMin || !out.waveMax ||
        !out.paradeR || !out.paradeG || !out.paradeB || !out.vscope) {
        return;
    }

    // Zero all output histograms and accumulators
    std::memset(out.luma, 0, HIST_BINS * sizeof(int32_t));
    std::memset(out.rBins, 0, HIST_BINS * sizeof(int32_t));
    std::memset(out.gBins, 0, HIST_BINS * sizeof(int32_t));
    std::memset(out.bBins, 0, HIST_BINS * sizeof(int32_t));

    float waveSum[WAVE_COLS] = {0.f};
    int32_t waveCnt[WAVE_COLS] = {0};
    float waveMinArr[WAVE_COLS];
    float waveMaxArr[WAVE_COLS];

    float parSumR[PARADE_N] = {0.f}, parSumG[PARADE_N] = {0.f}, parSumB[PARADE_N] = {0.f};
    int32_t parCnt[PARADE_N] = {0};

    for (int i = 0; i < WAVE_COLS; ++i) {
        waveMinArr[i] = 255.f;
        waveMaxArr[i] = 0.f;
    }

    int64_t spotSum = 0;
    int32_t spotCnt = 0;
    int64_t sumU = 0;
    int64_t sumV = 0;
    int32_t totalSamples = 0;
    int32_t hiClip = 0;
    int32_t loClip = 0;
    int32_t vscopeIdx = 0;

    // Central focus-peaking sharpness accumulator (horizontal + vertical gradient energy)
    int64_t gradEnergySum = 0;
    int32_t gradSampleCnt = 0;

    const int spotX0 = (width * 45) / 100;
    const int spotX1 = (width * 55) / 100;
    const int spotY0 = (height * 45) / 100;
    const int spotY1 = (height * 55) / 100;

    const int afX0 = (width * 25) / 100;
    const int afX1 = (width * 75) / 100;
    const int afY0 = (height * 25) / 100;
    const int afY1 = (height * 75) / 100;

    const int vscopeStride = std::max(1, ((width / step) * (height / step)) / VSCOPE_SAMPLES);
    int32_t vscopeTick = 0;

    const int chromaW = chroma_width_for(width);
    const int chromaH = chroma_height_for(height);

    auto accumulateSample = [&](int px, int py, int yv, int ui, int vi, int r, int g, int b) noexcept {
        out.luma[yv]++;
        out.rBins[r]++;
        out.gBins[g]++;
        out.bBins[b]++;

        if (yv >= 250) hiClip++;
        if (yv <= 5) loClip++;

        sumU += ui;
        sumV += vi;
        totalSamples++;

        if (px >= spotX0 && px < spotX1 && py >= spotY0 && py < spotY1) {
            spotSum += yv;
            spotCnt++;
        }

        const int wCol = (px * WAVE_COLS) / width;
        const float yf = static_cast<float>(yv);
        waveSum[wCol] += yf;
        waveCnt[wCol]++;
        if (yf < waveMinArr[wCol]) waveMinArr[wCol] = yf;
        if (yf > waveMaxArr[wCol]) waveMaxArr[wCol] = yf;

        const int pCol = (px * PARADE_N) / width;
        parSumR[pCol] += static_cast<float>(r);
        parSumG[pCol] += static_cast<float>(g);
        parSumB[pCol] += static_cast<float>(b);
        parCnt[pCol]++;

        if (vscopeIdx < VSCOPE_SAMPLES && (vscopeTick++ % vscopeStride == 0)) {
            out.vscope[vscopeIdx * 2]     = static_cast<float>(ui) / 128.0f;
            out.vscope[vscopeIdx * 2 + 1] = static_cast<float>(vi) / 128.0f;
            vscopeIdx++;
        }
    };

    for (int py = 0; py < height; py += step) {
        const uint8_t* yRow = y + static_cast<size_t>(py) * static_cast<size_t>(yStride);
        const uint8_t* yNextRow = (py + step < height)
            ? (y + static_cast<size_t>(py + step) * static_cast<size_t>(yStride))
            : yRow;
        const int uvRow = std::min(py >> 1, chromaH - 1);
        const uint8_t* uRow = u + static_cast<size_t>(uvRow) * static_cast<size_t>(uvStride);
        const uint8_t* vRow = v + static_cast<size_t>(uvRow) * static_cast<size_t>(uvStride);

        int px = 0;

#if defined(__ARM_NEON) || defined(__aarch64__)
        // Vectorized 8-pixel NEON fast path for contiguous luma sampling (step == 1)
        if (step == 1) {
            uint8_t uTmp[8], vTmp[8], rTmp[8], gTmp[8], bTmp[8];
            for (; px + 8 <= width; px += 8) {
                const uint8x8_t yVec = vld1_u8(yRow + px);
                for (int k = 0; k < 8; ++k) {
                    const int uvx = std::min((px + k) >> 1, chromaW - 1);
                    const size_t uvOff = static_cast<size_t>(uvx) * static_cast<size_t>(uvPixelStride);
                    uTmp[k] = uRow[uvOff];
                    vTmp[k] = vRow[uvOff];
                }
                const uint8x8_t uVec = vld1_u8(uTmp);
                const uint8x8_t vVec = vld1_u8(vTmp);
                yuv8ToRgb8Neon(yVec, uVec, vVec, rTmp, gTmp, bTmp);

                // Vectorized vertical gradient inside central AF window
                if (py >= afY0 && py < afY1 && px >= afX0 && px + 8 <= afX1) {
                    const uint8x8_t yDown = vld1_u8(yNextRow + px);
                    const uint8x8_t dy = vabd_u8(yVec, yDown);
                    const uint16x4_t dy16 = vpaddl_u8(dy);
                    const uint32x2_t dy32 = vpaddl_u16(dy16);
                    gradEnergySum += static_cast<int64_t>(vget_lane_u32(dy32, 0)) +
                                     static_cast<int64_t>(vget_lane_u32(dy32, 1));
                    gradSampleCnt += 8;
                }

                for (int k = 0; k < 8; ++k) {
                    const int cx = px + k;
                    const int yv = yRow[cx];
                    const int ui = static_cast<int>(uTmp[k]) - 128;
                    const int vi = static_cast<int>(vTmp[k]) - 128;
                    accumulateSample(cx, py, yv, ui, vi, rTmp[k], gTmp[k], bTmp[k]);
                }
            }
        }
#endif

        for (; px < width; px += step) {
            const int yv = yRow[px];
            const int uvx = std::min(px >> 1, chromaW - 1);
            const size_t uvOffset = static_cast<size_t>(uvx) * static_cast<size_t>(uvPixelStride);

            const int ui = static_cast<int>(uRow[uvOffset]) - 128;
            const int vi = static_cast<int>(vRow[uvOffset]) - 128;

            // Fixed-point BT.601 YUV -> RGB conversion
            const int r = clamp255(yv + ((359 * vi) >> 8));
            const int g = clamp255(yv - ((88 * ui + 183 * vi) >> 8));
            const int b = clamp255(yv + ((454 * ui) >> 8));

            if (py >= afY0 && py < afY1 && px >= afX0 && px < afX1) {
                const int rightX = std::min(px + step, width - 1);
                const int gx = std::abs(static_cast<int>(yRow[rightX]) - yv);
                const int gy = std::abs(static_cast<int>(yNextRow[px]) - yv);
                gradEnergySum += (gx + gy) >> 1;
                gradSampleCnt++;
            }

            accumulateSample(px, py, yv, ui, vi, r, g, b);
        }
    }

    // Finalize normalized waveform & RGB parade (0..100 IRE)
    for (int i = 0; i < WAVE_COLS; ++i) {
        if (waveCnt[i] > 0) {
            out.waveAvg[i] = (waveSum[i] / waveCnt[i]) * (100.0f / 255.0f);
            out.waveMin[i] = waveMinArr[i] * (100.0f / 255.0f);
            out.waveMax[i] = waveMaxArr[i] * (100.0f / 255.0f);
        } else {
            out.waveAvg[i] = 0.f;
            out.waveMin[i] = 0.f;
            out.waveMax[i] = 0.f;
        }
    }

    for (int i = 0; i < PARADE_N; ++i) {
        if (parCnt[i] > 0) {
            const float inv = (100.0f / 255.0f) / parCnt[i];
            out.paradeR[i] = parSumR[i] * inv;
            out.paradeG[i] = parSumG[i] * inv;
            out.paradeB[i] = parSumB[i] * inv;
        } else {
            out.paradeR[i] = 0.f;
            out.paradeG[i] = 0.f;
            out.paradeB[i] = 0.f;
        }
    }

    if (totalSamples > 0) {
        out.hiClipPct = (static_cast<float>(hiClip) * 100.0f) / totalSamples;
        out.loClipPct = (static_cast<float>(loClip) * 100.0f) / totalSamples;
        out.meanU = static_cast<float>(sumU) / totalSamples;
        out.meanV = static_cast<float>(sumV) / totalSamples;
    } else {
        out.hiClipPct = 0.f;
        out.loClipPct = 0.f;
        out.meanU = 0.f;
        out.meanV = 0.f;
    }

    out.spotIre = (spotCnt > 0) ? (static_cast<float>(spotSum) / spotCnt) * (100.0f / 255.0f) : 45.0f;
    if (gradSampleCnt > 0) {
        // Normalize average luma edge gradient (0..32 -> 0..100 sharpness acuity score)
        const float avgGrad = static_cast<float>(gradEnergySum) / static_cast<float>(gradSampleCnt);
        out.focusPeakScore = std::clamp((avgGrad / 32.0f) * 100.0f, 0.0f, 100.0f);
    } else {
        out.focusPeakScore = 0.0f;
    }
    out.vscopeCount = vscopeIdx;
}

} // namespace commix
