package com.txor.commix

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.sqrt

data class ImuSample(
    val timestampNs: Long,
    val gyroX: Float,
    val gyroY: Float,
    val gyroZ: Float,
    val accelX: Float,
    val accelY: Float,
    val accelZ: Float
)

/**
 * Gimbal-lock-free camera attitude in upright shooting coordinates:
 * - [azimuthDeg]: rear camera compass heading in [-180, 180], increasing clockwise (panning right)
 * - [pitchDeg]: optical elevation in [-90, 90] (0° = horizon, + = tilted up, - = tilted down)
 * - [rollDeg]: horizon roll in [-180, 180] (0° = upright level portrait, + = tilted clockwise)
 */
data class CameraAttitude(
    val azimuthDeg: Double,
    val pitchDeg: Double,
    val rollDeg: Double
)

/**
 * SensorHub manages 6-axis IMU sampling synchronized to Camera2 frame timestamps,
 * providing data for real-time electronic stabilization, panorama guidance, and Gyroflow logging.
 */
class SensorHub(private val context: Context, private val onOrientationChanged: (Map<String, Double>) -> Unit) {
    private val TAG = "CommixSensorHub"
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    private var rotationListener: SensorEventListener? = null
    private var imuListener: SensorEventListener? = null
    private var lastOrientationMs = 0L

    private val rotationMatrix = FloatArray(9)
    @Volatile private var hasRotationMatrix = false

    // Current synchronized values
    @Volatile var currentPitchDeg = 0.0
    @Volatile var currentRollDeg = 0.0
    @Volatile var currentAzimuthDeg = 0.0
    @Volatile var currentYawRateDegPerSec = 0.0
    @Volatile var lastGyroMagnitudeRadSec: Float = 0f
    @Volatile var onGyroVelocity: ((Float) -> Unit)? = null

    // Gyroflow logging ring buffer
    val imuLogQueue = ConcurrentLinkedQueue<ImuSample>()
    private val queueSize = AtomicInteger(0)
    private val droppedLogSamples = AtomicInteger(0)
    private var overflowWarned = false
    @Volatile var isLoggingImu = false

    private var lastGyroX = 0f
    private var lastGyroY = 0f
    private var lastGyroZ = 0f
    private var lastAccelX = 0f
    private var lastAccelY = 0f
    private var lastAccelZ = 0f
    private var hasAccelSample = false

    fun startOrientationUpdates(): Boolean {
        val sm = sensorManager ?: return false
        val sensor = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: sm.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            ?: return false

        rotationListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event == null) return

                if (event.sensor.type == Sensor.TYPE_GYROSCOPE) {
                    val gx = event.values[0]
                    val gy = event.values[1]
                    val gz = event.values[2]
                    val mag = sqrt(gx * gx + gy * gy + gz * gz)
                    lastGyroMagnitudeRadSec = mag
                    if (hasRotationMatrix) {
                        val rawYawDegSec = computeWorldYawRateDegPerSec(rotationMatrix, gx, gy, gz)
                        currentYawRateDegPerSec = currentYawRateDegPerSec * 0.65 + rawYawDegSec * 0.35
                    } else {
                        val rawYawDegSec = Math.toDegrees((-gy).toDouble())
                        currentYawRateDegPerSec = currentYawRateDegPerSec * 0.65 + rawYawDegSec * 0.35
                    }
                    onGyroVelocity?.invoke(mag)
                    if (com.txor.commix.bridge.NativeEngineBridge.isLoaded) {
                        com.txor.commix.bridge.NativeEngineBridge.nativePushBestShotVelocity(gx, gy, gz)
                    }
                    return
                }

                val now = System.currentTimeMillis()
                if (now - lastOrientationMs < 16) return
                lastOrientationMs = now

                if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
                    SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                    hasRotationMatrix = true
                    val attitude = computeCameraOrientationDeg(rotationMatrix)
                    currentAzimuthDeg = attitude.azimuthDeg
                    currentPitchDeg = attitude.pitchDeg
                    currentRollDeg = attitude.rollDeg
                    onOrientationChanged(
                        mapOf(
                            "pitch" to attitude.pitchDeg,
                            "roll" to attitude.rollDeg,
                            "azimuth" to attitude.azimuthDeg,
                            "yawRateDegSec" to currentYawRateDegPerSec
                        )
                    )
                } else if (event.sensor.type == Sensor.TYPE_ACCELEROMETER || event.sensor.type == Sensor.TYPE_GRAVITY) {
                    val attitude = computeGravityAttitudeDeg(event.values[0], event.values[1], event.values[2])
                    currentPitchDeg = attitude.pitchDeg
                    currentRollDeg = attitude.rollDeg
                    onOrientationChanged(
                        mapOf(
                            "pitch" to attitude.pitchDeg,
                            "roll" to attitude.rollDeg,
                            "azimuth" to currentAzimuthDeg,
                            "yawRateDegSec" to currentYawRateDegPerSec
                        )
                    )
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        val gyro = sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        if (gyro != null) {
            try { sm.registerListener(rotationListener, gyro, SensorManager.SENSOR_DELAY_GAME) } catch (_: Exception) {}
        }

        return try {
            sm.registerListener(rotationListener, sensor, SensorManager.SENSOR_DELAY_GAME)
        } catch (_: Exception) {
            false
        }
    }

    fun stopOrientationUpdates() {
        rotationListener?.let { sensorManager?.unregisterListener(it) }
        rotationListener = null
    }

    fun startImuLogging() {
        imuLogQueue.clear()
        queueSize.set(0)
        droppedLogSamples.set(0)
        overflowWarned = false
        hasAccelSample = false
        isLoggingImu = true

        val sm = sensorManager ?: return
        val gyro = sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        val accel = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (gyro == null) {
            // Angular velocity is the Gyroflow spec's mandatory column; without a
            // gyroscope no usable .gcsv can be produced.
            Log.w(TAG, "Gyroflow logging unavailable: device has no TYPE_GYROSCOPE sensor")
        }

        imuListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event == null || !isLoggingImu) return
                when (event.sensor.type) {
                    Sensor.TYPE_GYROSCOPE -> {
                        lastGyroX = event.values[0]
                        lastGyroY = event.values[1]
                        lastGyroZ = event.values[2]
                        if (com.txor.commix.bridge.NativeEngineBridge.isLoaded) {
                            com.txor.commix.bridge.NativeEngineBridge
                                .nativePushBestShotVelocity(lastGyroX, lastGyroY, lastGyroZ)
                        }
                        // One log row per real gyroscope event: the row timestamp is
                        // the gyro sample's own hardware timestamp. The accelerometer
                        // triple rides along from the accelerometer stream below.
                        recordImuSample(event.timestamp)
                    }

                    Sensor.TYPE_ACCELEROMETER -> {
                        lastAccelX = event.values[0]
                        lastAccelY = event.values[1]
                        lastAccelZ = event.values[2]
                        hasAccelSample = true
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        try {
            gyro?.let { sm.registerListener(imuListener!!, it, SensorManager.SENSOR_DELAY_FASTEST) }
        } catch (e: SecurityException) {
            try {
                gyro?.let { sm.registerListener(imuListener!!, it, SensorManager.SENSOR_DELAY_GAME) }
            } catch (_: Exception) {}
        } catch (_: Exception) {}

        try {
            accel?.let { sm.registerListener(imuListener!!, it, SensorManager.SENSOR_DELAY_FASTEST) }
        } catch (e: SecurityException) {
            try {
                accel?.let { sm.registerListener(imuListener!!, it, SensorManager.SENSOR_DELAY_GAME) }
            } catch (_: Exception) {}
        } catch (_: Exception) {}
    }

    /**
     * Records one `.gcsv` row per gyroscope event. Accelerometer events only refresh
     * the latest accel triple, so rows never carry stale-halves double rows.
     */
    private fun recordImuSample(timestampNs: Long) {
        if (!hasAccelSample) {
            // No real accelerometer measurement yet — inventing values is not an
            // option, so the first microseconds are simply not logged.
            return
        }

        // Forward live sample to bare-metal C++20 stabilization ring buffer
        if (com.txor.commix.bridge.NativeEngineBridge.isLoaded) {
            com.txor.commix.bridge.NativeEngineBridge.nativePushImuSample(
                timestampNs,
                lastGyroX,
                lastGyroY,
                lastGyroZ,
                lastAccelX,
                lastAccelY,
                lastAccelZ
            )
        }

        val size = queueSize.get()
        if (size >= IMU_LOG_MAX_SAMPLES) {
            // Drop from the tail, never the head. The head holds the beginning of
            // the take needed for synchronization against the video start timestamp.
            if (droppedLogSamples.incrementAndGet() == 1 && !overflowWarned) {
                overflowWarned = true
                Log.w(TAG, "Gyroflow log buffer full ($IMU_LOG_MAX_SAMPLES samples); tail samples discarded to preserve video sync head")
            }
            return
        }

        val sample = ImuSample(
            timestampNs = timestampNs,
            gyroX = lastGyroX,
            gyroY = lastGyroY,
            gyroZ = lastGyroZ,
            accelX = lastAccelX,
            accelY = lastAccelY,
            accelZ = lastAccelZ
        )
        imuLogQueue.add(sample)
        queueSize.incrementAndGet()
    }

    fun stopImuLogging(): List<ImuSample> {
        isLoggingImu = false
        imuListener?.let { sensorManager?.unregisterListener(it) }
        imuListener = null
        val dropped = droppedLogSamples.get()
        if (dropped > 0) {
            Log.w(TAG, "Gyroflow log tail dropped $dropped samples due to capacity limit ($IMU_LOG_MAX_SAMPLES)")
        }
        val samples = imuLogQueue.toList()
        imuLogQueue.clear()
        queueSize.set(0)
        return samples
    }

    companion object {
        /**
         * Computes upright rear-camera attitude from Android's 3x3 row-major device->world
         * rotation matrix (East=+X, North=+Y, Sky=+Z) without portrait gimbal lock.
         *
         * Rear camera forward vector is device -Z = (-R[2], -R[5], -R[8]).
         * When holding the phone upright facing the horizon:
         * - pitchDeg == 0° (+ = looking up, - = looking down)
         * - rollDeg  == 0° (+ = tilted clockwise, - = counter-clockwise)
         * - azimuthDeg in [-180°, 180°], increasing clockwise when panning right
         */
        fun computeCameraOrientationDeg(r: FloatArray): CameraAttitude {
            if (r.size < 9) return CameraAttitude(0.0, 0.0, 0.0)
            val fx = -r[2].toDouble()
            val fy = -r[5].toDouble()
            val fz = (-r[8].toDouble()).coerceIn(-1.0, 1.0)

            val pitchDeg = Math.toDegrees(asin(fz))

            // Heading: use optical forward projection on horizontal plane; near zenith/nadir fall back to screen-up (+Y)
            val horizLen = hypot(fx, fy)
            val azimuthDeg = if (horizLen > 0.08) {
                Math.toDegrees(atan2(fx, fy))
            } else {
                Math.toDegrees(atan2(r[1].toDouble(), r[4].toDouble()))
            }

            // Horizon roll: screen right (+X) vertical component is R[6], screen up (+Y) vertical component is R[7]
            val rollLen = hypot(r[6].toDouble(), r[7].toDouble())
            val rollDeg = if (rollLen > 0.1) {
                Math.toDegrees(atan2(-r[6].toDouble(), r[7].toDouble()))
            } else {
                0.0
            }

            return CameraAttitude(
                azimuthDeg = azimuthDeg,
                pitchDeg = pitchDeg,
                rollDeg = rollDeg
            )
        }

        /**
         * Gravity/accelerometer fallback in the exact same upright camera frame
         * (pitch=0° at horizon, roll=0° when level portrait).
         */
        fun computeGravityAttitudeDeg(ax: Float, ay: Float, az: Float): CameraAttitude {
            val g = sqrt((ax * ax + ay * ay + az * az).toDouble())
            if (g < 1e-3) return CameraAttitude(0.0, 0.0, 0.0)
            val fz = (-az.toDouble() / g).coerceIn(-1.0, 1.0)
            val pitchDeg = Math.toDegrees(asin(fz))
            val rollLen = hypot(ax.toDouble(), ay.toDouble())
            val rollDeg = if (rollLen > 0.5) {
                Math.toDegrees(atan2(-ax.toDouble(), ay.toDouble()))
            } else {
                0.0
            }
            return CameraAttitude(azimuthDeg = 0.0, pitchDeg = pitchDeg, rollDeg = rollDeg)
        }

        /**
         * Projects device-frame angular velocity (rad/s) onto World Up (R[6], R[7], R[8])
         * to get signed horizontal pan speed in deg/s (+ = panning right/clockwise, - = left).
         */
        fun computeWorldYawRateDegPerSec(r: FloatArray, gx: Float, gy: Float, gz: Float): Double {
            if (r.size < 9) return Math.toDegrees((-gy).toDouble())
            val worldUpDotGyro = gx.toDouble() * r[6].toDouble() +
                gy.toDouble() * r[7].toDouble() +
                gz.toDouble() * r[8].toDouble()
            return Math.toDegrees(-worldUpDotGyro)
        }

        /**
         * Gyroflow `.gcsv` exporter — format per
         * https://docs.gyroflow.xyz/app/technical-details/gcsv-format
         *
         * Mandatory layout implemented here:
         *  1. `GYROFLOW IMU LOG`   (identifies the file as an IMU log)
         *  2. `version,1.3`        (format version)
         *  3. `id,commix`          (unique logger id; mandatory line 3)
         *  4. `orientation,YxZ`    (mandatory line 4; Android sensor axes for a
         *                           portrait camera — users can remap in Gyroflow)
         * The C++ mirror of this header lives in
         * app/src/main/cpp/stabilization_engine.cpp (keep both byte-identical).
         */
        const val GYROFLOW_HEADER = "GYROFLOW IMU LOG\n"
        const val GYROFLOW_VERSION = "version,1.3\n"
        const val GYROFLOW_ID = "id,commix\n"
        const val GYROFLOW_ORIENTATION = "orientation,YxZ\n"
        const val GYROFLOW_VENDOR = "vendor,commix\n"
        const val GYROFLOW_TIMESTAMP = "tscale,1\n"
        const val GYROFLOW_GSCALE = "gscale,1\n"
        const val GYROFLOW_ASCALE = "ascale,0.10197162129779283\n"
        const val GYROFLOW_CSV_HEADER = "t,gx,gy,gz,ax,ay,az\n"

        private const val IMU_LOG_MAX_SAMPLES = 30_000

        fun exportGyroflowGcsv(
            file: File,
            samples: List<ImuSample>,
            videoFilename: String? = null,
            cameraName: String? = null,
            frameReadoutTimeMs: Double = 0.0
        ) {
            file.parentFile?.mkdirs()
            file.bufferedWriter().use { writer ->
                writer.write(GYROFLOW_HEADER)
                writer.write(GYROFLOW_VERSION)
                writer.write(GYROFLOW_ID)
                writer.write(GYROFLOW_ORIENTATION)
                writer.write(GYROFLOW_VENDOR)
                if (!cameraName.isNullOrBlank()) {
                    writer.write("camera_name,$cameraName\n")
                }
                if (!videoFilename.isNullOrBlank()) {
                    writer.write("videofilename,$videoFilename\n")
                }
                if (frameReadoutTimeMs > 0.0) {
                    writer.write(String.format(java.util.Locale.US, "frame_readout_time,%.2f\n", frameReadoutTimeMs))
                }
                // Android reports gyro in rad/s and accelerometer in m/s^2 — the
                // units Gyroflow expects — so the file declares unit scales and the
                // rows carry the sensor values verbatim.
                writer.write(GYROFLOW_TIMESTAMP)
                writer.write(GYROFLOW_GSCALE)
                writer.write(GYROFLOW_ASCALE)
                writer.write(GYROFLOW_CSV_HEADER)
                val baseTs = if (samples.isNotEmpty()) samples.first().timestampNs else 0L
                for (s in samples) {
                    val tSec = (s.timestampNs - baseTs) * 1e-9
                    writer.write(
                        String.format(
                            java.util.Locale.US,
                            "%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f\n",
                            tSec, s.gyroX, s.gyroY, s.gyroZ, s.accelX, s.accelY, s.accelZ
                        )
                    )
                }
            }
        }
    }

    val hasGyroscopeSensor: Boolean
        get() = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null

    fun exportGyroflowGcsv(
        file: File,
        samples: List<ImuSample>,
        videoFilename: String? = null,
        cameraName: String? = null,
        frameReadoutTimeMs: Double = 0.0
    ) = Companion.exportGyroflowGcsv(file, samples, videoFilename, cameraName, frameReadoutTimeMs)
}
