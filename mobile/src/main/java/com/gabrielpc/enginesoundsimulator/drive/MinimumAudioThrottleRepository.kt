package com.gabrielpc.enginesoundsimulator.drive

import android.content.Context
import com.gabrielpc.enginesoundsimulator.AppPreferenceStores
import kotlin.math.roundToInt

/** Lower bound applied to the FMOD engine throttle parameter only. */
internal object MinimumAudioThrottle {
    const val MIN = 0.0f
    const val MAX = 1.0f
    const val DEFAULT = 1.0f
    const val STEP = 0.05f

    fun normalize(value: Float): Float {
        val stepped = (value / STEP).roundToInt() * STEP
        return stepped.coerceIn(MIN, MAX)
    }
}

/** FMOD pedal throttle ramp duration from minimum to full load. */
internal object PedalAudioThrottleRampMilliseconds {
    val STOPS: IntArray = intArrayOf(0, 100, 500, 1_000, 2_000)

    const val DEFAULT = 100

    fun normalize(value: Int): Int {
        return STOPS.minByOrNull { kotlin.math.abs(it - value) } ?: DEFAULT
    }

    fun stopIndex(value: Int): Int {
        val normalized = normalize(value)
        return STOPS.indexOf(normalized).coerceAtLeast(0)
    }

    fun stopValue(index: Int): Int {
        return STOPS[index.coerceIn(0, STOPS.lastIndex)]
    }

    fun format(milliseconds: Int): String {
        val normalized = normalize(milliseconds)
        return if (normalized >= 1_000 && normalized % 1_000 == 0) {
            "${normalized / 1_000}s"
        } else {
            "${normalized}ms"
        }
    }
}

internal data class MinimumAudioThrottleSettings(
    val minimum: Float = MinimumAudioThrottle.DEFAULT,
    val rampUpMilliseconds: Int = PedalAudioThrottleRampMilliseconds.DEFAULT,
    val rampDownMilliseconds: Int = PedalAudioThrottleRampMilliseconds.DEFAULT,
)

internal class MinimumAudioThrottleRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        AppPreferenceStores.MINIMUM_AUDIO_THROTTLE,
        Context.MODE_PRIVATE,
    )

    fun load(): MinimumAudioThrottleSettings {
        val minimum = if (preferences.contains(KEY_MINIMUM)) {
            MinimumAudioThrottle.normalize(
                preferences.getFloat(KEY_MINIMUM, MinimumAudioThrottle.DEFAULT),
            )
        } else {
            val legacyForced = preferences.getBoolean(LEGACY_FORCE_ENABLED_KEY, true)
            val migrated = if (legacyForced) {
                1.0f
            } else {
                0.0f
            }
            save(MinimumAudioThrottleSettings(minimum = migrated))
            migrated
        }

        return MinimumAudioThrottleSettings(
            minimum = minimum,
            rampUpMilliseconds = PedalAudioThrottleRampMilliseconds.normalize(
                preferences.getInt(KEY_RAMP_UP_MS, PedalAudioThrottleRampMilliseconds.DEFAULT),
            ),
            rampDownMilliseconds = PedalAudioThrottleRampMilliseconds.normalize(
                preferences.getInt(KEY_RAMP_DOWN_MS, PedalAudioThrottleRampMilliseconds.DEFAULT),
            ),
        )
    }

    fun save(settings: MinimumAudioThrottleSettings) {
        preferences.edit()
            .putFloat(KEY_MINIMUM, MinimumAudioThrottle.normalize(settings.minimum))
            .putInt(
                KEY_RAMP_UP_MS,
                PedalAudioThrottleRampMilliseconds.normalize(settings.rampUpMilliseconds),
            )
            .putInt(
                KEY_RAMP_DOWN_MS,
                PedalAudioThrottleRampMilliseconds.normalize(settings.rampDownMilliseconds),
            )
            .remove(LEGACY_FORCE_ENABLED_KEY)
            .commit()
    }

    fun reset() {
        preferences.edit().clear().commit()
    }

    private companion object {
        const val KEY_MINIMUM = "minimum"
        const val KEY_RAMP_UP_MS = "ramp_up_ms"
        const val KEY_RAMP_DOWN_MS = "ramp_down_ms"
        const val LEGACY_FORCE_ENABLED_KEY = "enabled"
    }
}
