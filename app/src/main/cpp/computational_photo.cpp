#include "computational_photo.h"
#include "thread_pool.hpp"
#include <cmath>
#include <algorithm>
#include <cstring>
#include <vector>

#if defined(__ARM_NEON) || defined(__aarch64__)
#include <arm_neon.h>
#endif

namespace commix {

namespace {

// Coarse-to-fine integer pixel translation registration against reference frame 0.
// Used when astro/night alignment is requested so handheld or drifted bursts align cleanly.
struct PixelShift {
    int dx = 0;
    int dy = 0;
};

PixelShift estimateFrameShift(
    const uint8_t* refFrame,
    const uint8_t* targetFrame,
    int width,
    int height,
    int channels,
    int maxShift = 12
) noexcept {
    PixelShift best{0, 0};
    if (width < 48 || height < 48) return best;

    const int marginX = std::min(maxShift, width / 6);
    const int marginY = std::min(maxShift, height / 6);
    const int x0 = marginX;
    const int x1 = width - marginX;
    const int y0 = marginY;
    const int y1 = height - marginY;
    const int stepX = std::max(2, (x1 - x0) / 32);
    const int stepY = std::max(2, (y1 - y0) / 32);

    auto sadAt = [&](int dx, int dy) noexcept -> int64_t {
        int64_t sum = 0;
        for (int y = y0; y < y1; y += stepY) {
            const uint8_t* refRow = refFrame + static_cast<size_t>(y) * width * channels;
            const uint8_t* tgtRow = targetFrame + static_cast<size_t>(y + dy) * width * channels;
            for (int x = x0; x < x1; x += stepX) {
                const size_t rIdx = static_cast<size_t>(x) * channels;
                const size_t tIdx = static_cast<size_t>(x + dx) * channels;
                const int rl = (refRow[rIdx] + (refRow[rIdx + 1] << 1) + refRow[rIdx + 2]) >> 2;
                const int tl = (tgtRow[tIdx] + (tgtRow[tIdx + 1] << 1) + tgtRow[tIdx + 2]) >> 2;
                sum += std::abs(rl - tl);
            }
        }
        return sum;
    };

    // Coarse search (step 2)
    int64_t bestSad = sadAt(0, 0);
    for (int dy = -marginY; dy <= marginY; dy += 2) {
        for (int dx = -marginX; dx <= marginX; dx += 2) {
            if (dx == 0 && dy == 0) continue;
            const int64_t s = sadAt(dx, dy);
            if (s < bestSad) {
                bestSad = s;
                best.dx = dx;
                best.dy = dy;
            }
        }
    }

    // Fine 1-pixel refinement around coarse optimum
    const int cx = best.dx;
    const int cy = best.dy;
    for (int dy = std::max(-marginY, cy - 1); dy <= std::min(marginY, cy + 1); ++dy) {
        for (int dx = std::max(-marginX, cx - 1); dx <= std::min(marginX, cx + 1); ++dx) {
            if (dx == best.dx && dy == best.dy) continue;
            const int64_t s = sadAt(dx, dy);
            if (s < bestSad) {
                bestSad = s;
                best.dx = dx;
                best.dy = dy;
            }
        }
    }
    return best;
}

inline void sampleBilinear(
    const uint8_t* input,
    int width,
    int height,
    int channels,
    float srcX,
    float srcY,
    uint8_t* dstPixel
) noexcept {
    const int x0 = static_cast<int>(std::floor(srcX));
    const int y0 = static_cast<int>(std::floor(srcY));
    const int x1 = std::min(x0 + 1, width - 1);
    const int y1 = std::min(y0 + 1, height - 1);
    const float fx = srcX - static_cast<float>(x0);
    const float fy = srcY - static_cast<float>(y0);
    const float w00 = (1.0f - fx) * (1.0f - fy);
    const float w10 = fx * (1.0f - fy);
    const float w01 = (1.0f - fx) * fy;
    const float w11 = fx * fy;

    const size_t rowStride = static_cast<size_t>(width) * channels;
    const uint8_t* p00 = input + static_cast<size_t>(y0) * rowStride + static_cast<size_t>(x0) * channels;
    const uint8_t* p10 = input + static_cast<size_t>(y0) * rowStride + static_cast<size_t>(x1) * channels;
    const uint8_t* p01 = input + static_cast<size_t>(y1) * rowStride + static_cast<size_t>(x0) * channels;
    const uint8_t* p11 = input + static_cast<size_t>(y1) * rowStride + static_cast<size_t>(x1) * channels;

    for (int c = 0; c < channels; ++c) {
        const float val = p00[c] * w00 + p10[c] * w10 + p01[c] * w01 + p11[c] * w11;
        dstPixel[c] = static_cast<uint8_t>(std::clamp(val + 0.5f, 0.0f, 255.0f));
    }
}

} // namespace

// -----------------------------------------------------------------------------
// 1. Astrophotography: Multi-Threaded Aligned Median Stacking + Deep Shadow Lift
// -----------------------------------------------------------------------------
bool ComputationalPhotoEngine::stackAstroFrames(
    const uint8_t* const* framePointers,
    int frameCount,
    int width,
    int height,
    int channels,
    uint8_t* outputBuffer,
    bool deepShadowBoost
) noexcept {
    if (!framePointers || frameCount <= 0 || !outputBuffer || width <= 0 || height <= 0 || channels < 3) {
        return false;
    }

    const int n = std::min(frameCount, MAX_STACK_FRAMES);
    if (n == 1) {
        std::memcpy(outputBuffer, framePointers[0], static_cast<size_t>(width) * height * channels);
        return true;
    }

    // Estimate per-frame translation shifts against reference frame 0 when deepShadowBoost / astro alignment is active
    PixelShift shifts[MAX_STACK_FRAMES];
    if (deepShadowBoost && width >= 48 && height >= 48) {
        for (int f = 1; f < n; ++f) {
            shifts[f] = estimateFrameShift(framePointers[0], framePointers[f], width, height, channels);
        }
    }

    // Precompute shadow lift LUT if deepShadowBoost is enabled
    uint8_t shadowLut[256];
    if (deepShadowBoost) {
        for (int i = 0; i < 256; ++i) {
            const float norm = static_cast<float>(i) / 255.0f;
            const float lifted = std::pow(norm, 0.78f);
            shadowLut[i] = static_cast<uint8_t>(std::clamp(lifted * 255.0f + 0.5f, 0.0f, 255.0f));
        }
    }

    const size_t rowStride = static_cast<size_t>(width) * static_cast<size_t>(channels);

    ThreadPool::instance().parallelFor(height, [&](int yStart, int yEnd) {
        uint8_t window[MAX_STACK_FRAMES];
        for (int y = yStart; y < yEnd; ++y) {
            uint8_t* outRow = outputBuffer + static_cast<size_t>(y) * rowStride;
            for (int x = 0; x < width; ++x) {
                const size_t outBase = static_cast<size_t>(x) * channels;
                for (int c = 0; c < channels; ++c) {
                    if (c == 3) {
                        outRow[outBase + 3] = 255;
                        continue;
                    }
                    for (int f = 0; f < n; ++f) {
                        const int sy = std::clamp(y + shifts[f].dy, 0, height - 1);
                        const int sx = std::clamp(x + shifts[f].dx, 0, width - 1);
                        const size_t srcIdx = static_cast<size_t>(sy) * rowStride + static_cast<size_t>(sx) * channels + c;
                        window[f] = framePointers[f][srcIdx];
                    }
                    std::nth_element(window, window + (n / 2), window + n);
                    const uint8_t med = window[n / 2];
                    outRow[outBase + c] = deepShadowBoost ? shadowLut[med] : med;
                }
            }
        }
    });

    return true;
}

// -----------------------------------------------------------------------------
// 2. Multi-Threaded Computational Long Exposure Accumulator
// -----------------------------------------------------------------------------
bool ComputationalPhotoEngine::accumulateLongExposure(
    const uint8_t* currentFrame,
    float* accumulator,
    int width,
    int height,
    int channels,
    int frameIndex,
    LongExposureStyle style,
    float alpha
) noexcept {
    if (!currentFrame || !accumulator || width <= 0 || height <= 0 || channels < 3) return false;

    const size_t rowLen = static_cast<size_t>(width) * static_cast<size_t>(channels);
    const float clampedAlpha = std::clamp(alpha, 0.01f, 1.0f);

    ThreadPool::instance().parallelFor(height, [&](int yStart, int yEnd) {
        for (int y = yStart; y < yEnd; ++y) {
            const size_t offset = static_cast<size_t>(y) * rowLen;
            const uint8_t* src = currentFrame + offset;
            float* acc = accumulator + offset;

            if (frameIndex == 0) {
                for (size_t i = 0; i < rowLen; ++i) {
                    acc[i] = static_cast<float>(src[i]);
                }
                continue;
            }

            switch (style) {
                case LongExposureStyle::LightTrails:
                case LongExposureStyle::StarTrails:
                    for (size_t i = 0; i < rowLen; ++i) {
                        const float v = static_cast<float>(src[i]);
                        if (v > acc[i]) acc[i] = v;
                    }
                    break;
                case LongExposureStyle::MovingCrowd: {
                    const float invN = 1.0f / static_cast<float>(frameIndex + 1);
                    for (size_t i = 0; i < rowLen; ++i) {
                        acc[i] += (static_cast<float>(src[i]) - acc[i]) * invN;
                    }
                    break;
                }
                case LongExposureStyle::SilkyWater:
                default:
                    for (size_t i = 0; i < rowLen; ++i) {
                        acc[i] = acc[i] * (1.0f - clampedAlpha) + static_cast<float>(src[i]) * clampedAlpha;
                    }
                    break;
            }
        }
    });

    return true;
}

void ComputationalPhotoEngine::finalizeLongExposure(
    const float* accumulator,
    uint8_t* output,
    int pixelCount,
    int channels
) noexcept {
    if (!accumulator || !output || pixelCount <= 0 || channels < 3) return;
    const int totalBytes = pixelCount * channels;
    ThreadPool::instance().parallelFor(totalBytes, [&](int start, int end) {
        for (int i = start; i < end; ++i) {
            output[i] = static_cast<uint8_t>(std::clamp(accumulator[i] + 0.5f, 0.0f, 255.0f));
        }
    }, 4096);
}

// -----------------------------------------------------------------------------
// 3. Multi-Threaded + ARM64 NEON Multi-Exposure Compositor
// -----------------------------------------------------------------------------
bool ComputationalPhotoEngine::blendMultiExposure(
    const uint8_t* baseFrame,
    const uint8_t* blendFrame,
    uint8_t* output,
    int width,
    int height,
    int channels,
    MultiExposureBlendMode mode
) noexcept {
    if (!baseFrame || !blendFrame || !output || width <= 0 || height <= 0 || channels < 3) return false;

    const size_t rowLen = static_cast<size_t>(width) * static_cast<size_t>(channels);

    ThreadPool::instance().parallelFor(height, [&](int yStart, int yEnd) {
        for (int y = yStart; y < yEnd; ++y) {
            const size_t offset = static_cast<size_t>(y) * rowLen;
            const uint8_t* aPtr = baseFrame + offset;
            const uint8_t* bPtr = blendFrame + offset;
            uint8_t* oPtr = output + offset;
            size_t i = 0;

#if defined(__ARM_NEON) || defined(__aarch64__)
            for (; i + 16 <= rowLen; i += 16) {
                const uint8x16_t va = vld1q_u8(aPtr + i);
                const uint8x16_t vb = vld1q_u8(bPtr + i);
                uint8x16_t vo;
                switch (mode) {
                    case MultiExposureBlendMode::Additive: vo = vqaddq_u8(va, vb); break;
                    case MultiExposureBlendMode::Average:  vo = vhaddq_u8(va, vb); break;
                    case MultiExposureBlendMode::Lighten:  vo = vmaxq_u8(va, vb); break;
                    case MultiExposureBlendMode::Darken:   vo = vminq_u8(va, vb); break;
                }
                vst1q_u8(oPtr + i, vo);
            }
#endif

            for (; i < rowLen; ++i) {
                const int a = aPtr[i];
                const int b = bPtr[i];
                int res = a;
                switch (mode) {
                    case MultiExposureBlendMode::Additive:
                        res = std::min(255, a + b);
                        break;
                    case MultiExposureBlendMode::Average:
                        res = (a + b) >> 1;
                        break;
                    case MultiExposureBlendMode::Lighten:
                        res = std::max(a, b);
                        break;
                    case MultiExposureBlendMode::Darken:
                        res = std::min(a, b);
                        break;
                }
                oPtr[i] = static_cast<uint8_t>(res);
            }
        }
    });

    return true;
}

// -----------------------------------------------------------------------------
// 4. Multi-Threaded Focus Stacking: High-Frequency Sobel Variance Blending
// -----------------------------------------------------------------------------
bool ComputationalPhotoEngine::stackFocusSlices(
    const uint8_t* const* slicePointers,
    int sliceCount,
    int width,
    int height,
    int channels,
    uint8_t* output
) noexcept {
    if (!slicePointers || sliceCount <= 0 || !output || width < 3 || height < 3 || channels < 3) return false;

    const size_t totalBytes = static_cast<size_t>(width) * height * channels;
    std::memcpy(output, slicePointers[0], totalBytes);
    if (sliceCount == 1) return true;

    const size_t rowStride = static_cast<size_t>(width) * channels;

    ThreadPool::instance().parallelFor(height - 2, [&](int rStart, int rEnd) {
        const int yStart = rStart + 1;
        const int yEnd = rEnd + 1;
        for (int y = yStart; y < yEnd; ++y) {
            uint8_t* outRow = output + static_cast<size_t>(y) * rowStride;
            for (int x = 1; x < width - 1; ++x) {
                int bestSlice = 0;
                int maxEnergy = -1;

                for (int s = 0; s < sliceCount; ++s) {
                    const uint8_t* img = slicePointers[s];
                    auto luma = [&](int px, int py) noexcept -> int {
                        const size_t idx = static_cast<size_t>(py) * rowStride + static_cast<size_t>(px) * channels;
                        return (77 * img[idx] + 150 * img[idx + 1] + 29 * img[idx + 2]) >> 8;
                    };

                    const int gx = -luma(x - 1, y - 1) - 2 * luma(x - 1, y) - luma(x - 1, y + 1)
                                   + luma(x + 1, y - 1) + 2 * luma(x + 1, y) + luma(x + 1, y + 1);
                    const int gy = -luma(x - 1, y - 1) - 2 * luma(x, y - 1) - luma(x + 1, y - 1)
                                   + luma(x - 1, y + 1) + 2 * luma(x, y + 1) + luma(x + 1, y + 1);
                    const int energy = std::abs(gx) + std::abs(gy);
                    if (energy > maxEnergy) {
                        maxEnergy = energy;
                        bestSlice = s;
                    }
                }

                const size_t dstOff = static_cast<size_t>(x) * channels;
                const uint8_t* bestSrc = slicePointers[bestSlice] + static_cast<size_t>(y) * rowStride + dstOff;
                for (int c = 0; c < channels; ++c) {
                    outRow[dstOff + c] = bestSrc[c];
                }
            }
        }
    });

    return true;
}

// -----------------------------------------------------------------------------
// 5. Multi-Threaded Bilinear Lens Aberration Correction (4th-Order Polynomial)
// -----------------------------------------------------------------------------
bool ComputationalPhotoEngine::correctLensAberration(
    const uint8_t* input,
    uint8_t* output,
    int width,
    int height,
    int channels,
    float k1,
    float k2
) noexcept {
    if (!input || !output || width <= 0 || height <= 0 || channels < 3) return false;

    const float cx = width * 0.5f;
    const float cy = height * 0.5f;
    const float maxR = std::sqrt(cx * cx + cy * cy);
    if (maxR < 1e-4f) return false;
    const float invMaxR = 1.0f / maxR;
    const size_t rowStride = static_cast<size_t>(width) * channels;

    ThreadPool::instance().parallelFor(height, [&](int yStart, int yEnd) {
        for (int y = yStart; y < yEnd; ++y) {
            const float ny = (static_cast<float>(y) - cy) * invMaxR;
            uint8_t* outRow = output + static_cast<size_t>(y) * rowStride;
            for (int x = 0; x < width; ++x) {
                const float nx = (static_cast<float>(x) - cx) * invMaxR;
                const float r2 = nx * nx + ny * ny;
                const float factor = 1.0f + k1 * r2 + k2 * r2 * r2;

                const float srcX = nx * factor * maxR + cx;
                const float srcY = ny * factor * maxR + cy;
                uint8_t* dst = outRow + static_cast<size_t>(x) * channels;

                if (srcX >= 0.0f && srcX < static_cast<float>(width - 1) &&
                    srcY >= 0.0f && srcY < static_cast<float>(height - 1)) {
                    sampleBilinear(input, width, height, channels, srcX, srcY, dst);
                } else {
                    for (int c = 0; c < channels; ++c) {
                        dst[c] = (c == 3) ? 255 : 0;
                    }
                }
            }
        }
    });

    return true;
}

// -----------------------------------------------------------------------------
// 6. Multi-Threaded Bilinear Rolling Shutter Rectification
// -----------------------------------------------------------------------------
bool ComputationalPhotoEngine::rectifyRollingShutter(
    const uint8_t* input,
    uint8_t* output,
    int width,
    int height,
    int channels,
    float gyroYawVelocity,
    float readoutTimeMs
) noexcept {
    if (!input || !output || width <= 0 || height <= 0 || channels < 3) return false;

    const float maxShiftPx = gyroYawVelocity * (readoutTimeMs / 1000.0f) * static_cast<float>(width);
    const size_t rowStride = static_cast<size_t>(width) * channels;

    ThreadPool::instance().parallelFor(height, [&](int yStart, int yEnd) {
        for (int y = yStart; y < yEnd; ++y) {
            const float rowFrac = static_cast<float>(y) / static_cast<float>(height) - 0.5f;
            const float shiftX = rowFrac * maxShiftPx;
            uint8_t* outRow = output + static_cast<size_t>(y) * rowStride;

            for (int x = 0; x < width; ++x) {
                const float srcX = std::clamp(static_cast<float>(x) + shiftX, 0.0f, static_cast<float>(width - 1));
                sampleBilinear(input, width, height, channels, srcX, static_cast<float>(y),
                               outRow + static_cast<size_t>(x) * channels);
            }
        }
    });

    return true;
}

// -----------------------------------------------------------------------------
// 7. Optical Hyperfocal Distance Calculation
// -----------------------------------------------------------------------------
float ComputationalPhotoEngine::calculateHyperfocalDistance(
    float focalLengthMm,
    float fNumber,
    float circleOfConfusionMm
) noexcept {
    if (focalLengthMm <= 0.0f || fNumber <= 0.0f || circleOfConfusionMm <= 0.0f) return 0.0f;
    const float fM = focalLengthMm / 1000.0f;
    const float cM = circleOfConfusionMm / 1000.0f;
    return (fM * fM) / (fNumber * cM) + fM;
}

// -----------------------------------------------------------------------------
// 8. Aether Multi-Threaded Mertens HDR Exposure Fusion
// -----------------------------------------------------------------------------
bool ComputationalPhotoEngine::fuseHdrExposure(
    const uint8_t* const* framePointers,
    int frameCount,
    int width,
    int height,
    int channels,
    uint8_t* outputBuffer,
    float highlightCompression,
    float shadowRecovery
) noexcept {
    if (!framePointers || frameCount <= 0 || !outputBuffer || width <= 0 || height <= 0 || channels < 3) {
        return false;
    }

    const int n = std::min(frameCount, MAX_STACK_FRAMES);
    if (n == 1) {
        std::memcpy(outputBuffer, framePointers[0], static_cast<size_t>(width) * height * channels);
        return true;
    }

    // Align brackets against middle exposure frame (typically 0 EV reference)
    const int refIdx = n / 2;
    PixelShift shifts[MAX_STACK_FRAMES];
    if (width >= 48 && height >= 48) {
        for (int f = 0; f < n; ++f) {
            if (f == refIdx) continue;
            shifts[f] = estimateFrameShift(framePointers[refIdx], framePointers[f], width, height, channels, 10);
        }
    }

    // Precompute well-exposedness Gaussian LUT centered at 0.5 (sigma = 0.22)
    float wellExpLut[256];
    const float invTwoSigmaSq = 1.0f / (2.0f * 0.22f * 0.22f);
    for (int i = 0; i < 256; ++i) {
        const float d = (static_cast<float>(i) / 255.0f) - 0.5f;
        wellExpLut[i] = std::exp(-(d * d) * invTwoSigmaSq) + 1e-3f;
    }

    const float hiComp = std::clamp(highlightCompression, 0.0f, 1.0f);
    const float shLift = std::clamp(shadowRecovery, 0.0f, 1.0f);
    const size_t rowStride = static_cast<size_t>(width) * static_cast<size_t>(channels);

    ThreadPool::instance().parallelFor(height, [&](int yStart, int yEnd) {
        for (int y = yStart; y < yEnd; ++y) {
            uint8_t* outRow = outputBuffer + static_cast<size_t>(y) * rowStride;
            for (int x = 0; x < width; ++x) {
                float rAcc = 0.0f;
                float gAcc = 0.0f;
                float bAcc = 0.0f;
                float wSum = 0.0f;

                for (int f = 0; f < n; ++f) {
                    const int sy = std::clamp(y + shifts[f].dy, 0, height - 1);
                    const int sx = std::clamp(x + shifts[f].dx, 0, width - 1);
                    const uint8_t* img = framePointers[f];
                    const size_t idx = static_cast<size_t>(sy) * rowStride + static_cast<size_t>(sx) * channels;

                    const int r = img[idx + 0];
                    const int g = img[idx + 1];
                    const int b = img[idx + 2];

                    // Per-channel well-exposedness product (Mertens et al.)
                    const float eW = wellExpLut[r] * wellExpLut[g] * wellExpLut[b];

                    // Saturation weight (spread between max & min channel)
                    const int cMax = std::max(r, std::max(g, b));
                    const int cMin = std::min(r, std::min(g, b));
                    const float satW = 0.25f + (static_cast<float>(cMax - cMin) / 255.0f);

                    // Local Laplacian contrast estimate
                    float conW = 0.20f;
                    if (sx > 0 && sx < width - 1 && sy > 0 && sy < height - 1) {
                        const int cL = (r + (g << 1) + b) >> 2;
                        const size_t idxL = idx - channels;
                        const size_t idxR = idx + channels;
                        const size_t idxU = idx - rowStride;
                        const size_t idxD = idx + rowStride;
                        const int lL = (img[idxL] + (img[idxL + 1] << 1) + img[idxL + 2]) >> 2;
                        const int lR = (img[idxR] + (img[idxR + 1] << 1) + img[idxR + 2]) >> 2;
                        const int lU = (img[idxU] + (img[idxU + 1] << 1) + img[idxU + 2]) >> 2;
                        const int lD = (img[idxD] + (img[idxD + 1] << 1) + img[idxD + 2]) >> 2;
                        const int lap = std::abs(4 * cL - lL - lR - lU - lD);
                        conW += std::min(1.0f, static_cast<float>(lap) / 96.0f);
                    }

                    const float w = eW * satW * conW;
                    rAcc += static_cast<float>(r) * w;
                    gAcc += static_cast<float>(g) * w;
                    bAcc += static_cast<float>(b) * w;
                    wSum += w;
                }

                const float invW = (wSum > 1e-6f) ? (1.0f / (wSum * 255.0f)) : (1.0f / 255.0f);
                float rf = rAcc * invW;
                float gf = gAcc * invW;
                float bf = bAcc * invW;

                // Subtle shadow lift + filmic highlight shoulder compression
                if (shLift > 0.001f || hiComp > 0.001f) {
                    const float luma = 0.2126f * rf + 0.7152f * gf + 0.0722f * bf;
                    float targetLuma = luma;
                    if (shLift > 0.001f && luma < 0.45f) {
                        const float t = (0.45f - luma) / 0.45f;
                        targetLuma += shLift * 0.12f * t * t * std::sqrt(std::max(0.0f, luma));
                    }
                    if (hiComp > 0.001f && targetLuma > 0.72f) {
                        const float over = targetLuma - 0.72f;
                        targetLuma = 0.72f + over / (1.0f + over * hiComp * 2.2f);
                    }
                    const float scale = (luma > 1e-4f) ? (targetLuma / luma) : 1.0f;
                    rf *= scale;
                    gf *= scale;
                    bf *= scale;
                }

                const size_t outOff = static_cast<size_t>(x) * channels;
                outRow[outOff + 0] = static_cast<uint8_t>(std::clamp(rf * 255.0f + 0.5f, 0.0f, 255.0f));
                outRow[outOff + 1] = static_cast<uint8_t>(std::clamp(gf * 255.0f + 0.5f, 0.0f, 255.0f));
                outRow[outOff + 2] = static_cast<uint8_t>(std::clamp(bf * 255.0f + 0.5f, 0.0f, 255.0f));
                if (channels == 4) outRow[outOff + 3] = 255;
            }
        }
    });

    return true;
}

} // namespace commix
