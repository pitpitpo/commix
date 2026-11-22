package com.txor.commix

import com.txor.commix.model.CustomRigPreset
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import kotlin.math.abs

/**
 * Non-tautological JVM unit tests exercising production code paths for:
 *  1. StabilizationPolicyEngine (5 engines x 4 apply-when timings x HW/SW fallback x API 33+ Preview Stab x Sweep suppression)
 *  2. Software Photo Auto-Level inscribed rectangle crop geometry (zero black corners, aspect ratio preservation, deadband & clamp)
 *  3. Real-time Y-plane 32x24 IRE luma grid sampler (False Color & Zebras)
 *  4. Motorized Focus Pull A <-> B cubic ease-in-out interpolation
 *  5. CustomRigPreset C1-C3 encode/decode persistence round-trip & malformed input safety
 *  6. SensorHub.exportGyroflowGcsv v1.3 metadata headers (camera_name, videofilename, frame_readout_time)
 */
class StabilizationAndProFeaturesTest {

    private val flagshipCaps = StabilizationPolicyEngine.HardwareCapabilities(
        hasOis = true,
        hasEis = true,
        hasPreviewStabilization = true,
        apiLevel = 35
    )

    private val softwareOnlyCaps = StabilizationPolicyEngine.HardwareCapabilities(
        hasOis = false,
        hasEis = false,
        hasPreviewStabilization = false,
        apiLevel = 30
    )

    private val eisOnlyLegacyCaps = StabilizationPolicyEngine.HardwareCapabilities(
        hasOis = false,
        hasEis = true,
        hasPreviewStabilization = false,
        apiLevel = 31
    )

    @Test
    fun hybrid_always_usesOisAndPreviewStabilizationOnApi33Plus() {
        val previewPlan = StabilizationPolicyEngine.resolve(
            engineModeRaw = StabilizationPolicyEngine.ENGINE_HYBRID,
            applyWhenRaw = StabilizationPolicyEngine.APPLY_ALWAYS,
            phase = StabilizationPolicyEngine.CapturePhase.PREVIEW,
            activeCameraMode = "VIDEO",
            caps = flagshipCaps,
            photoAutoLevelEnabled = true,
            gyroflowExportEnabled = true
        )
        assertEquals(StabilizationPolicyEngine.HAL_OIS_ON, previewPlan.halOisMode)
        assertEquals(StabilizationPolicyEngine.HAL_EIS_PREVIEW_STABILIZATION, previewPlan.halVideoStabilizationMode)
        assertFalse(previewPlan.usedSoftwareFallback)
        assertTrue(previewPlan.shouldLogGyroflow)
        assertFalse(previewPlan.shouldApplyStillAutoLevel)
    }

    @Test
    fun recordingOnly_leavesPreviewUncroppedAndEngagesDuringRecordingAndStill() {
        val previewPlan = StabilizationPolicyEngine.resolve(
            engineModeRaw = StabilizationPolicyEngine.ENGINE_HYBRID,
            applyWhenRaw = StabilizationPolicyEngine.APPLY_RECORDING_ONLY,
            phase = StabilizationPolicyEngine.CapturePhase.PREVIEW,
            activeCameraMode = "VIDEO",
            caps = flagshipCaps,
            photoAutoLevelEnabled = true,
            gyroflowExportEnabled = true
        )
        assertEquals(StabilizationPolicyEngine.HAL_OIS_OFF, previewPlan.halOisMode)
        assertEquals(StabilizationPolicyEngine.HAL_EIS_OFF, previewPlan.halVideoStabilizationMode)

        val recordPlan = StabilizationPolicyEngine.resolve(
            engineModeRaw = StabilizationPolicyEngine.ENGINE_HYBRID,
            applyWhenRaw = StabilizationPolicyEngine.APPLY_RECORDING_ONLY,
            phase = StabilizationPolicyEngine.CapturePhase.VIDEO_RECORDING,
            activeCameraMode = "VIDEO",
            caps = flagshipCaps,
            photoAutoLevelEnabled = true,
            gyroflowExportEnabled = true
        )
        assertEquals(StabilizationPolicyEngine.HAL_OIS_ON, recordPlan.halOisMode)
        assertEquals(StabilizationPolicyEngine.HAL_EIS_ON, recordPlan.halVideoStabilizationMode)
        assertTrue(recordPlan.shouldLogGyroflow)

        val stillPlan = StabilizationPolicyEngine.resolve(
            engineModeRaw = StabilizationPolicyEngine.ENGINE_HYBRID,
            applyWhenRaw = StabilizationPolicyEngine.APPLY_RECORDING_ONLY,
            phase = StabilizationPolicyEngine.CapturePhase.STILL_CAPTURE,
            activeCameraMode = "PHOTO",
            caps = flagshipCaps,
            photoAutoLevelEnabled = true,
            gyroflowExportEnabled = false
        )
        assertEquals(StabilizationPolicyEngine.HAL_OIS_ON, stillPlan.halOisMode)
        assertTrue(stillPlan.shouldApplyStillAutoLevel)
    }

    @Test
    fun previewOnly_stabilizesPreviewButCapturesCleanUncroppedWithGyroflow() {
        val previewPlan = StabilizationPolicyEngine.resolve(
            engineModeRaw = StabilizationPolicyEngine.ENGINE_HYBRID,
            applyWhenRaw = StabilizationPolicyEngine.APPLY_PREVIEW_ONLY,
            phase = StabilizationPolicyEngine.CapturePhase.PREVIEW,
            activeCameraMode = "CINEMATIC",
            caps = flagshipCaps,
            photoAutoLevelEnabled = true,
            gyroflowExportEnabled = true
        )
        assertEquals(StabilizationPolicyEngine.HAL_OIS_ON, previewPlan.halOisMode)
        assertEquals(StabilizationPolicyEngine.HAL_EIS_PREVIEW_STABILIZATION, previewPlan.halVideoStabilizationMode)

        val recordPlan = StabilizationPolicyEngine.resolve(
            engineModeRaw = StabilizationPolicyEngine.ENGINE_HYBRID,
            applyWhenRaw = StabilizationPolicyEngine.APPLY_PREVIEW_ONLY,
            phase = StabilizationPolicyEngine.CapturePhase.VIDEO_RECORDING,
            activeCameraMode = "CINEMATIC",
            caps = flagshipCaps,
            photoAutoLevelEnabled = true,
            gyroflowExportEnabled = true
        )
        assertEquals(StabilizationPolicyEngine.HAL_OIS_OFF, recordPlan.halOisMode)
        assertEquals(StabilizationPolicyEngine.HAL_EIS_OFF, recordPlan.halVideoStabilizationMode)
        assertTrue(recordPlan.shouldLogGyroflow)
    }

    @Test
    fun postExportTiming_disablesLiveHardwareCropAndEnablesGyroflowAndStillAutoLevel() {
        val recPlan = StabilizationPolicyEngine.resolve(
            engineModeRaw = StabilizationPolicyEngine.ENGINE_HYBRID,
            applyWhenRaw = StabilizationPolicyEngine.APPLY_POST_EXPORT,
            phase = StabilizationPolicyEngine.CapturePhase.VIDEO_RECORDING,
            activeCameraMode = "VIDEO",
            caps = flagshipCaps,
            photoAutoLevelEnabled = true,
            gyroflowExportEnabled = false
        )
        assertEquals(StabilizationPolicyEngine.HAL_OIS_OFF, recPlan.halOisMode)
        assertEquals(StabilizationPolicyEngine.HAL_EIS_OFF, recPlan.halVideoStabilizationMode)
        assertTrue(recPlan.shouldLogGyroflow)

        val stillPlan = StabilizationPolicyEngine.resolve(
            engineModeRaw = StabilizationPolicyEngine.ENGINE_HYBRID,
            applyWhenRaw = StabilizationPolicyEngine.APPLY_POST_EXPORT,
            phase = StabilizationPolicyEngine.CapturePhase.STILL_CAPTURE,
            activeCameraMode = "PHOTO",
            caps = flagshipCaps,
            photoAutoLevelEnabled = true,
            gyroflowExportEnabled = false
        )
        assertTrue(stillPlan.shouldApplyStillAutoLevel)
    }

    @Test
    fun hardwareOisRequestedWithoutOisHardware_fallsBackGracefullyToSoftwareEisOrGyro() {
        val fallbackToEis = StabilizationPolicyEngine.resolve(
            engineModeRaw = StabilizationPolicyEngine.ENGINE_HARDWARE_OIS,
            applyWhenRaw = StabilizationPolicyEngine.APPLY_ALWAYS,
            phase = StabilizationPolicyEngine.CapturePhase.VIDEO_RECORDING,
            activeCameraMode = "VIDEO",
            caps = eisOnlyLegacyCaps,
            photoAutoLevelEnabled = false,
            gyroflowExportEnabled = false
        )
        assertEquals(StabilizationPolicyEngine.HAL_OIS_OFF, fallbackToEis.halOisMode)
        assertEquals(StabilizationPolicyEngine.HAL_EIS_ON, fallbackToEis.halVideoStabilizationMode)
        assertTrue(fallbackToEis.usedSoftwareFallback)

        val fallbackToGyro = StabilizationPolicyEngine.resolve(
            engineModeRaw = StabilizationPolicyEngine.ENGINE_HARDWARE_OIS,
            applyWhenRaw = StabilizationPolicyEngine.APPLY_ALWAYS,
            phase = StabilizationPolicyEngine.CapturePhase.VIDEO_RECORDING,
            activeCameraMode = "VIDEO",
            caps = softwareOnlyCaps,
            photoAutoLevelEnabled = false,
            gyroflowExportEnabled = false
        )
        assertEquals(StabilizationPolicyEngine.HAL_OIS_OFF, fallbackToGyro.halOisMode)
        assertEquals(StabilizationPolicyEngine.HAL_EIS_OFF, fallbackToGyro.halVideoStabilizationMode)
        assertTrue(fallbackToGyro.shouldLogGyroflow)
        assertTrue(fallbackToGyro.usedSoftwareFallback)
    }

    @Test
    fun panoramaAndSpatialModes_suppressLiveEisCropWhileKeepingOis() {
        for (sweepMode in listOf("PANORAMA", "SPATIAL")) {
            val plan = StabilizationPolicyEngine.resolve(
                engineModeRaw = StabilizationPolicyEngine.ENGINE_HYBRID,
                applyWhenRaw = StabilizationPolicyEngine.APPLY_ALWAYS,
                phase = StabilizationPolicyEngine.CapturePhase.PREVIEW,
                activeCameraMode = sweepMode,
                caps = flagshipCaps,
                photoAutoLevelEnabled = true,
                gyroflowExportEnabled = true
            )
            assertEquals("OIS should stay active in $sweepMode", StabilizationPolicyEngine.HAL_OIS_ON, plan.halOisMode)
            assertEquals("EIS crop must be suppressed in $sweepMode", StabilizationPolicyEngine.HAL_EIS_OFF, plan.halVideoStabilizationMode)
            assertFalse("Still auto-level must not alter $sweepMode tiles", plan.shouldApplyStillAutoLevel)
        }
    }

    @Test
    fun engineOff_disablesAllHardwareStabilizationAndAutoLevel() {
        val plan = StabilizationPolicyEngine.resolve(
            engineModeRaw = StabilizationPolicyEngine.ENGINE_OFF,
            applyWhenRaw = StabilizationPolicyEngine.APPLY_ALWAYS,
            phase = StabilizationPolicyEngine.CapturePhase.STILL_CAPTURE,
            activeCameraMode = "PHOTO",
            caps = flagshipCaps,
            photoAutoLevelEnabled = true,
            gyroflowExportEnabled = false
        )
        assertEquals(StabilizationPolicyEngine.HAL_OIS_OFF, plan.halOisMode)
        assertEquals(StabilizationPolicyEngine.HAL_EIS_OFF, plan.halVideoStabilizationMode)
        assertFalse(plan.shouldApplyStillAutoLevel)
        assertFalse(plan.shouldLogGyroflow)
    }

    @Test
    fun autoLevelInscribedCrop_respectsDeadbandAndPreservesAspectRatioWithZeroBlackCorners() {
        // Within 0.4 deg deadband -> null (no re-encode / no crop needed)
        assertNull(StabilizationPolicyEngine.computeInscribedCropRect(4000, 3000, 0.25f))
        assertNull(StabilizationPolicyEngine.computeInscribedCropRect(4000, 3000, -0.39f))

        // At 5 deg, 12 deg, 20 deg -> strictly inside bounds & aspect ratio preserved
        val srcW = 4000
        val srcH = 3000
        val srcAspect = srcW.toDouble() / srcH.toDouble()
        for (deg in listOf(5.0f, -12.0f, 20.0f, 35.0f)) {
            val rect = StabilizationPolicyEngine.computeInscribedCropRect(srcW, srcH, deg)
            assertNotNull("Expected valid crop rect at $deg deg", rect)
            rect!!
            assertTrue(rect.left >= 0)
            assertTrue(rect.top >= 0)
            assertTrue(rect.left + rect.width <= srcW)
            assertTrue(rect.top + rect.height <= srcH)
            assertTrue(rect.width < srcW)
            assertTrue(rect.height < srcH)
            val cropAspect = rect.width.toDouble() / rect.height.toDouble()
            assertEquals("Aspect ratio must be preserved at $deg deg", srcAspect, cropAspect, 0.01)
        }

        // Clamped above 20 deg so 35 deg matches 20 deg crop
        val rect20 = StabilizationPolicyEngine.computeInscribedCropRect(srcW, srcH, 20f)!!
        val rect35 = StabilizationPolicyEngine.computeInscribedCropRect(srcW, srcH, 35f)!!
        assertEquals(rect20, rect35)
    }

    @Test
    fun sampleLumaIreGrid_mapsYPlaneAccuratelyWithRowStridePadding() {
        val width = 64
        val height = 48
        val rowStride = 80 // padded stride > width
        val yPlane = ByteArray(rowStride * height) { 0 }
        for (y in 0 until height) {
            for (x in 0 until width) {
                // Left half black (0), right half clipped white (255)
                yPlane[y * rowStride + x] = if (x < width / 2) 0.toByte() else 255.toByte()
            }
        }
        val grid = StabilizationPolicyEngine.sampleLumaIreGrid(yPlane, width, height, rowStride, 8, 6)
        assertEquals(48, grid.size)
        for (gy in 0 until 6) {
            for (gx in 0 until 8) {
                val ire = grid[gy * 8 + gx]
                if (gx < 4) {
                    assertEquals(0f, ire, 0.1f)
                } else {
                    assertEquals(100f, ire, 0.1f)
                }
            }
        }
    }

    @Test
    fun evaluateFocusPullDiopters_isSmoothCubicEaseInOut() {
        assertEquals(1.0f, StabilizationPolicyEngine.evaluateFocusPullDiopters(1.0f, 5.0f, 0f), 1e-4f)
        assertEquals(5.0f, StabilizationPolicyEngine.evaluateFocusPullDiopters(1.0f, 5.0f, 1f), 1e-4f)
        assertEquals(3.0f, StabilizationPolicyEngine.evaluateFocusPullDiopters(1.0f, 5.0f, 0.5f), 1e-4f)
        // Ease-in: at t=0.25, eased = 4 * 0.25^3 = 0.0625 -> 1.0 + 4.0 * 0.0625 = 1.25
        assertEquals(1.25f, StabilizationPolicyEngine.evaluateFocusPullDiopters(1.0f, 5.0f, 0.25f), 1e-3f)
        // Ease-out symmetry: at t=0.75 -> 4.75
        assertEquals(4.75f, StabilizationPolicyEngine.evaluateFocusPullDiopters(1.0f, 5.0f, 0.75f), 1e-3f)
    }

    @Test
    fun customRigPreset_roundTripEncodeDecodeAndHandlesMalformedData() {
        val original = CustomRigPreset(
            slot = 2,
            name = "Cine Night",
            activeMode = "CINEMATIC",
            iso = 800,
            shutterNs = 20_833_333L,
            evStops = -0.5f,
            wbKelvin = 4200,
            wbTint = 4.5f,
            focusDiopters = 1.75f,
            toneCurve = "arri_logc",
            lutLook = "kodak_2383",
            stabilizationEngineMode = StabilizationPolicyEngine.ENGINE_GYRO_POST,
            stabilizationApplyWhen = StabilizationPolicyEngine.APPLY_POST_EXPORT
        )
        val encoded = original.encode()
        val decoded = CustomRigPreset.decode(2, encoded)
        assertNotNull(decoded)
        assertEquals(original, decoded)

        // Malformed / truncated input safely returns null
        assertNull(CustomRigPreset.decode(1, ""))
        assertNull(CustomRigPreset.decode(1, "broken|fields"))
    }

    @Test
    fun sensorHubExportGyroflowGcsv_writesV13HeadersWithCameraNameAndReadoutTime() {
        val tmpFile = File.createTempFile("commix_gyro_test_", ".gcsv")
        try {
            val samples = listOf(
                SensorHub.ImuSample(1_000_000_000L, 0.01f, -0.02f, 0.03f, 0.1f, 9.81f, -0.2f),
                SensorHub.ImuSample(1_005_000_000L, 0.04f, -0.01f, 0.02f, 0.15f, 9.79f, -0.18f)
            )
            SensorHub.exportGyroflowGcsv(
                file = tmpFile,
                samples = samples,
                videoFilename = "COMMIX_VID_001.mp4",
                cameraName = "Pixel 9 Pro (Cam 0)",
                frameReadoutTimeMs = 15.0
            )
            val lines = tmpFile.readLines()
            assertEquals("GYROFLOW IMU LOG", lines[0])
            assertEquals("version,1.3", lines[1])
            assertEquals("id,commix", lines[2])
            assertEquals("orientation,YxZ", lines[3])
            assertTrue(lines.contains("camera_name,Pixel 9 Pro (Cam 0)"))
            assertTrue(lines.contains("videofilename,COMMIX_VID_001.mp4"))
            assertTrue(lines.contains("frame_readout_time,15.00"))
            assertTrue(lines.contains("t,gx,gy,gz,ax,ay,az"))
            // Second sample should be at +0.005000 seconds
            val dataRows = lines.dropWhile { it != "t,gx,gy,gz,ax,ay,az" }.drop(1)
            assertEquals(2, dataRows.size)
            assertTrue(dataRows[0].startsWith("0.000000,"))
            assertTrue(dataRows[1].startsWith("0.005000,"))
        } finally {
            tmpFile.delete()
        }
    }
}
