#pragma once

#include <cstdint>
#include <string>

namespace commix {

enum class ToneCurvePreset {
    Linear,
    FilmPrint,
    CineD,
    Neutral
};

enum class GrainPreset {
    Off,
    Fine35mm,
    Medium16mm,
    Coarse8mm
};

/**
 * Process Zero and computational image processing pipeline in C++20.
 */
class ImagePipeline {
public:
    static void initToneLuts() noexcept;

    /**
     * Applies Process Zero tone mapping and analog silver halide grain simulation
     * directly to RGBA/RGB byte buffers with zero heap allocations.
     *
     * @param toneIntensity scales tone curve strength: 0.0 = identity bypass,
     *                      1.0 = full curve, up to 2.0 = accentuated contrast.
     */
    static bool processZero(
        uint8_t* __restrict__ pixelData,
        int width,
        int height,
        int channels,
        ToneCurvePreset curve,
        GrainPreset grain,
        float grainIntensity,
        float toneIntensity = 1.0f
    ) noexcept;

    /**
     * Multi-threaded separable Gaussian unsharp mask sharpening filter.
     * Uses half the memory of a naive two-copy implementation and distributes
     * both horizontal and vertical passes across the C++20 ThreadPool.
     */
    static void applyUnsharpMask(
        uint8_t* __restrict__ pixelData,
        int width,
        int height,
        int channels,
        float amount,
        float radius
    ) noexcept;

    /**
     * Autonomous C++20 Aether Master Finishing Pass:
     * 3x3 edge-preserving bilateral denoise + adaptive shadow recovery +
     * filmic highlight shoulder roll-off + halo-free micro-contrast clarity.
     */
    static bool aetherMasterEnhance(
        uint8_t* __restrict__ pixelData,
        int width,
        int height,
        int channels,
        float denoiseStrength,
        float clarityStrength,
        float shadowLift,
        float highlightRollOff
    ) noexcept;

    /**
     * Linear-light virtual ISO / shutter gain extension beyond phone HAL ceilings,
     * with shadow noise-floor gating and ACES-inspired highlight shoulder compression.
     */
    static bool applyVirtualGainAndTone(
        uint8_t* __restrict__ pixelData,
        int width,
        int height,
        int channels,
        float gainLinear
    ) noexcept;

private:
    static uint8_t s_filmPrintLut[256];
    static uint8_t s_cineDLut[256];
    static uint8_t s_neutralLut[256];
    static bool s_lutsInitialized;
};

} // namespace commix
