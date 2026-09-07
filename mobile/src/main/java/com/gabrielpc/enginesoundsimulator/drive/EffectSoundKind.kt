package com.gabrielpc.enginesoundsimulator.drive

import com.gabrielpc.enginesoundsimulator.audio.CarEffectModes

enum class EffectSoundKind { POPS_AND_BANGS, SHIFT, TRANSMISSION, TURBO }

internal fun CarEffectModes.withEnabled(kind: EffectSoundKind, enabled: Boolean): CarEffectModes = when (kind) {
    EffectSoundKind.POPS_AND_BANGS -> copy(popsAndBangsEnabled = enabled)
    EffectSoundKind.SHIFT -> copy(shiftSoundsEnabled = enabled)
    EffectSoundKind.TRANSMISSION -> copy(transmissionEnabled = enabled)
    EffectSoundKind.TURBO -> copy(turboEnabled = enabled)
}

internal fun EffectSoundOverrideSettings.withOverride(kind: EffectSoundKind, override: Boolean): EffectSoundOverrideSettings = when (kind) {
    EffectSoundKind.POPS_AND_BANGS -> copy(popsAndBangsOverride = override)
    EffectSoundKind.SHIFT -> copy(shiftSoundsOverride = override)
    EffectSoundKind.TRANSMISSION, EffectSoundKind.TURBO -> this
}
