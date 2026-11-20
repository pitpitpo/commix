#include <cassert>
#include <cmath>
#include <cstdio>
#include <cstring>
#include <iostream>
#include <vector>
#include <thread>
#include <atomic>
#include <chrono>
#include <cstdlib>
#include <unistd.h>

#include "../../main/cpp/computational_photo.h"
#include "../../main/cpp/stabilization_engine.h"
#include "../../main/cpp/scopes_engine.h"
#include "../../main/cpp/best_shot_engine.h"
#include "../../main/cpp/keyframe_engine.h"
#include "../../main/cpp/commix_telemetry.h"
#include "../../main/cpp/color_science.h"
#include "../../main/cpp/image_pipeline.h"
#include "../../main/cpp/thread_pool.hpp"

using namespace commix;

void test_hyperfocal_calculation() {
    std::cout << "[RUN] test_hyperfocal_calculation..." << std::endl;
    // Standard 50mm, f/2.8, CoC = 0.03mm (Full Frame)
    // H = (0.05 * 0.05) / (2.8 * 0.00003) + 0.05 = 29.8119m
    float h = ComputationalPhotoEngine::calculateHyperfocalDistance(50.0f, 2.8f, 0.03f);
    assert(std::abs(h - 29.8119f) < 0.05f);

    // Guard test: negative or zero focal length must return 0.0f
    assert(ComputationalPhotoEngine::calculateHyperfocalDistance(0.0f, 2.8f, 0.03f) == 0.0f);
    assert(ComputationalPhotoEngine::calculateHyperfocalDistance(-50.0f, 2.8f, 0.03f) == 0.0f);

    // Guard test: non-positive fNumber or circleOfConfusion
    assert(ComputationalPhotoEngine::calculateHyperfocalDistance(50.0f, 0.0f, 0.03f) == 0.0f);
    assert(ComputationalPhotoEngine::calculateHyperfocalDistance(50.0f, -2.8f, 0.03f) == 0.0f);
    assert(ComputationalPhotoEngine::calculateHyperfocalDistance(50.0f, 2.8f, 0.0f) == 0.0f);
    assert(ComputationalPhotoEngine::calculateHyperfocalDistance(50.0f, 2.8f, -0.03f) == 0.0f);
    std::cout << "  [PASS] Hyperfocal distance calculation & bounds validation." << std::endl;
}

void test_astro_stacking_median() {
    std::cout << "[RUN] test_astro_stacking_median..." << std::endl;
    // 3 frames, 2x2 pixels, 3 channels (RGB)
    const int w = 2, h = 2, c = 3;
    const size_t totalBytes = w * h * c;

    std::vector<uint8_t> frame0(totalBytes, 10);
    std::vector<uint8_t> frame1(totalBytes, 90);
    std::vector<uint8_t> frame2(totalBytes, 40);

    const uint8_t* frames[3] = { frame0.data(), frame1.data(), frame2.data() };
    std::vector<uint8_t> out(totalBytes, 0);

    bool ok = ComputationalPhotoEngine::stackAstroFrames(frames, 3, w, h, c, out.data(), false);
    assert(ok);
    // Median of [10, 40, 90] is 40
    for (size_t i = 0; i < totalBytes; ++i) {
        assert(out[i] == 40);
    }

    // Invalid parameters guard test
    assert(!ComputationalPhotoEngine::stackAstroFrames(nullptr, 3, w, h, c, out.data(), false));
    assert(!ComputationalPhotoEngine::stackAstroFrames(frames, 0, w, h, c, out.data(), false));
    assert(!ComputationalPhotoEngine::stackAstroFrames(frames, 3, 0, h, c, out.data(), false));
    assert(!ComputationalPhotoEngine::stackAstroFrames(frames, 3, w, h, 2, out.data(), false));
    std::cout << "  [PASS] Astro median stacking & input bounds validation." << std::endl;
}

void test_long_exposure_modes() {
    std::cout << "[RUN] test_long_exposure_modes..." << std::endl;
    const int w = 2, h = 2, c = 3;
    const size_t totalPixels = w * h * c;

    std::vector<uint8_t> f0(totalPixels, 100);
    std::vector<uint8_t> f1(totalPixels, 50);
    std::vector<float> acc(totalPixels, 0.0f);

    // Frame 0 initialization
    bool ok0 = ComputationalPhotoEngine::accumulateLongExposure(f0.data(), acc.data(), w, h, c, 0, LongExposureStyle::SilkyWater, 0.5f);
    assert(ok0);
    assert(acc[0] == 100.0f);

    // Frame 1 with SilkyWater (alpha=0.5 -> 0.5 * 100 + 0.5 * 50 = 75)
    bool ok1 = ComputationalPhotoEngine::accumulateLongExposure(f1.data(), acc.data(), w, h, c, 1, LongExposureStyle::SilkyWater, 0.5f);
    assert(ok1);
    assert(std::abs(acc[0] - 75.0f) < 0.01f);

    // LightTrails (max blending: max(75, 120) = 120)
    std::vector<uint8_t> f2(totalPixels, 120);
    bool ok2 = ComputationalPhotoEngine::accumulateLongExposure(f2.data(), acc.data(), w, h, c, 2, LongExposureStyle::LightTrails, 0.5f);
    assert(ok2);
    assert(std::abs(acc[0] - 120.0f) < 0.01f);

    std::vector<uint8_t> finalized(totalPixels, 0);
    ComputationalPhotoEngine::finalizeLongExposure(acc.data(), finalized.data(), w * h, c);
    assert(finalized[0] == 120);
    std::cout << "  [PASS] Long exposure accumulation (SilkyWater & LightTrails)." << std::endl;
}

void test_multi_exposure_blend_modes() {
    std::cout << "[RUN] test_multi_exposure_blend_modes..." << std::endl;
    const int w = 2, h = 2, c = 3;
    const size_t total = w * h * c;

    std::vector<uint8_t> base(total, 200);
    std::vector<uint8_t> blend(total, 100);
    std::vector<uint8_t> out(total, 0);

    // Additive: 200 + 100 = 300 -> clamped to 255
    ComputationalPhotoEngine::blendMultiExposure(base.data(), blend.data(), out.data(), w, h, c, MultiExposureBlendMode::Additive);
    assert(out[0] == 255);

    // Average: (200 + 100) / 2 = 150
    ComputationalPhotoEngine::blendMultiExposure(base.data(), blend.data(), out.data(), w, h, c, MultiExposureBlendMode::Average);
    assert(out[0] == 150);

    // Darken: min(200, 100) = 100
    ComputationalPhotoEngine::blendMultiExposure(base.data(), blend.data(), out.data(), w, h, c, MultiExposureBlendMode::Darken);
    assert(out[0] == 100);

    // Lighten: max(200, 100) = 200
    ComputationalPhotoEngine::blendMultiExposure(base.data(), blend.data(), out.data(), w, h, c, MultiExposureBlendMode::Lighten);
    assert(out[0] == 200);
    std::cout << "  [PASS] Multi-exposure blend modes (Additive, Average, Darken, Lighten)." << std::endl;
}

void test_stabilization_engine() {
    std::cout << "[RUN] test_stabilization_engine..." << std::endl;
    auto& stab = StabilizationEngine::instance();

    // Push samples
    for (int i = 0; i < 100; ++i) {
        stab.pushImuSample(i * 1000000LL, 0.01f, -0.02f, 0.0f, 0.0f, 9.8f, 0.0f);
    }

    stab.startLogging();
    for (int i = 100; i < 200; ++i) {
        stab.pushImuSample(i * 1000000LL, 0.02f, -0.01f, 0.0f, 0.0f, 9.8f, 0.0f);
    }

    // Guard test: nullptr outputPath must return false without crash
    assert(!stab.stopAndExportGyroflow(nullptr, "TestCam", 30.0));
    assert(!stab.stopAndExportGyroflow("", "TestCam", 30.0));

    // Valid export
    char tmpFile[64];
    std::snprintf(tmpFile, sizeof(tmpFile), "/tmp/commix_gyroflow_test_%d.csv", static_cast<int>(::getpid()));
    bool exported = stab.stopAndExportGyroflow(tmpFile, "TestCam", 30.0);
    assert(exported);

    FILE* fp = std::fopen(tmpFile, "r");
    assert(fp != nullptr);
    char line[128];
    char* l1 = std::fgets(line, sizeof(line), fp);
    assert(l1 != nullptr && std::strncmp(line, "GYROFLOW IMU LOG", 16) == 0);
    char* l2 = std::fgets(line, sizeof(line), fp);
    assert(l2 != nullptr && std::strncmp(line, "version,1.3", 11) == 0);
    char* l3 = std::fgets(line, sizeof(line), fp);
    assert(l3 != nullptr && std::strncmp(line, "id,commix", 9) == 0);
    char* l4 = std::fgets(line, sizeof(line), fp);
    assert(l4 != nullptr && std::strncmp(line, "orientation,YxZ", 15) == 0);
    char* l5 = std::fgets(line, sizeof(line), fp);
    assert(l5 != nullptr && std::strncmp(line, "vendor,commix", 13) == 0);
    char* l6 = std::fgets(line, sizeof(line), fp);
    assert(l6 != nullptr && std::strncmp(line, "camera_name,TestCam", 19) == 0);
    char* l7 = std::fgets(line, sizeof(line), fp);
    assert(l7 != nullptr && std::strncmp(line, "frame_rate,30.00", 16) == 0);
    char* l8 = std::fgets(line, sizeof(line), fp);
    assert(l8 != nullptr && std::strncmp(line, "tscale,1", 8) == 0);
    char* l9 = std::fgets(line, sizeof(line), fp);
    assert(l9 != nullptr && std::strncmp(line, "gscale,1", 8) == 0);
    char* l10 = std::fgets(line, sizeof(line), fp);
    assert(l10 != nullptr && std::strncmp(line, "ascale,", 7) == 0);
    char* l11 = std::fgets(line, sizeof(line), fp);
    assert(l11 != nullptr && std::strncmp(line, "t,gx,gy,gz,ax,ay,az", 19) == 0);

    char* lData = std::fgets(line, sizeof(line), fp);
    assert(lData != nullptr);
    double t = 0.0, gx = 0.0, gy = 0.0, gz = 0.0, ax = 0.0, ay = 0.0, az = 0.0;
    int n = std::sscanf(lData, "%lf,%lf,%lf,%lf,%lf,%lf,%lf", &t, &gx, &gy, &gz, &ax, &ay, &az);
    assert(n == 7);
    assert(std::abs(t - 0.0) < 1e-6);
    assert(std::abs(gx - 0.02) < 1e-5);
    assert(std::abs(gy - (-0.01)) < 1e-5);
    assert(std::abs(ay - 9.8) < 1e-5);

    int sampleRows = 1;
    double prevT = t;
    while (std::fgets(line, sizeof(line), fp)) {
        n = std::sscanf(line, "%lf,%lf,%lf,%lf,%lf,%lf,%lf", &t, &gx, &gy, &gz, &ax, &ay, &az);
        if (n == 7) {
            sampleRows++;
            assert(t >= prevT);
            assert(std::abs(gx - 0.02) < 1e-5);
            assert(std::abs(gy - (-0.01)) < 1e-5);
            assert(std::abs(ay - 9.8) < 1e-5);
            prevT = t;
        }
    }
    assert(sampleRows == 100);

    std::fclose(fp);
    std::remove(tmpFile);

    // Kalman filter test
    float p = 0.f, r = 0.f, y = 0.f;
    for (int i = 0; i < 20; ++i) {
        stab.updateKalman(1.0f, 2.0f, 3.0f, 0.016f, p, r, y);
    }
    // Should have converged towards 1.0, 2.0, 3.0
    assert(p > 0.8f && p <= 1.0f);
    assert(r > 1.6f && r <= 2.0f);
    assert(y > 2.4f && y <= 3.0f);
    std::cout << "  [PASS] Stabilization engine IMU ring buffer, Gyroflow export & Kalman filter." << std::endl;
}

void test_scopes_engine_neutral_and_bounds() {
    std::cout << "[RUN] test_scopes_engine_neutral_and_bounds..." << std::endl;
    const int w = 8, h = 8;
    std::vector<uint8_t> yPlane(w * h, 128); // Neutral grey 50%
    std::vector<uint8_t> uPlane((w / 2) * (h / 2), 128);
    std::vector<uint8_t> vPlane((w / 2) * (h / 2), 128);

    int32_t luma[HIST_BINS];
    int32_t rBins[HIST_BINS], gBins[HIST_BINS], bBins[HIST_BINS];
    float waveAvg[WAVE_COLS], waveMin[WAVE_COLS], waveMax[WAVE_COLS];
    float paradeR[PARADE_N], paradeG[PARADE_N], paradeB[PARADE_N];
    float vscope[VSCOPE_SAMPLES * 2];

    ScopeOut out;
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

    compute_scopes(yPlane.data(), uPlane.data(), vPlane.data(), w, h, w, w / 2, 1, 1, out);

    // All pixels are Y=128
    assert(out.luma[128] == w * h);
    assert(out.hiClipPct == 0.0f);
    assert(out.loClipPct == 0.0f);
    assert(std::abs(out.spotIre - 50.196f) < 1.0f);

    // Verify vectorscope coordinate mapping: X is B-Y (ui / 128.0), Y is R-Y (vi / 128.0)
    {
        std::vector<uint8_t> uCustom((w / 2) * (h / 2), 192); // ui = +64 -> 0.5f
        std::vector<uint8_t> vCustom((w / 2) * (h / 2), 64);  // vi = -64 -> -0.5f
        compute_scopes(yPlane.data(), uCustom.data(), vCustom.data(), w, h, w, w / 2, 1, 1, out);
        assert(out.vscopeCount > 0);
        assert(std::fabs(out.vscope[0] - 0.5f) < 1e-3f);
        assert(std::fabs(out.vscope[1] - (-0.5f)) < 1e-3f);
    }

    // Degraded buffer guard: width <= 0 or null pointers must return cleanly
    compute_scopes(nullptr, uPlane.data(), vPlane.data(), w, h, w, w / 2, 1, 1, out);
    compute_scopes(yPlane.data(), uPlane.data(), vPlane.data(), 0, 0, w, w / 2, 1, 1, out);
    std::cout << "  [PASS] Scopes engine neutral grey IRE & input bounds validation." << std::endl;
}

void test_telemetry_seqlock() {
    std::cout << "[RUN] test_telemetry_seqlock..." << std::endl;
    // Write audio and verify consistent seqlock sequence counter
    commix_write_audio(0.75f, -6.0f, -6.5f, -5.8f);
    auto* tb = commix_get_telemetry_buffer();
    assert(tb != nullptr);
    assert(tb->seq_begin.load() == tb->seq_end.load());
    assert((tb->seq_begin.load() & 1u) == 0); // Must be even (completed)
    assert(std::abs(tb->audio_norm - 0.75f) < 0.001f);
    assert(std::abs(tb->audio_db - -6.0f) < 0.001f);

    // While write_begin is in progress, sequence must be odd
    commix_telemetry_write_begin();
    assert(tb->seq_begin.load() & 1u);
    assert(tb->seq_end.load() & 1u); // Both begin and end must be odd during transaction
    commix_telemetry_write_end();
    assert((tb->seq_begin.load() & 1u) == 0);
    assert(tb->seq_begin.load() == tb->seq_end.load());

    // The assertions above seeded the buffer with four DIFFERENT values
    // (0.75 / -6.0 / -6.5 / -5.8). The reader below treats a stable snapshot
    // whose four fields differ as proof of a torn read, which is only valid if
    // every write during the measured window stores an identical value into all
    // four fields (the writer thread does: commix_write_audio(val,val,val,val)).
    // Reset the steady state to one such all-equal sample first, otherwise the
    // reader can start before the writer's first write, accept the still-pending
    // opening sample, and misreport its legitimately-different fields as a race.
    commix_write_audio(0.0f, 0.0f, 0.0f, 0.0f);

    // Concurrent reader/writer stress test:
    // Hammer buffer with high-frequency writes while reader validates seqlock contract
    std::atomic<bool> writerStop{false};
    std::atomic<size_t> tornReads{0};
    std::atomic<size_t> validReads{0};
    std::atomic<size_t> readAttempts{0};

    // The writer runs until the reader has finished, so every read below is
    // guaranteed to race an active writer. A fixed-duration writer can complete
    // before the reader is even scheduled, which would make this phase vacuous.
    std::thread writerThread([&]() {
        for (int i = 0; !writerStop.load(std::memory_order_relaxed); ++i) {
            float val = static_cast<float>(i);
            commix_write_audio(val, val, val, val);
        }
    });

    std::thread readerThread([&]() {
        auto* buf = commix_get_telemetry_buffer();
        // Fixed attempt count, all of them concurrent with the writer above.
        // Whether an attempt lands on a stable window or an in-flight write is
        // scheduler-dependent, so this phase asserts only what must always hold:
        // no accepted read ever tears. Acceptance of good data is asserted
        // deterministically after the writer stops.
        for (int i = 0; i < 100000; ++i) {
            readAttempts.fetch_add(1, std::memory_order_relaxed);
            uint32_t s1 = buf->seq_begin.load(std::memory_order_acquire);
            uint32_t e1 = buf->seq_end.load(std::memory_order_acquire);
            if ((s1 & 1u) || (s1 != e1)) continue; // In-flight write rejected correctly
            std::atomic_thread_fence(std::memory_order_seq_cst);

            float n = buf->audio_norm;
            float db = buf->audio_db;
            float l = buf->audio_left_db;
            float r = buf->audio_right_db;

            std::atomic_thread_fence(std::memory_order_seq_cst);
            uint32_t s2 = buf->seq_begin.load(std::memory_order_acquire);
            uint32_t e2 = buf->seq_end.load(std::memory_order_acquire);
            if (s1 != s2 || s1 != e2) continue; // Torn read rejected correctly

            // If accepted, all 4 fields MUST match because writer wrote identical floats
            if (n != db || n != l || n != r) {
                tornReads.fetch_add(1, std::memory_order_relaxed);
            } else {
                validReads.fetch_add(1, std::memory_order_relaxed);
            }
        }
    });

    readerThread.join();
    writerStop.store(true);
    writerThread.join();

    assert(readAttempts.load() == 100000); // the concurrency phase really ran
    assert(tornReads.load() == 0);         // Must be strictly ZERO accepted torn reads

    // Deterministic acceptance half of the contract: with the writer stopped the
    // buffer is in a completed state, and a single-threaded run of the exact
    // reader protocol above must accept it and observe the writer's last
    // all-equal sample. Asserting this here (instead of requiring validReads > 0
    // from the concurrent reader) keeps the test independent of the scheduler.
    {
        auto* buf = commix_get_telemetry_buffer();
        uint32_t s1 = buf->seq_begin.load(std::memory_order_acquire);
        uint32_t e1 = buf->seq_end.load(std::memory_order_acquire);
        assert((s1 & 1u) == 0u);           // completed, not mid-write
        assert(s1 == e1);                  // both counters agree
        const float n = buf->audio_norm;
        assert(n == buf->audio_db && n == buf->audio_left_db && n == buf->audio_right_db);
    }

    std::cout << "  [PASS] Telemetry seqlock concurrency contract ("
              << validReads.load() << " valid concurrent reads, 0 torn reads)." << std::endl;
}

void test_best_shot_engine() {
    std::cout << "[RUN] test_best_shot_engine..." << std::endl;
    BestShotEngine engine;

    // 1. Without velocity history, stability is capped at 50% and trigger can never fire
    auto s0 = engine.evaluate(nullptr, 50.0f, 0.0f, 0.0f);
    assert(s0.stabilityScore == 50.0f);
    assert(!s0.shouldTriggerShoot);

    // 2. Severe shake camera (large angular velocities) -> stability drops to 0%
    for (int i = 0; i < 10; ++i) {
        engine.pushVelocity(0.5f, 0.5f, 0.5f);
    }
    auto sShaky = engine.evaluate(nullptr, 50.0f, 0.0f, 0.0f);
    assert(sShaky.stabilityScore == 0.0f);
    assert(!sShaky.shouldTriggerShoot);

    // 3. Peak stillness (near-zero angular velocity) + good exposure + zero clipping -> trigger fires
    for (int i = 0; i < 30; ++i) {
        engine.pushVelocity(0.005f, 0.005f, 0.005f);
    }
    auto sStill = engine.evaluate(nullptr, 50.0f, 0.0f, 0.0f);
    assert(sStill.stabilityScore >= 88.0f);
    assert(sStill.totalScore >= 82.0f);
    assert(sStill.shouldTriggerShoot);

    std::cout << "  [PASS] BestShotEngine peak stillness determination & trigger contract." << std::endl;
}

void test_color_science_round_trip_and_continuity() {
    std::cout << "[RUN] test_color_science_round_trip_and_continuity..." << std::endl;
    const commix::TransferCurveType curves[] = {
        commix::TransferCurveType::ArriLogC3,
        commix::TransferCurveType::ArriLogC4,
        commix::TransferCurveType::CanonCLog2,
        commix::TransferCurveType::RedLog3G10,
        commix::TransferCurveType::SLog3
    };

    for (commix::TransferCurveType curve : curves) {
        for (int i = 0; i <= 1000; ++i) {
            float x = i / 1000.0f;
            float y = commix::ColorScienceEngine::linearToLog(x, curve);
            float xr = commix::ColorScienceEngine::logToLinear(y, curve);
            assert(std::fabs(xr - x) < 1e-3f);
        }
    }

    // Branch-cut continuity checks at the exact transition points.
    {
        constexpr float cut = 0.010591f;
        float a = commix::ColorScienceEngine::linearToLog(cut, commix::TransferCurveType::ArriLogC3);
        float b = commix::ColorScienceEngine::linearToLog(cut - 1e-6f, commix::TransferCurveType::ArriLogC3);
        assert(std::fabs(a - b) < 1e-4f);
    }
    {
        constexpr float cut = 0.0f;
        float a = commix::ColorScienceEngine::linearToLog(cut, commix::TransferCurveType::CanonCLog2);
        float b = commix::ColorScienceEngine::linearToLog(cut + 1e-6f, commix::TransferCurveType::CanonCLog2);
        assert(std::fabs(a - b) < 1e-4f);
    }
    {
        constexpr float cut = -0.018056996f;
        float a = commix::ColorScienceEngine::linearToLog(cut, commix::TransferCurveType::ArriLogC4);
        float b = commix::ColorScienceEngine::linearToLog(cut - 1e-6f, commix::TransferCurveType::ArriLogC4);
        assert(std::fabs(a - b) < 1e-3f);
    }
    {
        constexpr float cut = -0.01f;
        float a = commix::ColorScienceEngine::linearToLog(cut, commix::TransferCurveType::RedLog3G10);
        float b = commix::ColorScienceEngine::linearToLog(cut + 1e-6f, commix::TransferCurveType::RedLog3G10);
        assert(std::fabs(a - b) < 1e-3f);
    }
    {
        constexpr float cut = 0.01125f;
        float a = commix::ColorScienceEngine::linearToLog(cut, commix::TransferCurveType::SLog3);
        float b = commix::ColorScienceEngine::linearToLog(cut - 1e-6f, commix::TransferCurveType::SLog3);
        assert(std::fabs(a - b) < 1e-4f);
    }

    // CDL grading verification
    {
        uint8_t cdlPixels[3] = { 64, 128, 192 };
        AscCdlParams cdl;
        cdl.slopeR = 2.0f;
        cdl.offsetR = 0.0f;
        cdl.powerR = 1.0f;
        cdl.slopeG = 1.0f;
        cdl.slopeB = 1.0f;
        cdl.saturation = 1.0f;

        ColorScienceEngine::applyCdl(nullptr, 1, 1, 3, cdl);
        ColorScienceEngine::applyCdl(cdlPixels, 0, 1, 3, cdl);
        ColorScienceEngine::applyCdl(cdlPixels, 1, 1, 2, cdl);

        ColorScienceEngine::applyCdl(cdlPixels, 1, 1, 3, cdl);
        assert(std::abs(static_cast<int>(cdlPixels[0]) - 128) <= 1);
        assert(cdlPixels[1] == 128);
        assert(cdlPixels[2] == 192);
    }

    std::cout << "  [PASS] Color science round-trip, inversion & branch continuity." << std::endl;
}

void test_image_pipeline_basic_operations() {
    std::cout << "[RUN] test_image_pipeline_basic_operations..." << std::endl;

    // 1. Init tone LUTs is idempotent
    ImagePipeline::initToneLuts();
    ImagePipeline::initToneLuts();

    // 2. ProcessZero input validation guards
    const int w = 16, h = 16, c = 3;
    const size_t totalBytes = static_cast<size_t>(w) * h * c;
    std::vector<uint8_t> buffer(totalBytes, 128);

    assert(!ImagePipeline::processZero(nullptr, w, h, c, ToneCurvePreset::Linear, GrainPreset::Off, 0.0f));
    assert(!ImagePipeline::processZero(buffer.data(), 0, h, c, ToneCurvePreset::Linear, GrainPreset::Off, 0.0f));
    assert(!ImagePipeline::processZero(buffer.data(), -1, h, c, ToneCurvePreset::Linear, GrainPreset::Off, 0.0f));
    assert(!ImagePipeline::processZero(buffer.data(), w, 0, c, ToneCurvePreset::Linear, GrainPreset::Off, 0.0f));
    assert(!ImagePipeline::processZero(buffer.data(), w, -1, c, ToneCurvePreset::Linear, GrainPreset::Off, 0.0f));
    assert(!ImagePipeline::processZero(buffer.data(), w, h, 2, ToneCurvePreset::Linear, GrainPreset::Off, 0.0f));

    // 3. ProcessZero curves: Linear, FilmPrint, CineD, Neutral
    std::vector<uint8_t> linearBuf(totalBytes, 128);
    bool okLinear = ImagePipeline::processZero(linearBuf.data(), w, h, c, ToneCurvePreset::Linear, GrainPreset::Off, 0.0f);
    assert(okLinear);
    assert(linearBuf[0] == 128);

    std::vector<uint8_t> filmBuf(totalBytes, 128);
    bool okFilm = ImagePipeline::processZero(filmBuf.data(), w, h, c, ToneCurvePreset::FilmPrint, GrainPreset::Off, 0.0f);
    assert(okFilm);
    assert(filmBuf[0] != 128);

    std::vector<uint8_t> cineBuf(totalBytes, 128);
    bool okCine = ImagePipeline::processZero(cineBuf.data(), w, h, c, ToneCurvePreset::CineD, GrainPreset::Off, 0.0f);
    assert(okCine);
    assert(cineBuf[0] != 128);

    std::vector<uint8_t> neutralBuf(totalBytes, 128);
    bool okNeutral = ImagePipeline::processZero(neutralBuf.data(), w, h, c, ToneCurvePreset::Neutral, GrainPreset::Off, 0.0f);
    assert(okNeutral);
    assert(neutralBuf[0] != 128);

    // 4. ProcessZero grain simulation
    std::vector<uint8_t> grainBuf(totalBytes, 128);
    bool okGrain = ImagePipeline::processZero(grainBuf.data(), w, h, c, ToneCurvePreset::Linear, GrainPreset::Fine35mm, 1.0f);
    assert(okGrain);
    bool anyDiff = false;
    for (size_t i = 0; i < totalBytes; ++i) {
        if (grainBuf[i] != 128) {
            anyDiff = true;
            break;
        }
    }
    assert(anyDiff);

    // 5. Unsharp mask input validation guards
    ImagePipeline::applyUnsharpMask(nullptr, w, h, c, 1.0f, 1.0f);
    ImagePipeline::applyUnsharpMask(buffer.data(), 2, h, c, 1.0f, 1.0f);
    ImagePipeline::applyUnsharpMask(buffer.data(), w, 2, c, 1.0f, 1.0f);
    ImagePipeline::applyUnsharpMask(buffer.data(), w, h, 2, 1.0f, 1.0f);
    ImagePipeline::applyUnsharpMask(buffer.data(), w, h, c, 0.0f, 1.0f);

    // 6. Unsharp mask sharpening effect on edge
    std::vector<uint8_t> sharpBuf(totalBytes, 100);
    const size_t centerIdx = (8 * w + 8) * c;
    sharpBuf[centerIdx + 0] = 200;
    sharpBuf[centerIdx + 1] = 200;
    sharpBuf[centerIdx + 2] = 200;

    ImagePipeline::applyUnsharpMask(sharpBuf.data(), w, h, c, 1.0f, 1.0f);
    assert(sharpBuf[centerIdx + 0] > 200);
    assert(sharpBuf[centerIdx + 1] > 200);
    assert(sharpBuf[centerIdx + 2] > 200);

    // 7. The `radius` parameter must actually shape the blur kernel. On code
    //    that ignores radius (fixed 3x3 Laplacian) both runs below are
    //    bit-identical; here a wider radius must touch strictly more pixels.
    //    Non-saturating field (100) with a 200 impulse is used so the result is
    //    a real convolution rather than a clamp to white.
    {
        const int rw = 64, rh = 64, rc = 3;
        const size_t rTotal = static_cast<size_t>(rw) * rh * rc;
        const size_t rCenter = (32 * rw + 32) * rc;

        std::vector<uint8_t> base(rTotal, 100);
        base[rCenter + 0] = 200;
        base[rCenter + 1] = 200;
        base[rCenter + 2] = 200;

        std::vector<uint8_t> smallRadius = base;
        std::vector<uint8_t> largeRadius = base;
        ImagePipeline::applyUnsharpMask(smallRadius.data(), rw, rh, rc, 1.0f, 1.0f);
        ImagePipeline::applyUnsharpMask(largeRadius.data(), rw, rh, rc, 1.0f, 8.0f);

        size_t differ = 0, touchedSmall = 0, touchedLarge = 0;
        for (size_t p = 0; p < static_cast<size_t>(rw) * rh; ++p) {
            if (smallRadius[p * rc] != largeRadius[p * rc]) ++differ;
            if (smallRadius[p * rc] != base[p * rc]) ++touchedSmall;
            if (largeRadius[p * rc] != base[p * rc]) ++touchedLarge;
        }
        assert(differ > 0);          // radius is consumed, not ignored
        assert(touchedLarge > touchedSmall); // wider radius spreads the halo wider
    }

    std::cout << "  [PASS] ImagePipeline tone mapping, grain simulation & unsharp mask operations." << std::endl;
}

void test_enhanced_aether_engine_features() {
    std::cout << "[RUN] test_enhanced_aether_engine_features..." << std::endl;

    // 1. Focus peaking acuity: flat image ~0 vs sharp checkerboard > 15
    {
        const int w = 16, h = 16;
        std::vector<uint8_t> flatY(w * h, 128);
        std::vector<uint8_t> edgeY(w * h, 0);
        for (int r = 0; r < h; ++r) {
            for (int c = 0; c < w; ++c) {
                edgeY[r * w + c] = ((r + c) & 1) ? 235 : 20;
            }
        }
        std::vector<uint8_t> uv((w / 2) * (h / 2), 128);
        int32_t luma[HIST_BINS], rb[HIST_BINS], gb[HIST_BINS], bb[HIST_BINS];
        float wa[WAVE_COLS], wn[WAVE_COLS], wx[WAVE_COLS];
        float pr[PARADE_N], pg[PARADE_N], pb[PARADE_N], vs[VSCOPE_SAMPLES * 2];
        ScopeOut out{luma, rb, gb, bb, wa, wn, wx, pr, pg, pb, vs, 0, 0.f, 0.f, 0.f, 0.f};

        compute_scopes(flatY.data(), uv.data(), uv.data(), w, h, w, w / 2, 1, 1, out);
        assert(out.focusPeakScore < 0.5f);

        compute_scopes(edgeY.data(), uv.data(), uv.data(), w, h, w, w / 2, 1, 1, out);
        assert(out.focusPeakScore > 15.0f);
    }

    // 2. BestShotEngine real histogram contrast scoring (wide spread > flat spike)
    {
        BestShotEngine bs;
        int32_t flatHist[256] = {0};
        flatHist[128] = 1000;
        int32_t wideHist[256] = {0};
        for (int i = 20; i <= 230; i += 10) wideHist[i] = 50;

        auto sFlat = bs.evaluate(flatHist, 50.0f, 0.0f, 0.0f);
        auto sWide = bs.evaluate(wideHist, 50.0f, 0.0f, 0.0f);
        assert(sWide.contrastScore > sFlat.contrastScore + 30.0f);
    }

    // 3. ColorScienceEngine applyTransferCurve + blended applyCdl + remaining logToLinear curves
    {
        const TransferCurveType extraCurves[] = {
            TransferCurveType::Linear,
            TransferCurveType::Rec709,
            TransferCurveType::HLG,
            TransferCurveType::PQ_ST2084
        };
        for (auto curve : extraCurves) {
            float x = 0.25f;
            float y = ColorScienceEngine::linearToLog(x, curve);
            float xr = ColorScienceEngine::logToLinear(y, curve);
            assert(std::fabs(xr - x) < 1e-2f);
        }

        std::vector<uint8_t> px(16 * 16 * 3, 128);
        ColorScienceEngine::applyTransferCurve(px.data(), 16, 16, 3, TransferCurveType::SLog3, 1.0f);
        assert(px[0] != 128);

        uint8_t blendPx[3] = {100, 100, 100};
        AscCdlParams cdl;
        cdl.slopeR = 2.0f;
        ColorScienceEngine::applyCdl(blendPx, 1, 1, 3, cdl, 0.5f);
        // 50% blend between 100 and 200 = 150
        assert(std::abs(static_cast<int>(blendPx[0]) - 150) <= 2);
    }

    // 4. Focus stacking selects the sharp slice over blurry slices
    {
        const int w = 16, h = 16, c = 3;
        std::vector<uint8_t> blurry(w * h * c, 120);
        std::vector<uint8_t> sharp(w * h * c, 120);
        for (int r = 4; r < 12; ++r) {
            for (int col = 4; col < 12; ++col) {
                uint8_t v = ((r + col) & 1) ? 240 : 10;
                sharp[(r * w + col) * c + 0] = v;
                sharp[(r * w + col) * c + 1] = v;
                sharp[(r * w + col) * c + 2] = v;
            }
        }
        const uint8_t* slices[2] = {blurry.data(), sharp.data()};
        std::vector<uint8_t> out(w * h * c, 0);
        assert(ComputationalPhotoEngine::stackFocusSlices(slices, 2, w, h, c, out.data()));
        // Center pixel of sharp region must come from sharp slice (240 or 10, not 120)
        assert(out[(8 * w + 8) * c] != 120);
    }

    // 5. Lens aberration bilinear correction preserves center pixel
    {
        const int w = 16, h = 16, c = 3;
        std::vector<uint8_t> src(w * h * c, 180);
        std::vector<uint8_t> dst(w * h * c, 0);
        assert(ComputationalPhotoEngine::correctLensAberration(src.data(), dst.data(), w, h, c, -0.08f, 0.02f));
        assert(dst[(8 * w + 8) * c] == 180);
    }

    // 6. Mertens HDR Exposure Fusion recovers both shadow and highlight regions from brackets
    {
        const int w = 16, h = 16, c = 3;
        std::vector<uint8_t> under(w * h * c, 0);
        std::vector<uint8_t> mid(w * h * c, 0);
        std::vector<uint8_t> over(w * h * c, 0);
        for (int r = 0; r < h; ++r) {
            for (int col = 0; col < w; ++col) {
                const size_t idx = (static_cast<size_t>(r) * w + col) * c;
                const bool brightHalf = (col >= w / 2);
                // Left half is dark shadow (well-exposed only in over-exposed bracket)
                // Right half is bright highlight (well-exposed only in under-exposed bracket)
                const uint8_t uVal = brightHalf ? 140 : 10;
                const uint8_t mVal = brightHalf ? 245 : 40;
                const uint8_t oVal = brightHalf ? 255 : 135;
                under[idx] = under[idx + 1] = under[idx + 2] = uVal;
                mid[idx]   = mid[idx + 1]   = mid[idx + 2]   = mVal;
                over[idx]  = over[idx + 1]  = over[idx + 2]  = oVal;
            }
        }
        const uint8_t* brackets[3] = { under.data(), mid.data(), over.data() };
        std::vector<uint8_t> fused(w * h * c, 0);
        assert(ComputationalPhotoEngine::fuseHdrExposure(brackets, 3, w, h, c, fused.data(), 0.25f, 0.35f));
        const uint8_t shadowOut = fused[(8 * w + 2) * c];
        const uint8_t highlightOut = fused[(8 * w + 12) * c];
        // Shadow must be lifted well above mid-bracket's 40; highlight must be recovered well below 245
        assert(shadowOut > 75);
        assert(highlightOut < 210);
    }

    // 7. Aether Master Enhance preserves edges while suppressing flat noise
    {
        const int w = 16, h = 16, c = 3;
        std::vector<uint8_t> noisy(w * h * c, 100);
        noisy[(8 * w + 8) * c + 0] = 112;
        noisy[(8 * w + 8) * c + 1] = 112;
        noisy[(8 * w + 8) * c + 2] = 112;
        assert(ImagePipeline::aetherMasterEnhance(noisy.data(), w, h, c, 0.7f, 0.2f, 0.0f, 0.2f));
        // Isolated small noise impulse should be attenuated toward surroundings
        assert(noisy[(8 * w + 8) * c] < 112);
    }

    // 8. Virtual Extended Gain lifts dark pixels in linear light while protecting highlights
    {
        const int w = 8, h = 8, c = 3;
        std::vector<uint8_t> img(w * h * c, 60);
        img[0] = 240; img[1] = 240; img[2] = 240;
        assert(ImagePipeline::applyVirtualGainAndTone(img.data(), w, h, c, 4.0f));
        // Dark pixel (60) lifted substantially; highlight pixel (240) rolls off smoothly without wrap
        assert(img[(4 * w + 4) * c] > 110);
        assert(img[0] >= 240 && img[0] <= 255);
    }

    std::cout << "  [PASS] Enhanced Aether engine features (focus peak, contrast, transfer curves, focus stack, lens corr, HDR fusion, master enhance, virtual gain)." << std::endl;
}

void test_process_zero_never_waits_on_dropped_work() {
    std::cout << "[RUN] test_process_zero_never_waits_on_dropped_work..." << std::endl;

    // Contract: processZero() splits the image across the shared pool and must
    // return once every slice is accounted for. If enqueue() drops a slice (the
    // pool has been shut down) that slice must still be counted, otherwise the
    // completion wait can never reach its target and the call hangs forever.
    // The pre-latch implementation waited on a plain counter that dropped work
    // could never increment, so this call blocked indefinitely.
    //
    // This runs dead last: ThreadPool is a process-wide singleton and shutting it
    // down is irreversible.
    ThreadPool::instance().shutdown();

    std::atomic<bool> returned{false};
    std::atomic<bool> ok{false};
    std::thread worker([&]() {
        std::vector<uint8_t> buf(64 * 64 * 3, 128);
        ok.store(ImagePipeline::processZero(buf.data(), 64, 64, 3,
                                            ToneCurvePreset::Linear,
                                            GrainPreset::Off, 0.0f));
        returned.store(true);
    });

    for (int i = 0; i < 500 && !returned.load(); ++i) {
        std::this_thread::sleep_for(std::chrono::milliseconds(10));
    }
    if (!returned.load()) {
        std::cout << "  [FAIL] processZero blocked on a completion count that can "
                     "never reach its target after the pool dropped the work."
                  << std::endl;
        std::fflush(stdout);
        std::abort(); // non-zero exit for the test runner
    }
    worker.join();
    assert(ok.load());
    std::cout << "  [PASS] processZero returns after pool shutdown; dropped slices are accounted for." << std::endl;
}

int main() {
    std::cout << "==========================================================" << std::endl;
    std::cout << " Commix Native C++20 Aether Engine Verification Suite" << std::endl;
    std::cout << "==========================================================" << std::endl;
    test_hyperfocal_calculation();
    test_astro_stacking_median();
    test_long_exposure_modes();
    test_multi_exposure_blend_modes();
    test_stabilization_engine();
    test_scopes_engine_neutral_and_bounds();
    test_telemetry_seqlock();
    test_best_shot_engine();
    test_color_science_round_trip_and_continuity();
    test_image_pipeline_basic_operations();
    test_enhanced_aether_engine_features();
    test_process_zero_never_waits_on_dropped_work();
    std::cout << "==========================================================" << std::endl;
    std::cout << " ALL NATIVE C++ ENGINE TESTS PASSED PERFECTLY!" << std::endl;
    std::cout << "==========================================================" << std::endl;
    return 0;
}
