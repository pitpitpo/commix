#include "best_shot_engine.h"
#include <cmath>
#include <algorithm>
#include <chrono>

namespace commix {

BestShotEngine::BestShotEngine() noexcept
    : m_historyIndex(0), m_historyCount(0), m_lastTriggerTimeMs(0) {
    for (size_t i = 0; i < HISTORY_CAPACITY; ++i) {
        m_velocityHistory[i] = 0.0f;
    }
}

void BestShotEngine::pushVelocity(float gx, float gy, float gz) noexcept {
    float mag = std::sqrt(gx * gx + gy * gy + gz * gz);
    m_velocityHistory[m_historyIndex] = mag;
    m_historyIndex = (m_historyIndex + 1) % HISTORY_CAPACITY;
    if (m_historyCount < HISTORY_CAPACITY) {
        m_historyCount++;
    }
}

BestShotScore BestShotEngine::evaluate(
    const int32_t* lumaHistogram,
    float spotIre,
    float hiClipPct,
    float loClipPct
) noexcept {
    BestShotScore score{0.0f, 0.0f, 0.0f, 0.0f, false};

    // 1. Stability Score (0 .. 100): Low angular velocity over recent rolling window
    if (m_historyCount == 0) {
        score.stabilityScore = 50.0f;
    } else {
        float sumVel = 0.0f;
        for (size_t i = 0; i < m_historyCount; ++i) {
            sumVel += m_velocityHistory[i];
        }
        float avgVel = sumVel / static_cast<float>(m_historyCount);
        // 0.0 rad/s -> 100%, >= 0.35 rad/s -> 0%
        float stab = 1.0f - (avgVel / 0.35f);
        score.stabilityScore = std::clamp(stab * 100.0f, 0.0f, 100.0f);
    }

    // 2. Exposure Score (0 .. 100): Ideal target around 42 - 58 IRE
    float ireDist = std::abs(spotIre - 50.0f);
    float expScore = 100.0f - (ireDist * 1.8f);
    score.exposureScore = std::clamp(expScore, 0.0f, 100.0f);

    // 3. Dynamic Range Score (0 .. 100): Combine clipping penalty with real luma histogram spread
    float clipPenalty = (hiClipPct * 4.0f) + (loClipPct * 3.0f);
    float baseDr = std::clamp(100.0f - clipPenalty, 0.0f, 100.0f);

    if (lumaHistogram != nullptr) {
        int64_t total = 0;
        for (int i = 0; i < 256; ++i) {
            if (lumaHistogram[i] > 0) total += lumaHistogram[i];
        }
        if (total > 0) {
            // Compute 5th to 95th percentile tonal spread (ideal broadcast contrast span ~160..220 bins)
            const int64_t p5Target = (total * 5) / 100;
            const int64_t p95Target = (total * 95) / 100;
            int64_t acc = 0;
            int p5 = 0;
            int p95 = 255;
            bool foundP5 = false;
            for (int i = 0; i < 256; ++i) {
                acc += std::max(0, lumaHistogram[i]);
                if (!foundP5 && acc >= p5Target) {
                    p5 = i;
                    foundP5 = true;
                }
                if (acc >= p95Target) {
                    p95 = i;
                    break;
                }
            }
            const float spread = static_cast<float>(std::max(0, p95 - p5));
            // Flat scene (spread < 30) scores lower; rich tonal range (>= 140) scores up to 100
            const float spreadScore = std::clamp((spread / 140.0f) * 100.0f, 25.0f, 100.0f);
            score.dynamicRangeScore = std::clamp(0.65f * baseDr + 0.35f * spreadScore, 0.0f, 100.0f);
        } else {
            score.dynamicRangeScore = baseDr;
        }
    } else {
        score.dynamicRangeScore = baseDr;
    }

    // Weighted Total
    score.totalScore = (score.stabilityScore * 0.45f) +
                       (score.exposureScore * 0.30f) +
                       (score.dynamicRangeScore * 0.25f);

    // Trigger evaluation with 3.5 second hysteresis
    auto nowMs = std::chrono::duration_cast<std::chrono::milliseconds>(
        std::chrono::steady_clock::now().time_since_epoch()
    ).count();

    if (score.totalScore >= 82.0f && score.stabilityScore >= 88.0f) {
        if (nowMs - m_lastTriggerTimeMs > 3500) {
            score.shouldTriggerShoot = true;
            m_lastTriggerTimeMs = nowMs;
        }
    }

    return score;
}

} // namespace commix
