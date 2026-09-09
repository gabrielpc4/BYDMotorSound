package com.gabrielpc.enginesoundsimulator.drive

import android.content.Context
import com.gabrielpc.enginesoundsimulator.AppPreferenceStores

internal class VirtualGearSpeedBoundariesRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        AppPreferenceStores.VIRTUAL_GEAR_SPEED_BOUNDARIES,
        Context.MODE_PRIVATE,
    )

    fun load(): VirtualGearSpeedBoundariesSettings {
        val byPreset = VirtualGearSpeedBoundaries.PRESETS.associateWith { preset ->
            loadPreset(preset)
        }
        return VirtualGearSpeedBoundariesSettings(boundariesByPreset = byPreset).normalized()
    }

    fun save(settings: VirtualGearSpeedBoundariesSettings) {
        val normalized = settings.normalized()
        preferences.edit().apply {
            VirtualGearSpeedBoundaries.PRESETS.forEach { preset ->
                putString(
                    keyForPreset(preset),
                    encodeBoundaries(normalized.boundariesFor(preset)),
                )
            }
        }.commit()
    }

    fun savePreset(preset: Int, boundaries: List<Int>) {
        val gearCount = VirtualGearSpeedBoundaries.coerceVirtualPreset(preset)
        val normalized = VirtualGearSpeedBoundaries.normalizeBoundaries(boundaries, gearCount)
        preferences.edit()
            .putString(keyForPreset(gearCount), encodeBoundaries(normalized))
            .commit()
    }

    fun resetPreset(preset: Int) {
        savePreset(
            preset = preset,
            boundaries = VirtualGearSpeedBoundaries.defaultBoundariesKmh(
                VirtualGearSpeedBoundaries.coerceVirtualPreset(preset),
            ),
        )
    }

    fun reset() {
        preferences.edit().clear().commit()
    }

    private fun loadPreset(preset: Int): List<Int> {
        val gearCount = VirtualGearSpeedBoundaries.coerceVirtualPreset(preset)
        val encoded = preferences.getString(keyForPreset(gearCount), null)
            ?: return VirtualGearSpeedBoundaries.defaultBoundariesKmh(gearCount)
        val decoded = decodeBoundaries(encoded, gearCount)
        if (
            gearCount == 6 &&
            decoded == VirtualGearSpeedBoundaries.equalSplitBoundariesKmh(6)
        ) {
            return VirtualGearSpeedBoundaries.defaultBoundariesKmh(6)
        }

        return decoded
    }

    private fun encodeBoundaries(boundaries: List<Int>): String {
        return boundaries.joinToString(",")
    }

    private fun decodeBoundaries(encoded: String, gearCount: Int): List<Int> {
        val parsed = encoded.split(",")
            .mapNotNull { value -> value.trim().toIntOrNull() }

        return VirtualGearSpeedBoundaries.normalizeBoundaries(parsed, gearCount)
    }

    private fun keyForPreset(preset: Int): String {
        return "preset_$preset"
    }
}
