package com.gabrielpc.enginesoundsimulator.drive

import android.content.Context
import com.gabrielpc.enginesoundsimulator.AppPreferenceStores

/** Global dashboard override switches shared across every car. */
data class EffectSoundOverrideSettings(
    val popsAndBangsOverride: Boolean = false,
    val shiftSoundsOverride: Boolean = false,
)

internal class EffectSoundOverrideRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        AppPreferenceStores.EFFECT_SOUND_OVERRIDES,
        Context.MODE_PRIVATE,
    )
    private val legacyShiftPreferences = context.applicationContext.getSharedPreferences(
        AppPreferenceStores.SHIFT_SOUND_SETTINGS,
        Context.MODE_PRIVATE,
    )
    private val legacyCarEffectPreferences = context.applicationContext.getSharedPreferences(
        AppPreferenceStores.CAR_EFFECT_MODES,
        Context.MODE_PRIVATE,
    )

    fun load(): EffectSoundOverrideSettings {
        if (preferences.contains(KEY_POPS_OVERRIDE) || preferences.contains(KEY_SHIFT_OVERRIDE)) {
            return EffectSoundOverrideSettings(
                popsAndBangsOverride = preferences.getBoolean(KEY_POPS_OVERRIDE, false),
                shiftSoundsOverride = preferences.getBoolean(KEY_SHIFT_OVERRIDE, false),
            )
        }

        return EffectSoundOverrideSettings(
            popsAndBangsOverride = legacyCarEffectPreferences.all.keys.any { key ->
                key.endsWith(".pops_override") && legacyCarEffectPreferences.getBoolean(key, false)
            },
            shiftSoundsOverride = when {
                legacyShiftPreferences.contains(LEGACY_SHIFT_OVERRIDE_KEY) ->
                    legacyShiftPreferences.getBoolean(LEGACY_SHIFT_OVERRIDE_KEY, false)
                else ->
                    legacyCarEffectPreferences.all.keys.any { key ->
                        key.endsWith(".shift_override") && legacyCarEffectPreferences.getBoolean(key, false)
                    }
            },
        )
    }

    fun save(settings: EffectSoundOverrideSettings) {
        preferences.edit()
            .putBoolean(KEY_POPS_OVERRIDE, settings.popsAndBangsOverride)
            .putBoolean(KEY_SHIFT_OVERRIDE, settings.shiftSoundsOverride)
            .apply()
    }

    fun reset() {
        preferences.edit().clear().apply()
    }

    private companion object {
        const val KEY_POPS_OVERRIDE = "pops_override"
        const val KEY_SHIFT_OVERRIDE = "shift_override"
        const val LEGACY_SHIFT_OVERRIDE_KEY = "override_enabled"
    }
}
