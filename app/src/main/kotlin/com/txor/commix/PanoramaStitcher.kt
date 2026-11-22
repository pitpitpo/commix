package com.txor.commix

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import android.util.Log
import java.io.ByteArrayOutputStream
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class PanoramaSlice(
    val bitmap: Bitmap,
    val azimuthDeg: Float,
    val pitchDeg: Float = 0f,
    val rollDeg: Float = 0f,
    val timestamp: Long = System.currentTimeMillis(),
    val signedSweepDeg: Float = azimuthDeg
)

/**
 * Raw captured JPEG slice paired with the exact IMU pose sampled at shutter trigger time.
 */
data class PanoramaRawSlice(
    val jpegBytes: ByteArray,
    val signedSweepDeg: Float,
    val azimuthDeg: Float,
    val pitchDeg: Float = 0f,
    val rollDeg: Float = 0f
)

enum class PanoramaGuidanceSeverity {
    OK,
    WARN_TILT,
    WARN_DRIFT,
    WARN_SPEED,
    WARN_DIRECTION
}

/**
 * Snapshot produced on every orientation tick during active panorama capture.
 */
data class PanoramaSweepSnapshot(
    val direction: Int,                    // +1 = Left->Right, -1 = Right->Left
    val signedSweepDeg: Float,             // Continuous unwrapped sweep from start
    val forwardSweepDeg: Float,            // Signed progress along active sweep direction
    val maxForwardSweepDeg: Float,         // Monotonic watermark (never shrinks on jitter/backtrack)
    val progress: Float,                   // [0f..1f] toward 180° (wide) or 360° (sphere)
    val verticalDriftDeg: Float,           // pitch - startPitch (+ = aimed too high)
    val rollDeg: Float,
    val panSpeedDegPerSec: Float,
    val isMovingTooFast: Boolean,
    val isBacktracking: Boolean,
    val shouldCaptureSlice: Boolean,
    val shouldAutoFinishOnReverse: Boolean,
    val isTargetComplete: Boolean,
    val guidanceHint: String,
    val severity: PanoramaGuidanceSeverity
)

/**
 * Continuous unwrapped sweep tracker for bidirectional Panorama & 360° Photo Sphere shooting.
 * Solves ±180° wrap reversal, duplicate captures on backtrack, pan-speed gating, and vertical drift coaching.
 */
class PanoramaSweepTracker(
    private val sliceStepDeg: Float = DEFAULT_SLICE_STEP_DEG,
    private val maxSpeedDegPerSec: Float = DEFAULT_MAX_SPEED_DEG_SEC
) {
    var direction: Int = 1
        private set
    var is360: Boolean = false
        private set
    var startAzimuthDeg: Float = 0f
        private set
    var startPitchDeg: Float = 0f
        private set
    var signedSweepDeg: Float = 0f
        private set
    var maxForwardSweepDeg: Float = 0f
        private set
    var lastCapturedForwardSweepDeg: Float = 0f
        private set
    var capturedSlices: Int = 0
        private set

    private var prevAzimuthDeg: Float = 0f
    private var prevTimestampMs: Long = 0L
    private var smoothedSpeedDegPerSec: Float = 0f
    private var directionLocked: Boolean = false

    fun start(
        azimuthDeg: Float,
        pitchDeg: Float,
        preferredDirection: Int = 1,
        is360: Boolean = false,
        nowMs: Long = System.currentTimeMillis()
    ) {
        this.direction = if (preferredDirection >= 0) 1 else -1
        this.is360 = is360
        this.startAzimuthDeg = azimuthDeg
        this.startPitchDeg = pitchDeg
        this.prevAzimuthDeg = azimuthDeg
        this.prevTimestampMs = nowMs
        this.signedSweepDeg = 0f
        this.maxForwardSweepDeg = 0f
        this.lastCapturedForwardSweepDeg = 0f
        this.capturedSlices = 1 // Initial frame 0 triggered at start
        this.smoothedSpeedDegPerSec = 0f
        this.directionLocked = false
    }

    fun markSliceTriggered() {
        val currentForward = signedSweepDeg * direction
        lastCapturedForwardSweepDeg = max(lastCapturedForwardSweepDeg, currentForward)
        capturedSlices++
        if (maxForwardSweepDeg >= 8f) {
            directionLocked = true
        }
    }

    fun update(
        azimuthDeg: Float,
        pitchDeg: Float,
        rollDeg: Float,
        yawRateDegPerSec: Float = 0f,
        nowMs: Long = System.currentTimeMillis(),
        captureInFlight: Boolean = false
    ): PanoramaSweepSnapshot {
        val stepDelta = PanoramaStitcher.calculateWrappedDelta(azimuthDeg, prevAzimuthDeg)
        prevAzimuthDeg = azimuthDeg
        signedSweepDeg += stepDelta

        val dtSec = ((nowMs - prevTimestampMs).coerceAtLeast(1L)) / 1000f
        prevTimestampMs = nowMs
        val rawSpeed = if (abs(yawRateDegPerSec) > 0.25f) {
            abs(yawRateDegPerSec)
        } else {
            (abs(stepDelta) / dtSec).coerceAtMost(240f)
        }
        // Asymmetric EMA: fast attack when speeding up so "Slow down" warns immediately,
        // smooth release when decelerating so the coaching pill never flickers.
        val alpha = if (rawSpeed > smoothedSpeedDegPerSec) 0.55f else 0.30f
        smoothedSpeedDegPerSec = smoothedSpeedDegPerSec * (1f - alpha) + rawSpeed * alpha

        // Auto-flip sweep direction if the user starts panning the opposite way before locking in
        if (!directionLocked && capturedSlices <= 1) {
            if (signedSweepDeg * direction < -5.0f) {
                direction = -direction
                directionLocked = true
            } else if (signedSweepDeg * direction > 8.0f) {
                directionLocked = true
            }
        }

        val forwardSweepDeg = signedSweepDeg * direction
        if (forwardSweepDeg > maxForwardSweepDeg) {
            maxForwardSweepDeg = forwardSweepDeg
        }
        if (maxForwardSweepDeg >= 10f) {
            directionLocked = true
        }

        val backtrackDeg = (maxForwardSweepDeg - forwardSweepDeg).coerceAtLeast(0f)
        val verticalDriftDeg = pitchDeg - startPitchDeg
        val progress = PanoramaStitcher.calculateProgress(maxForwardSweepDeg, is360)
        val targetDeg = if (is360) 360f else 180f

        val isMovingTooFast = smoothedSpeedDegPerSec > maxSpeedDegPerSec
        val isBacktracking = backtrackDeg > 5.5f && maxForwardSweepDeg > 8f
        val shouldAutoFinishOnReverse = backtrackDeg > 18.0f && capturedSlices >= 3 && maxForwardSweepDeg >= 28f
        val isTargetComplete = maxForwardSweepDeg >= targetDeg - 2.0f

        val shouldCaptureSlice = !captureInFlight &&
            !isMovingTooFast &&
            !isBacktracking &&
            (forwardSweepDeg - lastCapturedForwardSweepDeg) >= sliceStepDeg &&
            forwardSweepDeg >= (maxForwardSweepDeg - 1.5f)

        val dirWord = if (direction >= 0) "right" else "left"
        val (hint, severity) = when {
            isMovingTooFast -> "Slow down" to PanoramaGuidanceSeverity.WARN_SPEED
            isBacktracking -> "Keep moving $dirWord" to PanoramaGuidanceSeverity.WARN_DIRECTION
            verticalDriftDeg > 4.5f -> "Tilt down slightly" to PanoramaGuidanceSeverity.WARN_DRIFT
            verticalDriftDeg < -4.5f -> "Tilt up slightly" to PanoramaGuidanceSeverity.WARN_DRIFT
            abs(rollDeg) > 6.5f -> "Level camera (${String.format(Locale.US, "%+.0f°", rollDeg)})" to PanoramaGuidanceSeverity.WARN_TILT
            else -> "Steady · ${maxForwardSweepDeg.roundToInt()}°" to PanoramaGuidanceSeverity.OK
        }

        return PanoramaSweepSnapshot(
            direction = direction,
            signedSweepDeg = signedSweepDeg,
            forwardSweepDeg = forwardSweepDeg,
            maxForwardSweepDeg = maxForwardSweepDeg,
            progress = progress,
            verticalDriftDeg = verticalDriftDeg,
            rollDeg = rollDeg,
            panSpeedDegPerSec = smoothedSpeedDegPerSec,
            isMovingTooFast = isMovingTooFast,
            isBacktracking = isBacktracking,
            shouldCaptureSlice = shouldCaptureSlice,
            shouldAutoFinishOnReverse = shouldAutoFinishOnReverse,
            isTargetComplete = isTargetComplete,
            guidanceHint = hint,
            severity = severity
        )
    }

    companion object {
        const val DEFAULT_SLICE_STEP_DEG = 12.0f
        const val DEFAULT_MAX_SPEED_DEG_SEC = 34.0f
    }
}

/**
 * PanoramaStitcher: Computational stitching engine for wide horizontal panoramas
 * and 360° Photo Spheres.
 *
 * Capabilities:
 * 1. Continuous unwrapped directional sweep tracking (L->R and R->L, full 360° without 180° reversal).
 * 2. Gyro-prior + 2D luminance patch SAD registration for pixel-locked horizontal & vertical alignment.
 * 3. Local overlap exposure gain matching + Cosine (Hann) feather blending to eliminate seams & bands.
 * 4. Automatic vertical wobble cropping for clean rectangular panorama edges.
 * 5. 2:1 Equirectangular projection with smooth vertical pole gradients and GPano XMP injection.
 */
object PanoramaStitcher {
    private const val TAG = "PanoramaStitcher"
    const val DEFAULT_HFOV_DEG = 68.0f
    private const val TARGET_STITCH_SLICE_HEIGHT = 1600

    /**
     * Calculates the minimal angular delta between two headings in [-180, 180] degrees,
     * correctly handling compass wrap-around (e.g. 179° to -179° is +2°).
     */
    fun calculateWrappedDelta(currentDeg: Float, startDeg: Float): Float {
        var delta = (currentDeg - startDeg) % 360f
        if (delta > 180f) delta -= 360f
        if (delta < -180f) delta += 360f
        return delta
    }

    /**
     * Computes capture progress [0.0..1.0] from accumulated sweep angle.
     * Target: 180° for standard panorama, 360° for 360 Photo Sphere.
     */
    fun calculateProgress(accumulatedAngleDeg: Float, is360: Boolean): Float {
        val targetDeg = if (is360) 360.0f else 180.0f
        return (abs(accumulatedAngleDeg) / targetDeg).coerceIn(0.0f, 1.0f)
    }

    /**
     * Orders captured slices spatially from left to right using continuous unwrapped sweep angle.
     * Handles both Left->Right (0, +12, +24...) and Right->Left (0, -12, -24...) sweeps seamlessly.
     */
    fun orderSlicesSpatially(slices: List<PanoramaRawSlice>): List<PanoramaRawSlice> {
        if (slices.size <= 1) return slices
        return slices.sortedBy { it.signedSweepDeg }
    }

    /**
     * Pure 2D luminance patch SAD registration to refine (dx, dy) alignment between two overlapping
     * strips around a gyro-predicted prior (priorDx, priorDy).
     *
     * @param leftLuma   Row-major 8-bit luminance buffer of left slice (size = width * height)
     * @param rightLuma  Row-major 8-bit luminance buffer of right slice (size = width * height)
     * @param width      Width of each downsampled slice
     * @param height     Height of each downsampled slice
     * @param priorDx    Expected horizontal offset of right slice relative to left slice (in pixels)
     * @param priorDy    Expected vertical offset of right slice relative to left slice (in pixels)
     * @param searchRadiusX Horizontal search half-window around priorDx
     * @param searchRadiusY Vertical search half-window around priorDy
     * @return Refined (dx, dy) maximizing photometric agreement in the overlap region
     */
    fun estimateOverlapAlignment(
        leftLuma: IntArray,
        rightLuma: IntArray,
        width: Int,
        height: Int,
        priorDx: Int,
        priorDy: Int,
        searchRadiusX: Int = max(6, width / 6),
        searchRadiusY: Int = max(4, height / 10)
    ): Pair<Int, Int> {
        if (width < 16 || height < 16 || leftLuma.size < width * height || rightLuma.size < width * height) {
            return priorDx to priorDy
        }

        val minDx = max(4, min(priorDx - searchRadiusX, (width * 0.85f).toInt()))
        val maxDx = min(width - 8, max(priorDx + searchRadiusX, minDx))
        val minDy = (priorDy - searchRadiusY).coerceIn(-height / 4, height / 4)
        val maxDy = (priorDy + searchRadiusY).coerceIn(minDy, height / 4)

        val yMargin = (height * 0.18f).toInt()
        val yBandStart = yMargin.coerceIn(0, height - 1)
        val yBandEnd = (height - yMargin).coerceIn(yBandStart + 1, height)

        fun scoreCandidate(dx: Int, dy: Int, stride: Int): Double {
            val overlapW = width - dx
            if (overlapW < 6) return Double.MAX_VALUE

            val xPad = max(1, overlapW / 12)
            val xStart = xPad
            val xEnd = overlapW - xPad
            if (xEnd <= xStart) return Double.MAX_VALUE

            var sumDiff = 0L
            var count = 0
            var y = yBandStart
            while (y < yBandEnd) {
                val leftY = y + dy
                if (leftY in 0 until height) {
                    val leftRow = leftY * width + dx
                    val rightRow = y * width
                    var x = xStart
                    while (x < xEnd) {
                        val d = leftLuma[leftRow + x] - rightLuma[rightRow + x]
                        sumDiff += if (d < 0) -d else d
                        count++
                        x += stride
                    }
                }
                y += stride
            }

            if (count < 16) return Double.MAX_VALUE
            val meanSad = sumDiff.toDouble() / count.toDouble()
            // Gentle prior regularization prevents flat-sky drift while never overriding real edges
            val dxErr = (dx - priorDx).toDouble() / max(1, searchRadiusX).toDouble()
            val dyErr = (dy - priorDy).toDouble() / max(1, searchRadiusY).toDouble()
            val priorPenalty = 0.35 * (dxErr * dxErr + dyErr * dyErr)
            return meanSad + priorPenalty
        }

        var bestDx = priorDx.coerceIn(minDx, maxDx)
        var bestDy = priorDy.coerceIn(minDy, maxDy)
        var bestScore = Double.MAX_VALUE

        // Coarse pass (step = 2)
        var dy = minDy
        while (dy <= maxDy) {
            var dx = minDx
            while (dx <= maxDx) {
                val s = scoreCandidate(dx, dy, stride = 2)
                if (s < bestScore) {
                    bestScore = s
                    bestDx = dx
                    bestDy = dy
                }
                dx += 2
            }
            dy += 2
        }

        // Fine pass (step = 1 in ±2px neighborhood around coarse winner)
        val fineMinDx = max(minDx, bestDx - 2)
        val fineMaxDx = min(maxDx, bestDx + 2)
        val fineMinDy = max(minDy, bestDy - 2)
        val fineMaxDy = min(maxDy, bestDy + 2)
        bestScore = Double.MAX_VALUE
        for (fDy in fineMinDy..fineMaxDy) {
            for (fDx in fineMinDx..fineMaxDx) {
                val s = scoreCandidate(fDx, fDy, stride = 1)
                if (s < bestScore) {
                    bestScore = s
                    bestDx = fDx
                    bestDy = fDy
                }
            }
        }

        return bestDx to bestDy
    }

    /**
     * Decodes a compact thumbnail suitable for the live Panorama filmstrip preview ribbon.
     */
    fun decodePreviewThumbnail(jpegBytes: ByteArray, targetHeight: Int = 160): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size, bounds)
            val srcH = bounds.outHeight
            if (srcH <= 0) return null
            var sample = 1
            while (srcH / (sample * 2) >= targetHeight) {
                sample *= 2
            }
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            val decodedRaw = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size, opts) ?: return null
            val exifDeg = OrientationPolicy.readExifRotationDegrees(jpegBytes)
            val decoded = if (exifDeg != 0) {
                val m = Matrix().apply { postRotate(exifDeg.toFloat()) }
                val rot = Bitmap.createBitmap(decodedRaw, 0, 0, decodedRaw.width, decodedRaw.height, m, true)
                if (rot !== decodedRaw) decodedRaw.recycle()
                rot
            } else {
                decodedRaw
            }
            val scale = targetHeight.toFloat() / decoded.height.toFloat()
            val scaledW = (decoded.width * scale).roundToInt().coerceAtLeast(24)
            if (abs(decoded.height - targetHeight) > 12) {
                val scaled = Bitmap.createScaledBitmap(decoded, scaledW, targetHeight, true)
                if (scaled !== decoded) decoded.recycle()
                scaled
            } else {
                decoded
            }
        } catch (t: Throwable) {
            Log.w(TAG, "decodePreviewThumbnail skipped: ${t.message}")
            null
        }
    }

    /**
     * High-quality OOM-safe stitching entry point using full pose metadata (signedSweep, azimuth, pitch, roll).
     */
    fun stitchCapturedSlices(
        rawSlices: List<PanoramaRawSlice>,
        is360: Boolean,
        lensHFOVDeg: Float = DEFAULT_HFOV_DEG,
        compressionQuality: Int = 94
    ): ByteArray {
        require(rawSlices.isNotEmpty()) { "Cannot stitch empty raw slices" }
        val ordered = if (is360) rawSlices else orderSlicesSpatially(rawSlices)
        val decoded = mutableListOf<PanoramaSlice>()

        try {
            for (raw in ordered) {
                val bmp = decodeDownsampledSlice(raw.jpegBytes, TARGET_STITCH_SLICE_HEIGHT)
                if (bmp != null) {
                    decoded.add(
                        PanoramaSlice(
                            bitmap = bmp,
                            azimuthDeg = raw.azimuthDeg,
                            pitchDeg = raw.pitchDeg,
                            rollDeg = raw.rollDeg,
                            signedSweepDeg = raw.signedSweepDeg
                        )
                    )
                }
            }
            if (decoded.isEmpty()) {
                throw IllegalStateException("Failed decoding JPEG slices for panorama")
            }

            val stitched = stitchSlices(decoded, is360, lensHFOVDeg)
            val bos = ByteArrayOutputStream()
            stitched.compress(Bitmap.CompressFormat.JPEG, compressionQuality.coerceIn(75, 100), bos)
            val width = stitched.width
            val height = stitched.height
            if (decoded.none { it.bitmap === stitched }) {
                stitched.recycle()
            }
            val jpegBytes = bos.toByteArray()

            return if (is360) {
                injectGPhotoSphereXmp(jpegBytes, width, height)
            } else {
                jpegBytes
            }
        } finally {
            decoded.forEach { slice ->
                if (!slice.bitmap.isRecycled) {
                    slice.bitmap.recycle()
                }
            }
        }
    }

    /**
     * Stitches a sequential list of slices into a panoramic bitmap.
     * If is360 is true, formats into a 2:1 equirectangular canvas.
     */
    fun stitchSlices(
        slices: List<PanoramaSlice>,
        is360: Boolean,
        lensHFOVDeg: Float = DEFAULT_HFOV_DEG
    ): Bitmap {
        require(slices.isNotEmpty()) { "Cannot stitch an empty list of slices" }
        if (slices.size == 1) {
            return if (is360) projectToEquirectangular(listOf(slices.first()), lensHFOVDeg) else slices.first().bitmap
        }

        return if (is360) {
            projectToEquirectangular(slices, lensHFOVDeg)
        } else {
            stitchHorizontal(slices, lensHFOVDeg)
        }
    }

    /**
     * Legacy compatibility entry point accepting (jpegBytes, azimuthDeg) pairs.
     */
    fun stitchByteArrays(
        rawSlices: List<Pair<ByteArray, Float>>,
        is360: Boolean,
        lensHFOVDeg: Float = DEFAULT_HFOV_DEG,
        compressionQuality: Int = 92
    ): ByteArray {
        require(rawSlices.isNotEmpty()) { "Cannot stitch empty raw slices" }
        var unwrapped = 0f
        var prevAz = rawSlices.first().second
        val wrapped = rawSlices.mapIndexed { idx, (bytes, az) ->
            if (idx > 0) {
                unwrapped += calculateWrappedDelta(az, prevAz)
                prevAz = az
            }
            PanoramaRawSlice(
                jpegBytes = bytes,
                signedSweepDeg = unwrapped,
                azimuthDeg = az
            )
        }
        return stitchCapturedSlices(wrapped, is360, lensHFOVDeg, compressionQuality)
    }

    private fun decodeDownsampledSlice(bytes: ByteArray, targetHeight: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val srcH = bounds.outHeight
        if (srcH <= 0) return null
        var sample = 1
        while (srcH / (sample * 2) >= targetHeight) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return null
        val exifDeg = OrientationPolicy.readExifRotationDegrees(bytes)
        return if (exifDeg != 0) {
            val m = Matrix().apply { postRotate(exifDeg.toFloat()) }
            val rot = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, m, true)
            if (rot !== decoded) decoded.recycle()
            rot
        } else {
            decoded
        }
    }

    /**
     * Computes a compact grayscale buffer for fast 2D patch alignment.
     */
    private fun extractDownsampledLuma(bitmap: Bitmap, scaleDiv: Int): Triple<IntArray, Int, Int> {
        val w = (bitmap.width / scaleDiv).coerceAtLeast(16)
        val h = (bitmap.height / scaleDiv).coerceAtLeast(16)
        val small = Bitmap.createScaledBitmap(bitmap, w, h, true)
        val px = IntArray(w * h)
        small.getPixels(px, 0, w, 0, 0, w, h)
        if (small !== bitmap) small.recycle()
        val luma = IntArray(w * h)
        for (i in px.indices) {
            val c = px[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            luma[i] = (r * 77 + g * 150 + b * 29) shr 8
        }
        return Triple(luma, w, h)
    }

    /**
     * Standard horizontal panorama stitcher with:
     * 1. Gyro + 2D luminance SAD overlap registration for both dx and dy.
     * 2. Local overlap exposure gain matching.
     * 3. Smooth Cosine (Hann) feather blending across the seam.
     * 4. Rectangular vertical auto-crop to remove hand-wobble borders.
     */
    private fun stitchHorizontal(
        slices: List<PanoramaSlice>,
        lensHFOVDeg: Float
    ): Bitmap {
        val refSlice = slices.first().bitmap
        val sliceW = refSlice.width
        val sliceH = refSlice.height
        val safeHfov = lensHFOVDeg.coerceIn(30f, 125f)
        val safeVfov = (safeHfov * (sliceH.toFloat() / sliceW.toFloat())).coerceIn(25f, 110f)

        // Downscale factor targeting ~160px wide strips for ultra-fast SAD registration
        val regDiv = max(1, (sliceW / 160f).roundToInt())
        val lumaList = slices.map { extractDownsampledLuma(it.bitmap, regDiv) }

        val shiftsX = IntArray(slices.size - 1)
        val shiftsY = IntArray(slices.size - 1)

        for (i in 0 until slices.size - 1) {
            val s0 = slices[i]
            val s1 = slices[i + 1]
            val deltaSweep = abs(s1.signedSweepDeg - s0.signedSweepDeg).let { d ->
                if (d > 0.5f) d else abs(calculateWrappedDelta(s1.azimuthDeg, s0.azimuthDeg))
            }
            val priorDxFull = (sliceW * (deltaSweep / safeHfov)).roundToInt()
                .coerceIn((sliceW * 0.08f).toInt(), (sliceW * 0.78f).toInt())
            // Tilting camera UP (+pitch) moves scene content DOWN in image (+y)
            val deltaPitch = s1.pitchDeg - s0.pitchDeg
            val priorDyFull = (sliceH * (deltaPitch / safeVfov)).roundToInt()
                .coerceIn(-sliceH / 8, sliceH / 8)

            val (luma0, lw, lh) = lumaList[i]
            val (luma1, rw, rh) = lumaList[i + 1]

            if (lw == rw && lh == rh) {
                val (refinedDxSmall, refinedDySmall) = estimateOverlapAlignment(
                    leftLuma = luma0,
                    rightLuma = luma1,
                    width = lw,
                    height = lh,
                    priorDx = (priorDxFull / regDiv).coerceIn(4, lw - 8),
                    priorDy = (priorDyFull / regDiv).coerceIn(-lh / 6, lh / 6),
                    searchRadiusX = max(6, (lw * 0.16f).roundToInt()),
                    searchRadiusY = max(4, (lh * 0.09f).roundToInt())
                )
                shiftsX[i] = (refinedDxSmall * regDiv).coerceIn((sliceW * 0.06f).toInt(), (sliceW * 0.82f).toInt())
                shiftsY[i] = (refinedDySmall * regDiv).coerceIn(-sliceH / 6, sliceH / 6)
            } else {
                shiftsX[i] = priorDxFull
                shiftsY[i] = priorDyFull
            }
        }

        // Accumulate absolute slice positions on the panorama canvas
        val posX = IntArray(slices.size)
        val posY = IntArray(slices.size)
        var minY = 0
        var maxY = 0
        for (i in 1 until slices.size) {
            posX[i] = posX[i - 1] + shiftsX[i - 1]
            posY[i] = posY[i - 1] + shiftsY[i - 1]
            if (posY[i] < minY) minY = posY[i]
            if (posY[i] > maxY) maxY = posY[i]
        }

        // Normalize Y so top-most slice starts at 0
        val yShift = -minY
        for (i in posY.indices) {
            posY[i] += yShift
        }

        val totalWidth = posX.last() + sliceW
        val totalHeight = (maxY - minY) + sliceH
        val canvasBmp = Bitmap.createBitmap(totalWidth, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(canvasBmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        // Draw first slice
        canvas.drawBitmap(slices.first().bitmap, posX[0].toFloat(), posY[0].toFloat(), paint)

        // Incrementally blend successive slices with exposure gain compensation + cosine feathering
        var cumulativeGain = 1.0f
        for (i in 0 until slices.size - 1) {
            val nextSlice = slices[i + 1].bitmap
            val shiftX = shiftsX[i]
            val nextX = posX[i + 1]
            val nextY = posY[i + 1]
            val prevY = posY[i]
            val overlapWidth = (sliceW - shiftX).coerceIn(0, sliceW)

            if (overlapWidth > 8) {
                val overlapTop = max(prevY, nextY)
                val overlapBottom = min(prevY + sliceH, nextY + sliceH)
                val overlapH = overlapBottom - overlapTop
                val stepGain = if (overlapH > 16) {
                    estimateExposureGain(
                        underBitmap = canvasBmp,
                        overBitmap = nextSlice,
                        underX = nextX,
                        underY = overlapTop,
                        overY = overlapTop - nextY,
                        overlapWidth = overlapWidth,
                        overlapHeight = overlapH
                    )
                } else 1.0f
                cumulativeGain = (cumulativeGain * 0.4f + stepGain * 0.6f).coerceIn(0.86f, 1.16f)

                blendCosineFeatherOverlap(
                    canvas = canvas,
                    underBitmap = canvasBmp,
                    overBitmap = nextSlice,
                    nextX = nextX,
                    nextY = nextY,
                    overlapWidth = overlapWidth,
                    overlapTop = overlapTop,
                    overlapBottom = overlapBottom,
                    gain = cumulativeGain
                )

                // Draw non-overlapping right portion of nextSlice with matching exposure gain
                val nonOverlapSrcX = overlapWidth
                val nonOverlapW = sliceW - nonOverlapSrcX
                if (nonOverlapW > 0) {
                    drawSliceRegionWithGain(
                        canvas = canvas,
                        srcBitmap = nextSlice,
                        srcX = nonOverlapSrcX,
                        srcY = 0,
                        width = nonOverlapW,
                        height = sliceH,
                        dstX = nextX + nonOverlapSrcX,
                        dstY = nextY,
                        gain = cumulativeGain,
                        paint = paint
                    )
                }
            } else {
                canvas.drawBitmap(nextSlice, nextX.toFloat(), nextY.toFloat(), paint)
            }
        }

        // Clean rectangular auto-crop so hand vertical drift leaves zero black corners
        val cropTop = posY.maxOrNull() ?: 0
        val cropBottom = (posY.minOrNull() ?: 0) + sliceH
        val cropH = cropBottom - cropTop
        return if (cropH >= (sliceH * 0.55f).toInt() && (cropTop > 0 || cropBottom < totalHeight)) {
            val cropped = Bitmap.createBitmap(canvasBmp, 0, cropTop, totalWidth, cropH)
            canvasBmp.recycle()
            cropped
        } else {
            canvasBmp
        }
    }

    /**
     * Estimates brightness ratio between the existing stitched canvas and the incoming slice
     * in the overlap region so minor AE/vignetting differences blend smoothly.
     */
    private fun estimateExposureGain(
        underBitmap: Bitmap,
        overBitmap: Bitmap,
        underX: Int,
        underY: Int,
        overY: Int,
        overlapWidth: Int,
        overlapHeight: Int
    ): Float {
        return try {
            val sampleW = min(overlapWidth, 64)
            val startOffX = (overlapWidth - sampleW) / 2
            val stepY = max(1, overlapHeight / 48)
            var sumUnder = 0.0
            var sumOver = 0.0
            var samples = 0
            val underRow = IntArray(sampleW)
            val overRow = IntArray(sampleW)
            var y = 0
            while (y < overlapHeight) {
                underBitmap.getPixels(underRow, 0, sampleW, underX + startOffX, underY + y, sampleW, 1)
                overBitmap.getPixels(overRow, 0, sampleW, startOffX, overY + y, sampleW, 1)
                for (x in 0 until sampleW step 2) {
                    val c1 = underRow[x]
                    val c2 = overRow[x]
                    val l1 = (((c1 shr 16) and 0xFF) * 77 + ((c1 shr 8) and 0xFF) * 150 + (c1 and 0xFF) * 29) shr 8
                    val l2 = (((c2 shr 16) and 0xFF) * 77 + ((c2 shr 8) and 0xFF) * 150 + (c2 and 0xFF) * 29) shr 8
                    if (l1 in 16..240 && l2 in 16..240) {
                        sumUnder += l1
                        sumOver += l2
                        samples++
                    }
                }
                y += stepY
            }
            if (samples >= 32 && sumOver > 1.0) {
                (sumUnder / sumOver).toFloat().coerceIn(0.85f, 1.18f)
            } else {
                1.0f
            }
        } catch (_: Exception) {
            1.0f
        }
    }

    /**
     * Smooth Cosine (Hann) feather blending across the overlap strip with exposure gain matching.
     */
    private fun blendCosineFeatherOverlap(
        canvas: Canvas,
        underBitmap: Bitmap,
        overBitmap: Bitmap,
        nextX: Int,
        nextY: Int,
        overlapWidth: Int,
        overlapTop: Int,
        overlapBottom: Int,
        gain: Float
    ) {
        val sliceH = overBitmap.height
        val validTop = overlapTop.coerceIn(0, underBitmap.height)
        val validBottom = overlapBottom.coerceIn(validTop, underBitmap.height)
        val overlapH = validBottom - validTop
        if (overlapH <= 0 || overlapWidth <= 0) return

        // Precompute cosine (Hann) blend curve concentrated in the middle 70% of the overlap
        val alphaLut = FloatArray(overlapWidth) { x ->
            val t = x.toFloat() / max(1, overlapWidth - 1).toFloat()
            val mapped = ((t - 0.15f) / 0.70f).coerceIn(0f, 1f)
            (0.5 * (1.0 - cos(PI * mapped))).toFloat()
        }

        val underPixels = IntArray(overlapWidth * overlapH)
        val overPixels = IntArray(overlapWidth * overlapH)
        val overStartY = (validTop - nextY).coerceIn(0, max(0, sliceH - overlapH))

        try {
            underBitmap.getPixels(underPixels, 0, overlapWidth, nextX, validTop, overlapWidth, overlapH)
            overBitmap.getPixels(overPixels, 0, overlapWidth, 0, overStartY, overlapWidth, overlapH)

            for (y in 0 until overlapH) {
                val rowOff = y * overlapWidth
                for (x in 0 until overlapWidth) {
                    val idx = rowOff + x
                    val alpha = alphaLut[x]
                    val invAlpha = 1.0f - alpha

                    val c1 = underPixels[idx]
                    val c2 = overPixels[idx]

                    val r1 = (c1 shr 16) and 0xFF
                    val g1 = (c1 shr 8) and 0xFF
                    val b1 = c1 and 0xFF

                    val r2 = (((c2 shr 16) and 0xFF) * gain).coerceIn(0f, 255f)
                    val g2 = (((c2 shr 8) and 0xFF) * gain).coerceIn(0f, 255f)
                    val b2 = ((c2 and 0xFF) * gain).coerceIn(0f, 255f)

                    val r = (r1 * invAlpha + r2 * alpha).roundToInt().coerceIn(0, 255)
                    val g = (g1 * invAlpha + g2 * alpha).roundToInt().coerceIn(0, 255)
                    val b = (b1 * invAlpha + b2 * alpha).roundToInt().coerceIn(0, 255)

                    underPixels[idx] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                }
            }

            underBitmap.setPixels(underPixels, 0, overlapWidth, nextX, validTop, overlapWidth, overlapH)
        } catch (e: Exception) {
            Log.w(TAG, "Cosine feather blend fallback: ${e.message}")
            canvas.drawBitmap(overBitmap, nextX.toFloat(), nextY.toFloat(), null)
        }
    }

    private fun drawSliceRegionWithGain(
        canvas: Canvas,
        srcBitmap: Bitmap,
        srcX: Int,
        srcY: Int,
        width: Int,
        height: Int,
        dstX: Int,
        dstY: Int,
        gain: Float,
        paint: Paint
    ) {
        val srcRect = Rect(srcX, srcY, srcX + width, srcY + height)
        val dstRect = Rect(dstX, dstY, dstX + width, dstY + height)
        if (abs(gain - 1.0f) < 0.015f) {
            canvas.drawBitmap(srcBitmap, srcRect, dstRect, paint)
            return
        }
        val gainPaint = Paint(paint).apply {
            val cm = android.graphics.ColorMatrix(
                floatArrayOf(
                    gain, 0f, 0f, 0f, 0f,
                    0f, gain, 0f, 0f, 0f,
                    0f, 0f, gain, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            colorFilter = android.graphics.ColorMatrixColorFilter(cm)
        }
        canvas.drawBitmap(srcBitmap, srcRect, dstRect, gainPaint)
    }

    /**
     * 2:1 Equirectangular projection for 360° Photo Spheres.
     * Distributes slices uniformly across 360° azimuth with smooth pitch alignment,
     * cosine horizontal feathering, and ambient sky/ground pole gradients.
     */
    private fun projectToEquirectangular(
        slices: List<PanoramaSlice>,
        lensHFOVDeg: Float
    ): Bitmap {
        val refSlice = slices.first().bitmap
        val sliceW = refSlice.width
        val sliceH = refSlice.height
        val safeHfov = lensHFOVDeg.coerceIn(30f, 125f)

        // Equirectangular standard aspect ratio is 2:1 (width = 2 * height)
        val eqHeight = max(1440, min(2048, (sliceH * 1.6f).roundToInt()))
        val eqWidth = eqHeight * 2

        val eqBitmap = Bitmap.createBitmap(eqWidth, eqHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(eqBitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        // Scaled band height preserving aspect relative to HFOV on the 360° equator
        val bandSliceW = (eqWidth * (safeHfov / 360f)).roundToInt().coerceIn(64, eqWidth / 2)
        val bandSliceH = (bandSliceW * (sliceH.toFloat() / sliceW.toFloat())).roundToInt()
            .coerceIn(eqHeight / 4, (eqHeight * 0.85f).roundToInt())

        val topColor = sampleAverageColor(refSlice, 0, 0, sliceW, min(24, sliceH))
        val bottomColor = sampleAverageColor(refSlice, 0, max(0, sliceH - 24), sliceW, sliceH)

        val horizonY = eqHeight / 2
        val sliceYStart = (horizonY - bandSliceH / 2).coerceIn(0, eqHeight - bandSliceH)
        val sliceYEnd = sliceYStart + bandSliceH

        // Smooth zenith gradient (top pole -> sky horizon)
        val zenithDark = blendColors(topColor, Color.rgb(18, 28, 48), 0.45f)
        val skyPaint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, sliceYStart.toFloat().coerceAtLeast(1f),
                zenithDark, topColor, Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, eqWidth.toFloat(), sliceYStart.toFloat(), skyPaint)

        // Smooth nadir gradient (ground horizon -> bottom pole)
        val nadirDark = blendColors(bottomColor, Color.rgb(20, 20, 22), 0.55f)
        val groundPaint = Paint().apply {
            shader = LinearGradient(
                0f, sliceYEnd.toFloat(), 0f, eqHeight.toFloat(),
                bottomColor, nadirDark, Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, sliceYEnd.toFloat(), eqWidth.toFloat(), eqHeight.toFloat(), groundPaint)

        // Project each slice onto equirectangular coordinates with pitch offset
        val startAzimuth = slices.first().azimuthDeg
        val startPitch = slices.first().pitchDeg
        for (slice in slices) {
            val deltaAzimuth = (calculateWrappedDelta(slice.azimuthDeg, startAzimuth) + 360f) % 360f
            val targetCenterX = (eqWidth * (deltaAzimuth / 360f)).roundToInt()
            val pitchOffsetPx = (-((slice.pitchDeg - startPitch) / 180f) * eqHeight).roundToInt()
                .coerceIn(-eqHeight / 6, eqHeight / 6)
            val topY = (sliceYStart + pitchOffsetPx).coerceIn(0, eqHeight - bandSliceH)
            val botY = topY + bandSliceH

            val scaledSlice = Bitmap.createScaledBitmap(slice.bitmap, bandSliceW, bandSliceH, true)
            val targetLeft = targetCenterX - bandSliceW / 2

            if (targetLeft < 0) {
                val splitSrcX = -targetLeft
                canvas.drawBitmap(
                    scaledSlice,
                    Rect(0, 0, splitSrcX, bandSliceH),
                    Rect(targetLeft + eqWidth, topY, eqWidth, botY),
                    paint
                )
                canvas.drawBitmap(
                    scaledSlice,
                    Rect(splitSrcX, 0, bandSliceW, bandSliceH),
                    Rect(0, topY, targetLeft + bandSliceW, botY),
                    paint
                )
            } else if (targetLeft + bandSliceW > eqWidth) {
                val splitSrcX = eqWidth - targetLeft
                canvas.drawBitmap(
                    scaledSlice,
                    Rect(0, 0, splitSrcX, bandSliceH),
                    Rect(targetLeft, topY, eqWidth, botY),
                    paint
                )
                canvas.drawBitmap(
                    scaledSlice,
                    Rect(splitSrcX, 0, bandSliceW, bandSliceH),
                    Rect(0, topY, bandSliceW - splitSrcX, botY),
                    paint
                )
            } else {
                canvas.drawBitmap(scaledSlice, targetLeft.toFloat(), topY.toFloat(), paint)
            }
            if (scaledSlice !== slice.bitmap) scaledSlice.recycle()
        }

        // Smooth blend 360° wrap seam at x=0 and x=eqWidth
        smoothSeam(eqBitmap, 0, min(48, eqWidth / 16), sliceYStart, sliceYEnd)
        return eqBitmap
    }

    private fun blendColors(c1: Int, c2: Int, ratio: Float): Int {
        val inv = 1f - ratio
        val r = (Color.red(c1) * inv + Color.red(c2) * ratio).roundToInt().coerceIn(0, 255)
        val g = (Color.green(c1) * inv + Color.green(c2) * ratio).roundToInt().coerceIn(0, 255)
        val b = (Color.blue(c1) * inv + Color.blue(c2) * ratio).roundToInt().coerceIn(0, 255)
        return Color.rgb(r, g, b)
    }

    /**
     * Smooths the 360-degree cylindrical wrap seam at x=0 and x=width.
     */
    private fun smoothSeam(bitmap: Bitmap, seamX: Int, radius: Int, yStart: Int, yEnd: Int) {
        val w = bitmap.width
        val h = yEnd - yStart
        if (radius <= 0 || radius * 2 >= w || h <= 0) return

        val stripW = radius * 2
        val leftStrip = IntArray(radius * h)
        val rightStrip = IntArray(radius * h)

        try {
            bitmap.getPixels(leftStrip, 0, radius, 0, yStart, radius, h)
            bitmap.getPixels(rightStrip, 0, radius, w - radius, yStart, radius, h)

            for (x in 0 until radius) {
                val t = (x + radius).toFloat() / stripW.toFloat()
                val alpha = (0.5 * (1.0 - cos(PI * t))).toFloat()
                val invAlpha = 1.0f - alpha
                for (y in 0 until h) {
                    val idx = y * radius + x
                    val cLeft = leftStrip[idx]
                    val cRight = rightStrip[idx]

                    val r = (((cRight shr 16) and 0xFF) * invAlpha + ((cLeft shr 16) and 0xFF) * alpha).roundToInt()
                    val g = (((cRight shr 8) and 0xFF) * invAlpha + ((cLeft shr 8) and 0xFF) * alpha).roundToInt()
                    val b = ((cRight and 0xFF) * invAlpha + (cLeft and 0xFF) * alpha).roundToInt()

                    leftStrip[idx] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                }
            }

            bitmap.setPixels(leftStrip, 0, radius, 0, yStart, radius, h)
        } catch (e: Exception) {
            Log.w(TAG, "Seam smoothing skipped: ${e.message}")
        }
    }

    private fun sampleAverageColor(bitmap: Bitmap, x0: Int, y0: Int, x1: Int, y1: Int): Int {
        val w = (x1 - x0).coerceAtLeast(1)
        val h = (y1 - y0).coerceAtLeast(1)
        val pixels = IntArray(w * h)
        return try {
            bitmap.getPixels(pixels, 0, w, x0, y0, w, h)
            var sumR = 0L
            var sumG = 0L
            var sumB = 0L
            for (p in pixels) {
                sumR += (p shr 16) and 0xFF
                sumG += (p shr 8) and 0xFF
                sumB += p and 0xFF
            }
            val count = pixels.size.toLong()
            val r = (sumR / count).toInt()
            val g = (sumG / count).toInt()
            val b = (sumB / count).toInt()
            (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        } catch (_: Exception) {
            Color.DKGRAY
        }
    }

    /**
     * Injects standard Google Photo Sphere (GPano) XMP metadata into JPEG bytes.
     * Compliant with Google Photos 360 viewer, Street View, and VR headsets.
     */
    fun injectGPhotoSphereXmp(jpegBytes: ByteArray, width: Int, height: Int): ByteArray {
        val xmpXml = buildGPanoXml(width, height)
        val xmpHeader = "http://ns.adobe.com/xap/1.0/\u0000".toByteArray(Charsets.UTF_8)
        val xmpBody = xmpXml.toByteArray(Charsets.UTF_8)
        val segLength = 2 + xmpHeader.size + xmpBody.size

        val app1Seg = ByteArrayOutputStream().apply {
            write(0xFF)
            write(0xE1) // APP1 marker
            write((segLength shr 8) and 0xFF)
            write(segLength and 0xFF)
            write(xmpHeader)
            write(xmpBody)
        }.toByteArray()

        // Locate SOI (0xFF 0xD8) to insert APP1 segment immediately after
        if (jpegBytes.size < 4 || jpegBytes[0] != 0xFF.toByte() || jpegBytes[1] != 0xD8.toByte()) {
            return jpegBytes
        }

        val out = ByteArrayOutputStream(jpegBytes.size + app1Seg.size)
        out.write(0xFF)
        out.write(0xD8)
        out.write(app1Seg)

        // Append remaining JPEG data skipping original SOI
        out.write(jpegBytes, 2, jpegBytes.size - 2)
        return out.toByteArray()
    }

    fun buildGPanoXml(width: Int, height: Int): String {
        return """
<x:xmpmeta xmlns:x="adobe:ns:meta/">
  <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
    <rdf:Description rdf:about=""
      xmlns:GPano="http://ns.google.com/photos/1.0/panorama/">
      <GPano:UsePanoramaViewer>True</GPano:UsePanoramaViewer>
      <GPano:CaptureSoftware>Commix Pro 360 Sphere</GPano:CaptureSoftware>
      <GPano:StitchingSoftware>Commix Panoramic Engine</GPano:StitchingSoftware>
      <GPano:ProjectionType>equirectangular</GPano:ProjectionType>
      <GPano:PoseHeadingDegrees>0.0</GPano:PoseHeadingDegrees>
      <GPano:PosePitchDegrees>0.0</GPano:PosePitchDegrees>
      <GPano:PoseRollDegrees>0.0</GPano:PoseRollDegrees>
      <GPano:InitialViewHeadingDegrees>0</GPano:InitialViewHeadingDegrees>
      <GPano:InitialViewPitchDegrees>0</GPano:InitialViewPitchDegrees>
      <GPano:InitialViewRollDegrees>0</GPano:InitialViewRollDegrees>
      <GPano:InitialHorizontalFOVDegrees>75.0</GPano:InitialHorizontalFOVDegrees>
      <GPano:CroppedAreaImageWidthPixels>$width</GPano:CroppedAreaImageWidthPixels>
      <GPano:CroppedAreaImageHeightPixels>$height</GPano:CroppedAreaImageHeightPixels>
      <GPano:FullPanoWidthPixels>$width</GPano:FullPanoWidthPixels>
      <GPano:FullPanoHeightPixels>$height</GPano:FullPanoHeightPixels>
      <GPano:CroppedAreaLeftPixels>0</GPano:CroppedAreaLeftPixels>
      <GPano:CroppedAreaTopPixels>0</GPano:CroppedAreaTopPixels>
    </rdf:Description>
  </rdf:RDF>
</x:xmpmeta>
        """.trimIndent()
    }
}
