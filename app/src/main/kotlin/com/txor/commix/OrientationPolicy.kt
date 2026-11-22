package com.txor.commix

/**
 * Encapsulates Android Camera2 JPEG / EXIF orientation math.
 * Pure logic — 100% JVM testable with zero Android SDK dependencies.
 */
object OrientationPolicy {

    const val ROTATION_0 = 0
    const val ROTATION_90 = 90
    const val ROTATION_180 = 180
    const val ROTATION_270 = 270

    /** Maps `android.view.Surface.ROTATION_*` (0..3) to degrees. */
    fun degreesFromSurfaceRotation(surfaceRotation: Int): Int = when (surfaceRotation) {
        0 -> ROTATION_0
        1 -> ROTATION_90
        2 -> ROTATION_180
        3 -> ROTATION_270
        else -> ROTATION_0
    }

    /** Normalises any integer to one of 0/90/180/270. */
    fun quantizeDegrees(degrees: Int): Int {
        val q = Math.round(degrees / 90.0).toInt()
        return ((q * 90) % 360 + 360) % 360
    }

    /**
     * @param sensorOrientationDegrees SENSOR_ORIENTATION, degrees clockwise.
     * @param deviceRotationDegrees current device rotation; near-90 values snap to nearest quadrant.
     * @param mirrorForFrontFacing true when the delivered image is already mirrored.
     * @return 0, 90, 180 or 270 — valid for both `CaptureRequest.JPEG_ORIENTATION` and EXIF.
     */
    fun jpegOrientation(
        sensorOrientationDegrees: Int,
        deviceRotationDegrees: Int,
        mirrorForFrontFacing: Boolean
    ): Int {
        val sensor = quantizeDegrees(sensorOrientationDegrees)
        var device = quantizeDegrees(deviceRotationDegrees)
        if (mirrorForFrontFacing) device = (360 - device) % 360
        return (sensor + device) % 360
    }

    /**
     * Maps an EXIF `TAG_ORIENTATION` integer constant (1..8) to clockwise rotation degrees.
     */
    fun exifTagToDegrees(exifOrientationTag: Int): Int = when (exifOrientationTag) {
        6, 5 -> ROTATION_90   // ORIENTATION_ROTATE_90 / TRANSPOSE
        3, 4 -> ROTATION_180  // ORIENTATION_ROTATE_180 / FLIP_VERTICAL
        8, 7 -> ROTATION_270  // ORIENTATION_ROTATE_270 / TRANSVERSE
        else -> ROTATION_0
    }

    /**
     * Reads the embedded EXIF orientation rotation (0/90/180/270) from a JPEG byte array.
     * Returns 0 if the JPEG has already been pixel-rotated by the Camera2 HAL (EXIF tag = 1/normal)
     * or if no EXIF header is present.
     */
    fun readExifRotationDegrees(jpegBytes: ByteArray): Int {
        if (jpegBytes.size < 4) return ROTATION_0
        return try {
            val exif = androidx.exifinterface.media.ExifInterface(java.io.ByteArrayInputStream(jpegBytes))
            val tag = exif.getAttributeInt(
                androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
                androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL
            )
            exifTagToDegrees(tag)
        } catch (_: Throwable) {
            ROTATION_0
        }
    }

    /**
     * Resolves (pixelRotateDegrees, exifWriteDegrees) when re-encoding a Bitmap decoded from a
     * Camera2 JPEG whose embedded EXIF orientation is [sourceExifDegrees]:
     * - If the HAL already rotated the pixels (`sourceExifDegrees == 0`), neither pixels nor EXIF
     *   should be rotated again (eliminating double-rotation!).
     * - If the HAL left unrotated pixels with an EXIF tag (`sourceExifDegrees != 0`), formats
     *   that don't carry EXIF get pixel rotation (`pixelRotate = sourceExifDegrees, exif = 0`),
     *   while JPEG preserves pixels and carries `exif = sourceExifDegrees`.
     */
    fun resolvePostProcessOrientation(
        sourceExifDegrees: Int,
        outputFormat: String
    ): Pair<Int, Int> {
        val norm = quantizeDegrees(sourceExifDegrees)
        if (norm == ROTATION_0) return ROTATION_0 to ROTATION_0
        return if (requiresPixelRotation(outputFormat)) {
            norm to ROTATION_0
        } else {
            ROTATION_0 to norm
        }
    }

    /**
     * True when the format carries no EXIF orientation tag, so the pixels themselves must be
     * rotated before writing. PNG/WebP never carry it, and `BitmapFactory` drops the tag it reads.
     */
    fun requiresPixelRotation(formatName: String): Boolean =
        !(formatName.equals("jpeg", ignoreCase = true) || formatName.equals("jpg", ignoreCase = true))
}
