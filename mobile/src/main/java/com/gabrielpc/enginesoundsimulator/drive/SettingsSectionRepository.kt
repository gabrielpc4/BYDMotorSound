package com.gabrielpc.enginesoundsimulator.drive

import android.content.Context
import com.gabrielpc.enginesoundsimulator.AppPreferenceStores

internal enum class SettingsSection {
    GENERAL,
    BACKFIRE,
    LOUDNESS,
    SPEED_AUDIO,
    BANK_IMPORT,
    ;

    companion object {
        fun fromPersisted(value: String?): SettingsSection {
            return entries.firstOrNull { section ->
                section.name == value
            } ?: GENERAL
        }
    }
}

internal class SettingsSectionRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        AppPreferenceStores.SETTINGS_SECTION,
        Context.MODE_PRIVATE,
    )

    fun load(): SettingsSection {
        return SettingsSection.fromPersisted(preferences.getString(KEY_SELECTED_SECTION, null))
    }

    fun save(section: SettingsSection) {
        preferences.edit()
            .putString(KEY_SELECTED_SECTION, section.name)
            .commit()
    }

    fun reset() {
        preferences.edit().clear().commit()
    }

    private companion object {
        const val KEY_SELECTED_SECTION = "selected_section"
    }
}
