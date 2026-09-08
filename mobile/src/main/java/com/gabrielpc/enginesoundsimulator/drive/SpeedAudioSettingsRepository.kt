package com.gabrielpc.enginesoundsimulator.drive

import android.content.Context
import com.gabrielpc.enginesoundsimulator.AppPreferenceStores

internal class SpeedAudioSettingsRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        AppPreferenceStores.SPEED_AUDIO_SETTINGS,
        Context.MODE_PRIVATE,
    )

    fun load(): SpeedAudioSettings {
        val hasCurveFormat = preferences.contains(KEY_CURVE_POINT_0_RPM)

        if (hasCurveFormat) {
            val points = if (!preferences.contains(curveRpmKey(3))) {
                val legacyDefaults = listOf(
                    RpmGainCurvePoint(rpm = 1_000, gainOffset = 0.0f),
                    RpmGainCurvePoint(rpm = 4_000, gainOffset = -0.25f),
                    RpmGainCurvePoint(rpm = 7_000, gainOffset = 0.25f),
                )
                SpeedAudioGain.migrateFromThreePointCurve(
                    List(3) { index ->
                        RpmGainCurvePoint(
                            rpm = preferences.getInt(
                                curveRpmKey(index),
                                legacyDefaults[index].rpm,
                            ),
                            gainOffset = preferences.getFloat(
                                curveGainKey(index),
                                legacyDefaults[index].gainOffset,
                            ),
                        )
                    },
                )
            } else {
                loadCurvePoints()
            }

            val loaded = SpeedAudioSettings(
                curvePoints = points,
                speedGainCoefficient = preferences.getFloat(
                    KEY_SPEED_GAIN_COEFFICIENT,
                    SpeedAudioGain.DEFAULT_SPEED_GAIN_COEFFICIENT,
                ),
            ).normalized()

            if (!preferences.contains(curveRpmKey(3))) {
                save(loaded)
            }

            return loaded
        }

        if (preferences.contains(KEY_LEGACY_LOW_RANGE_MAX_RPM)) {
            val migrated = SpeedAudioSettings(
                curvePoints = SpeedAudioGain.migrateFromLegacyBands(
                    lowRangeMaxRpm = preferences.getInt(
                        KEY_LEGACY_LOW_RANGE_MAX_RPM,
                        3_000,
                    ),
                    midRangeMaxRpm = preferences.getInt(
                        KEY_LEGACY_MID_RANGE_MAX_RPM,
                        5_000,
                    ),
                    lowRangeGain = preferences.getFloat(
                        KEY_LEGACY_LOW_RANGE_GAIN,
                        0.0f,
                    ),
                    midRangeGain = preferences.getFloat(
                        KEY_LEGACY_MID_RANGE_GAIN,
                        -0.25f,
                    ),
                    highRangeGain = preferences.getFloat(
                        KEY_LEGACY_HIGH_RANGE_GAIN,
                        0.25f,
                    ),
                ),
                speedGainCoefficient = preferences.getFloat(
                    KEY_SPEED_GAIN_COEFFICIENT,
                    SpeedAudioGain.DEFAULT_SPEED_GAIN_COEFFICIENT,
                ),
            ).normalized()
            save(migrated)
            return migrated
        }

        return SpeedAudioSettings().normalized()
    }

    fun save(settings: SpeedAudioSettings) {
        val normalized = settings.normalized()
        val editor = preferences.edit()

        normalized.curvePoints.forEachIndexed { index, point ->
            editor.putInt(curveRpmKey(index), point.rpm)
            editor.putFloat(curveGainKey(index), point.gainOffset)
        }
        editor.putFloat(KEY_SPEED_GAIN_COEFFICIENT, normalized.speedGainCoefficient)
        editor.commit()
    }

    fun reset() {
        preferences.edit().clear().commit()
    }

    private fun loadCurvePoints(): List<RpmGainCurvePoint> {
        return List(SpeedAudioGain.CURVE_POINT_COUNT) { index ->
            RpmGainCurvePoint(
                rpm = preferences.getInt(
                    curveRpmKey(index),
                    SpeedAudioGain.DEFAULT_CURVE_POINTS[index].rpm,
                ),
                gainOffset = preferences.getFloat(
                    curveGainKey(index),
                    SpeedAudioGain.DEFAULT_CURVE_POINTS[index].gainOffset,
                ),
            )
        }
    }

    private fun curveRpmKey(index: Int): String {
        return "curve_point_${index}_rpm"
    }

    private fun curveGainKey(index: Int): String {
        return "curve_point_${index}_gain"
    }

    private companion object {
        const val KEY_CURVE_POINT_0_RPM = "curve_point_0_rpm"
        const val KEY_SPEED_GAIN_COEFFICIENT = "speed_gain_coefficient"

        const val KEY_LEGACY_LOW_RANGE_MAX_RPM = "low_range_max_rpm"
        const val KEY_LEGACY_MID_RANGE_MAX_RPM = "mid_range_max_rpm"
        const val KEY_LEGACY_LOW_RANGE_GAIN = "low_range_gain"
        const val KEY_LEGACY_MID_RANGE_GAIN = "mid_range_gain"
        const val KEY_LEGACY_HIGH_RANGE_GAIN = "high_range_gain"
    }
}
