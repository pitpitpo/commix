package com.txor.commix.bridge

import java.nio.ByteBuffer

/**
 * JNI bridge for C++20 computational photography, color science,
 * stabilization, keyframe automation, and Process Zero engines.
 */
object NativeEngineBridge {
    @Volatile
    var isLoaded: Boolean = false
        private set

    init {
        try {
            System.loadLibrary("commix_native")
            isLoaded = true
        } catch (_: UnsatisfiedLinkError) {
            isLoaded = false
        }
    }

    // 1. Process Zero & Image Pipeline
    external fun nativeProcessZeroDirect(
        pixelBuf: ByteBuffer,
        width: Int,
        height: Int,
        channels: Int,
        curveIdx: Int,
        grainIdx: Int,
        grainIntensity: Float
    ): Boolean

    external fun nativeProcessZeroIntensityDirect(
        pixelBuf: ByteBuffer,
        width: Int,
        height: Int,
        channels: Int,
        curveIdx: Int,
        grainIdx: Int,
        grainIntensity: Float,
        toneIntensity: Float
    ): Boolean

    external fun nativeApplyCdlDirect(
        pixelBuf: ByteBuffer,
        width: Int,
        height: Int,
        channels: Int,
        sR: Float, sG: Float, sB: Float,
        oR: Float, oG: Float, oB: Float,
        pR: Float, pG: Float, pB: Float,
        sat: Float
    )

    external fun nativeApplyCdlBlendedDirect(
        pixelBuf: ByteBuffer,
        width: Int,
        height: Int,
        channels: Int,
        sR: Float, sG: Float, sB: Float,
        oR: Float, oG: Float, oB: Float,
        pR: Float, pG: Float, pB: Float,
        sat: Float,
        blend: Float
    )

    /**
     * Applies broadcast log/HDR transfer curve (0=Rec709, 1=AppleLog, 2=ArriLogC3,
     * 3=ArriLogC4, 4=SonySLog3, 5=CanonCLog2, 6=RedLog3G10, 7=HLG, 8=PQ).
     */
    external fun nativeApplyTransferCurveDirect(
        pixelBuf: ByteBuffer,
        width: Int,
        height: Int,
        channels: Int,
        curveIdx: Int,
        blend: Float
    )

    external fun nativeApplyUnsharpMaskDirect(
        pixelBuf: ByteBuffer,
        width: Int,
        height: Int,
        channels: Int,
        amount: Float,
        radius: Float
    )

    external fun nativeAetherMasterEnhanceDirect(
        pixelBuf: ByteBuffer,
        width: Int,
        height: Int,
        channels: Int,
        denoiseStrength: Float,
        clarityStrength: Float,
        shadowLift: Float,
        highlightRollOff: Float
    ): Boolean

    external fun nativeApplyVirtualGainDirect(
        pixelBuf: ByteBuffer,
        width: Int,
        height: Int,
        channels: Int,
        gainLinear: Float
    ): Boolean

    external fun nativeFuseHdrExposureDirect(
        frameBufs: Array<ByteBuffer>,
        frameCount: Int,
        width: Int,
        height: Int,
        channels: Int,
        outBuf: ByteBuffer,
        highlightCompression: Float,
        shadowRecovery: Float
    ): Boolean

    // 2. Computational Photography (Astro/Night, Long Exposure, Multi-Exposure, Focus Stack, Lens Optical)
    external fun nativeStackFramesDirect(
        frameBufs: Array<ByteBuffer>,
        frameCount: Int,
        width: Int,
        height: Int,
        channels: Int,
        outBuf: ByteBuffer,
        deepShadowBoost: Boolean
    ): Boolean

    external fun nativeAccumulateLongExposureDirect(
        frameBuf: ByteBuffer,
        accumFloatBuf: ByteBuffer,
        width: Int,
        height: Int,
        channels: Int,
        frameIndex: Int,
        styleIdx: Int,
        alpha: Float
    ): Boolean

    external fun nativeFinalizeLongExposureDirect(
        accumFloatBuf: ByteBuffer,
        outBuf: ByteBuffer,
        pixelCount: Int,
        channels: Int
    ): Boolean

    external fun nativeBlendMultiExposureDirect(
        baseBuf: ByteBuffer,
        blendBuf: ByteBuffer,
        outBuf: ByteBuffer,
        width: Int,
        height: Int,
        channels: Int,
        modeIdx: Int
    ): Boolean

    external fun nativeStackFocusSlicesDirect(
        sliceBufs: Array<ByteBuffer>,
        sliceCount: Int,
        width: Int,
        height: Int,
        channels: Int,
        outBuf: ByteBuffer
    ): Boolean

    external fun nativeCorrectLensAberrationDirect(
        inputBuf: ByteBuffer,
        outputBuf: ByteBuffer,
        width: Int,
        height: Int,
        channels: Int,
        k1: Float,
        k2: Float
    ): Boolean

    external fun nativeCalculateHyperfocalDistance(
        focalLengthMm: Float,
        fNumber: Float,
        cocMm: Float
    ): Float

    // 3. Stabilization & Gyroflow
    external fun nativePushImuSample(
        timestampNs: Long,
        gx: Float, gy: Float, gz: Float,
        ax: Float, ay: Float, az: Float
    )

    external fun nativeUpdateKalman(
        pitch: Float, roll: Float, yaw: Float, dt: Float,
        outEuler: FloatArray
    )

    external fun nativeStartImuLogging()
    external fun nativeStopAndExportGyroflow(outputPath: String, cameraName: String, frameRate: Double): Boolean
    external fun nativeComputeL1Path(inputArr: FloatArray, outputArr: FloatArray, lambda: Float)

    // 4. Best Shot AI
    external fun nativePushBestShotVelocity(gx: Float, gy: Float, gz: Float)
    external fun nativeEvaluateBestShot(spotIre: Float, hiClip: Float, loClip: Float): Float

    // 5. Keyframe Automation
    external fun nativeKeyframeReset()
    external fun nativeKeyframeAddPoint(trackId: Int, timeSec: Float, value: Float, curveIdx: Int)
    external fun nativeKeyframeStart(durationSec: Float)
    external fun nativeKeyframeStop()
    external fun nativeKeyframeTick(dtSec: Float, outValues: FloatArray): Boolean
}
