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
    val master = mixerGlobal.overall * mixerSpecific.overall
    return AudioMixGains(
        transmission = mixerGlobal.transmission * mixerSpecific.transmission * master,
        gearShift = mixerGlobal.gearShift * mixerSpecific.gearShift * master,
        turbo = mixerGlobal.turbo * mixerSpecific.turbo * master,
        backfire = mixerGlobal.backfire * mixerSpecific.backfire * master,
        limiter = mixerGlobal.limiter * mixerSpecific.limiter * master,
        supercharger = mixerGlobal.supercharger * mixerSpecific.supercharger * master,
    )
}

internal data class EffectiveHostGains(
    val engineInterior: Float,
    val engineExterior: Float,
    val effectsHost: Float,
)

internal fun effectiveEngineIdleGain(
    mixerGlobal: MixerGlobalGains,
    mixerSpecific: MixerCarSpecificGains,
): Float {
    val master = mixerGlobal.overall * mixerSpecific.overall
    return mixerSpecific.engineIdle * master
}

internal fun effectiveEffectsHostForOverrides(
    mixerGlobal: MixerGlobalGains,
    mixerSpecific: MixerCarSpecificGains,
): Float {
    // Bundled override one-shots keep the EFFECTS trim but skip GLOBAL GAIN / OVERALL masters.
    return mixerGlobal.effectsHost * mixerSpecific.effectsHost
}

internal fun effectiveHostGains(
    mixerGlobal: MixerGlobalGains,
    mixerSpecific: MixerCarSpecificGains,
): EffectiveHostGains {
    val master = mixerGlobal.overall * mixerSpecific.overall
    return EffectiveHostGains(
        engineInterior = mixerGlobal.engineInterior * mixerSpecific.engineInterior * master,
        engineExterior = mixerGlobal.engineExterior * mixerSpecific.engineExterior * master,
        effectsHost = mixerGlobal.effectsHost * mixerSpecific.effectsHost * master,
    )
}
