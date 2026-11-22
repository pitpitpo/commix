package com.txor.commix

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.Log
import java.io.ByteArrayOutputStream
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan

enum class SpatialFov(val degrees: Int, val label: String) {
    SPATIAL_180(180, "180°"),
    SPATIAL_360(360, "360°");

    companion object {
        fun fromDegrees(deg: Int): SpatialFov =
            if (deg >= 270) SPATIAL_360 else SPATIAL_180
    }
}

data class SpatialTargetNode(
    val id: Int,
    val yawDeg: Float,   // Relative azimuth from capture origin in [-180, 180]
    val pitchDeg: Float, // Elevation angle in [-90, +90] (+ is up toward zenith)
    val ring: String,    // "HORIZON", "UPPER", "LOWER", "ZENITH", "NADIR"
    val captured: Boolean = false
)

data class SpatialRawFrame(
    val jpegBytes: ByteArray,
    val relYawDeg: Float,
    val relPitchDeg: Float,
    val rollDeg: Float,
    val targetNodeId: Int
)

data class SpatialProjectedPoint(
    val normX: Float,   // 0..1 across viewfinder (can be outside [0,1] when off-screen)
    val normY: Float,   // 0..1 down viewfinder
    val inFront: Boolean,
    val angularDistanceDeg: Float
)

/**
 * SpatialCaptureEngine: End-to-end 180° VR Hemisphere & 360° Full Equirectangular Sphere engine.
 *
 * - Generates spherical target lattices tailored for 180° (front hemisphere dome) and 360° (full sphere).
 * - Computes exact spherical great-circle angular separation and 3D gnomonic tangent-plane projections
 *   with live roll compensation so AR target rings float accurately on the camera viewfinder.
 * - Stitches multi-row spherical captures into equirectangular canvases (1:1 180°×180° or 2:1 360°×180°)
 *   using roll-rectified placement, soft radial feather blending, 360° wrap-seam closure, and ambient
 *   sky/ground zenith/nadir interpolation.
 * - Injects standards-compliant Google Photo Sphere / VR180 GPano XMP APP1 metadata so viewers
 *   (Google Photos, VR headsets, spatial galleries) render both 180° and 360° natively without distortion.
 */
object SpatialCaptureEngine {
    private const val TAG = "SpatialCaptureEngine"
    const val DEFAULT_HFOV_DEG = 66.0f
    const val DEFAULT_VFOV_DEG = 84.0f
    const val ALIGNMENT_LOCK_TOLERANCE_DEG = 8.5f

    /**
     * Generates the spherical target lattice for either 180° VR Hemisphere or 360° Full Sphere.
     */
    fun generateTargetGrid(fov: SpatialFov): List<SpatialTargetNode> {
        val nodes = mutableListOf<SpatialTargetNode>()
        var nextId = 0

        if (fov == SpatialFov.SPATIAL_180) {
            // Front 180° hemisphere dome:
            // Horizon ring: 5 nodes (-72°, -36°, 0°, +36°, +72°)
            // Upper +38° ring: 4 nodes (-54°, -18°, +18°, +54°)
            // Lower -38° ring: 4 nodes (-54°, -18°, +18°, +54°)
            val horizonYaws = listOf(0f, 36f, -36f, 72f, -72f)
            for (y in horizonYaws) {
                nodes.add(SpatialTargetNode(nextId++, y, 0f, "HORIZON"))
            }
            val elevatedYaws = listOf(-54f, -18f, 18f, 54f)
            for (y in elevatedYaws) {
                nodes.add(SpatialTargetNode(nextId++, y, 38f, "UPPER"))
            }
            for (y in elevatedYaws) {
                nodes.add(SpatialTargetNode(nextId++, y, -38f, "LOWER"))
            }
        } else {
            // Full 360° sphere:
            // Horizon ring (0°): 8 nodes every 45°
            // Upper ring (+42°): 6 nodes every 60°
            // Lower ring (-42°): 6 nodes every 60°
            // Zenith (+80°) and Nadir (-80°): 2 polar caps (22 nodes total)
            for (i in 0 until 8) {
                val yaw = normalizeYaw(i * 45f)
                nodes.add(SpatialTargetNode(nextId++, yaw, 0f, "HORIZON"))
            }
            for (i in 0 until 6) {
                val yaw = normalizeYaw(i * 60f)
                nodes.add(SpatialTargetNode(nextId++, yaw, 42f, "UPPER"))
            }
            for (i in 0 until 6) {
                val yaw = normalizeYaw(i * 60f)
                nodes.add(SpatialTargetNode(nextId++, yaw, -42f, "LOWER"))
            }
            nodes.add(SpatialTargetNode(nextId++, 0f, 80f, "ZENITH"))
            nodes.add(SpatialTargetNode(nextId++, 0f, -80f, "NADIR"))
        }

        return nodes
    }

    /** Normalizes any angle into [-180, 180). */
    fun normalizeYaw(deg: Float): Float {
        var a = deg % 360f
        if (a >= 180f) a -= 360f
        if (a < -180f) a += 360f
        return a
    }

    /**
     * Exact spherical great-circle distance in degrees between two (yaw, pitch) unit vectors.
     */
    fun angularDistanceDeg(yaw1Deg: Float, pitch1Deg: Float, yaw2Deg: Float, pitch2Deg: Float): Float {
        val y1 = yaw1Deg * PI / 180.0
        val p1 = pitch1Deg.coerceIn(-89.9f, 89.9f) * PI / 180.0
        val y2 = yaw2Deg * PI / 180.0
        val p2 = pitch2Deg.coerceIn(-89.9f, 89.9f) * PI / 180.0

        val x1 = cos(p1) * sin(y1)
        val u1 = sin(p1)
        val z1 = cos(p1) * cos(y1)

        val x2 = cos(p2) * sin(y2)
        val u2 = sin(p2)
        val z2 = cos(p2) * cos(y2)

        val dot = (x1 * x2 + u1 * u2 + z1 * z2).coerceIn(-1.0, 1.0)
        return (acos(dot) * 180.0 / PI).toFloat()
    }

    /** Finds the closest uncaptured target node to the current camera attitude. */
    fun findNearestUncapturedNode(
        relYawDeg: Float,
        relPitchDeg: Float,
        nodes: List<SpatialTargetNode>
    ): Pair<SpatialTargetNode, Float>? {
        var best: SpatialTargetNode? = null
        var bestDist = Float.MAX_VALUE
        for (node in nodes) {
            if (node.captured) continue
            val d = angularDistanceDeg(relYawDeg, relPitchDeg, node.yawDeg, node.pitchDeg)
            if (d < bestDist) {
                bestDist = d
                best = node
            }
        }
        return best?.let { it to bestDist }
    }

    /** Returns true when the viewfinder center is aligned tightly onto [targetNode]. */
    fun isAlignedToNode(
        relYawDeg: Float,
        relPitchDeg: Float,
        targetNode: SpatialTargetNode,
        toleranceDeg: Float = ALIGNMENT_LOCK_TOLERANCE_DEG
    ): Boolean {
        return angularDistanceDeg(relYawDeg, relPitchDeg, targetNode.yawDeg, targetNode.pitchDeg) <= toleranceDeg
    }

    /**
     * Projects a 3D spherical target (targetYaw, targetPitch) onto normalized viewfinder coordinates
     * [0..1] x [0..1] using camera-frame tangent-plane projection with roll compensation.
     */
    fun projectPointToViewfinder(
        targetYawDeg: Float,
        targetPitchDeg: Float,
        currentYawDeg: Float,
        currentPitchDeg: Float,
        rollDeg: Float = 0f,
        hfovDeg: Float = DEFAULT_HFOV_DEG,
        vfovDeg: Float = DEFAULT_VFOV_DEG
    ): SpatialProjectedPoint {
        val dist = angularDistanceDeg(currentYawDeg, currentPitchDeg, targetYawDeg, targetPitchDeg)

        val dYaw = normalizeYaw(targetYawDeg - currentYawDeg) * PI / 180.0
        val tPitch = targetPitchDeg.coerceIn(-89.5f, 89.5f) * PI / 180.0
        val cPitch = currentPitchDeg.coerceIn(-89.5f, 89.5f) * PI / 180.0

        // Target vector in yaw-aligned local frame (camera forward = +Z, right = +X, up = +Y)
        val tx = cos(tPitch) * sin(dYaw)
        val ty = sin(tPitch)
        val tz = cos(tPitch) * cos(dYaw)

        // Rotate by -currentPitch around X axis to enter camera look frame
        val cosP = cos(cPitch)
        val sinP = sin(cPitch)
        val camX = tx
        val camY = ty * cosP - tz * sinP
        val camZ = ty * sinP + tz * cosP

        // Apply camera roll compensation around Z axis
        val rollRad = rollDeg * PI / 180.0
        val cosR = cos(rollRad)
        val sinR = sin(rollRad)
        val rx = camX * cosR - camY * sinR
        val ry = camX * sinR + camY * cosR

        val inFront = camZ > 0.12
        val halfTanH = tan((hfovDeg * 0.5) * PI / 180.0).coerceAtLeast(0.1)
        val halfTanV = tan((vfovDeg * 0.5) * PI / 180.0).coerceAtLeast(0.1)

        val denom = if (inFront) camZ else 0.12
        val normX = (0.5 + (rx / denom) / (2.0 * halfTanH)).toFloat()
        val normY = (0.5 - (ry / denom) / (2.0 * halfTanV)).toFloat()

        return SpatialProjectedPoint(
            normX = normX,
            normY = normY,
            inFront = inFront,
            angularDistanceDeg = dist
        )
    }

    /** Computes a human-readable directional coaching cue toward the nearest uncaptured node. */
    fun coachingHint(
        relYawDeg: Float,
        relPitchDeg: Float,
        nearest: Pair<SpatialTargetNode, Float>?
    ): String {
        if (nearest == null) return "Sphere complete — stitching..."
        val (node, _) = nearest
        val dist = angularDistanceDeg(relYawDeg, relPitchDeg, node.yawDeg, node.pitchDeg)
        if (dist <= ALIGNMENT_LOCK_TOLERANCE_DEG) return "Hold steady — locking capture"
        val dyaw = normalizeYaw(node.yawDeg - relYawDeg)
        val dpitch = node.pitchDeg - relPitchDeg
        return when {
            abs(dpitch) > abs(dyaw) && dpitch > 6f -> "Tilt up to glowing dot"
            abs(dpitch) > abs(dyaw) && dpitch < -6f -> "Tilt down to glowing dot"
            dyaw > 6f -> "Pan right to glowing dot"
            dyaw < -6f -> "Pan left to glowing dot"
            else -> "Center reticle on glowing dot"
        }
    }

    /**
     * Stitches multi-row spatial captures into an equirectangular JPEG with valid GPano XMP.
     * - 180° mode produces a 1:1 (180°×180°) spherical equirectangular projection with cropped-area GPano tags.
     * - 360° mode produces a 2:1 (360°×180°) full equirectangular sphere with seamless wrap-around closure.
     */
    fun stitchSpatialToJpeg(
        frames: List<SpatialRawFrame>,
        fov: SpatialFov,
        initialHeadingDeg: Float = 0f,
        hfovDeg: Float = DEFAULT_HFOV_DEG,
        vfovDeg: Float = DEFAULT_VFOV_DEG,
        quality: Int = 95
    ): ByteArray {
        require(frames.isNotEmpty()) { "Cannot stitch empty spatial frame list" }

        val decoded = frames.mapNotNull { f ->
            val bmp = decodeSpatialTileSafe(f.jpegBytes, maxDimension = 1024)
            if (bmp != null) f to bmp else null
        }
        if (decoded.isEmpty()) {
            throw IllegalStateException("Failed to decode spatial capture frames")
        }

        val stitched = stitchSpatialBitmaps(decoded, fov, hfovDeg, vfovDeg)
        val bos = ByteArrayOutputStream()
        stitched.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(70, 100), bos)
        val rawJpeg = bos.toByteArray()
        val outW = stitched.width
        val outH = stitched.height

        decoded.forEach { (_, bmp) -> if (!bmp.isRecycled) bmp.recycle() }
        if (!stitched.isRecycled) stitched.recycle()

        return injectSpatialGPanoXmp(
            jpegBytes = rawJpeg,
            width = outW,
            height = outH,
            fov = fov,
            poseHeadingDeg = initialHeadingDeg
        )
    }

    /**
     * Decodes a spatial JPEG tile using power-of-two downsampling (preventing OOM on 12MP–64MP
     * multi-row spheres) and applies any embedded EXIF rotation so tiles always project upright.
     */
    private fun decodeSpatialTileSafe(jpegBytes: ByteArray, maxDimension: Int = 1024): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size, bounds)
            val srcMax = max(bounds.outWidth, bounds.outHeight)
            if (srcMax <= 0) return null
            var sample = 1
            while (srcMax / (sample * 2) >= maxDimension) {
                sample *= 2
            }
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val decoded = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size, opts) ?: return null
            val exifDeg = OrientationPolicy.readExifRotationDegrees(jpegBytes)
            if (exifDeg != 0) {
                val matrix = Matrix().apply { postRotate(exifDeg.toFloat()) }
                val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
                if (rotated !== decoded) decoded.recycle()
                rotated
            } else {
                decoded
            }
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Projects decoded multi-row spherical frames onto a 180° (1:1) or 360° (2:1) equirectangular bitmap.
     */
    fun stitchSpatialBitmaps(
        frames: List<Pair<SpatialRawFrame, Bitmap>>,
        fov: SpatialFov,
        hfovDeg: Float = DEFAULT_HFOV_DEG,
        vfovDeg: Float = DEFAULT_VFOV_DEG
    ): Bitmap {
        require(frames.isNotEmpty()) { "Cannot stitch empty spatial bitmap list" }

        val firstBmp = frames.first().second
        val baseH = max(1440, min(2560, (firstBmp.height * 1.6f).roundToInt()))
        val outH = (baseH / 2) * 2
        val outW = if (fov == SpatialFov.SPATIAL_360) outH * 2 else outH
        val totalYawSpan = fov.degrees.toFloat() // 180f or 360f

        val outBitmap = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(outBitmap)

        // 1. Ambient sky-to-ground polar gradient backdrop so uncaptured zenith/nadir caps blend naturally
        val topColor = sampleStripAverage(firstBmp, top = true)
        val bottomColor = sampleStripAverage(firstBmp, top = false)
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, 0f, outH.toFloat(),
                intArrayOf(topColor, blendColor(topColor, bottomColor, 0.5f), bottomColor),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, outW.toFloat(), outH.toFloat(), bgPaint)

        val tilePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        // Sort so horizon frames paint first, then upper/lower rings, then polar caps on top with soft edges
        val ordered = frames.sortedBy { abs(it.first.relPitchDeg) }

        for ((meta, srcBmp) in ordered) {
            val clampedPitch = meta.relPitchDeg.coerceIn(-85f, 85f)
            val cosLat = cos(clampedPitch * PI / 180.0).toFloat().coerceAtLeast(0.28f)

            // Tile footprint on equirectangular grid (widens horizontally near poles by 1/cos(lat))
            val tileW = ((hfovDeg / totalYawSpan) * outW / cosLat).coerceIn(outW * 0.14f, outW * 0.85f)
            val tileH = ((vfovDeg / 180f) * outH).coerceIn(outH * 0.22f, outH * 0.75f)

            val centerY = ((90f - clampedPitch) / 180f) * outH
            val normYaw = if (fov == SpatialFov.SPATIAL_360) {
                ((normalizeYaw(meta.relYawDeg) + 180f) / 360f) // Center origin at 0.5
            } else {
                ((meta.relYawDeg.coerceIn(-95f, 95f) + 90f) / 180f)
            }
            val centerX = normYaw * outW

            val feathered = createFeatheredTile(srcBmp, tileW.roundToInt().coerceAtLeast(16), tileH.roundToInt().coerceAtLeast(16))

            fun drawAt(cx: Float, cy: Float) {
                val matrix = Matrix().apply {
                    postTranslate(-feathered.width / 2f, -feathered.height / 2f)
                    if (abs(meta.rollDeg) > 0.5f) {
                        postRotate(-meta.rollDeg)
                    }
                    postTranslate(cx, cy)
                }
                canvas.drawBitmap(feathered, matrix, tilePaint)
            }

            drawAt(centerX, centerY)
            // Seamless 360° wrap-around duplication when a tile straddles x=0 or x=outW
            if (fov == SpatialFov.SPATIAL_360) {
                if (centerX - tileW * 0.6f < 0f) {
                    drawAt(centerX + outW, centerY)
                }
                if (centerX + tileW * 0.6f > outW) {
                    drawAt(centerX - outW, centerY)
                }
            }
            if (!feathered.isRecycled) feathered.recycle()
        }

        return outBitmap
    }

    /**
     * Scales a frame tile and applies soft radial edge feathering so overlapping spherical rows merge seamlessly.
     */
    private fun createFeatheredTile(src: Bitmap, dstW: Int, dstH: Int): Bitmap {
        val scaled = Bitmap.createScaledBitmap(src, dstW, dstH, true)
        val out = Bitmap.createBitmap(dstW, dstH, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        c.drawBitmap(scaled, 0f, 0f, p)
        if (scaled !== src && !scaled.isRecycled) scaled.recycle()

        // Apply elliptical edge alpha mask using DST_IN radial gradient
        val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_IN)
            val rad = max(dstW, dstH) * 0.58f
            shader = RadialGradient(
                dstW / 2f, dstH / 2f, rad.coerceAtLeast(1f),
                intArrayOf(Color.WHITE, Color.WHITE, Color.TRANSPARENT),
                floatArrayOf(0f, 0.72f, 1.0f),
                Shader.TileMode.CLAMP
            )
        }
        c.drawRect(0f, 0f, dstW.toFloat(), dstH.toFloat(), maskPaint)
        return out
    }

    private fun sampleStripAverage(bitmap: Bitmap, top: Boolean): Int {
        val w = bitmap.width.coerceAtLeast(1)
        val stripH = min(16, bitmap.height).coerceAtLeast(1)
        val y0 = if (top) 0 else (bitmap.height - stripH).coerceAtLeast(0)
        val pixels = IntArray(w * stripH)
        return try {
            bitmap.getPixels(pixels, 0, w, 0, y0, w, stripH)
            var r = 0L; var g = 0L; var b = 0L
            for (px in pixels) {
                r += (px shr 16) and 0xFF
                g += (px shr 8) and 0xFF
                b += px and 0xFF
            }
            val n = pixels.size.toLong().coerceAtLeast(1L)
            (0xFF shl 24) or ((r / n).toInt() shl 16) or ((g / n).toInt() shl 8) or (b / n).toInt()
        } catch (e: Exception) {
            Log.w(TAG, "Strip color sample fallback: ${e.message}")
            if (top) Color.rgb(30, 42, 64) else Color.rgb(28, 28, 30)
        }
    }

    private fun blendColor(c1: Int, c2: Int, t: Float): Int {
        val inv = 1f - t
        val r = (((c1 shr 16) and 0xFF) * inv + ((c2 shr 16) and 0xFF) * t).toInt().coerceIn(0, 255)
        val g = (((c1 shr 8) and 0xFF) * inv + ((c2 shr 8) and 0xFF) * t).toInt().coerceIn(0, 255)
        val b = ((c1 and 0xFF) * inv + (c2 and 0xFF) * t).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    /**
     * Builds standards-compliant Google Photo Sphere / VR180 GPano XMP XML for 180° or 360° spatial captures.
     */
    fun buildSpatialGPanoXml(
        width: Int,
        height: Int,
        fov: SpatialFov,
        poseHeadingDeg: Float = 0f
    ): String {
        val fullW = if (fov == SpatialFov.SPATIAL_360) width else width * 2
        val fullH = height
        val cropLeft = if (fov == SpatialFov.SPATIAL_360) 0 else width / 2
        val softwareTag = if (fov == SpatialFov.SPATIAL_360) "Commix Pro Spatial 360" else "Commix Pro Spatial 180"
        val normHeading = ((poseHeadingDeg % 360f) + 360f) % 360f

        return """
<x:xmpmeta xmlns:x="adobe:ns:meta/">
  <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
    <rdf:Description rdf:about=""
      xmlns:GPano="http://ns.google.com/photos/1.0/panorama/">
      <GPano:UsePanoramaViewer>True</GPano:UsePanoramaViewer>
      <GPano:CaptureSoftware>$softwareTag</GPano:CaptureSoftware>
      <GPano:StitchingSoftware>Commix Aether Spatial Engine</GPano:StitchingSoftware>
      <GPano:ProjectionType>equirectangular</GPano:ProjectionType>
      <GPano:PoseHeadingDegrees>${String.format(Locale.US, "%.1f", normHeading)}</GPano:PoseHeadingDegrees>
      <GPano:PosePitchDegrees>0.0</GPano:PosePitchDegrees>
      <GPano:PoseRollDegrees>0.0</GPano:PoseRollDegrees>
      <GPano:InitialViewHeadingDegrees>0</GPano:InitialViewHeadingDegrees>
      <GPano:InitialViewPitchDegrees>0</GPano:InitialViewPitchDegrees>
      <GPano:InitialViewRollDegrees>0</GPano:InitialViewRollDegrees>
      <GPano:InitialHorizontalFOVDegrees>90.0</GPano:InitialHorizontalFOVDegrees>
      <GPano:CroppedAreaImageWidthPixels>$width</GPano:CroppedAreaImageWidthPixels>
      <GPano:CroppedAreaImageHeightPixels>$height</GPano:CroppedAreaImageHeightPixels>
      <GPano:FullPanoWidthPixels>$fullW</GPano:FullPanoWidthPixels>
      <GPano:FullPanoHeightPixels>$fullH</GPano:FullPanoHeightPixels>
      <GPano:CroppedAreaLeftPixels>$cropLeft</GPano:CroppedAreaLeftPixels>
      <GPano:CroppedAreaTopPixels>0</GPano:CroppedAreaTopPixels>
    </rdf:Description>
  </rdf:RDF>
</x:xmpmeta>
        """.trimIndent()
    }

    /**
     * Injects GPano XMP APP1 segment immediately after the JPEG SOI marker (0xFF 0xD8).
     */
    fun injectSpatialGPanoXmp(
        jpegBytes: ByteArray,
        width: Int,
        height: Int,
        fov: SpatialFov,
        poseHeadingDeg: Float = 0f
    ): ByteArray {
        if (jpegBytes.size < 4 || jpegBytes[0] != 0xFF.toByte() || jpegBytes[1] != 0xD8.toByte()) {
            return jpegBytes
        }
        val xmpXml = buildSpatialGPanoXml(width, height, fov, poseHeadingDeg)
        val xmpHeader = "http://ns.adobe.com/xap/1.0/\u0000".toByteArray(Charsets.UTF_8)
        val xmpBody = xmpXml.toByteArray(Charsets.UTF_8)
        val segLength = 2 + xmpHeader.size + xmpBody.size

        val app1Seg = ByteArrayOutputStream().apply {
            write(0xFF)
            write(0xE1)
            write((segLength shr 8) and 0xFF)
            write(segLength and 0xFF)
            write(xmpHeader)
            write(xmpBody)
        }.toByteArray()

        val out = ByteArrayOutputStream(jpegBytes.size + app1Seg.size)
        out.write(0xFF)
        out.write(0xD8)
        out.write(app1Seg)
        out.write(jpegBytes, 2, jpegBytes.size - 2)
        return out.toByteArray()
    }
}
