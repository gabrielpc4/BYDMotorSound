package com.gabrielpc.enginesoundsimulator.drive

import android.content.Context
import com.gabrielpc.enginesoundsimulator.AppPreferenceStores
import com.gabrielpc.enginesoundsimulator.simulation.VirtualGearProfile

/** Persists the active forward-gear profile (original bank or virtual count). */
internal class GearProfileSelectionRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        AppPreferenceStores.VIRTUAL_GEAR_COUNT,
        Context.MODE_PRIVATE,
    )

    fun load(): GearProfileSelection {
        migrateLegacyCountIfNeeded()
        return GearProfileSelection.fromPersisted(
            preferences.getString(KEY_GEAR_PROFILE_SELECTION, null),
        )
    }

    fun save(selection: GearProfileSelection) {
        preferences.edit()
            .putString(KEY_GEAR_PROFILE_SELECTION, GearProfileSelection.toPersisted(selection))
            .commit()
    }

    fun reset() {
        preferences.edit()
            .remove(KEY_GEAR_PROFILE_SELECTION)
            .remove(KEY_LEGACY_VIRTUAL_GEAR_COUNT)
            .commit()
    }

    private fun migrateLegacyCountIfNeeded() {
        if (preferences.contains(KEY_GEAR_PROFILE_SELECTION)) {
            return
        }

        if (!preferences.contains(KEY_LEGACY_VIRTUAL_GEAR_COUNT)) {
            return
        }

        val legacyCount = preferences.getInt(
            KEY_LEGACY_VIRTUAL_GEAR_COUNT,
            VirtualGearProfile.DEFAULT_VIRTUAL_GEARS,
        )
        save(GearProfileSelection.migrateLegacyVirtualCount(legacyCount))
    }

    private companion object {
        const val KEY_GEAR_PROFILE_SELECTION = "gear_profile_selection"
        const val KEY_LEGACY_VIRTUAL_GEAR_COUNT = "virtual_forward_gear_count"
    }
}
