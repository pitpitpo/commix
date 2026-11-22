package com.txor.commix

import com.txor.commix.automation.AutomationEngine
import com.txor.commix.model.CameraUiState
import com.txor.commix.ui.components.CommixModeCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UiConsistencyAndModeCatalogTest {

    @Test
    fun everyCameraModeHasDedicatedSettingsTab() {
        assertEquals(11, CommixModeCatalog.ALL_CAMERA_MODES.size)
        for (mode in CommixModeCatalog.ALL_CAMERA_MODES) {
            assertTrue(
                "Expected settings tab for mode '$mode'",
                mode in CommixModeCatalog.ALL_SETTINGS_TABS
            )
            assertEquals(mode, CommixModeCatalog.resolveSettingsTabForMode(mode))
        }
        assertEquals("GENERAL", CommixModeCatalog.resolveSettingsTabForMode("UNKNOWN_MODE"))
    }

    @Test
    fun automationTriggerNormalizationHandlesLegacyAndCanonicalKeys() {
        val canonicalKeys = CommixModeCatalog.AUTOMATION_TRIGGER_OPTIONS.map { it.first }.toSet()
        assertEquals(
            setOf("timer_3s", "timer_5s", "audio_clap", "gyro_shake", "instant"),
            canonicalKeys
        )

        // Legacy & alias inputs normalize cleanly to canonical keys
        assertEquals("timer_3s", CommixModeCatalog.normalizeAutomationTrigger("timer"))
        assertEquals("timer_3s", CommixModeCatalog.normalizeAutomationTrigger("3s"))
        assertEquals("timer_5s", CommixModeCatalog.normalizeAutomationTrigger("5s"))
        assertEquals("audio_clap", CommixModeCatalog.normalizeAutomationTrigger("clap"))
        assertEquals("gyro_shake", CommixModeCatalog.normalizeAutomationTrigger("shake"))
        assertEquals("instant", CommixModeCatalog.normalizeAutomationTrigger("immediate"))
        assertEquals("timer_3s", CommixModeCatalog.normalizeAutomationTrigger("invalid_value"))

        // Every canonical option round-trips and produces a concise uppercase badge label
        for ((key, label) in CommixModeCatalog.AUTOMATION_TRIGGER_OPTIONS) {
            assertEquals(key, CommixModeCatalog.normalizeAutomationTrigger(key))
            assertTrue(label.isNotBlank())
            val shortLabel = CommixModeCatalog.formatAutomationTriggerShort(key)
            assertTrue(shortLabel.isNotBlank())
            assertEquals(shortLabel.uppercase(), shortLabel)
        }
    }

    @Test
    fun cameraUiStateDefaultsToCanonicalAutomationTriggerAndSwitchesAllModes() {
        val initial = CameraUiState()
        assertEquals("timer_3s", initial.automationTriggerMode)

        // Customize PHOTO ISO, switch through every mode in catalog, verify PRO enables pro controls, return to PHOTO
        var state = initial.copy(iso = 800, evStops = -0.5f)
        for (mode in CommixModeCatalog.ALL_CAMERA_MODES) {
            if (mode == state.activeMode) continue
            state = state.switchToMode(mode)
            assertEquals(mode, state.activeMode)
            if (mode == "PRO") {
                assertTrue("PRO mode should open pro controls by default", state.showProControls)
            } else {
                assertFalse("$mode should keep viewfinder clean by default", state.showProControls)
            }
        }

        val backToPhoto = state.switchToMode("PHOTO")
        assertEquals("PHOTO", backToPhoto.activeMode)
        assertEquals(800, backToPhoto.iso)
        assertEquals(-0.5f, backToPhoto.evStops, 0.001f)
    }

    @Test
    fun automationPresetsAreSingleSourceOfTruthAndEvaluateCleanly() {
        val presets = AutomationEngine.BUILT_IN_PRESETS
        assertTrue(presets.size >= 5)
        val ids = presets.map { it.id }.toSet()
        assertEquals(presets.size, ids.size)

        for (preset in presets) {
            assertTrue(preset.title.isNotBlank())
            assertTrue(preset.description.isNotBlank())
            assertTrue(preset.durationSeconds > 0f)
            assertTrue(preset.allTracks.isNotEmpty())
            assertEquals(preset.id, AutomationEngine.getPresetById(preset.id).id)

            // Verify start, midpoint, and end evaluations produce valid values
            val startVals = preset.evaluate(0f)
            val midVals = preset.evaluate(preset.durationSeconds * 0.5f)
            val endVals = preset.evaluate(preset.durationSeconds)
            assertNotEquals(startVals, endVals)
            // Midpoint must not be NaN when present
            midVals.zoom?.let { assertFalse(it.isNaN()) }
            midVals.ev?.let { assertFalse(it.isNaN()) }
            midVals.focusDiopters?.let { assertFalse(it.isNaN()) }
        }
    }
}
