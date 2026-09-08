package com.gabrielpc.enginesoundsimulator.drive

import android.content.Context
import com.gabrielpc.enginesoundsimulator.AppPreferenceStores

internal class SpeedAudioSettingsRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        AppPreferenceStores.SPEED_AUDIO_SETTINGS,
        Context.MODE_PRIVATE,
    )

    fun load(): SpeedAudioSettings {
        return SpeedAudioSettings(
            lowRangeMaxRpm = preferences.getInt(
                KEY_LOW_RANGE_MAX_RPM,
                SpeedAudioGain.DEFAULT_LOW_RANGE_MAX_RPM,
            ),
            midRangeMaxRpm = preferences.getInt(
                KEY_MID_RANGE_MAX_RPM,
                SpeedAudioGain.DEFAULT_MID_RANGE_MAX_RPM,
            ),
            lowRangeGain = preferences.getFloat(
                KEY_LOW_RANGE_GAIN,
                SpeedAudioGain.DEFAULT_LOW_RANGE_GAIN,
            ),
            midRangeGain = preferences.getFloat(
                KEY_MID_RANGE_GAIN,
                SpeedAudioGain.DEFAULT_MID_RANGE_GAIN,
            ),
            highRangeGain = preferences.getFloat(
                KEY_HIGH_RANGE_GAIN,
                SpeedAudioGain.DEFAULT_HIGH_RANGE_GAIN,
            ),
            speedGainCoefficient = preferences.getFloat(
                KEY_SPEED_GAIN_COEFFICIENT,
                SpeedAudioGain.DEFAULT_SPEED_GAIN_COEFFICIENT,
            ),
        ).normalized()
    }

    fun save(settings: SpeedAudioSettings) {
        val normalized = settings.normalized()
        preferences.edit()
            .putInt(KEY_LOW_RANGE_MAX_RPM, normalized.lowRangeMaxRpm)
            .putInt(KEY_MID_RANGE_MAX_RPM, normalized.midRangeMaxRpm)
            .putFloat(KEY_LOW_RANGE_GAIN, normalized.lowRangeGain)
            .putFloat(KEY_MID_RANGE_GAIN, normalized.midRangeGain)
            .putFloat(KEY_HIGH_RANGE_GAIN, normalized.highRangeGain)
            .putFloat(KEY_SPEED_GAIN_COEFFICIENT, normalized.speedGainCoefficient)
            .commit()
    }

    fun reset() {
        preferences.edit().clear().commit()
    }

    private companion object {
        const val KEY_LOW_RANGE_MAX_RPM = "low_range_max_rpm"
        const val KEY_MID_RANGE_MAX_RPM = "mid_range_max_rpm"
        const val KEY_LOW_RANGE_GAIN = "low_range_gain"
        const val KEY_MID_RANGE_GAIN = "mid_range_gain"
        const val KEY_HIGH_RANGE_GAIN = "high_range_gain"
        const val KEY_SPEED_GAIN_COEFFICIENT = "speed_gain_coefficient"
    }
}
