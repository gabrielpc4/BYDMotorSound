package com.gabrielpc.enginesoundsimulator.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class AppVolumeAndMixGainsTest {
    @Test
    fun percentConversionAndLegacyMigrationAreClamped() {
        assertEquals(0f, AppVolumeSettings(0).linear, 0.0001f)
        assertEquals(1f, AppVolumeSettings().linear, 0.0001f)
        assertEquals(2f, AppVolumeSettings(200).linear, 0.0001f)
        assertEquals(137, AppVolumeSettings.fromLegacyOverall(1.374f).percent)
        assertEquals(200, AppVolumeSettings.fromLegacyOverall(8f).percent)
        assertEquals(100, AppVolumeSettings.fromLegacyOverall(Float.NaN).percent)
    }

    @Test
    fun masterComponentsAreAppliedExactlyOnce() {
        assertEquals(
            1.5f,
            composeMasterOutputGain(
                appVolumeLinear = 2f,
                normalizationLinear = 0.5f,
                carSpecificOverallLinear = 1.5f,
            ),
            0.0001f,
        )
        assertEquals(0f, composeMasterOutputGain(0f, 4f, 5f), 0.0001f)
        assertEquals(
            1.5f,
            composeMasterOutputGain(
                appVolumeLinear = 1.5f,
                normalizationLinear = 0.5f,
                carSpecificOverallLinear = 1f,
                acousticAdjustmentLinear = 0.8f,
                applyLufsNormalization = false,
                applyIphoneAcousticAdjustment = false,
            ),
            0.0001f,
        )
    }

    @Test
    fun overallDoesNotLeakIntoHostCategoryOrIdleLayers() {
        val global = MixerGlobalGains(
            engineInterior = 2f,
            engineExterior = 3f,
            effectsHost = 4f,
            transmission = 1.5f,
        )
        val specific = MixerCarSpecificGains(
            overall = 5f,
            engineInterior = 0.5f,
            engineExterior = 0.25f,
            effectsHost = 0.5f,
            engineIdle = 0.7f,
            transmission = 2f,
        )

        assertEquals(1f, effectiveHostGains(global, specific).engineInterior, 0.0001f)
        assertEquals(0.75f, effectiveHostGains(global, specific).engineExterior, 0.0001f)
        assertEquals(2f, effectiveHostGains(global, specific).effectsHost, 0.0001f)
        assertEquals(3f, effectiveCategoryGains(global, specific).transmission, 0.0001f)
        assertEquals(0.7f, effectiveEngineIdleGain(specific), 0.0001f)
    }
}
