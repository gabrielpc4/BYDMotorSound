package com.gabrielpc.enginesoundsimulator.audio

import android.content.Context
import com.gabrielpc.enginesoundsimulator.AppPreferenceStores

/** App-wide mixer multipliers applied on top of each car's dashboard mix settings. */
data class MixerGlobalGains(
    val engineHost: Float = 1.0f,
    val effectsHost: Float = 1.0f,
    val transmission: Float = 1.0f,
    val gearShift: Float = 1.0f,
    val turbo: Float = 1.0f,
    val backfire: Float = 1.0f,
    val limiter: Float = 1.0f,
) {
    fun normalized(): MixerGlobalGains = copy(
        engineHost = engineHost.coerceIn(MIN, MAX),
        effectsHost = effectsHost.coerceIn(MIN, MAX),
        transmission = transmission.coerceIn(MIN, MAX),
        gearShift = gearShift.coerceIn(MIN, MAX),
        turbo = turbo.coerceIn(MIN, MAX),
        backfire = backfire.coerceIn(MIN, MAX),
        limiter = limiter.coerceIn(MIN, MAX),
    )

    companion object {
        const val MIN = 0.5f
        const val MAX = 3.0f
    }
}

internal fun AudioMixGains.effectiveWith(mixerGlobal: MixerGlobalGains): AudioMixGains = copy(
    engineHost = engineHost * mixerGlobal.engineHost,
    effectsHost = effectsHost * mixerGlobal.effectsHost,
    transmission = transmission * mixerGlobal.transmission,
    gearShift = gearShift * mixerGlobal.gearShift,
    turbo = turbo * mixerGlobal.turbo,
    backfire = backfire * mixerGlobal.backfire,
    limiter = limiter * mixerGlobal.limiter,
)

internal class MixerGlobalGainRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        AppPreferenceStores.MIXER_GLOBAL_GAINS,
        Context.MODE_PRIVATE,
    )

    fun load(): MixerGlobalGains = MixerGlobalGains(
        engineHost = read("engine_host"),
        effectsHost = read("effects_host"),
        transmission = read("transmission"),
        gearShift = read("gear_shift"),
        turbo = read("turbo"),
        backfire = read("backfire"),
        limiter = read("limiter"),
    ).normalized()

    fun save(gains: MixerGlobalGains) {
        val normalized = gains.normalized()
        preferences.edit()
            .putFloat("engine_host", normalized.engineHost)
            .putFloat("effects_host", normalized.effectsHost)
            .putFloat("transmission", normalized.transmission)
            .putFloat("gear_shift", normalized.gearShift)
            .putFloat("turbo", normalized.turbo)
            .putFloat("backfire", normalized.backfire)
            .putFloat("limiter", normalized.limiter)
            .commit()
    }

    fun resetAll() {
        preferences.edit().clear().commit()
    }

    private fun read(key: String): Float = preferences.getFloat(key, 1.0f)
}
