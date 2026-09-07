package com.gabrielpc.enginesoundsimulator.audio

import android.content.Context
import com.gabrielpc.enginesoundsimulator.AppPreferenceStores

/** Per-car Exterior Pure toggle; pairs with [EngineSoundPerspectiveRepository] for listener choice. */
internal class ExteriorAudioModeRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        AppPreferenceStores.EXTERIOR_AUDIO_MODE,
        Context.MODE_PRIVATE,
    )

    fun load(profile: FmodBankProfile): Boolean =
        preferences.getBoolean(key(profile.id), false)

    fun save(profile: FmodBankProfile, enabled: Boolean) {
        preferences.edit()
            .putBoolean(key(profile.id), enabled)
            .commit()
    }

    fun reset() {
        preferences.edit().clear().commit()
    }

    private fun key(profileId: String): String = "$profileId.pure_exterior"
}
