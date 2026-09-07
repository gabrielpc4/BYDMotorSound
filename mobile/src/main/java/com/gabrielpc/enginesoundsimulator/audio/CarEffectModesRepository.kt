package com.gabrielpc.enginesoundsimulator.audio

import android.content.Context
import com.gabrielpc.enginesoundsimulator.AppPreferenceStores

/** Per-car sound switches shown on the main dashboard. */
internal data class CarEffectModes(
    val popsAndBangsEnabled: Boolean = true,
    val shiftSoundsEnabled: Boolean = true,
)

internal class CarEffectModesRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        AppPreferenceStores.CAR_EFFECT_MODES,
        Context.MODE_PRIVATE,
    )

    fun load(profile: FmodBankProfile): CarEffectModes = CarEffectModes(
        popsAndBangsEnabled = read(profile, "pops_enabled", true),
        shiftSoundsEnabled = read(profile, "shift_enabled", true),
    )

    fun save(profile: FmodBankProfile, modes: CarEffectModes) {
        preferences.edit()
            .putBoolean(key(profile, "pops_enabled"), modes.popsAndBangsEnabled)
            .putBoolean(key(profile, "shift_enabled"), modes.shiftSoundsEnabled)
            .commit()
    }

    fun resetAll() { preferences.edit().clear().commit() }

    private fun read(profile: FmodBankProfile, name: String, default: Boolean): Boolean =
        preferences.getBoolean(key(profile, name), default)

    private fun key(profile: FmodBankProfile, name: String): String = "${profile.id}.$name"
}
