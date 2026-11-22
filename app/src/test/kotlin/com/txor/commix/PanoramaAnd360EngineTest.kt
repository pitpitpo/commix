package com.txor.commix

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class PanoramaAnd360EngineTest {

    @Test
    fun testWrappedDeltaRegularRotation() {
        val delta1 = PanoramaStitcher.calculateWrappedDelta(45f, 0f)
        assertEquals(45f, delta1, 0.001f)

        val delta2 = PanoramaStitcher.calculateWrappedDelta(0f, 45f)
        assertEquals(-45f, delta2, 0.001f)
    }

    @Test
    fun testWrappedDeltaAcrossCompassBoundary() {
        // Rotating from +175° to -175° is a clockwise rotation of +10°
        val cwDelta = PanoramaStitcher.calculateWrappedDelta(-175f, 175f)
        assertEquals(10f, cwDelta, 0.001f)

        // Rotating from -175° to +175° is a counter-clockwise rotation of -10°
        val ccwDelta = PanoramaStitcher.calculateWrappedDelta(175f, -175f)
        assertEquals(-10f, ccwDelta, 0.001f)

        // Small step near north: 359° to 1° (wrapped in 0..360 space)
        val northDelta = PanoramaStitcher.calculateWrappedDelta(1f, 359f)
        assertEquals(2f, northDelta, 0.001f)
    }

    @Test
    fun testProgressCalculationStandardPanorama() {
        assertEquals(0.0f, PanoramaStitcher.calculateProgress(0f, is360 = false), 0.001f)
        assertEquals(0.25f, PanoramaStitcher.calculateProgress(45f, is360 = false), 0.001f)
        assertEquals(0.5f, PanoramaStitcher.calculateProgress(90f, is360 = false), 0.001f)
        assertEquals(1.0f, PanoramaStitcher.calculateProgress(180f, is360 = false), 0.001f)
        // Coerced at 1.0f beyond 180°
        assertEquals(1.0f, PanoramaStitcher.calculateProgress(210f, is360 = false), 0.001f)
    }

    @Test
    fun testProgressCalculation360PhotoSphere() {
        assertEquals(0.0f, PanoramaStitcher.calculateProgress(0f, is360 = true), 0.001f)
        assertEquals(0.25f, PanoramaStitcher.calculateProgress(90f, is360 = true), 0.001f)
        assertEquals(0.5f, PanoramaStitcher.calculateProgress(180f, is360 = true), 0.001f)
        assertEquals(0.75f, PanoramaStitcher.calculateProgress(270f, is360 = true), 0.001f)
        assertEquals(1.0f, PanoramaStitcher.calculateProgress(360f, is360 = true), 0.001f)
        // Coerced at 1.0f beyond 360°
        assertEquals(1.0f, PanoramaStitcher.calculateProgress(390f, is360 = true), 0.001f)
    }

    @Test
    fun testGPanoXmlGeneration() {
        val xml = PanoramaStitcher.buildGPanoXml(4096, 2048)
        assertNotNull(xml)
        assertTrue(xml.contains("<GPano:UsePanoramaViewer>True</GPano:UsePanoramaViewer>"))
        assertTrue(xml.contains("<GPano:ProjectionType>equirectangular</GPano:ProjectionType>"))
        assertTrue(xml.contains("<GPano:FullPanoWidthPixels>4096</GPano:FullPanoWidthPixels>"))
        assertTrue(xml.contains("<GPano:FullPanoHeightPixels>2048</GPano:FullPanoHeightPixels>"))
        assertTrue(xml.contains("<GPano:CroppedAreaImageWidthPixels>4096</GPano:CroppedAreaImageWidthPixels>"))
        assertTrue(xml.contains("<GPano:CroppedAreaImageHeightPixels>2048</GPano:CroppedAreaImageHeightPixels>"))
        assertTrue(xml.contains("http://ns.google.com/photos/1.0/panorama/"))
    }

    @Test
    fun testGPhotoSphereXmpInjectionIntoJpeg() {
        // Mock minimal JPEG stream starting with SOI (0xFF, 0xD8) and mock tail
        val mockJpeg = byteArrayOf(
            0xFF.toByte(), 0xD8.toByte(),
            0xFF.toByte(), 0xDB.toByte(), 0x00.toByte(), 0x04.toByte(), 0x01.toByte(), 0x02.toByte(),
            0xFF.toByte(), 0xD9.toByte()
        )

        val injected = PanoramaStitcher.injectGPhotoSphereXmp(mockJpeg, 1920, 960)
        assertNotNull(injected)
        assertTrue(injected.size > mockJpeg.size)

        // Verify SOI preserved at byte 0 and 1
        assertEquals(0xFF.toByte(), injected[0])
        assertEquals(0xD8.toByte(), injected[1])

        // Verify APP1 marker inserted at byte 2 and 3
        assertEquals(0xFF.toByte(), injected[2])
        assertEquals(0xE1.toByte(), injected[3])

        // Convert byte array to string to verify XMP presence
        val injectedStr = String(injected, Charsets.ISO_8859_1)
        assertTrue(injectedStr.contains("http://ns.adobe.com/xap/1.0/"))
        assertTrue(injectedStr.contains("GPano:UsePanoramaViewer"))
        assertTrue(injectedStr.contains("equirectangular"))
    }

    @Test
    fun testProcessingJobQueuePanoramaType() = runBlocking {
        val queue = ProcessingJobQueue.instance
        queue.resumeAll()

        val job = queue.enqueueJob(
            title = "Test Pano Stitching",
            type = ProcessingJobType.PANORAMA_STITCHING,
            priority = JobPriority.HIGH
        ) { updateProgress ->
            updateProgress(0.5f, "Aligning slices")
            delay(50)
            updateProgress(1.0f, "Stitching complete")
        }

        assertEquals(ProcessingJobType.PANORAMA_STITCHING, job.type)
        assertEquals("Panorama & 360 Stitching", job.type.displayName)
        assertEquals("PANO", job.type.shortCode)

        delay(200)
        val finishedJob = queue.jobs.value.find { it.id == job.id }
        assertNotNull(finishedJob)
        assertEquals(JobStatus.COMPLETED, finishedJob?.status)
    }

    @Test
    fun testSensorHubUprightPortraitNoGimbalLock() {
        // Upright portrait facing North: +X=East, +Y=Up, +Z=South (rear camera -Z=North)
        val rNorth = floatArrayOf(
            1f, 0f, 0f,
            0f, 0f, -1f,
            0f, 1f, 0f
        )
        val attNorth = SensorHub.computeCameraOrientationDeg(rNorth)
        assertEquals(0.0, attNorth.azimuthDeg, 0.01)
        assertEquals(0.0, attNorth.pitchDeg, 0.01)
        assertEquals(0.0, attNorth.rollDeg, 0.01)

        // Upright portrait panned +90° clockwise to face East
        val rEast = floatArrayOf(
            0f, 0f, -1f,
            -1f, 0f, 0f,
            0f, 1f, 0f
        )
        val attEast = SensorHub.computeCameraOrientationDeg(rEast)
        assertEquals(90.0, attEast.azimuthDeg, 0.01)
        assertEquals(0.0, attEast.pitchDeg, 0.01)
        assertEquals(0.0, attEast.rollDeg, 0.01)

        // Facing North, tilted UP by +20°
        val rad20 = Math.toRadians(20.0)
        val c20 = kotlin.math.cos(rad20).toFloat()
        val s20 = kotlin.math.sin(rad20).toFloat()
        val rTiltedUp = floatArrayOf(
            1f, 0f, 0f,
            0f, -s20, -c20,
            0f, c20, -s20
        )
        val attUp = SensorHub.computeCameraOrientationDeg(rTiltedUp)
        assertEquals(0.0, attUp.azimuthDeg, 0.05)
        assertEquals(20.0, attUp.pitchDeg, 0.05)
        assertEquals(0.0, attUp.rollDeg, 0.05)

        // Facing North, rolled clockwise by +15°
        val rad15 = Math.toRadians(15.0)
        val c15 = kotlin.math.cos(rad15).toFloat()
        val s15 = kotlin.math.sin(rad15).toFloat()
        val rRolledCw = floatArrayOf(
            c15, s15, 0f,
            0f, 0f, -1f,
            -s15, c15, 0f
        )
        val attRoll = SensorHub.computeCameraOrientationDeg(rRolledCw)
        assertEquals(0.0, attRoll.azimuthDeg, 0.05)
        assertEquals(0.0, attRoll.pitchDeg, 0.05)
        assertEquals(15.0, attRoll.rollDeg, 0.05)

        // Gravity fallback matches exact same zero-point in upright portrait
        val gravLevel = SensorHub.computeGravityAttitudeDeg(0f, 9.81f, 0f)
        assertEquals(0.0, gravLevel.pitchDeg, 0.01)
        assertEquals(0.0, gravLevel.rollDeg, 0.01)

        // World-vertical yaw rate: rotating right in upright portrait has gy = -1 rad/s -> +57.295°/s
        val yawDegSec = SensorHub.computeWorldYawRateDegPerSec(rNorth, 0f, -1.0f, 0f)
        assertEquals(Math.toDegrees(1.0), yawDegSec, 0.05)
    }

    @Test
    fun testSweepTrackerFull360CrossesCompassSeamSmoothly() {
        val tracker = PanoramaSweepTracker(sliceStepDeg = 12f, maxSpeedDegPerSec = 34f)
        var tMs = 1000L
        tracker.start(azimuthDeg = 0f, pitchDeg = 0f, preferredDirection = 1, is360 = true, nowMs = tMs)

        var lastProgress = 0f
        var triggeredSlices = 1
        var finalSnap: PanoramaSweepSnapshot? = null

        // Sweep 0° -> 360° in 3° steps at 15°/s (200ms per 3° step)
        for (step in 1..120) {
            tMs += 200L
            val rawAngle = step * 3f
            val wrappedAz = ((rawAngle + 180f) % 360f) - 180f
            val snap = tracker.update(
                azimuthDeg = wrappedAz,
                pitchDeg = 0f,
                rollDeg = 0f,
                yawRateDegPerSec = 15f,
                nowMs = tMs,
                captureInFlight = false
            )
            assertTrue("Progress must never decrease during forward 360° sweep (step=$step)", snap.progress >= lastProgress - 0.0001f)
            lastProgress = snap.progress
            if (snap.shouldCaptureSlice) {
                tracker.markSliceTriggered()
                triggeredSlices++
            }
            finalSnap = snap
        }

        assertNotNull(finalSnap)
        assertEquals(1.0f, finalSnap!!.progress, 0.01f)
        assertTrue(finalSnap.isTargetComplete)
        assertTrue("Expected ~30 slices across 360° at 12° spacing, got $triggeredSlices", triggeredSlices in 28..32)
    }

    @Test
    fun testSweepTrackerBidirectionalAutoFlipBacktrackSpeedAndDrift() {
        val tracker = PanoramaSweepTracker(sliceStepDeg = 12f, maxSpeedDegPerSec = 34f)
        var tMs = 5000L
        // User preferred L->R (+1), but starts panning LEFT (-1) right away -> auto-flips to -1
        tracker.start(azimuthDeg = 0f, pitchDeg = 0f, preferredDirection = 1, is360 = false, nowMs = tMs)

        tMs += 500L
        val flipSnap = tracker.update(azimuthDeg = -8f, pitchDeg = 0f, rollDeg = 0f, yawRateDegPerSec = -16f, nowMs = tMs)
        assertEquals(-1, flipSnap.direction)
        assertEquals(8f, flipSnap.maxForwardSweepDeg, 0.1f)

        // Continue left to -48° capturing slices smoothly
        var az = -8f
        repeat(10) {
            tMs += 250L
            az -= 4f
            val snap = tracker.update(azimuthDeg = az, pitchDeg = 0f, rollDeg = 0f, yawRateDegPerSec = -16f, nowMs = tMs)
            if (snap.shouldCaptureSlice) tracker.markSliceTriggered()
        }
        assertTrue(tracker.capturedSlices >= 4)

        // Test speed warning: pan at 60°/s -> WARN_SPEED and suppress capture
        tMs += 100L
        val fastSnap = tracker.update(azimuthDeg = az - 6f, pitchDeg = 0f, rollDeg = 0f, yawRateDegPerSec = -60f, nowMs = tMs)
        assertTrue(fastSnap.isMovingTooFast)
        assertEquals(PanoramaGuidanceSeverity.WARN_SPEED, fastSnap.severity)
        assertFalse(fastSnap.shouldCaptureSlice)
        assertEquals("Slow down", fastSnap.guidanceHint)

        // Settle speed and test vertical drift coaching (+7° pitch -> "Tilt down slightly")
        tMs += 400L
        var driftSnap = tracker.update(azimuthDeg = az - 7f, pitchDeg = 7f, rollDeg = 0f, yawRateDegPerSec = -5f, nowMs = tMs)
        tMs += 400L
        driftSnap = tracker.update(azimuthDeg = az - 8f, pitchDeg = 7f, rollDeg = 0f, yawRateDegPerSec = -5f, nowMs = tMs)
        tMs += 400L
        driftSnap = tracker.update(azimuthDeg = az - 9f, pitchDeg = 7f, rollDeg = 0f, yawRateDegPerSec = -5f, nowMs = tMs)
        assertEquals(PanoramaGuidanceSeverity.WARN_DRIFT, driftSnap.severity)
        assertEquals("Tilt down slightly", driftSnap.guidanceHint)

        // Reverse direction by 22° -> triggers backtrack warning and auto-finish on reverse
        tMs += 500L
        val reverseSnap = tracker.update(azimuthDeg = az + 14f, pitchDeg = 0f, rollDeg = 0f, yawRateDegPerSec = 10f, nowMs = tMs)
        assertTrue(reverseSnap.isBacktracking)
        assertTrue(reverseSnap.shouldAutoFinishOnReverse)
        assertFalse(reverseSnap.shouldCaptureSlice)
    }

    @Test
    fun testOrderSlicesSpatiallyBothDirections() {
        val dummy = byteArrayOf(1)
        val rightToLeft = listOf(
            PanoramaRawSlice(dummy, signedSweepDeg = 0f, azimuthDeg = 0f),
            PanoramaRawSlice(dummy, signedSweepDeg = -12f, azimuthDeg = -12f),
            PanoramaRawSlice(dummy, signedSweepDeg = -24f, azimuthDeg = -24f)
        )
        val ordered = PanoramaStitcher.orderSlicesSpatially(rightToLeft)
        assertEquals(listOf(-24f, -12f, 0f), ordered.map { it.signedSweepDeg })
    }

    @Test
    fun testEstimateOverlapAlignmentRecoversExact2DShift() {
        // Synthesize a deterministic 2D textured panorama canvas (200 x 100)
        val sceneW = 200
        val sceneH = 100
        val scene = IntArray(sceneW * sceneH) { idx ->
            val x = idx % sceneW
            val y = idx / sceneW
            val wave1 = (kotlin.math.sin(x * 0.21) * 55.0 + kotlin.math.cos(y * 0.27) * 45.0).toInt()
            val checker = if (((x / 9) + (y / 7)) % 2 == 0) 35 else -35
            val edge = if (x in 72..78) 60 else 0
            (128 + wave1 + checker + edge).coerceIn(0, 255)
        }

        val sliceW = 120
        val sliceH = 80
        val trueDx = 34
        val trueDy = -5 // Right slice starts 5px above left slice in scene coords -> right(x, y) == left(x + trueDx, y + trueDy)

        val leftOriginX = 10
        val leftOriginY = 12
        val leftLuma = IntArray(sliceW * sliceH)
        val rightLuma = IntArray(sliceW * sliceH)

        for (y in 0 until sliceH) {
            for (x in 0 until sliceW) {
                leftLuma[y * sliceW + x] = scene[(leftOriginY + y) * sceneW + (leftOriginX + x)]
                rightLuma[y * sliceW + x] = scene[(leftOriginY + y + trueDy) * sceneW + (leftOriginX + x + trueDx)]
            }
        }

        // Feed a deliberately noisy gyro prior (off by 6px in X and 5px in Y)
        val (estDx, estDy) = PanoramaStitcher.estimateOverlapAlignment(
            leftLuma = leftLuma,
            rightLuma = rightLuma,
            width = sliceW,
            height = sliceH,
            priorDx = 28,
            priorDy = 0,
            searchRadiusX = 16,
            searchRadiusY = 10
        )

        assertTrue("Expected dx=$trueDx (±1), got $estDx", kotlin.math.abs(estDx - trueDx) <= 1)
        assertTrue("Expected dy=$trueDy (±1), got $estDy", kotlin.math.abs(estDy - trueDy) <= 1)
    }
}
