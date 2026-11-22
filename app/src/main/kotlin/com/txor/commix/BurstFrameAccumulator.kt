package com.txor.commix

/**
 * Coordinates Camera2 multi-frame burst capture between:
 *  - `CaptureCallback.onCaptureSequenceCompleted` / `onCaptureSequenceAborted` (camera thread)
 *  - `ImageReader.OnImageAvailableListener` (image reader thread)
 *
 * On Android Camera2, `onCaptureSequenceCompleted` frequently fires BEFORE the final
 * ISP-encoded JPEG buffers finish draining from `ImageReader`. Finalizing immediately inside
 * `onCaptureSequenceCompleted` drops trailing frames and leaks them into the single-shot
 * gallery saver.
 *
 * This accumulator guarantees:
 *  1. Frames are captured exclusively while a burst session is armed.
 *  2. Reaching [expectedCount] finalizes immediately and exactly once.
 *  3. If `onCaptureSequenceCompleted` fires before all frames arrive, the session stays armed
 *     for [drainGraceMs] to collect trailing ISP buffers before finalizing.
 *  4. Hardware aborts or stalled pipelines finalize cleanly exactly once so shutter state never
 *     hangs.
 */
class BurstFrameAccumulator(
    private val scheduleTimeout: (delayMs: Long, action: () -> Unit) -> Unit,
    private val cancelTimeout: () -> Unit = {}
) {
    private val lock = Any()
    private var active: Boolean = false
    private var sessionId: Long = 0L
    private var expectedCount: Int = 0
    private var sequenceCompleted: Boolean = false
    private val frames = mutableListOf<ByteArray>()
    private var onFinalize: ((List<ByteArray>) -> Unit)? = null

    val isActive: Boolean
        get() = synchronized(lock) { active }

    val collectedCount: Int
        get() = synchronized(lock) { frames.size }

    /**
     * Arms a new burst session expecting [expectedCount] frames.
     * Returns the monotonic session ID used to correlate sequence callbacks.
     */
    fun begin(
        expectedCount: Int,
        hardTimeoutMs: Long = 15_000L,
        onFinalize: (List<ByteArray>) -> Unit
    ): Long {
        val newId: Long
        synchronized(lock) {
            cancelTimeout()
            sessionId += 1L
            newId = sessionId
            active = true
            this.expectedCount = expectedCount.coerceAtLeast(1)
            sequenceCompleted = false
            frames.clear()
            this.onFinalize = onFinalize
            scheduleTimeout(hardTimeoutMs.coerceAtLeast(500L)) {
                finalizeSession(newId)
            }
        }
        return newId
    }

    /**
     * Offers an incoming JPEG frame from `ImageReader`.
     * Returns `true` if the frame belonged to the active burst (consumed), or `false` if no
     * burst is active (caller should handle it as a normal still shot).
     */
    fun onFrame(bytes: ByteArray): Boolean {
        var callback: ((List<ByteArray>) -> Unit)? = null
        var snapshot: List<ByteArray>? = null
        synchronized(lock) {
            if (!active) return false
            frames.add(bytes)
            if (frames.size >= expectedCount) {
                active = false
                cancelTimeout()
                snapshot = ArrayList(frames)
                frames.clear()
                callback = onFinalize
                onFinalize = null
            }
        }
        if (callback != null && snapshot != null) {
            callback?.invoke(snapshot!!)
        }
        return true
    }

    /**
     * Signals that Camera2's `onCaptureSequenceCompleted` has fired for [forSessionId].
     * If all expected frames are already present, finalizes immediately; otherwise keeps
     * collecting for [drainGraceMs] to absorb trailing ISP buffers.
     */
    fun onSequenceCompleted(forSessionId: Long, drainGraceMs: Long = 1200L) {
        var callback: ((List<ByteArray>) -> Unit)? = null
        var snapshot: List<ByteArray>? = null
        synchronized(lock) {
            if (!active || sessionId != forSessionId) return
            sequenceCompleted = true
            if (frames.size >= expectedCount) {
                active = false
                cancelTimeout()
                snapshot = ArrayList(frames)
                frames.clear()
                callback = onFinalize
                onFinalize = null
            } else {
                cancelTimeout()
                scheduleTimeout(drainGraceMs.coerceAtLeast(100L)) {
                    finalizeSession(forSessionId)
                }
            }
        }
        if (callback != null && snapshot != null) {
            callback?.invoke(snapshot!!)
        }
    }

    /**
     * Signals that Camera2 aborted the capture sequence for [forSessionId].
     * Finalizes immediately with whatever frames were received so callers can report failure.
     */
    fun onSequenceAborted(forSessionId: Long) {
        finalizeSession(forSessionId)
    }

    /**
     * Cancels any active burst without invoking its finalize callback.
     */
    fun cancel() {
        synchronized(lock) {
            cancelTimeout()
            active = false
            sequenceCompleted = false
            frames.clear()
            onFinalize = null
        }
    }

    private fun finalizeSession(forSessionId: Long) {
        var callback: ((List<ByteArray>) -> Unit)? = null
        var snapshot: List<ByteArray>? = null
        synchronized(lock) {
            if (!active || sessionId != forSessionId) return
            active = false
            cancelTimeout()
            snapshot = ArrayList(frames)
            frames.clear()
            callback = onFinalize
            onFinalize = null
        }
        if (callback != null && snapshot != null) {
            callback?.invoke(snapshot!!)
        }
    }
}
