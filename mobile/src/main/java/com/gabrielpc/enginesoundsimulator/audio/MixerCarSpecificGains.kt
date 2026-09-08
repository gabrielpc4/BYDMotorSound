package com.gabrielpc.enginesoundsimulator.audio

import android.content.Context
import com.gabrielpc.enginesoundsimulator.AppPreferenceStores

/** Per-car, per-listening-perspective mixer trims layered on top of global gains. */
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
        overall = MixerGlobalGains.clamp(overall),
        engineInterior = MixerGlobalGains.clamp(engineInterior),
        engineExterior = MixerGlobalGains.clamp(engineExterior),
        effectsHost = MixerGlobalGains.clamp(effectsHost),
        transmission = MixerGlobalGains.clamp(transmission),
        gearShift = MixerGlobalGains.clamp(gearShift),
        turbo = MixerGlobalGains.clamp(turbo),
        backfire = MixerGlobalGains.clamp(backfire),
        limiter = MixerGlobalGains.clamp(limiter),
        supercharger = MixerGlobalGains.clamp(supercharger),
    )
}

internal class MixerCarSpecificGainRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        AppPreferenceStores.MIXER_CAR_SPECIFIC_GAINS,
        Context.MODE_PRIVATE,
    )

    fun load(profile: FmodBankProfile, perspective: EngineSoundPerspective): MixerCarSpecificGains {
        return MixerCarSpecificGains(
            overall = read(profile, perspective, "overall"),
            engineInterior = read(profile, perspective, "engine_interior"),
            engineExterior = read(profile, perspective, "engine_exterior"),
            effectsHost = read(profile, perspective, "effects_host"),
            transmission = read(profile, perspective, "transmission"),
            gearShift = read(profile, perspective, "gear_shift"),
            turbo = read(profile, perspective, "turbo"),
            backfire = read(profile, perspective, "backfire"),
            limiter = read(profile, perspective, "limiter"),
            supercharger = read(profile, perspective, "supercharger"),
        ).normalized()
    }

    fun save(
        profile: FmodBankProfile,
        perspective: EngineSoundPerspective,
        gains: MixerCarSpecificGains,
    ) {
        val normalized = gains.normalized()
        val editor = preferences.edit()
        editor.putFloat(key(profile, perspective, "overall"), normalized.overall)
        editor.putFloat(key(profile, perspective, "engine_interior"), normalized.engineInterior)
        editor.putFloat(key(profile, perspective, "engine_exterior"), normalized.engineExterior)
        editor.putFloat(key(profile, perspective, "effects_host"), normalized.effectsHost)
        editor.putFloat(key(profile, perspective, "transmission"), normalized.transmission)
        editor.putFloat(key(profile, perspective, "gear_shift"), normalized.gearShift)
        editor.putFloat(key(profile, perspective, "turbo"), normalized.turbo)
        editor.putFloat(key(profile, perspective, "backfire"), normalized.backfire)
        editor.putFloat(key(profile, perspective, "limiter"), normalized.limiter)
        editor.putFloat(key(profile, perspective, "supercharger"), normalized.supercharger)
        editor.commit()
    }

    fun resetAll() {
        preferences.edit().clear().commit()
    }

    private fun read(
        profile: FmodBankProfile,
        perspective: EngineSoundPerspective,
        category: String,
    ): Float {
        return preferences.getFloat(key(profile, perspective, category), 1.0f)
    }

    private fun key(
        profile: FmodBankProfile,
        perspective: EngineSoundPerspective,
        category: String,
    ): String {
        return "${profile.id}.${perspective.name}.$category"
    }
}
