package com.gabrielpc.enginesoundsimulator.drive

import android.content.Context
import com.gabrielpc.enginesoundsimulator.AppPreferenceStores
import com.gabrielpc.enginesoundsimulator.audio.MixerGlobalGains

/** Per-car gain trims for bundled override one-shots on the main dashboard. */
data class EffectSoundOverrideGains(
    val shiftGain: Float = 1.0f,
    val backfireGain: Float = 1.0f,
) {
    fun normalized(): EffectSoundOverrideGains = copy(
        shiftGain = normalizePresetGain(shiftGain),
        backfireGain = normalizePresetGain(backfireGain),
    )
}

internal object EffectSoundOverrideGainPresets {
    const val MIN = 0.5f
    const val MAX = 3.0f
    const val DEFAULT = 1.0f
}

internal fun normalizePresetGain(value: Float): Float {
    return value.coerceIn(EffectSoundOverrideGainPresets.MIN, EffectSoundOverrideGainPresets.MAX)
}

internal fun effectiveEffectSoundOverrideGains(
    mixerGlobal: MixerGlobalGains,
    local: EffectSoundOverrideGains,
): EffectSoundOverrideGains {
    return EffectSoundOverrideGains(
        shiftGain = (local.shiftGain * mixerGlobal.shiftOverrideGain).coerceAtLeast(0f),
        backfireGain = (local.backfireGain * mixerGlobal.backfireOverrideGain).coerceAtLeast(0f),
    )
}

internal class EffectSoundOverrideGainRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        AppPreferenceStores.EFFECT_SOUND_OVERRIDE_GAINS,
        Context.MODE_PRIVATE,
    )

    fun load(profile: com.gabrielpc.enginesoundsimulator.audio.FmodBankProfile): EffectSoundOverrideGains {
        return EffectSoundOverrideGains(
            shiftGain = read(profile, "shift"),
            backfireGain = read(profile, "backfire"),
        ).normalized()
    }

    fun save(profile: com.gabrielpc.enginesoundsimulator.audio.FmodBankProfile, gains: EffectSoundOverrideGains) {
        val normalized = gains.normalized()
        preferences.edit()
            .putFloat(key(profile, "shift"), normalized.shiftGain)
            .putFloat(key(profile, "backfire"), normalized.backfireGain)
            .commit()
    }

    fun resetAll() {
        preferences.edit().clear().commit()
    }

    private fun read(profile: com.gabrielpc.enginesoundsimulator.audio.FmodBankProfile, category: String): Float {
        return preferences
            .getFloat(key(profile, category), EffectSoundOverrideGainPresets.DEFAULT)
            .let(::normalizePresetGain)
    }

    private fun key(profile: com.gabrielpc.enginesoundsimulator.audio.FmodBankProfile, category: String): String {
        return "${profile.id}.$category"
    }
}
