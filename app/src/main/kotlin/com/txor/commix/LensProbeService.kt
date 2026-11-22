package com.txor.commix

import android.graphics.ImageFormat
import android.graphics.Rect
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.util.Range
import android.util.Size
import java.util.HashMap
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Result data transfer object for probed camera capabilities.
 * 100% dynamic, containing hardware parameters queried directly from Camera2 HAL3.
 */
data class CameraProbeResult(
    val cameraId: String,
    val lensFacing: Int,
    val isFront: Boolean,
    val isDepth: Boolean,
    val isMacro: Boolean,
    val isUltraWide: Boolean,
    val isTelephoto: Boolean,
    val hasFlash: Boolean,
    val hasOis: Boolean,
    val hasEis: Boolean,
    val hasAf: Boolean,
    val hasRaw: Boolean,
    val sensorOrientation: Int,
    val activeArraySize: Rect,
    val isoRange: Range<Int>?,
    val expRange: Range<Long>?,
    val focusMaxDiopters: Float,
    val evRange: Range<Int>?,
    val evStep: Float,
    val maxDigitalZoom: Double,
    val zoomRatioRange: Range<Float>?,
    val standardJpegSize: Size,
    val hiResJpegSize: Size,
    val sensorMp: Double,
    val apertures: FloatArray,
    val focalLengths: FloatArray,
    val supportedJpegSizes: List<Size> = emptyList(),
    val parentLogicalId: String? = null,
    val physicalCameraIds: List<String> = emptyList(),
    val isMonochrome: Boolean = false,
    val isPeriscope: Boolean = false,
    val isUltraHighRes: Boolean = false,
    val supports10Bit: Boolean = false,
    val hardwareLevel: Int = CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED,
    val rawFocalLengthMm: Float = 0f,
    val opticalZoomFactor: Float = 1.0f,
    val rawSensorSize: Size? = null,
    val maxFps: Double = 30.0,
    val discoverySource: String = "HAL",
    val hasPreviewStabilization: Boolean = false
)

/**
 * High-performance universal hardware probe service for Camera2 HAL3 characteristics.
 * Discovers official logical IDs, physical sub-cameras (for Physical-via-Logical routing),
 * concurrent camera sets, and hidden OEM auxiliary camera IDs across all Android vendors.
 */
object LensProbeService {

    /**
     * Probes all available camera sensors dynamically and returns typed CameraProbeResult objects
     * with normalized optical zoom factors relative to the primary rear Wide camera.
     */
    fun probeCameraResults(manager: CameraManager): List<CameraProbeResult> {
        val out = mutableListOf<CameraProbeResult>()
        val ids = java.util.Collections.synchronizedSet(LinkedHashSet<String>())
        val physicalParentMap = java.util.concurrent.ConcurrentHashMap<String, String>()
        val discoverySourceMap = java.util.concurrent.ConcurrentHashMap<String, String>()

        // 1. Immediate priority: official standard visible camera IDs
        val officialIds = try { manager.cameraIdList.toList() } catch (_: Exception) { emptyList() }
        for (id in officialIds) {
            ids.add(id)
            discoverySourceMap.putIfAbsent(id, "HAL")
        }

        // 2. Physical sub-camera IDs from logical multi-camera arrays (API 28+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            for (logicalId in officialIds) {
                try {
                    val c = manager.getCameraCharacteristics(logicalId)
                    for (physId in c.physicalCameraIds) {
                        ids.add(physId)
                        physicalParentMap.putIfAbsent(physId, logicalId)
                        discoverySourceMap.putIfAbsent(physId, "PHYSICAL")
                    }
                } catch (_: Exception) {}
            }
        }

        // 3. Concurrent camera combinations (API 30+) can expose aux/dual-lens IDs
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                for (group in manager.concurrentCameraIds) {
                    for (cid in group) {
                        ids.add(cid)
                        discoverySourceMap.putIfAbsent(cid, "CONCURRENT")
                    }
                }
            } catch (_: Exception) {}
        }

        // 4. Fast initial probe of common OEM/aux IDs (Qualcomm/MediaTek/Samsung/Xiaomi/OnePlus/Vivo)
        val initialAuxCandidates = ((0..24) + listOf(40, 41) + (50..58) + (60..65) + (100..105)).map { it.toString() }
        for (idStr in initialAuxCandidates) {
            if (!ids.contains(idStr)) {
                try {
                    val c = manager.getCameraCharacteristics(idStr)
                    ids.add(idStr)
                    discoverySourceMap.putIfAbsent(idStr, "OEM_AUX")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        try {
                            for (physId in c.physicalCameraIds) {
                                ids.add(physId)
                                physicalParentMap.putIfAbsent(physId, idStr)
                                discoverySourceMap.putIfAbsent(physId, "PHYSICAL")
                            }
                        } catch (_: Exception) {}
                    }
                } catch (_: Exception) {}
            }
        }

        val sortedIds = ids.sortedWith(Comparator { a, b ->
            val aInt = a.toIntOrNull()
            val bInt = b.toIntOrNull()
            when {
                aInt != null && bInt != null -> aInt.compareTo(bInt)
                aInt != null -> -1
                bInt != null -> 1
                else -> a.compareTo(b)
            }
        })

        for (id in sortedIds) {
            val parentLogical = physicalParentMap[id]?.takeIf { it != id }
            val src = discoverySourceMap[id] ?: "HAL"
            buildCameraProbeResult(manager, id, parentLogical, src)?.let { out.add(it) }
        }

        return normalizeOpticalZoomFactors(out)
    }

    /**
     * Scans exhaustive OEM auxiliary camera ID ranges (0..255 + vendor aux blocks 500..512, 1000..1005)
     * plus any physical sub-cameras discovered on background threads without blocking startup.
     */
    fun startBackgroundIdSpam(
        manager: CameraManager,
        knownIds: Set<String>,
        onDiscovered: (CameraProbeResult) -> Unit
    ) {
        val executor = java.util.concurrent.Executors.newFixedThreadPool(4)
        val candidateIds = ((0..255).map { it.toString() } +
            (500..512).map { it.toString() } +
            (1000..1005).map { it.toString() }).distinct()
        val seen = java.util.Collections.synchronizedSet(HashSet(knownIds))

        val tasks = candidateIds.filter { !seen.contains(it) }.map { idStr ->
            java.util.concurrent.Callable<Unit> {
                try {
                    val c = manager.getCameraCharacteristics(idStr)
                    if (seen.add(idStr)) {
                        buildCameraProbeResult(manager, idStr, parentLogicalId = null, discoverySource = "DEEP_SCAN")
                            ?.let { onDiscovered(it) }
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        for (physId in c.physicalCameraIds) {
                            if (seen.add(physId)) {
                                buildCameraProbeResult(manager, physId, parentLogicalId = idStr, discoverySource = "PHYSICAL")
                                    ?.let { onDiscovered(it) }
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
        }

        Thread {
            try {
                executor.invokeAll(tasks)
            } catch (_: Exception) {
            } finally {
                executor.shutdown()
            }
        }.apply {
            name = "Commix-Lens-Probe-Worker"
            isDaemon = true
            start()
        }
    }

    fun buildCameraProbeResult(
        manager: CameraManager,
        id: String,
        parentLogicalId: String? = null,
        discoverySource: String = "HAL"
    ): CameraProbeResult? {
        val c = try { manager.getCameraCharacteristics(id) } catch (_: Exception) { return null }
        val streamMap = c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: return null
        val stSizes = streamMap.getOutputSizes(android.graphics.SurfaceTexture::class.java)
        val jpegDirectSizes = streamMap.getOutputSizes(ImageFormat.JPEG)
        val rawDirectSizes = try { streamMap.getOutputSizes(ImageFormat.RAW_SENSOR) } catch (_: Throwable) { null }
        val hasAnyUsableStream = !stSizes.isNullOrEmpty() || !jpegDirectSizes.isNullOrEmpty() || !rawDirectSizes.isNullOrEmpty()
        if (!hasAnyUsableStream) {
            // Cannot provide preview, still, or raw output; skip non-imaging hardware ID
            return null
        }

        val caps = c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: IntArray(0)
        val isOfficialId = try { manager.cameraIdList.contains(id) } catch (_: Exception) { false }
        val isBackwardCompatible = caps.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE)
        val isMonochrome = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            caps.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MONOCHROME)
        } else false
        // Allow physical sub-cameras (routed via parentLogicalId) and monochrome sensors even when OEM hides BACKWARD_COMPATIBLE
        if (!isOfficialId && !isBackwardCompatible && parentLogicalId == null && !isMonochrome) {
            return null
        }

        val facing = c.get(CameraCharacteristics.LENS_FACING) ?: CameraCharacteristics.LENS_FACING_BACK
        val isFront = (facing == CameraCharacteristics.LENS_FACING_FRONT)
        val fls = c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS) ?: FloatArray(0)
        val rawFocalMm = if (fls.isNotEmpty()) fls[0] else 0f
        val sensorSize = c.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)

        // Dynamic 35mm equivalent focal length computation
        var eq = 0f
        if (sensorSize != null && fls.isNotEmpty()) {
            val diag = sqrt(sensorSize.width * sensorSize.width + sensorSize.height * sensorSize.height)
            eq = if (diag > 0f) fls[0] * (43.2666f / diag) else fls[0] * 5.0f
        } else if (fls.isNotEmpty()) {
            eq = fls[0] * 5.0f
        }
        val displayFocal = if (eq > 0f) eq.roundToInt().toFloat() else (if (fls.isNotEmpty()) fls[0] else 24f)

        val minFocus = c.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0f

        // Dynamic optical lens classification - ZERO hardcoded camera IDs!
        val isDepth = caps.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_DEPTH_OUTPUT) && !isBackwardCompatible && !isMonochrome
        val classification = classifyLensOptics(
            isFront = isFront,
            isDepth = isDepth,
            minFocusDiopters = minFocus,
            displayFocal35mm = displayFocal,
            sensorWidthMm = sensorSize?.width ?: 6.4f
        )
        val isMacro = classification.isMacro
        val isUltraWide = classification.isUltraWide
        val isTele = classification.isTelephoto
        val isPeriscope = isTele && displayFocal >= 90f

        val hasOis = (c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION) ?: IntArray(0))
            .contains(CameraCharacteristics.LENS_OPTICAL_STABILIZATION_MODE_ON)
        val videoStabModes = c.get(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES) ?: IntArray(0)
        val hasEis = videoStabModes.contains(CameraCharacteristics.CONTROL_VIDEO_STABILIZATION_MODE_ON)
        val hasPreviewStabilization = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            videoStabModes.contains(CameraCharacteristics.CONTROL_VIDEO_STABILIZATION_MODE_PREVIEW_STABILIZATION)
        } else false
        val hasAf = minFocus > 0f
        val hasRaw = caps.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW)
        val isUltraHighRes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            caps.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_ULTRA_HIGH_RESOLUTION_SENSOR)
        } else false
        val supports10Bit = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            caps.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_DYNAMIC_RANGE_TEN_BIT)
        } else false
        val hardwareLevel = c.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
            ?: CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED
        val hasFlash = c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false

        val physicalIds = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try { c.physicalCameraIds.toList() } catch (_: Exception) { emptyList() }
        } else emptyList()

        val sensorOrientation = c.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90
        val activeArray = c.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE) ?: Rect(0, 0, 0, 0)
        val pixelArray = c.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)
        val isoRange = c.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
        val expRange = c.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
        val evRange = c.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)
        val evStepRational = c.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP)
        val evStep = if (evStepRational != null && evStepRational.denominator != 0) {
            evStepRational.numerator.toFloat() / evStepRational.denominator.toFloat()
        } else 0.1f

        val maxDigitalZoom = (c.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM)?.toDouble() ?: 1.0).coerceAtLeast(1.0)
        val zoomRatioRange = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            c.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)
        } else null

        val allJpegSizes = mutableSetOf<Size>()
        val allRawSizes = mutableSetOf<Size>()
        streamMap.getOutputSizes(ImageFormat.JPEG)?.let { allJpegSizes.addAll(it) }

        // High resolution outputs (API 23+)
        try {
            streamMap.getHighResolutionOutputSizes(ImageFormat.JPEG)?.let { allJpegSizes.addAll(it) }
        } catch (_: Throwable) {}

        // RAW and Quad-Bayer / remosaic sensor streams
        try {
            streamMap.getOutputSizes(ImageFormat.RAW_SENSOR)?.let {
                allJpegSizes.addAll(it)
                allRawSizes.addAll(it)
            }
            streamMap.getOutputSizes(ImageFormat.RAW10)?.let { allJpegSizes.addAll(it) }
            streamMap.getHighResolutionOutputSizes(ImageFormat.RAW_SENSOR)?.let {
                allJpegSizes.addAll(it)
                allRawSizes.addAll(it)
            }
        } catch (_: Throwable) {}

        // Android 12+ Maximum Resolution Configuration Map (API 31+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val maxMap = c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP_MAXIMUM_RESOLUTION)
                maxMap?.getOutputSizes(ImageFormat.JPEG)?.let { allJpegSizes.addAll(it) }
                maxMap?.getOutputSizes(ImageFormat.RAW_SENSOR)?.let {
                    allJpegSizes.addAll(it)
                    allRawSizes.addAll(it)
                }
            } catch (_: Throwable) {}
            try {
                val maxActive = c.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE_MAXIMUM_RESOLUTION)
                if (maxActive != null && maxActive.width() > 0 && maxActive.height() > 0) {
                    allJpegSizes.add(Size(maxActive.width(), maxActive.height()))
                }
            } catch (_: Throwable) {}
            try {
                val maxPixel = c.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE_MAXIMUM_RESOLUTION)
                if (maxPixel != null && maxPixel.width > 0 && maxPixel.height > 0) {
                    allJpegSizes.add(Size(maxPixel.width, maxPixel.height))
                }
            } catch (_: Throwable) {}
        }

        // Hardware Active Array & Pixel Array Sizes (directly from sensor HAL3)
        if (activeArray.width() > 0 && activeArray.height() > 0) {
            allJpegSizes.add(Size(activeArray.width(), activeArray.height()))
        }
        if (pixelArray != null && pixelArray.width > 0 && pixelArray.height > 0) {
            allJpegSizes.add(Size(pixelArray.width, pixelArray.height))
        }

        // Xiaomi / Samsung / MediaTek / Vivo / OnePlus Vendor Super-Resolution Stream Configurations
        val superResKeys = listOf(
            "com.xiaomi.scaler.availableSuperResolutionStreamConfigurations",
            "xiaomi.scaler.availableSuperResolutionStreamConfigurations",
            "com.xiaomi.scaler.availableStreamConfigurations",
            "com.samsung.android.scaler.availableStreamConfigurations",
            "com.mediatek.scaler.availableStreamConfigurations",
            "org.quic.camera2.streamconfigs.HDRVideoMode",
            "com.oplus.available.highres.stream.configurations"
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            for (keyName in superResKeys) {
                try {
                    val key = CameraCharacteristics.Key<IntArray>(keyName, IntArray::class.java)
                    val arr = c.get(key)
                    if (arr != null) {
                        var i = 0
                        while (i + 3 < arr.size) {
                            val fmt = arr[i]
                            val w = arr[i + 1]
                            val h = arr[i + 2]
                            if (w > 0 && h > 0 && (fmt == 33 || fmt == 35 || fmt == 256 || fmt == 0x21 || fmt == 0x23)) {
                                allJpegSizes.add(Size(w, h))
                            }
                            i += 4
                        }
                    }
                } catch (_: Throwable) {}
            }
        }

        // Dynamically scan all available characteristics keys for superresolution / remosaic configurations
        try {
            for (k in c.keys) {
                val kname = k.name.lowercase()
                if (kname.contains("superresolution") || kname.contains("remosaic")) {
                    try {
                        val arr = c.get(k) as? IntArray
                        if (arr != null) {
                            var i = 0
                            while (i + 3 < arr.size) {
                                val fmt = arr[i]
                                val w = arr[i + 1]
                                val h = arr[i + 2]
                                if (w > 0 && h > 0 && (fmt == 33 || fmt == 35 || fmt == 256 || fmt == 0x21 || fmt == 0x23)) {
                                    allJpegSizes.add(Size(w, h))
                                }
                                i += 4
                            }
                        }
                    } catch (_: Throwable) {}
                }
            }
        } catch (_: Throwable) {}

        val sortedSizes = allJpegSizes
            .filter { it.width > 0 && it.height > 0 }
            .distinctBy { it.width.toLong() * it.height.toLong() }
            .sortedByDescending { it.width.toLong() * it.height.toLong() }

        val rawSensorSize = allRawSizes
            .filter { it.width > 0 && it.height > 0 }
            .maxByOrNull { it.width.toLong() * it.height.toLong() }

        val hiResJpeg = sortedSizes.firstOrNull()
            ?: Size(activeArray.width().coerceAtLeast(1920), activeArray.height().coerceAtLeast(1080))

        val standardJpeg = sortedSizes.filter { it.width <= 1920 && it.height <= 1440 }
            .firstOrNull() ?: hiResJpeg

        val maxHardwarePixels = calculateMaxHardwarePixels(
            activeW = activeArray.width().toLong(),
            activeH = activeArray.height().toLong(),
            pixelW = pixelArray?.width?.toLong() ?: 0L,
            pixelH = pixelArray?.height?.toLong() ?: 0L,
            hiResW = hiResJpeg.width.toLong(),
            hiResH = hiResJpeg.height.toLong()
        )
        val sensorMp = computeSensorMegapixels(maxHardwarePixels)

        val apertures = c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES) ?: FloatArray(0)

        // Compute true peak supported FPS across standard AE ranges + high-speed ranges
        var peakFps = 30.0
        try {
            c.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)?.forEach { r ->
                if (r.upper.toDouble() > peakFps) peakFps = r.upper.toDouble()
            }
            streamMap.highSpeedVideoFpsRanges?.forEach { r ->
                if (r.upper.toDouble() > peakFps) peakFps = r.upper.toDouble()
            }
        } catch (_: Throwable) {}

        return CameraProbeResult(
            cameraId = id,
            lensFacing = facing,
            isFront = isFront,
            isDepth = isDepth,
            isMacro = isMacro,
            isUltraWide = isUltraWide,
            isTelephoto = isTele,
            hasFlash = hasFlash,
            hasOis = hasOis,
            hasEis = hasEis,
            hasAf = hasAf,
            hasRaw = hasRaw,
            sensorOrientation = sensorOrientation,
            activeArraySize = activeArray,
            isoRange = isoRange,
            expRange = expRange,
            focusMaxDiopters = minFocus,
            evRange = evRange,
            evStep = evStep,
            maxDigitalZoom = maxDigitalZoom,
            zoomRatioRange = zoomRatioRange,
            standardJpegSize = standardJpeg,
            hiResJpegSize = hiResJpeg,
            sensorMp = sensorMp,
            apertures = apertures,
            focalLengths = floatArrayOf(displayFocal),
            supportedJpegSizes = sortedSizes,
            parentLogicalId = parentLogicalId,
            physicalCameraIds = physicalIds,
            isMonochrome = isMonochrome,
            isPeriscope = isPeriscope,
            isUltraHighRes = isUltraHighRes,
            supports10Bit = supports10Bit,
            hardwareLevel = hardwareLevel,
            rawFocalLengthMm = rawFocalMm,
            opticalZoomFactor = 1.0f,
            rawSensorSize = rawSensorSize,
            maxFps = peakFps,
            discoverySource = discoverySource,
            hasPreviewStabilization = hasPreviewStabilization
        )
    }

    /**
     * Normalizes each rear camera's opticalZoomFactor relative to the primary rear Wide camera's
     * 35mm-equivalent focal length (e.g. 13mm -> 0.5x, 24mm -> 1.0x, 72mm -> 3.0x, 120mm -> 5.0x).
     */
    fun normalizeOpticalZoomFactors(cameras: List<CameraProbeResult>): List<CameraProbeResult> {
        if (cameras.isEmpty()) return cameras
        val primaryWide = cameras.firstOrNull { !it.isFront && !it.isDepth && !it.isMacro && !it.isUltraWide && !it.isTelephoto && !it.isMonochrome }
            ?: cameras.firstOrNull { !it.isFront && !it.isDepth && !it.isMacro }
            ?: return cameras
        val baseFocal = primaryWide.focalLengths.firstOrNull()?.takeIf { it > 1f } ?: 24f

        return cameras.map { cam ->
            if (cam.isFront || cam.isDepth || cam.isMacro) {
                cam.copy(opticalZoomFactor = 1.0f)
            } else {
                val fl = cam.focalLengths.firstOrNull()?.takeIf { it > 1f } ?: baseFocal
                val rawRatio = fl / baseFocal
                val quantized = when {
                    rawRatio < 0.55f -> 0.5f
                    rawRatio < 0.72f -> 0.6f
                    rawRatio < 0.88f -> 0.8f
                    rawRatio in 0.88f..1.18f -> 1.0f
                    rawRatio in 1.75f..2.25f -> 2.0f
                    rawRatio in 2.65f..3.35f -> 3.0f
                    rawRatio in 4.4f..5.6f -> 5.0f
                    rawRatio in 9.0f..11.2f -> 10.0f
                    else -> ((rawRatio * 10f).roundToInt() / 10f).coerceIn(0.4f, 15.0f)
                }
                cam.copy(opticalZoomFactor = quantized)
            }
        }
    }

    /**
     * Formats a crisp viewfinder lens pill badge (e.g. "0.5×", "1×", "3×", "5×", "MACRO", "MONO").
     */
    fun formatLensBadge(cam: CameraProbeResult): String = when {
        cam.isFront -> "SELFIE"
        cam.isMonochrome -> "MONO"
        cam.isMacro -> "MACRO"
        cam.isDepth -> "DEPTH"
        cam.opticalZoomFactor < 1.0f -> String.format(java.util.Locale.US, "%.1f×", cam.opticalZoomFactor)
        cam.opticalZoomFactor == cam.opticalZoomFactor.toInt().toFloat() -> "${cam.opticalZoomFactor.toInt()}×"
        else -> String.format(java.util.Locale.US, "%.1f×", cam.opticalZoomFactor)
    }

    fun computeSensorMegapixels(maxHardwarePixels: Long): Double {
        return maxHardwarePixels.toDouble() / 1_000_000.0
    }

    fun calculateMaxHardwarePixels(
        activeW: Long,
        activeH: Long,
        pixelW: Long = 0L,
        pixelH: Long = 0L,
        hiResW: Long = 0L,
        hiResH: Long = 0L
    ): Long {
        return maxOf(
            activeW * activeH,
            pixelW * pixelH,
            hiResW * hiResH
        )
    }

    fun classifyLensOptics(
        isFront: Boolean,
        isDepth: Boolean,
        minFocusDiopters: Float,
        displayFocal35mm: Float,
        sensorWidthMm: Float = 6.4f
    ): LensClassification {
        val isMacro = !isFront && !isDepth && (minFocusDiopters >= 20.0f || (minFocusDiopters >= 10.0f && sensorWidthMm < 3.2f))
        val isUltraWide = !isFront && !isDepth && !isMacro && (displayFocal35mm < 24.0f)
        val isTele = !isFront && !isDepth && !isMacro && !isUltraWide && (displayFocal35mm >= 45.0f)
        val isWide = !isFront && !isDepth && !isMacro && !isUltraWide && !isTele
        return LensClassification(isFront, isDepth, isMacro, isUltraWide, isTele, isWide)
    }

    data class LensClassification(
        val isFront: Boolean,
        val isDepth: Boolean,
        val isMacro: Boolean,
        val isUltraWide: Boolean,
        val isTelephoto: Boolean,
        val isWide: Boolean
    )

    /**
     * Legacy Map adapter for backwards compatibility with any untyped consumers.
     */
    fun probeAllCameras(manager: CameraManager): List<Map<String, Any>> {
        return probeCameraResults(manager).map { r ->
            val role = when {
                r.isFront -> "FRONT"
                r.isMonochrome -> "MONOCHROME"
                r.isMacro -> "MACRO"
                r.isUltraWide -> "ULTRAWIDE"
                r.isPeriscope -> "PERISCOPE"
                r.isTelephoto -> "TELEPHOTO"
                else -> "WIDE"
            }
            val m = HashMap<String, Any>()
            m["id"] = r.cameraId
            m["facing"] = if (r.isFront) "FRONT" else "BACK"
            m["role"] = role
            m["focalLength"] = if (r.focalLengths.isNotEmpty()) r.focalLengths[0] else 24f
            m["rawFocalLength"] = if (r.rawFocalLengthMm > 0f) r.rawFocalLengthMm else (if (r.focalLengths.isNotEmpty()) r.focalLengths[0] else 0f)
            m["aperture"] = if (r.apertures.isNotEmpty()) r.apertures[0] else 1.8f
            m["sensorMp"] = r.sensorMp
            m["minFocusDistance"] = r.focusMaxDiopters
            m["hasAf"] = r.hasAf
            m["hasOis"] = r.hasOis
            m["hasRaw"] = r.hasRaw
            m["isDepth"] = r.isDepth
            m["isMacro"] = r.isMacro
            m["isUltraWide"] = r.isUltraWide
            m["opticalZoom"] = r.opticalZoomFactor
            r.parentLogicalId?.let { m["parentLogicalId"] = it }
            m
        }
    }
}
