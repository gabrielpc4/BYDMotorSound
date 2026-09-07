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
    val overall = mixerSpecific.overall
    return AudioMixGains(
        transmission = mixerGlobal.transmission * mixerSpecific.transmission * overall,
        gearShift = mixerGlobal.gearShift * mixerSpecific.gearShift * overall,
        turbo = mixerGlobal.turbo * mixerSpecific.turbo * overall,
        backfire = mixerGlobal.backfire * mixerSpecific.backfire * overall,
        limiter = mixerGlobal.limiter * mixerSpecific.limiter * overall,
        supercharger = mixerGlobal.supercharger * mixerSpecific.supercharger * overall,
    )
}

internal data class EffectiveHostGains(
    val engineInterior: Float,
    val engineExterior: Float,
    val effectsHost: Float,
)

internal fun effectiveHostGains(
    mixerGlobal: MixerGlobalGains,
    mixerSpecific: MixerCarSpecificGains,
): EffectiveHostGains {
    val overall = mixerSpecific.overall
    return EffectiveHostGains(
        engineInterior = mixerGlobal.engineInterior * mixerSpecific.engineInterior * overall,
        engineExterior = mixerGlobal.engineExterior * mixerSpecific.engineExterior * overall,
        effectsHost = mixerGlobal.effectsHost * mixerSpecific.effectsHost * overall,
    )
}
