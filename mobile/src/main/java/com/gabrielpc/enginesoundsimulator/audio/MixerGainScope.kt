package com.gabrielpc.enginesoundsimulator.audio

import android.content.Context
import com.gabrielpc.enginesoundsimulator.AppPreferenceStores

/** Which mixer layer the category sliders edit in the mixer panel. */
enum class MixerGainScope(val displayName: String) {
    GLOBAL("GLOBAL"),
    SPECIFIC("SPECIFIC"),
}

internal class MixerGainScopeRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        AppPreferenceStores.MIXER_GAIN_SCOPE,
        Context.MODE_PRIVATE,
    )

    fun load(): MixerGainScope {
        val saved = preferences.getString("selected", null)

        return MixerGainScope.entries.firstOrNull { scope ->
            scope.name == saved
        } ?: MixerGainScope.GLOBAL
    }

    fun save(scope: MixerGainScope) {
        preferences.edit()
            .putString("selected", scope.name)
            .commit()
    }
}
