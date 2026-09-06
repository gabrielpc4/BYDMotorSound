package com.gabrielpc.enginesoundsimulator.audio

import android.content.Context
import com.gabrielpc.enginesoundsimulator.AppPreferenceStores

/** Per-car authored-event trim controls shown in the mixer. */
internal data class AudioMixGains(
    val transmission: Float = 1.0f,
    val gearShift: Float = 1.0f,
    val turbo: Float = 1.0f,
    val backfire: Float = 1.0f,
    val engineHost: Float = 1.0f,
    val effectsHost: Float = 1.0f,
)

internal class AudioMixGainRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        AppPreferenceStores.AUDIO_MIX_GAINS,
        Context.MODE_PRIVATE,
    )

    fun load(profile: FmodBankProfile): AudioMixGains = AudioMixGains(
        transmission = read(profile, "transmission"),
        gearShift = read(profile, "gear_shift"),
        turbo = read(profile, "turbo"),
        backfire = read(profile, "backfire"),
        engineHost = readHost(profile, "engine_host", 0.5f, 3.0f),
        effectsHost = readHost(profile, "effects_host", 0.5f, 4.0f),
    )

    fun save(profile: FmodBankProfile, gains: AudioMixGains) {
        preferences.edit()
            .putFloat(key(profile, "transmission"), gains.transmission.coerceAtLeast(0f))
            .putFloat(key(profile, "gear_shift"), gains.gearShift.coerceAtLeast(0f))
            .putFloat(key(profile, "turbo"), gains.turbo.coerceAtLeast(0f))
            .putFloat(key(profile, "backfire"), gains.backfire.coerceAtLeast(0f))
            .putFloat(key(profile, "engine_host"), gains.engineHost.coerceIn(0.5f, 3.0f))
            .putFloat(key(profile, "effects_host"), gains.effectsHost.coerceIn(0.5f, 4.0f))
            .commit()
    }

    fun resetAll() {
        preferences.edit().clear().commit()
    }

    private fun read(profile: FmodBankProfile, category: String): Float = preferences
        .getFloat(key(profile, category), 1.0f)
        .coerceIn(0.5f, 3.0f)

    private fun readHost(
        profile: FmodBankProfile,
        category: String,
        min: Float,
        max: Float,
    ): Float {
        return preferences
            .getFloat(key(profile, category), 1.0f)
            .coerceIn(min, max)
    }

    private fun key(profile: FmodBankProfile, category: String): String = "${profile.id}.$category"
}
