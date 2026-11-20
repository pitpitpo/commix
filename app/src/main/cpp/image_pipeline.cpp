#include "image_pipeline.h"
#include "thread_pool.hpp"
#include <cmath>
#include <algorithm>
#include <mutex>
#include <vector>

namespace commix {

uint8_t ImagePipeline::s_filmPrintLut[256];
uint8_t ImagePipeline::s_cineDLut[256];
uint8_t ImagePipeline::s_neutralLut[256];
bool ImagePipeline::s_lutsInitialized = false;

namespace {

inline uint8_t clampByte(float v) noexcept {
    if (v <= 0.0f) return 0;
    if (v >= 255.0f) return 255;
    return static_cast<uint8_t>(v + 0.5f);
}

// Fast 64-bit Xorshift128+ PRNG for organic analog silver-halide film grain
struct XorShift128Plus {
    uint64_t s0;
    uint64_t s1;

    explicit XorShift128Plus(uint64_t seed) noexcept {
        // SplitMix64 seeding guarantees non-zero well-mixed state
        uint64_t z = seed + 0x9E3779B97F4A7C15ULL;
        z = (z ^ (z >> 30)) * 0xBF58476D1CE4E5B9ULL;
        z = (z ^ (z >> 27)) * 0x94D049BB133111EBULL;
        s0 = z ^ (z >> 31);
        z = (seed ^ 0xA0761D6478BD642FULL) + 0x9E3779B97F4A7C15ULL;
        z = (z ^ (z >> 30)) * 0xBF58476D1CE4E5B9ULL;
        z = (z ^ (z >> 27)) * 0x94D049BB133111EBULL;
        s1 = z ^ (z >> 31);
        if ((s0 | s1) == 0ULL) s1 = 0x517CC1B727220A95ULL;
    }

    inline uint64_t next() noexcept {
        uint64_t x = s0;
        const uint64_t y = s1;
        s0 = y;
        x ^= x << 23;
        s1 = x ^ y ^ (x >> 17) ^ (y >> 26);
        return s1 + y;
    }

    // Returns uniform float in [-1.0, 1.0]
    inline float nextSignedUnit() noexcept {
        // Use top 24 bits for crisp floating-point uniformity
        const uint32_t bits = static_cast<uint32_t>(next() >> 40);
        return (static_cast<float>(bits) * (2.0f / 16777215.0f)) - 1.0f;
    }
};

} // namespace

void ImagePipeline::initToneLuts() noexcept {
    static std::once_flag initFlag;
    std::call_once(initFlag, []() {
        for (int i = 0; i < 256; ++i) {
            float x = i / 255.0f;

            // FilmPrint: Gentle shadow rolloff, contrasty midtones, compressed highlights
            float film = 1.0f / (1.0f + std::exp(-7.0f * (x - 0.45f)));
            s_filmPrintLut[i] = static_cast<uint8_t>(std::clamp(film * 255.0f, 0.0f, 255.0f));

            // CineD: Flatter shadows and highlights for maximum dynamic range
            float cine = std::pow(x, 0.85f);
            s_cineDLut[i] = static_cast<uint8_t>(std::clamp(cine * 255.0f, 0.0f, 255.0f));

            // Neutral: Smooth filmic gamma 2.2 curve
            float neut = std::pow(x, 1.0f / 1.15f);
            s_neutralLut[i] = static_cast<uint8_t>(std::clamp(neut * 255.0f, 0.0f, 255.0f));
        }
        s_lutsInitialized = true;
    });
}

bool ImagePipeline::processZero(
    uint8_t* __restrict__ pixelData,
    int width,
    int height,
    int channels,
    ToneCurvePreset curve,
    GrainPreset grain,
    float grainIntensity,
    float toneIntensity
) noexcept {
    if (!pixelData || width <= 0 || height <= 0 || channels < 3) return false;
    initToneLuts();

    const uint8_t* baseLut = nullptr;
    if (curve == ToneCurvePreset::FilmPrint) baseLut = s_filmPrintLut;
    else if (curve == ToneCurvePreset::CineD) baseLut = s_cineDLut;
    else if (curve == ToneCurvePreset::Neutral) baseLut = s_neutralLut;

    const float clampedToneIntensity = std::clamp(toneIntensity, 0.0f, 2.0f);
    // Precompute effective 256-entry scaled LUT so inner pixel loop is a single table lookup
    uint8_t effectiveLut[256];
    const bool applyCurve = (baseLut != nullptr) && (clampedToneIntensity > 0.001f);
    if (applyCurve) {
        for (int i = 0; i < 256; ++i) {
            const float blended = static_cast<float>(i) +
                                  (static_cast<float>(baseLut[i]) - static_cast<float>(i)) * clampedToneIntensity;
            effectiveLut[i] = clampByte(blended);
        }
    }

    float grainAmp = 0.0f;
    if (grain == GrainPreset::Fine35mm) grainAmp = 8.0f * grainIntensity;
    else if (grain == GrainPreset::Medium16mm) grainAmp = 16.0f * grainIntensity;
    else if (grain == GrainPreset::Coarse8mm) grainAmp = 28.0f * grainIntensity;
    const bool applyGrain = (grain != GrainPreset::Off && grainAmp > 0.01f);

    if (!applyCurve && !applyGrain) {
        return true;
    }

    // Precompute midtone grain envelope LUT (peaks at mid-grey luma 128, tapers at blacks/whites)
    float grainWeightLut[256];
    if (applyGrain) {
        for (int i = 0; i < 256; ++i) {
            const float lumaNorm = static_cast<float>(i) / 255.0f;
            const float midtoneWeight = 1.0f - std::abs(lumaNorm - 0.5f) * 1.8f;
            grainWeightLut[i] = std::max(0.1f, midtoneWeight) * grainAmp;
        }
    }

    const size_t rowStride = static_cast<size_t>(width) * static_cast<size_t>(channels);

    ThreadPool::instance().parallelFor(height, [&](int yStart, int yEnd) {
        XorShift128Plus rng(0x9E3779B97F4A7C15ULL ^ (static_cast<uint64_t>(yStart + 1) * 0xBF58476D1CE4E5B9ULL));

        for (int y = yStart; y < yEnd; ++y) {
            uint8_t* row = pixelData + static_cast<size_t>(y) * rowStride;
            if (!applyGrain) {
                for (int x = 0; x < width; ++x) {
                    const size_t idx = static_cast<size_t>(x) * channels;
                    row[idx + 0] = effectiveLut[row[idx + 0]];
                    row[idx + 1] = effectiveLut[row[idx + 1]];
                    row[idx + 2] = effectiveLut[row[idx + 2]];
                }
            } else {
                for (int x = 0; x < width; ++x) {
                    const size_t idx = static_cast<size_t>(x) * channels;
                    int r = applyCurve ? effectiveLut[row[idx + 0]] : row[idx + 0];
                    int g = applyCurve ? effectiveLut[row[idx + 1]] : row[idx + 1];
                    int b = applyCurve ? effectiveLut[row[idx + 2]] : row[idx + 2];

                    const int luma = (77 * r + 150 * g + 29 * b) >> 8;
                    const float noise = rng.nextSignedUnit() * grainWeightLut[luma];

                    row[idx + 0] = clampByte(static_cast<float>(r) + noise);
                    row[idx + 1] = clampByte(static_cast<float>(g) + noise);
                    row[idx + 2] = clampByte(static_cast<float>(b) + noise);
                }
            }
        }
    });

    return true;
}

void ImagePipeline::applyUnsharpMask(
    uint8_t* __restrict__ pixelData,
    int width,
    int height,
    int channels,
    float amount,
    float radius
) noexcept {
    if (!pixelData || width < 3 || height < 3 || channels < 3 || amount <= 0.0f) return;

    // Build normalized 1D Gaussian kernel sized from `radius` (sigma = max(radius, 0.5)).
    const float sigma = std::clamp(radius, 0.5f, 16.0f);
    const int kRadius = std::max(1, static_cast<int>(std::ceil(sigma * 2.5f)));
    const int kSize = 2 * kRadius + 1;
    std::vector<float> kernel(static_cast<size_t>(kSize));
    float kSum = 0.0f;
    const float invTwoSigmaSq = 1.0f / (2.0f * sigma * sigma);
    for (int i = -kRadius; i <= kRadius; ++i) {
        const float w = std::exp(-static_cast<float>(i * i) * invTwoSigmaSq);
        kernel[static_cast<size_t>(i + kRadius)] = w;
        kSum += w;
    }
    const float invKSum = 1.0f / kSum;
    for (float& w : kernel) w *= invKSum;

    // Single intermediate float buffer (50% less RAM than storing a duplicate float copy of src).
    const size_t totalPixels = static_cast<size_t>(width) * static_cast<size_t>(height);
    std::vector<float> horiz(totalPixels * 3);
    const size_t srcRowStride = static_cast<size_t>(width) * static_cast<size_t>(channels);
    const size_t tmpRowStride = static_cast<size_t>(width) * 3;

    // Pass 1: Multi-threaded horizontal 1D Gaussian directly from pixelData -> horiz
    ThreadPool::instance().parallelFor(height, [&](int yStart, int yEnd) {
        for (int y = yStart; y < yEnd; ++y) {
            const uint8_t* srcRow = pixelData + static_cast<size_t>(y) * srcRowStride;
            float* dstRow = horiz.data() + static_cast<size_t>(y) * tmpRowStride;
            for (int x = 0; x < width; ++x) {
                float rAcc = 0.0f, gAcc = 0.0f, bAcc = 0.0f;
                for (int k = -kRadius; k <= kRadius; ++k) {
                    const int sx = std::clamp(x + k, 0, width - 1);
                    const float w = kernel[static_cast<size_t>(k + kRadius)];
                    const size_t sIdx = static_cast<size_t>(sx) * channels;
                    rAcc += static_cast<float>(srcRow[sIdx + 0]) * w;
                    gAcc += static_cast<float>(srcRow[sIdx + 1]) * w;
                    bAcc += static_cast<float>(srcRow[sIdx + 2]) * w;
                }
                const size_t dIdx = static_cast<size_t>(x) * 3;
                dstRow[dIdx + 0] = rAcc;
                dstRow[dIdx + 1] = gAcc;
                dstRow[dIdx + 2] = bAcc;
            }
        }
    });

    // Pass 2: Multi-threaded vertical 1D Gaussian + in-place unsharp combine -> pixelData
    ThreadPool::instance().parallelFor(height, [&](int yStart, int yEnd) {
        for (int y = yStart; y < yEnd; ++y) {
            uint8_t* dstRow = pixelData + static_cast<size_t>(y) * srcRowStride;
            for (int x = 0; x < width; ++x) {
                float rBlur = 0.0f, gBlur = 0.0f, bBlur = 0.0f;
                const size_t xOff = static_cast<size_t>(x) * 3;
                for (int k = -kRadius; k <= kRadius; ++k) {
                    const int sy = std::clamp(y + k, 0, height - 1);
                    const float w = kernel[static_cast<size_t>(k + kRadius)];
                    const float* tPx = horiz.data() + static_cast<size_t>(sy) * tmpRowStride + xOff;
                    rBlur += tPx[0] * w;
                    gBlur += tPx[1] * w;
                    bBlur += tPx[2] * w;
                }
                const size_t idx = static_cast<size_t>(x) * channels;
                const float rOrig = static_cast<float>(dstRow[idx + 0]);
                const float gOrig = static_cast<float>(dstRow[idx + 1]);
                const float bOrig = static_cast<float>(dstRow[idx + 2]);
                dstRow[idx + 0] = clampByte(rOrig + (rOrig - rBlur) * amount);
                dstRow[idx + 1] = clampByte(gOrig + (gOrig - gBlur) * amount);
                dstRow[idx + 2] = clampByte(bOrig + (bOrig - bBlur) * amount);
            }
        }
    });
}

bool ImagePipeline::aetherMasterEnhance(
    uint8_t* __restrict__ pixelData,
    int width,
    int height,
    int channels,
    float denoiseStrength,
    float clarityStrength,
    float shadowLift,
    float highlightRollOff
) noexcept {
    if (!pixelData || width < 3 || height < 3 || channels < 3) return false;

    const float dn = std::clamp(denoiseStrength, 0.0f, 1.0f);
    const float cl = std::clamp(clarityStrength, 0.0f, 1.5f);
    const float sh = std::clamp(shadowLift, 0.0f, 1.0f);
    const float hi = std::clamp(highlightRollOff, 0.0f, 1.0f);

    const size_t rowStride = static_cast<size_t>(width) * static_cast<size_t>(channels);
    const size_t totalBytes = static_cast<size_t>(height) * rowStride;
    std::vector<uint8_t> srcCopy(pixelData, pixelData + totalBytes);

    // Precompute bilateral range weight LUT for fast edge-preserving 3x3 filtering
    const float rangeSigma = 10.0f + dn * 28.0f;
    const float invTwoRangeSq = 1.0f / (2.0f * rangeSigma * rangeSigma);
    float rangeLut[256];
    for (int d = 0; d < 256; ++d) {
        rangeLut[d] = std::exp(-static_cast<float>(d * d) * invTwoRangeSq);
    }

    ThreadPool::instance().parallelFor(height, [&](int yStart, int yEnd) {
        for (int y = yStart; y < yEnd; ++y) {
            uint8_t* dstRow = pixelData + static_cast<size_t>(y) * rowStride;
            for (int x = 0; x < width; ++x) {
                const size_t cIdx = static_cast<size_t>(y) * rowStride + static_cast<size_t>(x) * channels;
                const float cr = static_cast<float>(srcCopy[cIdx + 0]);
                const float cg = static_cast<float>(srcCopy[cIdx + 1]);
                const float cb = static_cast<float>(srcCopy[cIdx + 2]);
                const int cLuma = (77 * srcCopy[cIdx + 0] + 150 * srcCopy[cIdx + 1] + 29 * srcCopy[cIdx + 2]) >> 8;

                float rSmooth = cr;
                float gSmooth = cg;
                float bSmooth = cb;
                float lumaMean = static_cast<float>(cLuma);

                if (x > 0 && x < width - 1 && y > 0 && y < height - 1 && (dn > 0.01f || cl > 0.01f)) {
                    float rAcc = 0.0f, gAcc = 0.0f, bAcc = 0.0f, wSum = 0.0f;
                    float lAcc = 0.0f;
                    for (int dy = -1; dy <= 1; ++dy) {
                        const uint8_t* nRow = srcCopy.data() + static_cast<size_t>(y + dy) * rowStride;
                        for (int dx = -1; dx <= 1; ++dx) {
                            const size_t nOff = static_cast<size_t>(x + dx) * channels;
                            const int nr = nRow[nOff + 0];
                            const int ng = nRow[nOff + 1];
                            const int nb = nRow[nOff + 2];
                            const int nLuma = (77 * nr + 150 * ng + 29 * nb) >> 8;
                            lAcc += static_cast<float>(nLuma);

                            const int diff = std::abs(nLuma - cLuma);
                            const float spatial = (dx == 0 && dy == 0) ? 2.0f : ((dx == 0 || dy == 0) ? 1.0f : 0.7f);
                            const float w = spatial * rangeLut[diff];
                            rAcc += static_cast<float>(nr) * w;
                            gAcc += static_cast<float>(ng) * w;
                            bAcc += static_cast<float>(nb) * w;
                            wSum += w;
                        }
                    }
                    const float invW = 1.0f / std::max(wSum, 1e-4f);
                    const float rBilat = rAcc * invW;
                    const float gBilat = gAcc * invW;
                    const float bBilat = bAcc * invW;
                    lumaMean = lAcc * (1.0f / 9.0f);

                    rSmooth = cr + (rBilat - cr) * dn;
                    gSmooth = cg + (gBilat - cg) * dn;
                    bSmooth = cb + (bBilat - cb) * dn;
                }

                // Halo-free local micro-contrast / clarity on midtone edges
                if (cl > 0.01f) {
                    const float detail = (static_cast<float>(cLuma) - lumaMean) * cl * 0.45f;
                    // Dampen clarity in deep shadows to avoid amplifying sensor noise
                    const float shadowGate = std::clamp(static_cast<float>(cLuma) / 48.0f, 0.0f, 1.0f);
                    const float dBoost = detail * shadowGate;
                    rSmooth += dBoost;
                    gSmooth += dBoost;
                    bSmooth += dBoost;
                }

                // Adaptive shadow recovery & filmic highlight roll-off
                float rf = std::clamp(rSmooth / 255.0f, 0.0f, 1.0f);
                float gf = std::clamp(gSmooth / 255.0f, 0.0f, 1.0f);
                float bf = std::clamp(bSmooth / 255.0f, 0.0f, 1.0f);

                if (sh > 0.01f || hi > 0.01f) {
                    const float yNorm = 0.2126f * rf + 0.7152f * gf + 0.0722f * bf;
                    float yOut = yNorm;
                    if (sh > 0.01f && yNorm < 0.42f) {
                        const float t = (0.42f - yNorm) / 0.42f;
                        yOut += sh * 0.14f * t * t * std::sqrt(yNorm);
                    }
                    if (hi > 0.01f && yOut > 0.76f) {
                        const float over = yOut - 0.76f;
                        yOut = 0.76f + over / (1.0f + over * hi * 2.5f);
                    }
                    const float ratio = (yNorm > 1e-4f) ? (yOut / yNorm) : 1.0f;
                    rf *= ratio;
                    gf *= ratio;
                    bf *= ratio;
                }

                const size_t dOff = static_cast<size_t>(x) * channels;
                dstRow[dOff + 0] = clampByte(rf * 255.0f);
                dstRow[dOff + 1] = clampByte(gf * 255.0f);
                dstRow[dOff + 2] = clampByte(bf * 255.0f);
            }
        }
    });

    return true;
}

bool ImagePipeline::applyVirtualGainAndTone(
    uint8_t* __restrict__ pixelData,
    int width,
    int height,
    int channels,
    float gainLinear
) noexcept {
    if (!pixelData || width <= 0 || height <= 0 || channels < 3) return false;
    const float g = std::clamp(gainLinear, 0.25f, 16.0f);
    if (std::abs(g - 1.0f) < 0.01f) return true;

    // Precompute sRGB -> linear -> virtual gain -> ACES filmic shoulder -> sRGB 256-entry LUT
    uint8_t gainLut[256];
    for (int i = 0; i < 256; ++i) {
        const float s = static_cast<float>(i) / 255.0f;
        const float lin = (s <= 0.04045f) ? (s / 12.92f) : std::pow((s + 0.055f) / 1.055f, 2.4f);
        const float boosted = lin * g;
        // Soft ACES-inspired rational shoulder prevents harsh highlight clipping under high virtual gain
        const float mapped = (boosted * (2.51f * boosted + 0.03f)) /
                             (boosted * (2.43f * boosted + 0.59f) + 0.14f);
        const float clamped = std::clamp(mapped, 0.0f, 1.0f);
        const float sOut = (clamped <= 0.0031308f)
                               ? (clamped * 12.92f)
                               : (1.055f * std::pow(clamped, 1.0f / 2.4f) - 0.055f);
        gainLut[i] = clampByte(sOut * 255.0f);
    }

    const size_t rowStride = static_cast<size_t>(width) * static_cast<size_t>(channels);
    ThreadPool::instance().parallelFor(height, [&](int yStart, int yEnd) {
        for (int y = yStart; y < yEnd; ++y) {
            uint8_t* row = pixelData + static_cast<size_t>(y) * rowStride;
            for (int x = 0; x < width; ++x) {
                const size_t idx = static_cast<size_t>(x) * channels;
                row[idx + 0] = gainLut[row[idx + 0]];
                row[idx + 1] = gainLut[row[idx + 1]];
                row[idx + 2] = gainLut[row[idx + 2]];
            }
        }
    });

    return true;
}

} // namespace commix
