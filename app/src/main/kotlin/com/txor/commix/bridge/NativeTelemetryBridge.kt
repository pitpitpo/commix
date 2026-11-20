package com.txor.commix.bridge

import java.nio.ByteBuffer

/**
 * JNI bridge for C++20 high-frequency telemetry and broadcast scopes engine.
 */
object NativeTelemetryBridge {
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

    external fun nativeWriteAudio(norm: Float, db: Float, leftDb: Float, rightDb: Float)
    external fun nativeWriteImu(pitch: Float, roll: Float, yaw: Float)
    external fun nativeWriteLens(afDist: Float, afState: Int)

    external fun nativeComputeScopesDirect(
        yBuf: ByteBuffer,
        uBuf: ByteBuffer,
        vBuf: ByteBuffer,
        width: Int,
        height: Int,
        yStride: Int,
        uvStride: Int,
        uvPixelStride: Int,
        step: Int
    )

    /**
     * Reads telemetry snapshot via atomic seqlock.
     * outFloats layout (11..14 floats):
     * [0]=audioNorm, [1]=audioDb, [2]=leftDb, [3]=rightDb,
     * [4]=pitch, [5]=roll, [6]=yaw,
     * [7]=spotIre, [8]=hiClipPct, [9]=loClipPct, [10]=vscopeCount,
     * [11]=afDistance, [12]=afState, [13]=focusPeakScore
     */
    external fun nativeReadTelemetry(
        outFloats: FloatArray?,
        outLuma: IntArray?,
        outWave: FloatArray?,
        outVscope: FloatArray?
    ): Boolean

    /**
     * Reads 64-column RGB Parade channels (0..100 IRE) via atomic seqlock.
     */
    external fun nativeReadParade(
        outParadeR: FloatArray?,
        outParadeG: FloatArray?,
        outParadeB: FloatArray?
    ): Boolean
}
