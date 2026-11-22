package com.txor.commix

import com.txor.commix.automation.AutomationEngine
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Non-tautological unit tests exercising the exact production code paths fixed during the
 * end-to-end feature reliability audit:
 *  - MediaCodecRecorder PCM16 endianness + multi-chunk audio queuing
 *  - MediaCodecRecorder Camera2 hardware PTS zero-basing & monotonicity
 *  - MediaCodecRecorder portrait/landscape 1080p fallback aspect-ratio math
 *  - BurstFrameAccumulator Camera2 sequence-vs-ImageReader race coordinator
 *  - PhotoFormatEngine HEIC fallback container/extension consistency
 *  - OrientationPolicy EXIF orientation parsing & double-rotation prevention
 *  - AutomationEngine C++ native tick decoding (-999 EV sentinel vs 0.0 EV midpoint)
 */
class FeatureReliabilityAuditTest {

    // ── 1. MediaCodecRecorder: PCM16 Little-Endian + Chunked Queuing ─────────────────────────

    @Test
    fun writePcm16LittleEndianProducesExactLittleEndianBytesEvenFromBigEndianBuffer() {
        val samples = shortArrayOf(0x1234.toShort(), (-1).toShort(), Short.MIN_VALUE, 0x007F)
        // ByteBuffer.allocate defaults to BIG_ENDIAN — the exact bug that caused AAC static noise.
        val buf = ByteBuffer.allocate(samples.size * 2)
        assertEquals(ByteOrder.BIG_ENDIAN, buf.order())

        val bytesWritten = MediaCodecRecorder.writePcm16LittleEndian(
            pcmData = samples,
            shortOffset = 0,
            shortCount = samples.size,
            dst = buf
        )

        assertEquals(8, bytesWritten)
        assertEquals(ByteOrder.LITTLE_ENDIAN, buf.order())
        assertArrayEquals(
            byteArrayOf(
                0x34.toByte(), 0x12.toByte(), // 0x1234 LE
                0xFF.toByte(), 0xFF.toByte(), // -1 LE
                0x00.toByte(), 0x80.toByte(), // -32768 LE
                0x7F.toByte(), 0x00.toByte()  // 0x007F LE
            ),
            buf.array()
        )
    }

    @Test
    fun writePcm16LittleEndianChunksAcrossSmallerEncoderBuffersWithoutDroppingSamples() {
        val samples = ShortArray(10) { i -> (i + 1).toShort() }
        // Hardware AAC input buffer smaller than AudioRecord read (3 shorts = 6 bytes per chunk).
        val smallBuf = ByteBuffer.allocate(6)
        val reconstructed = mutableListOf<Byte>()

        var offset = 0
        while (offset < samples.size) {
            val written = MediaCodecRecorder.writePcm16LittleEndian(
                pcmData = samples,
                shortOffset = offset,
                shortCount = samples.size - offset,
                dst = smallBuf
            )
            assertTrue(written > 0)
            for (b in 0 until written) {
                reconstructed.add(smallBuf.array()[b])
            }
            offset += written / 2
        }

        assertEquals(samples.size, offset)
        assertEquals(samples.size * 2, reconstructed.size)
        // Verify final sample (10 = 0x000A LE -> 0x0A, 0x00) was not truncated.
        assertEquals(0x0A.toByte(), reconstructed[18])
        assertEquals(0x00.toByte(), reconstructed[19])
    }

    @Test
    fun writePcm16LittleEndianHandlesOutOfBoundsSafely() {
        val buf = ByteBuffer.allocate(8)
        assertEquals(
            0,
            MediaCodecRecorder.writePcm16LittleEndian(shortArrayOf(1, 2), shortOffset = 5, shortCount = 2, dst = buf)
        )
        assertEquals(
            0,
            MediaCodecRecorder.writePcm16LittleEndian(shortArrayOf(1, 2), shortOffset = 0, shortCount = 0, dst = buf)
        )
    }

    // ── 2. MediaCodecRecorder: Hardware PTS Zero-Basing & Monotonicity ───────────────────────

    @Test
    fun normalizeHardwarePtsUsZeroBasesUptimeAndEnforcesMonotonicity() {
        val bootUptimeUs = 14_321_987_000L // ~3.97 hours since device boot
        val pts0 = MediaCodecRecorder.normalizeHardwarePtsUs(bootUptimeUs, bootUptimeUs, -1L)
        val pts1 = MediaCodecRecorder.normalizeHardwarePtsUs(bootUptimeUs + 33_333L, bootUptimeUs, pts0)
        val pts2 = MediaCodecRecorder.normalizeHardwarePtsUs(bootUptimeUs + 66_667L, bootUptimeUs, pts1)
        // Out-of-order / duplicate hardware timestamp must still advance monotonically for MediaMuxer.
        val ptsDuplicate = MediaCodecRecorder.normalizeHardwarePtsUs(bootUptimeUs + 66_667L, bootUptimeUs, pts2)

        assertEquals(0L, pts0)
        assertEquals(33_333L, pts1)
        assertEquals(66_667L, pts2)
        assertEquals(67_667L, ptsDuplicate)
    }

    // ── 3. MediaCodecRecorder: Portrait & Landscape 1080p Fallback Sizing ────────────────────

    @Test
    fun compute1080pFallbackSizePreservesPortraitAndLandscapeAspectRatiosAlignedTo16() {
        val (landW, landH) = MediaCodecRecorder.compute1080pFallbackSize(3840, 2160)
        assertEquals(1920, landW)
        assertEquals(1072, landH) // floor(1080 / 16) * 16
        assertEquals(0, landW % 16)
        assertEquals(0, landH % 16)

        val (portW, portH) = MediaCodecRecorder.compute1080pFallbackSize(2160, 3840)
        assertEquals(1072, portW) // Previously broken as 592x1920!
        assertEquals(1920, portH)
        assertEquals(0, portW % 16)
        assertEquals(0, portH % 16)
        val srcAspect = 2160.0 / 3840.0
        val outAspect = portW.toDouble() / portH.toDouble()
        assertTrue("Portrait aspect ratio distorted: $outAspect vs $srcAspect", kotlin.math.abs(outAspect - srcAspect) < 0.01)

        val (sqW, sqH) = MediaCodecRecorder.compute1080pFallbackSize(2048, 2048)
        assertEquals(1920, sqW)
        assertEquals(1920, sqH)
    }

    // ── 4. BurstFrameAccumulator: Camera2 Sequence-vs-ImageReader Race Coordination ──────────

    @Test
    fun burstAccumulatorWaitsForTrailingIspFramesAfterSequenceCompleted() {
        var scheduledAction: (() -> Unit)? = null
        var scheduledDelayMs = -1L
        var cancelCalls = 0
        val acc = BurstFrameAccumulator(
            scheduleTimeout = { delayMs, action ->
                scheduledDelayMs = delayMs
                scheduledAction = action
            },
            cancelTimeout = {
                cancelCalls++
                scheduledAction = null
            }
        )

        var finalizeCount = 0
        var finalFrames: List<ByteArray> = emptyList()
        val sid = acc.begin(expectedCount = 4) { frames ->
            finalizeCount++
            finalFrames = frames
        }

        // 2 frames arrive before onCaptureSequenceCompleted fires.
        assertTrue(acc.onFrame(byteArrayOf(1)))
        assertTrue(acc.onFrame(byteArrayOf(2)))
        assertEquals(0, finalizeCount)

        // Camera2 fires onCaptureSequenceCompleted BEFORE final 2 ISP JPEGs drain!
        acc.onSequenceCompleted(sid, drainGraceMs = 1200L)
        assertEquals(0, finalizeCount)
        assertTrue("Accumulator must remain armed for trailing ISP frames", acc.isActive)
        assertEquals(1200L, scheduledDelayMs)

        // Trailing 2 frames arrive during grace window -> must be captured & finalized once!
        assertTrue(acc.onFrame(byteArrayOf(3)))
        assertEquals(0, finalizeCount)
        assertTrue(acc.onFrame(byteArrayOf(4)))

        assertEquals(1, finalizeCount)
        assertEquals(4, finalFrames.size)
        assertFalse(acc.isActive)
        assertTrue(cancelCalls >= 2)
        // Subsequent still frame must NOT be swallowed by finished burst.
        assertFalse(acc.onFrame(byteArrayOf(99)))
    }

    @Test
    fun burstAccumulatorFinalizesPartialFramesOnDrainGraceTimeoutWhenHalDropsFrame() {
        var pendingTimeout: (() -> Unit)? = null
        val acc = BurstFrameAccumulator(
            scheduleTimeout = { _, action -> pendingTimeout = action },
            cancelTimeout = { pendingTimeout = null }
        )

        var finalizeCount = 0
        var finalFrames: List<ByteArray> = emptyList()
        val sid = acc.begin(expectedCount = 4) { frames ->
            finalizeCount++
            finalFrames = frames
        }

        assertTrue(acc.onFrame(byteArrayOf(10)))
        assertTrue(acc.onFrame(byteArrayOf(20)))
        assertTrue(acc.onFrame(byteArrayOf(30)))
        // 4th frame dropped by HAL; sequence completes and grace timer fires.
        acc.onSequenceCompleted(sid, drainGraceMs = 1200L)
        assertEquals(0, finalizeCount)

        pendingTimeout?.invoke()
        assertEquals(1, finalizeCount)
        assertEquals(3, finalFrames.size)
        assertFalse(acc.isActive)

        // Repeated abort/timeout must never double-finalize.
        acc.onSequenceAborted(sid)
        assertEquals(1, finalizeCount)
    }

    @Test
    fun burstAccumulatorFinalizesImmediatelyOnSequenceAbortedAndIgnoresStaleSessionIds() {
        val acc = BurstFrameAccumulator(scheduleTimeout = { _, _ -> })
        var firstFinalized = 0
        var secondFinalized = 0

        val sid1 = acc.begin(expectedCount = 3) { firstFinalized++ }
        assertTrue(acc.onFrame(byteArrayOf(1)))
        acc.onSequenceAborted(sid1)
        assertEquals(1, firstFinalized)
        assertFalse(acc.isActive)

        val sid2 = acc.begin(expectedCount = 2) { secondFinalized++ }
        // Stale callback from sid1 must not abort sid2.
        acc.onSequenceAborted(sid1)
        assertTrue(acc.isActive)
        assertEquals(0, secondFinalized)

        assertTrue(acc.onFrame(byteArrayOf(2)))
        assertTrue(acc.onFrame(byteArrayOf(3)))
        assertEquals(1, secondFinalized)
        assertFalse(acc.isActive)
        // Late onSequenceCompleted for already-completed sid2 must be a no-op.
        acc.onSequenceCompleted(sid2)
        assertEquals(1, secondFinalized)
    }

    // ── 5. PhotoFormatEngine: HEIC Fallback Container & Extension Consistency ────────────────

    @Test
    fun resolveOutputFormatFallsBackCleanlyWhenHeicUnsupported() {
        val resolvedLossy = PhotoFormatEngine.resolveOutputFormat("heic", quality = 95, heicSupported = false)
        assertEquals(PhotoFormatEngine.FORMAT_JPEG, resolvedLossy)
        assertEquals(".jpg", PhotoFormatEngine.getExtension(resolvedLossy))
        assertEquals("image/jpeg", PhotoFormatEngine.getMimeType(resolvedLossy))

        val resolvedMax = PhotoFormatEngine.resolveOutputFormat("heic", quality = 100, heicSupported = false)
        assertEquals(PhotoFormatEngine.FORMAT_PNG, resolvedMax)
        assertEquals(".png", PhotoFormatEngine.getExtension(resolvedMax))
        assertEquals("image/png", PhotoFormatEngine.getMimeType(resolvedMax))

        val resolvedNative = PhotoFormatEngine.resolveOutputFormat("heic", quality = 95, heicSupported = true)
        assertEquals(PhotoFormatEngine.FORMAT_HEIC, resolvedNative)
        assertEquals(".heic", PhotoFormatEngine.getExtension(resolvedNative))
        assertEquals("image/heic", PhotoFormatEngine.getMimeType(resolvedNative))

        // Other formats stay untouched regardless of HEIC availability.
        assertEquals("tiff", PhotoFormatEngine.resolveOutputFormat("tiff", quality = 100, heicSupported = false))
        assertEquals("webp", PhotoFormatEngine.resolveOutputFormat("webp", quality = 90, heicSupported = false))
    }

    // ── 6. OrientationPolicy: EXIF Tag Decoding & Zero Double-Rotation ───────────────────────

    @Test
    fun exifTagToDegreesMapsAllStandardOrientationTags() {
        assertEquals(0, OrientationPolicy.exifTagToDegrees(1))   // ORIENTATION_NORMAL
        assertEquals(90, OrientationPolicy.exifTagToDegrees(6))  // ORIENTATION_ROTATE_90
        assertEquals(180, OrientationPolicy.exifTagToDegrees(3)) // ORIENTATION_ROTATE_180
        assertEquals(270, OrientationPolicy.exifTagToDegrees(8)) // ORIENTATION_ROTATE_270
        assertEquals(0, OrientationPolicy.exifTagToDegrees(0))   // ORIENTATION_UNDEFINED
    }

    @Test
    fun resolvePostProcessOrientationPreventsDoubleRotationWhenHalAlreadyRotatedPixels() {
        // HAL pixel-rotated JPEG carries EXIF tag 1 (0 deg) -> must NOT rotate pixels OR write 90 deg EXIF.
        assertEquals(0 to 0, OrientationPolicy.resolvePostProcessOrientation(0, "jpeg"))
        assertEquals(0 to 0, OrientationPolicy.resolvePostProcessOrientation(0, "png"))
        assertEquals(0 to 0, OrientationPolicy.resolvePostProcessOrientation(0, "webp"))
        assertEquals(0 to 0, OrientationPolicy.resolvePostProcessOrientation(0, "tiff"))

        // HAL unrotated JPEG with EXIF 90 deg -> JPEG keeps pixels & writes EXIF 90; PNG/WebP/BMP rotate pixels.
        assertEquals(0 to 90, OrientationPolicy.resolvePostProcessOrientation(90, "jpeg"))
        assertEquals(90 to 0, OrientationPolicy.resolvePostProcessOrientation(90, "png"))
        assertEquals(90 to 0, OrientationPolicy.resolvePostProcessOrientation(90, "webp"))
        assertEquals(270 to 0, OrientationPolicy.resolvePostProcessOrientation(270, "lossless"))
    }

    // ── 7. AutomationEngine: Native C++ Tick Sentinel Decoding ───────────────────────────────

    @Test
    fun decodeNativeTickValuesRespectsUnsetEvSentinelAndPreservesZeroEvMidpoint() {
        // C++ KeyframeEngine emits -999.0f for EV when no EV track is programmed.
        val allUnset = AutomationEngine.decodeNativeTickValues(
            floatArrayOf(-1f, -1f, -1f, -999f, -1f, -1f)
        )
        assertNull(allUnset.zoom)
        assertNull(allUnset.iso)
        assertNull(allUnset.shutterNs)
        assertNull("Unset -999 EV sentinel must decode to null, not -999 EV", allUnset.ev)
        assertNull(allUnset.wbKelvin)
        assertNull(allUnset.focusDiopters)

        // Midpoint of Sunrise EV Ramp (-1.5 -> +1.0) crosses 0.0 EV, and focus 0.0 = infinity.
        val midpoint = AutomationEngine.decodeNativeTickValues(
            floatArrayOf(2.5f, 400f, 16_666_666f, 0.0f, 5200f, 0.0f)
        )
        assertEquals(2.5, midpoint.zoom!!, 1e-4)
        assertEquals(400, midpoint.iso)
        assertEquals(16_666_666L, midpoint.shutterNs)
        assertEquals(0.0f, midpoint.ev!!, 1e-4f)
        assertEquals(5200, midpoint.wbKelvin)
        assertEquals(0.0f, midpoint.focusDiopters!!, 1e-4f)

        // Undersized array fails safely.
        val shortArr = AutomationEngine.decodeNativeTickValues(floatArrayOf(1.0f))
        assertNull(shortArr.zoom)
        assertNull(shortArr.ev)
    }
}
