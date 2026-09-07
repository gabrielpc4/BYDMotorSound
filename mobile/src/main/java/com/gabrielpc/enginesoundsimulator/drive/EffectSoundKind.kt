package com.gabrielpc.enginesoundsimulator.drive

enum class EffectSoundKind { POPS_AND_BANGS, SHIFT, TRANSMISSION, TURBO }

internal fun EffectSoundOverrideSettings.withOverride(kind: EffectSoundKind, override: Boolean): EffectSoundOverrideSettings = when (kind) {
    EffectSoundKind.POPS_AND_BANGS -> copy(popsAndBangsOverride = override)
    EffectSoundKind.SHIFT -> copy(shiftSoundsOverride = override)
    EffectSoundKind.TRANSMISSION, EffectSoundKind.TURBO -> this
}
