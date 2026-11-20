package com.txor.commix

import com.txor.commix.model.CameraUiState
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class SpatialCaptureEngineTest {

    @Test
    fun spatial180GridGenerates13HemisphereNodesWithinFront180() {
        val grid = SpatialCaptureEngine.generateTargetGrid(SpatialFov.SPATIAL_180)
        assertEquals(13, grid.size)
        assertEquals(13, grid.map { it.id }.toSet().size)
        assertEquals(5, grid.count { it.ring == "HORIZON" })
        assertEquals(4, grid.count { it.ring == "UPPER" })
        assertEquals(4, grid.count { it.ring == "LOWER" })
        assertTrue("All 180° target yaws must lie inside [-90°, 90°]", grid.all { abs(it.yawDeg) <= 90f })
        assertTrue("Origin anchor node (0°, 0°) must exist", grid.any { it.yawDeg == 0f && it.pitchDeg == 0f })
    }

    @Test
    fun spatial360GridGenerates22FullSphereNodesIncludingPoles() {
        val grid = SpatialCaptureEngine.generateTargetGrid(SpatialFov.SPATIAL_360)
        assertEquals(22, grid.size)
        assertEquals(22, grid.map { it.id }.toSet().size)
        assertEquals(8, grid.count { it.ring == "HORIZON" })
        assertEquals(6, grid.count { it.ring == "UPPER" })
        assertEquals(6, grid.count { it.ring == "LOWER" })
        assertEquals(1, grid.count { it.ring == "ZENITH" && it.pitchDeg >= 75f })
        assertEquals(1, grid.count { it.ring == "NADIR" && it.pitchDeg <= -75f })
        // Full 360° coverage must include targets behind the user (|yaw| > 120°)
        assertTrue(grid.any { abs(it.yawDeg) >= 135f })
    }

    @Test
    fun greatCircleAngularDistanceHandlesWrapAroundAndPoles() {
        // Across the +/-180° meridian
        val wrapDist = SpatialCaptureEngine.angularDistanceDeg(179f, 0f, -179f, 0f)
        assertEquals(2.0f, wrapDist, 0.05f)

        // Equator to north pole (clamped near 90°)
        val poleDist = SpatialCaptureEngine.angularDistanceDeg(0f, 0f, 120f, 89.9f)
        assertEquals(89.9f, poleDist, 0.2f)

        // Identical look direction
        val zeroDist = SpatialCaptureEngine.angularDistanceDeg(-45f, 30f, -45f, 30f)
        assertEquals(0f, zeroDist, 0.001f)
    }

    @Test
    fun projectionCentersAlignedTargetAndFlagsRearTargetsNotInFront() {
        // Looking directly at target -> exact center (0.5, 0.5)
        val center = SpatialCaptureEngine.projectPointToViewfinder(
            targetYawDeg = 36f,
            targetPitchDeg = 15f,
            currentYawDeg = 36f,
            currentPitchDeg = 15f,
            rollDeg = 0f
        )
        assertTrue(center.inFront)
        assertEquals(0.5f, center.normX, 0.002f)
        assertEquals(0.5f, center.normY, 0.002f)
        assertEquals(0f, center.angularDistanceDeg, 0.01f)

        // Target to the right (+15° yaw) and up (+15° pitch) -> normX > 0.5, normY < 0.5
        val upRight = SpatialCaptureEngine.projectPointToViewfinder(
            targetYawDeg = 15f,
            targetPitchDeg = 15f,
            currentYawDeg = 0f,
            currentPitchDeg = 0f,
            rollDeg = 0f
        )
        assertTrue(upRight.inFront)
        assertTrue("Target right of camera must project right of center", upRight.normX > 0.6f)
        assertTrue("Target above camera must project above center", upRight.normY < 0.4f)

        // Target directly behind camera (180° away) -> inFront == false
        val behind = SpatialCaptureEngine.projectPointToViewfinder(
            targetYawDeg = 180f,
            targetPitchDeg = 0f,
            currentYawDeg = 0f,
            currentPitchDeg = 0f,
            rollDeg = 0f
        )
        assertFalse(behind.inFront)
        assertEquals(180f, behind.angularDistanceDeg, 0.1f)
    }

    @Test
    fun nearestUncapturedNodeSkipsCapturedAndTriggersAlignmentWithinTolerance() {
        val grid = SpatialCaptureEngine.generateTargetGrid(SpatialFov.SPATIAL_180)
        // Mark origin node captured
        val updated = grid.map { if (it.yawDeg == 0f && it.pitchDeg == 0f) it.copy(captured = true) else it }

        val nearest = SpatialCaptureEngine.findNearestUncapturedNode(34f, 2f, updated)
        assertNotNull(nearest)
        assertEquals(36f, nearest!!.first.yawDeg, 0.01f)
        assertEquals(0f, nearest.first.pitchDeg, 0.01f)
        assertTrue(SpatialCaptureEngine.isAlignedToNode(34f, 2f, nearest.first))
        assertFalse(SpatialCaptureEngine.isAlignedToNode(10f, 20f, nearest.first))

        val hintAligned = SpatialCaptureEngine.coachingHint(34f, 2f, nearest)
        assertTrue(hintAligned.contains("Hold steady", ignoreCase = true))

        val hintRight = SpatialCaptureEngine.coachingHint(0f, 0f, nearest)
        assertTrue(hintRight.contains("Pan right", ignoreCase = true))
    }

    @Test
    fun gpanoXmpDifferentiates180CroppedHemisphereAnd360FullEquirectangular() {
        val fakeJpeg = byteArrayOf(
            0xFF.toByte(), 0xD8.toByte(), // SOI
            0xFF.toByte(), 0xD9.toByte()  // EOI
        )

        // 180° VR Hemisphere: 2048x2048 cropped out of 4096x2048 full sphere centered at left=1024
        val jpeg180 = SpatialCaptureEngine.injectSpatialGPanoXmp(
            jpegBytes = fakeJpeg,
            width = 2048,
            height = 2048,
            fov = SpatialFov.SPATIAL_180,
            poseHeadingDeg = 92.5f
        )
        assertEquals(0xFF.toByte(), jpeg180[0])
        assertEquals(0xD8.toByte(), jpeg180[1])
        assertEquals(0xFF.toByte(), jpeg180[2])
        assertEquals(0xE1.toByte(), jpeg180[3])
        val text180 = String(jpeg180, Charsets.UTF_8)
        assertTrue(text180.contains("<GPano:UsePanoramaViewer>True</GPano:UsePanoramaViewer>"))
        assertTrue(text180.contains("<GPano:ProjectionType>equirectangular</GPano:ProjectionType>"))
        assertTrue(text180.contains("<GPano:CroppedAreaImageWidthPixels>2048</GPano:CroppedAreaImageWidthPixels>"))
        assertTrue(text180.contains("<GPano:FullPanoWidthPixels>4096</GPano:FullPanoWidthPixels>"))
        assertTrue(text180.contains("<GPano:CroppedAreaLeftPixels>1024</GPano:CroppedAreaLeftPixels>"))
        assertTrue(text180.contains("<GPano:PoseHeadingDegrees>92.5</GPano:PoseHeadingDegrees>"))

        // 360° Full Sphere: 4096x2048 full equirectangular with zero horizontal crop offset
        val jpeg360 = SpatialCaptureEngine.injectSpatialGPanoXmp(
            jpegBytes = fakeJpeg,
            width = 4096,
            height = 2048,
            fov = SpatialFov.SPATIAL_360,
            poseHeadingDeg = -10f
        )
        val text360 = String(jpeg360, Charsets.UTF_8)
        assertTrue(text360.contains("<GPano:CroppedAreaImageWidthPixels>4096</GPano:CroppedAreaImageWidthPixels>"))
        assertTrue(text360.contains("<GPano:FullPanoWidthPixels>4096</GPano:FullPanoWidthPixels>"))
        assertTrue(text360.contains("<GPano:CroppedAreaLeftPixels>0</GPano:CroppedAreaLeftPixels>"))
        assertTrue(text360.contains("<GPano:PoseHeadingDegrees>350.0</GPano:PoseHeadingDegrees>"))
    }

    @Test
    fun cameraUiStateSpatialDefaultsAreValid() {
        val state = CameraUiState()
        assertEquals(180, state.spatialFovDeg)
        assertFalse(state.isSpatialCapturing)
        assertTrue(state.spatialAutoCapture)
        assertEquals(13, state.spatialTotalNodes)
        assertEquals(SpatialFov.SPATIAL_180, SpatialFov.fromDegrees(state.spatialFovDeg))
        assertEquals(SpatialFov.SPATIAL_360, SpatialFov.fromDegrees(360))
    }
}
