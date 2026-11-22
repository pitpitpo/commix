package com.txor.commix.data

import android.content.Context
import android.content.SharedPreferences
import com.txor.commix.model.CameraUiState
import com.txor.commix.ui.components.CommixModeCatalog

/**
 * Complete Persistent Settings Manager for all Commix camera, pro optics,
 * computational imaging, scopes, color science, EXIF, and spatial preferences.
 */
class CommixPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "commix_preferences"

        // Universal Display & Guides
        private const val KEY_VIEWFINDER_DISABLED = "viewfinderDisabled"
        private const val KEY_CLEAN_VIEWFINDER = "cleanViewfinderMode"
        private const val KEY_GRID_TYPE = "gridType"
        private const val KEY_SHOW_GRID = "showGrid"
        private const val KEY_HORIZON_LEVELER = "horizonLevelerEnabled"

        // Haptics & System
        private const val KEY_HAPTICS = "hapticsEnabled"
        private const val KEY_ANIMATIONS = "animationsEnabled"
        private const val KEY_SHOW_STORAGE = "showStorageRemaining"
        private const val KEY_ADAPTIVE_PERFORMANCE = "adaptivePerformanceEnabled"

        // EXIF & Privacy
        private const val KEY_EXIF_CAMERA_INFO = "exifCameraInfo"
        private const val KEY_EXIF_LENS_INFO = "exifLensInfo"
        private const val KEY_EXIF_EXPOSURE = "exifExposureSettings"
        private const val KEY_EXIF_DATETIME = "exifDateTime"
        private const val KEY_EXIF_LOCATION = "exifLocation"
        private const val KEY_EXIF_IMAGE_DESC = "exifImageDescription"
        private const val KEY_EXIF_USER_COMMENT = "exifUserComment"
        private const val KEY_EXIF_COLOR_GEOM = "exifColorGeometry"

        // Formats & Pipeline
        private const val KEY_PHOTO_FORMAT = "photoFormat"
        private const val KEY_PHOTO_QUALITY = "photoQuality"
        private const val KEY_PHOTO_ENHANCE_ALGO = "photoEnhanceAlgo"
        private const val KEY_PROCESS_ZERO = "processZeroEnabled"
        private const val KEY_PROCESS_ZERO_INTENSITY = "processZeroIntensity"
        private const val KEY_VIDEO_CODEC = "videoCodec"
        private const val KEY_VIDEO_RESOLUTION = "videoResolution"
        private const val KEY_VIDEO_FPS = "videoFpsFloat"
        private const val KEY_VIDEO_BITRATE = "videoBitrateBps"
        private const val KEY_AUDIO_GAIN_DB = "audioGainDb"
        private const val KEY_AUDIO_LOW_CUT = "audioLowCut"
        private const val KEY_UNCOMPRESSED_AUDIO = "uncompressedAudio"
        private const val KEY_EIS_MODE = "eisMode"

        // Color Science & Cinema Optics
        private const val KEY_TONE_CURVE = "toneCurve"
        private const val KEY_LUT_LOOK = "lutLook"
        private const val KEY_LUT_BLEND = "lutBlend"
        private const val KEY_FILM_GRAIN = "filmGrain"
        private const val KEY_GRAIN_INTENSITY = "grainIntensity"
        private const val KEY_WB_TINT = "wbTint"
        private const val KEY_ND_FILTER = "ndFilter"
        private const val KEY_ANAMORPHIC_RATIO = "anamorphicRatio"
        private const val KEY_SHUTTER_ANGLE_LOCKED = "shutterAngleLocked"
        private const val KEY_CINEMA_MATTE = "cinemaMatteRatio"
        private const val KEY_DEFAULT_SHUTTER_ANGLE = "defaultShutterAngle"

        // Computational & Mode Parameters
        private const val KEY_AEB_ENABLED = "aebEnabled"
        private const val KEY_AEB_SHOTS = "aebShots"
        private const val KEY_AEB_SPREAD_EV = "aebSpreadEv"
        private const val KEY_AEB_HDR_FUSION = "aebHdrFusion"
        private const val KEY_NIGHT_FRAME_COUNT = "nightFrameCount"
        private const val KEY_NIGHT_ASTRO = "nightAstroAlignment"
        private const val KEY_NIGHT_TONE = "nightTone"
        private const val KEY_SLOW_MO_FPS = "slowMoFps"
        private const val KEY_TIMELAPSE_INTERVAL = "timeLapseIntervalSec"
        private const val KEY_AUTOMATION_PRESET = "automationPresetId"
        private const val KEY_AUTOMATION_TRIGGER = "automationTriggerMode"
        private const val KEY_QUEUE_THREADS = "queueWorkerThreads"
        private const val KEY_QUEUE_AUTO_STACKING = "queueAutoQueueStacking"
        private const val KEY_LENS_CORRECTION = "lensCorrectionEnabled"
        private const val KEY_SELF_TIMER = "selfTimerSec"
        private const val KEY_LONG_EXP_STYLE = "longExposureStyle"
        private const val KEY_LONG_EXP_FRAMES = "longExposureFrames"
        private const val KEY_FOCUS_STACK = "focusStackEnabled"
        private const val KEY_FOCUS_STACK_SLICES = "focusStackSlices"
        private const val KEY_MULTI_EXP_MODE = "multiExposureMode"

        // Scopes & Panoramic / Spatial
        private const val KEY_SHOW_HISTOGRAM = "showHistogram"
        private const val KEY_SHOW_WAVEFORM = "showWaveform"
        private const val KEY_SHOW_RGB_PARADE = "showRgbParade"
        private const val KEY_SHOW_VECTORSCOPE = "showVectorscope"
        private const val KEY_IS_360_SPHERE = "is360SphereMode"
        private const val KEY_SPATIAL_FOV_DEG = "spatialFovDeg"
        private const val KEY_SPATIAL_AUTO_CAPTURE = "spatialAutoCapture"

        // Stabilization Policy & Market Pro Features
        private const val KEY_STAB_ENGINE_MODE = "stabilizationEngineMode"
        private const val KEY_STAB_APPLY_WHEN = "stabilizationApplyWhen"
        private const val KEY_PHOTO_AUTO_LEVEL = "photoAutoLevelEnabled"
        private const val KEY_GYROFLOW_EXPORT = "gyroflowExportEnabled"
        private const val KEY_SHOW_ZEBRAS = "showZebras"
        private const val KEY_ZEBRA_THRESHOLD_IRE = "zebraThresholdIre"
        private const val KEY_SHOW_FALSE_COLOR = "showFalseColor"
        private const val KEY_SHOW_SAFE_AREA = "showSafeAreaGuides"
        private const val KEY_FOCUS_PULL_DURATION = "focusPullDurationSec"
        private const val KEY_BURST_SHOT_COUNT = "burstShotCount"
        private const val KEY_SLOW_MO_PLAYBACK_FPS = "slowMoPlaybackFps"
        private const val KEY_TIMELAPSE_SHOT_LIMIT = "timeLapseShotLimit"
        private const val KEY_NIGHT_RED_UI = "nightRedUiEnabled"
        private const val KEY_CUSTOM_SLOT_PREFIX = "customRigPreset_"
    }

    fun load(defaultState: CameraUiState): CameraUiState {
        val grid = prefs.getString(KEY_GRID_TYPE, defaultState.gridType) ?: defaultState.gridType
        val rawTrigger = prefs.getString(KEY_AUTOMATION_TRIGGER, defaultState.automationTriggerMode)
            ?: defaultState.automationTriggerMode
        val loadedFps = prefs.getFloat(KEY_VIDEO_FPS, defaultState.videoFpsFloat.toFloat()).toDouble()
        return defaultState.copy(
            viewfinderDisabled = prefs.getBoolean(KEY_VIEWFINDER_DISABLED, defaultState.viewfinderDisabled),
            cleanViewfinderMode = prefs.getBoolean(KEY_CLEAN_VIEWFINDER, defaultState.cleanViewfinderMode),
            gridType = grid,
            showGrid = prefs.getBoolean(KEY_SHOW_GRID, defaultState.showGrid),
            showHorizonLeveler = prefs.getBoolean(KEY_HORIZON_LEVELER, defaultState.showHorizonLeveler),

            hapticsEnabled = prefs.getBoolean(KEY_HAPTICS, defaultState.hapticsEnabled),
            animationsEnabled = prefs.getBoolean(KEY_ANIMATIONS, defaultState.animationsEnabled),
            showStorageRemaining = prefs.getBoolean(KEY_SHOW_STORAGE, defaultState.showStorageRemaining),
            adaptivePerformanceEnabled = prefs.getBoolean(KEY_ADAPTIVE_PERFORMANCE, defaultState.adaptivePerformanceEnabled),

            exifCameraInfo = prefs.getBoolean(KEY_EXIF_CAMERA_INFO, defaultState.exifCameraInfo),
            exifLensInfo = prefs.getBoolean(KEY_EXIF_LENS_INFO, defaultState.exifLensInfo),
            exifExposureSettings = prefs.getBoolean(KEY_EXIF_EXPOSURE, defaultState.exifExposureSettings),
            exifDateTime = prefs.getBoolean(KEY_EXIF_DATETIME, defaultState.exifDateTime),
            exifLocation = prefs.getBoolean(KEY_EXIF_LOCATION, defaultState.exifLocation),
            exifImageDescription = prefs.getBoolean(KEY_EXIF_IMAGE_DESC, defaultState.exifImageDescription),
            exifUserComment = prefs.getBoolean(KEY_EXIF_USER_COMMENT, defaultState.exifUserComment),
            exifColorGeometry = prefs.getBoolean(KEY_EXIF_COLOR_GEOM, defaultState.exifColorGeometry),

            photoFormat = prefs.getString(KEY_PHOTO_FORMAT, defaultState.photoFormat) ?: defaultState.photoFormat,
            photoQuality = prefs.getInt(KEY_PHOTO_QUALITY, defaultState.photoQuality),
            photoEnhanceAlgo = prefs.getString(KEY_PHOTO_ENHANCE_ALGO, defaultState.photoEnhanceAlgo) ?: defaultState.photoEnhanceAlgo,
            isProcessZeroActive = prefs.getBoolean(KEY_PROCESS_ZERO, defaultState.isProcessZeroActive),
            processZeroIntensity = prefs.getFloat(KEY_PROCESS_ZERO_INTENSITY, defaultState.processZeroIntensity),

            videoCodec = prefs.getString(KEY_VIDEO_CODEC, defaultState.videoCodec) ?: defaultState.videoCodec,
            videoResolution = prefs.getString(KEY_VIDEO_RESOLUTION, defaultState.videoResolution) ?: defaultState.videoResolution,
            videoFpsFloat = loadedFps,
            videoFps = loadedFps.toInt(),
            videoBitrateBps = prefs.getInt(KEY_VIDEO_BITRATE, defaultState.videoBitrateBps),
            audioGainDb = prefs.getFloat(KEY_AUDIO_GAIN_DB, defaultState.audioGainDb),
            audioLowCut = prefs.getBoolean(KEY_AUDIO_LOW_CUT, defaultState.audioLowCut),
            uncompressedAudio = prefs.getBoolean(KEY_UNCOMPRESSED_AUDIO, defaultState.uncompressedAudio),
            eisMode = prefs.getString(KEY_EIS_MODE, defaultState.eisMode) ?: defaultState.eisMode,

            toneCurve = prefs.getString(KEY_TONE_CURVE, defaultState.toneCurve) ?: defaultState.toneCurve,
            lutLook = prefs.getString(KEY_LUT_LOOK, defaultState.lutLook) ?: defaultState.lutLook,
            lutBlend = prefs.getFloat(KEY_LUT_BLEND, defaultState.lutBlend),
            filmGrain = prefs.getString(KEY_FILM_GRAIN, defaultState.filmGrain) ?: defaultState.filmGrain,
            grainIntensity = prefs.getFloat(KEY_GRAIN_INTENSITY, defaultState.grainIntensity),
            wbTint = prefs.getFloat(KEY_WB_TINT, defaultState.wbTint),
            ndFilter = prefs.getString(KEY_ND_FILTER, defaultState.ndFilter) ?: defaultState.ndFilter,
            anamorphicRatio = prefs.getFloat(KEY_ANAMORPHIC_RATIO, defaultState.anamorphicRatio),
            shutterAngleLocked = prefs.getBoolean(KEY_SHUTTER_ANGLE_LOCKED, defaultState.shutterAngleLocked),
            cinemaMatteRatio = prefs.getString(KEY_CINEMA_MATTE, defaultState.cinemaMatteRatio) ?: defaultState.cinemaMatteRatio,
            defaultShutterAngle = prefs.getString(KEY_DEFAULT_SHUTTER_ANGLE, defaultState.defaultShutterAngle) ?: defaultState.defaultShutterAngle,

            aebEnabled = prefs.getBoolean(KEY_AEB_ENABLED, defaultState.aebEnabled),
            aebShots = prefs.getInt(KEY_AEB_SHOTS, defaultState.aebShots),
            aebSpreadEv = prefs.getFloat(KEY_AEB_SPREAD_EV, defaultState.aebSpreadEv),
            aebHdrFusion = prefs.getBoolean(KEY_AEB_HDR_FUSION, defaultState.aebHdrFusion),
            nightFrameCount = prefs.getInt(KEY_NIGHT_FRAME_COUNT, defaultState.nightFrameCount),
            nightAstroAlignment = prefs.getBoolean(KEY_NIGHT_ASTRO, defaultState.nightAstroAlignment),
            nightTone = prefs.getString(KEY_NIGHT_TONE, defaultState.nightTone) ?: defaultState.nightTone,
            slowMoFps = prefs.getInt(KEY_SLOW_MO_FPS, defaultState.slowMoFps),
            timeLapseIntervalSec = prefs.getInt(KEY_TIMELAPSE_INTERVAL, defaultState.timeLapseIntervalSec),
            automationPresetId = prefs.getString(KEY_AUTOMATION_PRESET, defaultState.automationPresetId) ?: defaultState.automationPresetId,
            automationTriggerMode = CommixModeCatalog.normalizeAutomationTrigger(rawTrigger),
            queueWorkerThreads = prefs.getInt(KEY_QUEUE_THREADS, defaultState.queueWorkerThreads),
            queueAutoQueueStacking = prefs.getBoolean(KEY_QUEUE_AUTO_STACKING, defaultState.queueAutoQueueStacking),
            lensCorrectionEnabled = prefs.getBoolean(KEY_LENS_CORRECTION, defaultState.lensCorrectionEnabled),
            selfTimerSec = prefs.getInt(KEY_SELF_TIMER, defaultState.selfTimerSec),
            longExposureStyle = prefs.getString(KEY_LONG_EXP_STYLE, defaultState.longExposureStyle) ?: defaultState.longExposureStyle,
            longExposureFrames = prefs.getInt(KEY_LONG_EXP_FRAMES, defaultState.longExposureFrames),
            focusStackEnabled = prefs.getBoolean(KEY_FOCUS_STACK, defaultState.focusStackEnabled),
            focusStackSlices = prefs.getInt(KEY_FOCUS_STACK_SLICES, defaultState.focusStackSlices),
            multiExposureMode = prefs.getString(KEY_MULTI_EXP_MODE, defaultState.multiExposureMode) ?: defaultState.multiExposureMode,

            showHistogram = prefs.getBoolean(KEY_SHOW_HISTOGRAM, defaultState.showHistogram),
            showWaveform = prefs.getBoolean(KEY_SHOW_WAVEFORM, defaultState.showWaveform),
            showRgbParade = prefs.getBoolean(KEY_SHOW_RGB_PARADE, defaultState.showRgbParade),
            showVectorscope = prefs.getBoolean(KEY_SHOW_VECTORSCOPE, defaultState.showVectorscope),
            is360SphereMode = prefs.getBoolean(KEY_IS_360_SPHERE, defaultState.is360SphereMode),
            spatialFovDeg = prefs.getInt(KEY_SPATIAL_FOV_DEG, defaultState.spatialFovDeg),
            spatialAutoCapture = prefs.getBoolean(KEY_SPATIAL_AUTO_CAPTURE, defaultState.spatialAutoCapture),

            stabilizationEngineMode = com.txor.commix.StabilizationPolicyEngine.normalizeEngineMode(
                prefs.getString(KEY_STAB_ENGINE_MODE, defaultState.stabilizationEngineMode)
            ),
            stabilizationApplyWhen = com.txor.commix.StabilizationPolicyEngine.normalizeApplyWhen(
                prefs.getString(KEY_STAB_APPLY_WHEN, defaultState.stabilizationApplyWhen)
            ),
            photoAutoLevelEnabled = prefs.getBoolean(KEY_PHOTO_AUTO_LEVEL, defaultState.photoAutoLevelEnabled),
            gyroflowExportEnabled = prefs.getBoolean(KEY_GYROFLOW_EXPORT, defaultState.gyroflowExportEnabled),
            showZebras = prefs.getBoolean(KEY_SHOW_ZEBRAS, defaultState.showZebras),
            zebraThresholdIre = prefs.getInt(KEY_ZEBRA_THRESHOLD_IRE, defaultState.zebraThresholdIre),
            showFalseColor = prefs.getBoolean(KEY_SHOW_FALSE_COLOR, defaultState.showFalseColor),
            showSafeAreaGuides = prefs.getBoolean(KEY_SHOW_SAFE_AREA, defaultState.showSafeAreaGuides),
            focusPullDurationSec = prefs.getFloat(KEY_FOCUS_PULL_DURATION, defaultState.focusPullDurationSec),
            burstShotCount = prefs.getInt(KEY_BURST_SHOT_COUNT, defaultState.burstShotCount),
            slowMoPlaybackFps = prefs.getInt(KEY_SLOW_MO_PLAYBACK_FPS, defaultState.slowMoPlaybackFps),
            timeLapseShotLimit = prefs.getInt(KEY_TIMELAPSE_SHOT_LIMIT, defaultState.timeLapseShotLimit),
            nightRedUiEnabled = prefs.getBoolean(KEY_NIGHT_RED_UI, defaultState.nightRedUiEnabled),
            savedCustomSlots = getSavedCustomSlotNumbers()
        )
    }

    fun save(state: CameraUiState) {
        prefs.edit()
            .putBoolean(KEY_VIEWFINDER_DISABLED, state.viewfinderDisabled)
            .putBoolean(KEY_CLEAN_VIEWFINDER, state.cleanViewfinderMode)
            .putString(KEY_GRID_TYPE, state.gridType)
            .putBoolean(KEY_SHOW_GRID, state.showGrid)
            .putBoolean(KEY_HORIZON_LEVELER, state.showHorizonLeveler)

            .putBoolean(KEY_HAPTICS, state.hapticsEnabled)
            .putBoolean(KEY_ANIMATIONS, state.animationsEnabled)
            .putBoolean(KEY_SHOW_STORAGE, state.showStorageRemaining)
            .putBoolean(KEY_ADAPTIVE_PERFORMANCE, state.adaptivePerformanceEnabled)

            .putBoolean(KEY_EXIF_CAMERA_INFO, state.exifCameraInfo)
            .putBoolean(KEY_EXIF_LENS_INFO, state.exifLensInfo)
            .putBoolean(KEY_EXIF_EXPOSURE, state.exifExposureSettings)
            .putBoolean(KEY_EXIF_DATETIME, state.exifDateTime)
            .putBoolean(KEY_EXIF_LOCATION, state.exifLocation)
            .putBoolean(KEY_EXIF_IMAGE_DESC, state.exifImageDescription)
            .putBoolean(KEY_EXIF_USER_COMMENT, state.exifUserComment)
            .putBoolean(KEY_EXIF_COLOR_GEOM, state.exifColorGeometry)

            .putString(KEY_PHOTO_FORMAT, state.photoFormat)
            .putInt(KEY_PHOTO_QUALITY, state.photoQuality)
            .putString(KEY_PHOTO_ENHANCE_ALGO, state.photoEnhanceAlgo)
            .putBoolean(KEY_PROCESS_ZERO, state.isProcessZeroActive)
            .putFloat(KEY_PROCESS_ZERO_INTENSITY, state.processZeroIntensity)

            .putString(KEY_VIDEO_CODEC, state.videoCodec)
            .putString(KEY_VIDEO_RESOLUTION, state.videoResolution)
            .putFloat(KEY_VIDEO_FPS, state.videoFpsFloat.toFloat())
            .putInt(KEY_VIDEO_BITRATE, state.videoBitrateBps)
            .putFloat(KEY_AUDIO_GAIN_DB, state.audioGainDb)
            .putBoolean(KEY_AUDIO_LOW_CUT, state.audioLowCut)
            .putBoolean(KEY_UNCOMPRESSED_AUDIO, state.uncompressedAudio)
            .putString(KEY_EIS_MODE, state.eisMode)

            .putString(KEY_TONE_CURVE, state.toneCurve)
            .putString(KEY_LUT_LOOK, state.lutLook)
            .putFloat(KEY_LUT_BLEND, state.lutBlend)
            .putString(KEY_FILM_GRAIN, state.filmGrain)
            .putFloat(KEY_GRAIN_INTENSITY, state.grainIntensity)
            .putFloat(KEY_WB_TINT, state.wbTint)
            .putString(KEY_ND_FILTER, state.ndFilter)
            .putFloat(KEY_ANAMORPHIC_RATIO, state.anamorphicRatio)
            .putBoolean(KEY_SHUTTER_ANGLE_LOCKED, state.shutterAngleLocked)
            .putString(KEY_CINEMA_MATTE, state.cinemaMatteRatio)
            .putString(KEY_DEFAULT_SHUTTER_ANGLE, state.defaultShutterAngle)

            .putBoolean(KEY_AEB_ENABLED, state.aebEnabled)
            .putInt(KEY_AEB_SHOTS, state.aebShots)
            .putFloat(KEY_AEB_SPREAD_EV, state.aebSpreadEv)
            .putBoolean(KEY_AEB_HDR_FUSION, state.aebHdrFusion)
            .putInt(KEY_NIGHT_FRAME_COUNT, state.nightFrameCount)
            .putBoolean(KEY_NIGHT_ASTRO, state.nightAstroAlignment)
            .putString(KEY_NIGHT_TONE, state.nightTone)
            .putInt(KEY_SLOW_MO_FPS, state.slowMoFps)
            .putInt(KEY_TIMELAPSE_INTERVAL, state.timeLapseIntervalSec)
            .putString(KEY_AUTOMATION_PRESET, state.automationPresetId)
            .putString(KEY_AUTOMATION_TRIGGER, CommixModeCatalog.normalizeAutomationTrigger(state.automationTriggerMode))
            .putInt(KEY_QUEUE_THREADS, state.queueWorkerThreads)
            .putBoolean(KEY_QUEUE_AUTO_STACKING, state.queueAutoQueueStacking)
            .putBoolean(KEY_LENS_CORRECTION, state.lensCorrectionEnabled)
            .putInt(KEY_SELF_TIMER, state.selfTimerSec)
            .putString(KEY_LONG_EXP_STYLE, state.longExposureStyle)
            .putInt(KEY_LONG_EXP_FRAMES, state.longExposureFrames)
            .putBoolean(KEY_FOCUS_STACK, state.focusStackEnabled)
            .putInt(KEY_FOCUS_STACK_SLICES, state.focusStackSlices)
            .putString(KEY_MULTI_EXP_MODE, state.multiExposureMode)

            .putBoolean(KEY_SHOW_HISTOGRAM, state.showHistogram)
            .putBoolean(KEY_SHOW_WAVEFORM, state.showWaveform)
            .putBoolean(KEY_SHOW_RGB_PARADE, state.showRgbParade)
            .putBoolean(KEY_SHOW_VECTORSCOPE, state.showVectorscope)
            .putBoolean(KEY_IS_360_SPHERE, state.is360SphereMode)
            .putInt(KEY_SPATIAL_FOV_DEG, state.spatialFovDeg)
            .putBoolean(KEY_SPATIAL_AUTO_CAPTURE, state.spatialAutoCapture)
            .putString(KEY_STAB_ENGINE_MODE, state.stabilizationEngineMode)
            .putString(KEY_STAB_APPLY_WHEN, state.stabilizationApplyWhen)
            .putBoolean(KEY_PHOTO_AUTO_LEVEL, state.photoAutoLevelEnabled)
            .putBoolean(KEY_GYROFLOW_EXPORT, state.gyroflowExportEnabled)
            .putBoolean(KEY_SHOW_ZEBRAS, state.showZebras)
            .putInt(KEY_ZEBRA_THRESHOLD_IRE, state.zebraThresholdIre)
            .putBoolean(KEY_SHOW_FALSE_COLOR, state.showFalseColor)
            .putBoolean(KEY_SHOW_SAFE_AREA, state.showSafeAreaGuides)
            .putFloat(KEY_FOCUS_PULL_DURATION, state.focusPullDurationSec)
            .putInt(KEY_BURST_SHOT_COUNT, state.burstShotCount)
            .putInt(KEY_SLOW_MO_PLAYBACK_FPS, state.slowMoPlaybackFps)
            .putInt(KEY_TIMELAPSE_SHOT_LIMIT, state.timeLapseShotLimit)
            .putBoolean(KEY_NIGHT_RED_UI, state.nightRedUiEnabled)
            .apply()
    }

    fun saveCustomPreset(preset: com.txor.commix.model.CustomRigPreset) {
        prefs.edit().putString("$KEY_CUSTOM_SLOT_PREFIX${preset.slot}", preset.encode()).apply()
    }

    fun loadCustomPreset(slot: Int): com.txor.commix.model.CustomRigPreset? {
        return com.txor.commix.model.CustomRigPreset.decode(prefs.getString("$KEY_CUSTOM_SLOT_PREFIX$slot", null))
    }

    fun getSavedCustomSlotNumbers(): Set<Int> {
        return (1..3).filter { prefs.contains("$KEY_CUSTOM_SLOT_PREFIX$it") }.toSet()
    }
}
