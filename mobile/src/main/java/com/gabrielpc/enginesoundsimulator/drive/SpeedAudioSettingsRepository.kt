package com.gabrielpc.enginesoundsimulator.drive

import android.content.Context
import com.gabrielpc.enginesoundsimulator.AppPreferenceStores

internal class SpeedAudioSettingsRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        AppPreferenceStores.SPEED_AUDIO_SETTINGS,
        Context.MODE_PRIVATE,
    )

    fun load(): SpeedAudioSettings {
        if (preferences.contains(KEY_CRUISING_GAIN)) {
            return SpeedAudioSettings(
                cruisingGain = preferences.getFloat(
                    KEY_CRUISING_GAIN,
                    SpeedAudioGain.DEFAULT_CRUISING_GAIN,
                ),
                racingGain = preferences.getFloat(
                    KEY_RACING_GAIN,
                    SpeedAudioGain.DEFAULT_RACING_GAIN,
                ),
                modeBlendSeconds = preferences.getFloat(
                    KEY_MODE_BLEND_SECONDS,
                    SpeedAudioGain.DEFAULT_MODE_BLEND_SECONDS,
                ),
                speedGainCoefficient = preferences.getFloat(
                    KEY_SPEED_GAIN_COEFFICIENT,
                    SpeedAudioGain.DEFAULT_SPEED_GAIN_COEFFICIENT,
                ),
            ).normalized()
        }

        val migrated = migrateLegacySettings()
        save(migrated)
        return migrated
    }

    fun save(settings: SpeedAudioSettings) {
        val normalized = settings.normalized()
        preferences.edit()
            .putFloat(KEY_CRUISING_GAIN, normalized.cruisingGain)
            .putFloat(KEY_RACING_GAIN, normalized.racingGain)
            .putFloat(KEY_MODE_BLEND_SECONDS, normalized.modeBlendSeconds)
            .putFloat(KEY_SPEED_GAIN_COEFFICIENT, normalized.speedGainCoefficient)
            .commit()
    }

    fun reset() {
        preferences.edit().clear().commit()
    }

    private fun migrateLegacySettings(): SpeedAudioSettings {
        var cruisingGain = SpeedAudioGain.DEFAULT_CRUISING_GAIN
        var racingGain = SpeedAudioGain.DEFAULT_RACING_GAIN

        if (preferences.contains(KEY_CURVE_POINT_0_GAIN)) {
            cruisingGain = preferences.getFloat(curveGainKey(1), cruisingGain)
            racingGain = preferences.getFloat(curveGainKey(3), racingGain)
        } else if (preferences.contains(KEY_LEGACY_LOW_RANGE_GAIN)) {
            cruisingGain = preferences.getFloat(KEY_LEGACY_LOW_RANGE_GAIN, cruisingGain)
            racingGain = preferences.getFloat(KEY_LEGACY_HIGH_RANGE_GAIN, racingGain)
        }

        return SpeedAudioSettings(
            cruisingGain = cruisingGain,
            racingGain = racingGain,
            modeBlendSeconds = SpeedAudioGain.DEFAULT_MODE_BLEND_SECONDS,
            speedGainCoefficient = preferences.getFloat(
                KEY_SPEED_GAIN_COEFFICIENT,
                SpeedAudioGain.DEFAULT_SPEED_GAIN_COEFFICIENT,
            ),
        ).normalized()
    }

    private fun curveGainKey(index: Int): String {
        return "curve_point_${index}_gain"
    }

    private companion object {
        const val KEY_CRUISING_GAIN = "cruising_gain"
        const val KEY_RACING_GAIN = "racing_gain"
        const val KEY_MODE_BLEND_SECONDS = "mode_blend_seconds"
        const val KEY_SPEED_GAIN_COEFFICIENT = "speed_gain_coefficient"

        const val KEY_CURVE_POINT_0_GAIN = "curve_point_0_gain"
        const val KEY_LEGACY_LOW_RANGE_GAIN = "low_range_gain"
        const val KEY_LEGACY_HIGH_RANGE_GAIN = "high_range_gain"
    }
}
