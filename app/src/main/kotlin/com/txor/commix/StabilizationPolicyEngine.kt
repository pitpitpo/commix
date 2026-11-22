package com.txor.commix

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Unified Hardware + Software Stabilization Policy Engine.
 *
 * Resolves exact Camera2 HAL3 hardware controls (`LENS_OPTICAL_STABILIZATION_MODE`,
 * `CONTROL_VIDEO_STABILIZATION_MODE` including Android 13+ `PREVIEW_STABILIZATION`),
 * software IMU Auto-Level horizon straightening, and Gyroflow `.gcsv` sidecar export
 * based on:
 *  1. Probed hardware capabilities of the active lens (`hasOis`, `hasEis`, `hasPreviewStabilization`)
 *  2. User's chosen stabilization engine (`off`, `hardware_ois`, `software_eis`, `hybrid`, `gyro_post`)
 *  3. User's chosen application timing (`always`, `recording_only`, `preview_only`, `post_export`)
 *  4. Current capture phase (`PREVIEW`, `VIDEO_RECORDING`, `STILL_CAPTURE`) and active camera mode
 *
 * Pure Kotlin logic — 100% JVM unit testable with zero Android framework coupling.
 */
object StabilizationPolicyEngine {

    // Stabilization Engine Modes
    const val ENGINE_OFF = "off"
    const val ENGINE_HARDWARE_OIS = "hardware_ois"
    const val ENGINE_SOFTWARE_EIS = "software_eis"
    const val ENGINE_HYBRID = "hybrid"
    const val ENGINE_GYRO_POST = "gyro_post"

    // When Stabilization Is Applied
    const val APPLY_ALWAYS = "always"
    const val APPLY_RECORDING_ONLY = "recording_only"
    const val APPLY_PREVIEW_ONLY = "preview_only"
    const val APPLY_POST_EXPORT = "post_export"

    // Camera2 HAL3 integer constants (mirrors CaptureRequest.*)
    const val HAL_OIS_OFF = 0
    const val HAL_OIS_ON = 1
    const val HAL_EIS_OFF = 0
    const val HAL_EIS_ON = 1
    const val HAL_EIS_PREVIEW_STABILIZATION = 2 // API 33+ CONTROL_VIDEO_STABILIZATION_MODE_PREVIEW_STABILIZATION

    const val AUTO_LEVEL_DEADBAND_DEG = 0.4f
    const val AUTO_LEVEL_MAX_DEG = 20.0f

    enum class CapturePhase {
        PREVIEW,
        STILL_CAPTURE,
        VIDEO_RECORDING
    }

    data class HardwareCapabilities(
        val hasOis: Boolean,
        val hasEis: Boolean,
        val hasPreviewStabilization: Boolean,
        val apiLevel: Int = 33
    )

    data class StabilizationPlan(
        val halOisMode: Int,
        val halVideoStabilizationMode: Int,
        val shouldLogGyroflow: Boolean,
        val shouldApplyStillAutoLevel: Boolean,
        val usedSoftwareFallback: Boolean,
        val activePipelineLabel: String
    )

    data class CropRectPx(
        val left: Int,
        val top: Int,
        val width: Int,
        val height: Int
    )

    val ALL_ENGINE_MODES: List<Pair<String, String>> = listOf(
        ENGINE_OFF to "Off",
        ENGINE_HARDWARE_OIS to "HW OIS",
        ENGINE_SOFTWARE_EIS to "SW EIS",
        ENGINE_HYBRID to "Hybrid",
        ENGINE_GYRO_POST to "Gyro Post"
    )

    val ALL_APPLY_TIMINGS: List<Pair<String, String>> = listOf(
        APPLY_ALWAYS to "Always",
        APPLY_RECORDING_ONLY to "Capture Only",
        APPLY_PREVIEW_ONLY to "Preview Only",
        APPLY_POST_EXPORT to "Post Only"
    )

    fun normalizeEngineMode(raw: String?): String = when (raw?.lowercase()) {
        ENGINE_OFF -> ENGINE_OFF
        ENGINE_HARDWARE_OIS, "ois" -> ENGINE_HARDWARE_OIS
        ENGINE_SOFTWARE_EIS, "eis", "standard", "super_steady" -> ENGINE_SOFTWARE_EIS
        ENGINE_HYBRID, "legend", "hw_sw" -> ENGINE_HYBRID
        ENGINE_GYRO_POST, "gyroflow", "post" -> ENGINE_GYRO_POST
        else -> ENGINE_HYBRID
    }

    fun normalizeApplyWhen(raw: String?): String = when (raw?.lowercase()) {
        APPLY_ALWAYS -> APPLY_ALWAYS
        APPLY_RECORDING_ONLY, "capture_only", "record_only" -> APPLY_RECORDING_ONLY
        APPLY_PREVIEW_ONLY -> APPLY_PREVIEW_ONLY
        APPLY_POST_EXPORT, "post_only" -> APPLY_POST_EXPORT
        else -> APPLY_ALWAYS
    }

    /**
     * Resolves the exact hardware + software stabilization plan for the given phase, mode,
     * capabilities, and user timing preference.
     */
    fun resolve(
        engineModeRaw: String,
        applyWhenRaw: String,
        phase: CapturePhase,
        activeCameraMode: String,
        caps: HardwareCapabilities,
        photoAutoLevelEnabled: Boolean,
        gyroflowExportEnabled: Boolean
    ): StabilizationPlan {
        val engine = normalizeEngineMode(engineModeRaw)
        val timing = normalizeApplyWhen(applyWhenRaw)

        // Panorama & Spatial spherical sweeps require uncropped optical geometry for IMU/SAD alignment.
        val isSweepMode = activeCameraMode == "PANORAMA" || activeCameraMode == "SPATIAL"

        if (engine == ENGINE_OFF) {
            return StabilizationPlan(
                halOisMode = HAL_OIS_OFF,
                halVideoStabilizationMode = HAL_EIS_OFF,
                shouldLogGyroflow = false,
                shouldApplyStillAutoLevel = false,
                usedSoftwareFallback = false,
                activePipelineLabel = "OFF (Tripod / Gimbal)"
            )
        }

        // Determine if real-time stabilization is allowed during this CapturePhase.
        val liveAllowedForPhase = when (timing) {
            APPLY_ALWAYS -> true
            APPLY_RECORDING_ONLY -> phase == CapturePhase.VIDEO_RECORDING || phase == CapturePhase.STILL_CAPTURE
            APPLY_PREVIEW_ONLY -> phase == CapturePhase.PREVIEW
            APPLY_POST_EXPORT -> false
            else -> true
        }

        val canUsePreviewStab = caps.hasPreviewStabilization && caps.apiLevel >= 33
        val bestLiveEis = when {
            isSweepMode -> HAL_EIS_OFF
            !liveAllowedForPhase -> HAL_EIS_OFF
            phase == CapturePhase.PREVIEW && canUsePreviewStab -> HAL_EIS_PREVIEW_STABILIZATION
            phase == CapturePhase.VIDEO_RECORDING && timing == APPLY_ALWAYS && canUsePreviewStab -> HAL_EIS_PREVIEW_STABILIZATION
            caps.hasEis -> HAL_EIS_ON
            canUsePreviewStab -> HAL_EIS_PREVIEW_STABILIZATION
            else -> HAL_EIS_OFF
        }

        var oisOut = HAL_OIS_OFF
        var eisOut = HAL_EIS_OFF
        var swFallback = false
        val label: String

        when (engine) {
            ENGINE_HARDWARE_OIS -> {
                if (caps.hasOis) {
                    oisOut = if (liveAllowedForPhase) HAL_OIS_ON else HAL_OIS_OFF
                    eisOut = HAL_EIS_OFF
                    label = if (oisOut == HAL_OIS_ON) "HW OIS (VCM)" else "HW OIS (Armed)"
                } else {
                    // Graceful software fallback when lens lacks physical OIS hardware.
                    swFallback = true
                    eisOut = bestLiveEis
                    label = if (eisOut != HAL_EIS_OFF) "SW EIS (OIS Fallback)" else "SW Gyro (OIS Fallback)"
                }
            }

            ENGINE_SOFTWARE_EIS -> {
                oisOut = HAL_OIS_OFF
                eisOut = bestLiveEis
                swFallback = (eisOut == HAL_EIS_OFF && liveAllowedForPhase && !isSweepMode)
                label = when {
                    isSweepMode -> "SW IMU (Sweep Safe)"
                    eisOut == HAL_EIS_PREVIEW_STABILIZATION -> "SW Preview EIS"
                    eisOut == HAL_EIS_ON -> "SW HAL EIS"
                    !liveAllowedForPhase -> "SW EIS (${formatTimingShort(timing)})"
                    else -> "SW Gyroflow IMU"
                }
            }

            ENGINE_HYBRID -> {
                oisOut = if (caps.hasOis && liveAllowedForPhase) HAL_OIS_ON else HAL_OIS_OFF
                eisOut = bestLiveEis
                swFallback = (!caps.hasOis && eisOut == HAL_EIS_OFF)
                label = when {
                    oisOut == HAL_OIS_ON && eisOut != HAL_EIS_OFF -> "HYBRID HW+SW"
                    oisOut == HAL_OIS_ON && isSweepMode -> "HW OIS (Sweep Safe)"
                    oisOut == HAL_OIS_ON -> "HW OIS + Gyro"
                    eisOut != HAL_EIS_OFF -> "SW EIS + Gyro"
                    !liveAllowedForPhase -> "HYBRID (${formatTimingShort(timing)})"
                    else -> "SW Gyroflow IMU"
                }
            }

            ENGINE_GYRO_POST -> {
                // Post-only workflow: zero live EIS crop so full sensor FOV is preserved for Gyroflow.
                oisOut = HAL_OIS_OFF
                eisOut = HAL_EIS_OFF
                label = "GYROFLOW POST (0% Crop)"
            }

            else -> {
                label = "HYBRID"
            }
        }

        // Gyroflow .gcsv logging runs during video recording whenever:
        //  - user chose GYRO_POST or APPLY_POST_EXPORT or APPLY_PREVIEW_ONLY (clean capture), OR
        //  - user kept gyroflowExportEnabled on with HYBRID/SW_EIS, OR
        //  - hardware fallback forced IMU stabilization.
        val logGyroflow = when {
            engine == ENGINE_GYRO_POST -> true
            timing == APPLY_POST_EXPORT -> true
            timing == APPLY_PREVIEW_ONLY -> true
            swFallback -> true
            else -> gyroflowExportEnabled
        }

        // Software still photo auto-level applies on still captures (never sweeps) when enabled
        // and timing allows capture/post processing.
        val allowStillAutoLevel = photoAutoLevelEnabled &&
            !isSweepMode &&
            (timing == APPLY_ALWAYS || timing == APPLY_RECORDING_ONLY || timing == APPLY_POST_EXPORT || engine == ENGINE_GYRO_POST)

        return StabilizationPlan(
            halOisMode = oisOut,
            halVideoStabilizationMode = eisOut,
            shouldLogGyroflow = logGyroflow,
            shouldApplyStillAutoLevel = allowStillAutoLevel,
            usedSoftwareFallback = swFallback,
            activePipelineLabel = label
        )
    }

    fun formatEngineShort(engineRaw: String): String = when (normalizeEngineMode(engineRaw)) {
        ENGINE_OFF -> "OFF"
        ENGINE_HARDWARE_OIS -> "OIS"
        ENGINE_SOFTWARE_EIS -> "EIS"
        ENGINE_HYBRID -> "HYBRID"
        ENGINE_GYRO_POST -> "GYRO"
        else -> "HYBRID"
    }

    fun formatTimingShort(timingRaw: String): String = when (normalizeApplyWhen(timingRaw)) {
        APPLY_ALWAYS -> "ALWAYS"
        APPLY_RECORDING_ONLY -> "CAP ONLY"
        APPLY_PREVIEW_ONLY -> "VF ONLY"
        APPLY_POST_EXPORT -> "POST"
        else -> "ALWAYS"
    }

    /**
     * Computes the largest centered axis-aligned rectangle (preserving original aspect ratio)
     * inscribed inside a [width] × [height] image rotated by [rollDeg] degrees, guaranteeing
     * zero black corner wedges after software horizon straightening.
     */
    fun computeInscribedCropRect(width: Int, height: Int, rollDeg: Float): CropRectPx {
        if (width <= 1 || height <= 1) return CropRectPx(0, 0, width.coerceAtLeast(0), height.coerceAtLeast(0))
        val clampedAbsDeg = abs(rollDeg).coerceAtMost(AUTO_LEVEL_MAX_DEG)
        if (clampedAbsDeg < AUTO_LEVEL_DEADBAND_DEG) {
            return CropRectPx(0, 0, width, height)
        }
        val rad = clampedAbsDeg.toDouble() * PI / 180.0
        val sinA = abs(sin(rad))
        val cosA = abs(cos(rad))
        val w = width.toDouble()
        val h = height.toDouble()

        // Scale factor s so that an s*w by s*h rectangle fits inside the original w by h frame
        // after rotating by `rad` (exact aspect-preserving inscribed rectangle):
        //   s * (w * cosA + h * sinA) <= w
        //   s * (w * sinA + h * cosA) <= h
        val scaleW = w / (w * cosA + h * sinA)
        val scaleH = h / (w * sinA + h * cosA)
        val scale = minOf(scaleW, scaleH).coerceIn(0.5, 1.0)

        val cropW = (w * scale).roundToInt().coerceIn(2, width)
        val cropH = (h * scale).roundToInt().coerceIn(2, height)
        val left = ((width - cropW) / 2).coerceAtLeast(0)
        val top = ((height - cropH) / 2).coerceAtLeast(0)
        return CropRectPx(left = left, top = top, width = cropW, height = cropH)
    }

    /**
     * Fast pure-JVM luma grid extractor from a YUV_420_888 Y-plane byte buffer.
     * Produces [gridW] × [gridH] IRE values in `0f..100f` for live False Color & Zebra overlays.
     */
    fun sampleLumaIreGrid(
        yPlane: ByteArray,
        width: Int,
        height: Int,
        rowStride: Int,
        gridW: Int = 32,
        gridH: Int = 24
    ): FloatArray {
        val out = FloatArray(gridW * gridH)
        if (width <= 0 || height <= 0 || rowStride < width || yPlane.isEmpty()) return out
        val maxIdx = yPlane.size - 1
        for (gy in 0 until gridH) {
            val srcY = (((gy + 0.5f) / gridH) * height).toInt().coerceIn(0, height - 1)
            val rowBase = srcY * rowStride
            for (gx in 0 until gridW) {
                val srcX = (((gx + 0.5f) / gridW) * width).toInt().coerceIn(0, width - 1)
                val idx = rowBase + srcX
                val luma = if (idx in 0..maxIdx) (yPlane[idx].toInt() and 0xFF) else 0
                out[gy * gridW + gx] = (luma / 255f) * 100f
            }
        }
        return out
    }

    /**
     * Evaluates smooth cubic ease-in-out focus pull progress between [pointADiopters] and [pointBDiopters].
     */
    fun evaluateFocusPullDiopters(pointADiopters: Float, pointBDiopters: Float, progress01: Float): Float {
        val t = progress01.coerceIn(0f, 1f)
        val eased = if (t < 0.5f) {
            4f * t * t * t
        } else {
            val u = -2f * t + 2f
            1f - (u * u * u) / 2f
        }
        return pointADiopters + (pointBDiopters - pointADiopters) * eased
    }
}
