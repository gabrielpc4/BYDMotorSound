package com.gabrielpc.enginesoundsimulator.audio

/** Effective category trims sent to FMOD after global and per-car mixer layers. */
internal data class AudioMixGains(
    val transmission: Float = 1.0f,
    val gearShift: Float = 1.0f,
    val turbo: Float = 1.0f,
    val backfire: Float = 1.0f,
    val limiter: Float = 1.0f,
    val supercharger: Float = 1.0f,
)

internal fun effectiveCategoryGains(
    mixerGlobal: MixerGlobalGains,
    mixerSpecific: MixerCarSpecificGains,
): AudioMixGains {
    return AudioMixGains(
        transmission = mixerGlobal.transmission * mixerSpecific.transmission,
        gearShift = mixerGlobal.gearShift * mixerSpecific.gearShift,
        turbo = mixerGlobal.turbo * mixerSpecific.turbo,
        backfire = mixerGlobal.backfire * mixerSpecific.backfire,
        limiter = mixerGlobal.limiter * mixerSpecific.limiter,
        supercharger = mixerGlobal.supercharger * mixerSpecific.supercharger,
    )
}

internal data class EffectiveHostGains(
    val engineInterior: Float,
    val engineExterior: Float,
    val effectsHost: Float,
)

internal fun effectiveEngineIdleGain(
    mixerSpecific: MixerCarSpecificGains,
): Float {
    return mixerSpecific.engineIdle
}

internal fun effectiveEffectsHostForOverrides(
    mixerGlobal: MixerGlobalGains,
    mixerSpecific: MixerCarSpecificGains,
): Float {
    return mixerGlobal.effectsHost * mixerSpecific.effectsHost
}

internal fun effectiveHostGains(
    mixerGlobal: MixerGlobalGains,
    mixerSpecific: MixerCarSpecificGains,
): EffectiveHostGains {
    return EffectiveHostGains(
        engineInterior = mixerGlobal.engineInterior * mixerSpecific.engineInterior,
        engineExterior = mixerGlobal.engineExterior * mixerSpecific.engineExterior,
        effectsHost = mixerGlobal.effectsHost * mixerSpecific.effectsHost,
    )
}
