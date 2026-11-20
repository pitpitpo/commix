#include "color_science.h"
#include "thread_pool.hpp"
#include <cmath>
#include <algorithm>

namespace commix {

namespace {

inline float srgbToLinear(float c) noexcept {
    if (c <= 0.04045f) return c / 12.92f;
    return std::pow((c + 0.055f) / 1.055f, 2.4f);
}

inline uint8_t clampByte(float v) noexcept {
    if (v <= 0.0f) return 0;
    if (v >= 255.0f) return 255;
    return static_cast<uint8_t>(v + 0.5f);
}

} // namespace

float ColorScienceEngine::linearToLog(float x, TransferCurveType curve) noexcept {
    switch (curve) {
        case TransferCurveType::ArriLogC3: {
            // ARRI LogC3 EI 800
            constexpr float cut = 0.010591f;
            constexpr float a = 5.555556f;
            constexpr float b = 0.052272f;
            constexpr float c = 0.247190f;
            constexpr float d = 0.385537f;
            constexpr float e = 5.367655f;
            constexpr float f = 0.092809f;
            return (x > cut) ? (c * std::log10(a * x + b) + d) : (e * x + f);
        }
        case TransferCurveType::ArriLogC4: {
            // ARRI LogC4 official specification
            constexpr float a = 2231.8263090676883f;   // (2^18 - 16) / 117.45
            constexpr float b = 64.0f;                 // (1023 - 95) / 1023
            constexpr float c = 0.0647954196341293f;   // 95 / 1023
            constexpr float s = 0.1135972086105891f;   // (7 * ln(2) * 2^(7 - 14*c/b)) / (a * s)
            constexpr float t = -0.0180569961199113f;  // (2^(14*(-c/b) + 6) - 64) / a
            if (x >= t) {
                return (std::log2(a * x + b) - 6.0f) / 14.0f * (1.0f - c) + c;
            } else {
                return (x - t) / s;
            }
        }
        case TransferCurveType::SonySLog3: {
            if (x >= 0.01125000f) {
                return (420.0f + std::log10((x + 0.01f) / (0.18f + 0.01f)) * 261.5f) / 1023.0f;
            } else {
                return (x * (171.2102946929f - 95.0f) / 0.01125000f + 95.0f) / 1023.0f;
            }
        }
        case TransferCurveType::CanonCLog2: {
            constexpr float c1 = 0.24136077f;
            constexpr float c2 = 87.09937546f;
            constexpr float c3 = 0.092864125f;
            if (x < 0.0f) {
                return -c1 * std::log10(-x * c2 + 1.0f) + c3;
            } else {
                return c1 * std::log10(x * c2 + 1.0f) + c3;
            }
        }
        case TransferCurveType::RedLog3G10: {
            constexpr float a = 0.224282f;
            constexpr float b = 155.975327f;
            constexpr float c = 0.01f;
            constexpr float g = 15.1927f;
            float xShift = x + c;
            if (xShift < 0.0f) {
                return xShift * g;
            }
            return a * std::log10(xShift * b + 1.0f);
        }
        case TransferCurveType::AppleLog: {
            constexpr float R0 = -0.05641088f;
            constexpr float Rt = 0.01f;
            constexpr float c = 47.28711236f;
            constexpr float beta = 0.00964052f;
            constexpr float gamma = 0.08550479f;
            constexpr float delta = 0.69336945f;
            if (x < R0) return 0.0f;
            if (x < Rt) return c * (x - R0) * (x - R0);
            return gamma * std::log2(x + beta) + delta;
        }
        case TransferCurveType::Hlg: {
            // ARIB STD-B67 HLG
            constexpr float a = 0.17883277f;
            constexpr float b = 0.28466892f;
            constexpr float c = 0.55991073f;
            if (x <= 0.0f) return 0.0f;
            if (x <= 1.0f / 12.0f) return std::sqrt(3.0f * x);
            return a * std::log(12.0f * x - b) + c;
        }
        case TransferCurveType::Pq: {
            // SMPTE ST 2084 PQ
            constexpr float m1 = 2610.0f / 16384.0f;
            constexpr float m2 = 2523.0f / 32.0f;
            constexpr float c1 = 3424.0f / 4096.0f;
            constexpr float c2 = 2413.0f / 128.0f;
            constexpr float c3 = 2392.0f / 128.0f;
            float y = std::max(0.0f, x);
            float ym1 = std::pow(y, m1);
            return std::pow((c1 + c2 * ym1) / (1.0f + c3 * ym1), m2);
        }
        case TransferCurveType::Rec709:
        default: {
            if (x < 0.0f) return 0.0f;
            return (x < 0.018f) ? (4.5f * x) : (1.099f * std::pow(x, 0.45f) - 0.099f);
        }
    }
}

float ColorScienceEngine::logToLinear(float y, TransferCurveType curve) noexcept {
    switch (curve) {
        case TransferCurveType::ArriLogC3: {
            constexpr float cut = 0.010591f;
            constexpr float a = 5.555556f;
            constexpr float b = 0.052272f;
            constexpr float c = 0.247190f;
            constexpr float d = 0.385537f;
            constexpr float e = 5.367655f;
            constexpr float f = 0.092809f;
            constexpr float yCut = e * cut + f;
            return (y > yCut) ? ((std::pow(10.0f, (y - d) / c) - b) / a) : ((y - f) / e);
        }
        case TransferCurveType::ArriLogC4: {
            constexpr float a = 2231.8263090676883f;
            constexpr float c = 0.0647954196341293f;
            constexpr float s = 0.1135972086105891f;
            constexpr float t = -0.0180569961199113f;
            if (y >= 0.0f) {
                const float expTerm = ((y - c) / (1.0f - c)) * 14.0f + 6.0f;
                return (std::pow(2.0f, expTerm) - 64.0f) / a;
            } else {
                return y * s + t;
            }
        }
        case TransferCurveType::SonySLog3: {
            constexpr float yCut = 171.2102946929f / 1023.0f;
            if (y >= yCut) {
                return std::pow(10.0f, (y * 1023.0f - 420.0f) / 261.5f) * (0.18f + 0.01f) - 0.01f;
            } else {
                return (y * 1023.0f - 95.0f) * 0.01125000f / (171.2102946929f - 95.0f);
            }
        }
        case TransferCurveType::CanonCLog2: {
            constexpr float c1 = 0.24136077f;
            constexpr float c2 = 87.09937546f;
            constexpr float c3 = 0.092864125f;
            if (y < c3) {
                return -(std::pow(10.0f, (c3 - y) / c1) - 1.0f) / c2;
            } else {
                return (std::pow(10.0f, (y - c3) / c1) - 1.0f) / c2;
            }
        }
        case TransferCurveType::RedLog3G10: {
            constexpr float a = 0.224282f;
            constexpr float b = 155.975327f;
            constexpr float c = 0.01f;
            constexpr float g = 15.1927f;
            if (y < 0.0f) {
                return (y / g) - c;
            }
            return ((std::pow(10.0f, y / a) - 1.0f) / b) - c;
        }
        case TransferCurveType::AppleLog: {
            constexpr float R0 = -0.05641088f;
            constexpr float Rt = 0.01f;
            constexpr float c = 47.28711236f;
            constexpr float beta = 0.00964052f;
            constexpr float gamma = 0.08550479f;
            constexpr float delta = 0.69336945f;
            constexpr float yCut = c * (Rt - R0) * (Rt - R0);
            if (y <= 0.0f) return R0;
            if (y < yCut) return std::sqrt(y / c) + R0;
            return std::pow(2.0f, (y - delta) / gamma) - beta;
        }
        case TransferCurveType::Hlg: {
            constexpr float a = 0.17883277f;
            constexpr float b = 0.28466892f;
            constexpr float c = 0.55991073f;
            if (y <= 0.0f) return 0.0f;
            if (y <= 0.5f) return (y * y) / 3.0f;
            return (std::exp((y - c) / a) + b) / 12.0f;
        }
        case TransferCurveType::Pq: {
            constexpr float m1 = 2610.0f / 16384.0f;
            constexpr float m2 = 2523.0f / 32.0f;
            constexpr float c1 = 3424.0f / 4096.0f;
            constexpr float c2 = 2413.0f / 128.0f;
            constexpr float c3 = 2392.0f / 128.0f;
            if (y <= 0.0f) return 0.0f;
            float ym2 = std::pow(y, 1.0f / m2);
            float num = std::max(0.0f, ym2 - c1);
            float den = std::max(1e-7f, c2 - c3 * ym2);
            return std::pow(num / den, 1.0f / m1);
        }
        case TransferCurveType::Rec709:
        default: {
            if (y < 0.0f) return 0.0f;
            return (y < 0.081f) ? (y / 4.5f) : std::pow((y + 0.099f) / 1.099f, 1.0f / 0.45f);
        }
    }
}

void ColorScienceEngine::applyCdl(
    uint8_t* __restrict__ pixelData,
    int width,
    int height,
    int channels,
    const AscCdlParams& cdl,
    float blend
) noexcept {
    if (!pixelData || width <= 0 || height <= 0 || channels < 3) return;
    const float alpha = std::clamp(blend, 0.0f, 1.0f);
    if (alpha <= 0.001f) return;

    // Precompute per-channel 256-entry Slope/Offset/Power LUTs (eliminates 3x std::pow per pixel!)
    float lutR[256], lutG[256], lutB[256];
    for (int i = 0; i < 256; ++i) {
        const float v = static_cast<float>(i) / 255.0f;
        const float rPre = std::max(0.0f, v * cdl.slopeR + cdl.offsetR);
        const float gPre = std::max(0.0f, v * cdl.slopeG + cdl.offsetG);
        const float bPre = std::max(0.0f, v * cdl.slopeB + cdl.offsetB);
        lutR[i] = std::pow(rPre, cdl.powerR);
        lutG[i] = std::pow(gPre, cdl.powerG);
        lutB[i] = std::pow(bPre, cdl.powerB);
    }

    const float sat = cdl.saturation;
    const bool applySat = std::fabs(sat - 1.0f) > 0.001f;
    const bool partialBlend = alpha < 0.999f;
    const float invAlpha = 1.0f - alpha;
    const size_t rowStride = static_cast<size_t>(width) * static_cast<size_t>(channels);

    ThreadPool::instance().parallelFor(height, [&](int yStart, int yEnd) {
        for (int y = yStart; y < yEnd; ++y) {
            uint8_t* row = pixelData + static_cast<size_t>(y) * rowStride;
            for (int x = 0; x < width; ++x) {
                const size_t idx = static_cast<size_t>(x) * channels;
                const uint8_t origR = row[idx + 0];
                const uint8_t origG = row[idx + 1];
                const uint8_t origB = row[idx + 2];

                float r = lutR[origR];
                float g = lutG[origG];
                float b = lutB[origB];

                if (applySat) {
                    const float luma = 0.2126f * r + 0.7152f * g + 0.0722f * b;
                    r = luma + sat * (r - luma);
                    g = luma + sat * (g - luma);
                    b = luma + sat * (b - luma);
                }

                float outR = std::clamp(r, 0.0f, 1.0f) * 255.0f;
                float outG = std::clamp(g, 0.0f, 1.0f) * 255.0f;
                float outB = std::clamp(b, 0.0f, 1.0f) * 255.0f;

                if (partialBlend) {
                    outR = origR * invAlpha + outR * alpha;
                    outG = origG * invAlpha + outG * alpha;
                    outB = origB * invAlpha + outB * alpha;
                }

                row[idx + 0] = clampByte(outR);
                row[idx + 1] = clampByte(outG);
                row[idx + 2] = clampByte(outB);
            }
        }
    });
}

void ColorScienceEngine::applyTransferCurve(
    uint8_t* __restrict__ pixelData,
    int width,
    int height,
    int channels,
    TransferCurveType curve,
    float blend
) noexcept {
    if (!pixelData || width <= 0 || height <= 0 || channels < 3) return;
    const float alpha = std::clamp(blend, 0.0f, 1.0f);
    if (alpha <= 0.001f) return;

    // Precompute exact 256-entry sRGB -> scene-linear -> broadcast transfer curve LUT
    uint8_t lut[256];
    for (int i = 0; i < 256; ++i) {
        const float srgb = static_cast<float>(i) / 255.0f;
        const float lin = srgbToLinear(srgb);
        const float encoded = std::clamp(linearToLog(lin, curve), 0.0f, 1.0f);
        const float mixed = srgb * (1.0f - alpha) + encoded * alpha;
        lut[i] = clampByte(mixed * 255.0f);
    }

    const size_t rowStride = static_cast<size_t>(width) * static_cast<size_t>(channels);
    ThreadPool::instance().parallelFor(height, [&](int yStart, int yEnd) {
        for (int y = yStart; y < yEnd; ++y) {
            uint8_t* row = pixelData + static_cast<size_t>(y) * rowStride;
            for (int x = 0; x < width; ++x) {
                const size_t idx = static_cast<size_t>(x) * channels;
                row[idx + 0] = lut[row[idx + 0]];
                row[idx + 1] = lut[row[idx + 1]];
                row[idx + 2] = lut[row[idx + 2]];
            }
        }
    });
}

} // namespace commix
