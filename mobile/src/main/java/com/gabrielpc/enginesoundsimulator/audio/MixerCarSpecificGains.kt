package com.gabrielpc.enginesoundsimulator.audio

import android.content.Context
import com.gabrielpc.enginesoundsimulator.AppPreferenceStores

/** Per-car mixer trims layered between app-wide global gains and dashboard presets. */
data class MixerCarSpecificGains(
    val overall: Float = 1.0f,
    val engineInterior: Float = 1.0f,
    val engineExterior: Float = 1.0f,
    val effectsHost: Float = 1.0f,
    val transmission: Float = 1.0f,
    val gearShift: Float = 1.0f,
    val turbo: Float = 1.0f,
    val backfire: Float = 1.0f,
    val limiter: Float = 1.0f,
    val supercharger: Float = 1.0f,
) {
    fun normalized(): MixerCarSpecificGains = copy(
        overall = MixerGlobalGains.snap(overall),
        engineInterior = MixerGlobalGains.snap(engineInterior),
        engineExterior = MixerGlobalGains.snap(engineExterior),
        effectsHost = MixerGlobalGains.snap(effectsHost),
        transmission = MixerGlobalGains.snap(transmission),
        gearShift = MixerGlobalGains.snap(gearShift),
        turbo = MixerGlobalGains.snap(turbo),
        backfire = MixerGlobalGains.snap(backfire),
        limiter = MixerGlobalGains.snap(limiter),
        supercharger = MixerGlobalGains.snap(supercharger),
    )
}

internal class MixerCarSpecificGainRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        AppPreferenceStores.MIXER_CAR_SPECIFIC_GAINS,
        Context.MODE_PRIVATE,
    )

    fun load(profile: FmodBankProfile): MixerCarSpecificGains {
        return MixerCarSpecificGains(
            overall = read(profile, "overall"),
            engineInterior = read(profile, "engine_interior"),
            engineExterior = read(profile, "engine_exterior"),
            effectsHost = read(profile, "effects_host"),
            transmission = read(profile, "transmission"),
            gearShift = read(profile, "gear_shift"),
            turbo = read(profile, "turbo"),
            backfire = read(profile, "backfire"),
            limiter = read(profile, "limiter"),
            supercharger = read(profile, "supercharger"),
        ).normalized()
    }

    fun save(profile: FmodBankProfile, gains: MixerCarSpecificGains) {
        val normalized = gains.normalized()
        preferences.edit()
            .putFloat(key(profile, "overall"), normalized.overall)
            .putFloat(key(profile, "engine_interior"), normalized.engineInterior)
            .putFloat(key(profile, "engine_exterior"), normalized.engineExterior)
            .putFloat(key(profile, "effects_host"), normalized.effectsHost)
            .putFloat(key(profile, "transmission"), normalized.transmission)
            .putFloat(key(profile, "gear_shift"), normalized.gearShift)
            .putFloat(key(profile, "turbo"), normalized.turbo)
            .putFloat(key(profile, "backfire"), normalized.backfire)
            .putFloat(key(profile, "limiter"), normalized.limiter)
            .putFloat(key(profile, "supercharger"), normalized.supercharger)
            .commit()
    }

    fun resetAll() {
        preferences.edit().clear().commit()
    }

    private fun read(profile: FmodBankProfile, category: String): Float {
        return preferences.getFloat(key(profile, category), 1.0f)
    }

    private fun key(profile: FmodBankProfile, category: String): String = "${profile.id}.$category"
}
