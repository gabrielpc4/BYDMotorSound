package com.gabrielpc.enginesoundsimulator.drive

import kotlin.math.roundToInt
import com.gabrielpc.enginesoundsimulator.simulation.AutomaticTransmissionMode

/** Host-gain offsets for cruising vs racing/manual, plus an optional speed bonus. */
object SpeedAudioGain {
    const val GAIN_MIN = -1.0f
    const val GAIN_MAX = 0.0f
    const val GAIN_STEP = 0.05f

    const val MODE_BLEND_SECONDS_MIN = 0.0f
    const val MODE_BLEND_SECONDS_MAX = 5.0f
    const val MODE_BLEND_SECONDS_STEP = 0.05f

    const val SPEED_COEFFICIENT_MIN = 0.0f
    const val SPEED_COEFFICIENT_MAX = 2.0f
    const val SPEED_COEFFICIENT_STEP = 0.05f

    const val DEFAULT_CRUISING_GAIN = 0.0f
    const val DEFAULT_RACING_GAIN = -0.25f
    const val DEFAULT_MODE_BLEND_SECONDS = 0.75f
    const val DEFAULT_SPEED_GAIN_COEFFICIENT = 0.0f

    /** Reference road speed for the speed coefficient (full bonus at this speed). */
    const val SPEED_REFERENCE_KMH = 190.0

    fun normalizeGain(value: Float): Float {
        val stepped = (value / GAIN_STEP).roundToInt() * GAIN_STEP
        return stepped.coerceIn(GAIN_MIN, GAIN_MAX)
    }

    fun normalizeModeBlendSeconds(value: Float): Float {
        val stepped = (value / MODE_BLEND_SECONDS_STEP).roundToInt() * MODE_BLEND_SECONDS_STEP
        return stepped.coerceIn(MODE_BLEND_SECONDS_MIN, MODE_BLEND_SECONDS_MAX)
    }

    fun normalizeSpeedCoefficient(value: Float): Float {
        val stepped = (value / SPEED_COEFFICIENT_STEP).roundToInt() * SPEED_COEFFICIENT_STEP
        return stepped.coerceIn(SPEED_COEFFICIENT_MIN, SPEED_COEFFICIENT_MAX)
    }

    fun modeGainSliderSteps(): Int {
        return ((GAIN_MAX - GAIN_MIN) / GAIN_STEP).roundToInt()
    }

    fun modeBlendSliderSteps(): Int {
        return ((MODE_BLEND_SECONDS_MAX - MODE_BLEND_SECONDS_MIN) / MODE_BLEND_SECONDS_STEP).roundToInt()
    }

    fun speedCoefficientSliderSteps(): Int {
        return ((SPEED_COEFFICIENT_MAX - SPEED_COEFFICIENT_MIN) / SPEED_COEFFICIENT_STEP).roundToInt() - 1
    }

    fun formatGainOffset(value: Float): String {
        val percent = (normalizeGain(value) * 100f).roundToInt()

        if (percent >= 0) {
            return "+$percent%"
        }

        return "$percent%"
    }

    fun formatModeBlendSeconds(value: Float): String {
        return String.format(java.util.Locale.US, "%.2fs", normalizeModeBlendSeconds(value))
    }

    fun formatSpeedCoefficient(value: Float): String {
        return String.format(java.util.Locale.US, "%.2fx", normalizeSpeedCoefficient(value))
    }

    fun lerp(start: Float, end: Float, amount: Float): Float {
        val blend = amount.coerceIn(0f, 1f)
        return start + (end - start) * blend
    }
}

data class SpeedAudioSettings(
    val cruisingGain: Float = SpeedAudioGain.DEFAULT_CRUISING_GAIN,
    val racingGain: Float = SpeedAudioGain.DEFAULT_RACING_GAIN,
    val modeBlendSeconds: Float = SpeedAudioGain.DEFAULT_MODE_BLEND_SECONDS,
    val speedGainCoefficient: Float = SpeedAudioGain.DEFAULT_SPEED_GAIN_COEFFICIENT,
) {
    fun normalized(): SpeedAudioSettings {
        return copy(
            cruisingGain = SpeedAudioGain.normalizeGain(cruisingGain),
            racingGain = SpeedAudioGain.normalizeGain(racingGain),
            modeBlendSeconds = SpeedAudioGain.normalizeModeBlendSeconds(modeBlendSeconds),
            speedGainCoefficient = SpeedAudioGain.normalizeSpeedCoefficient(speedGainCoefficient),
        )
    }

    internal fun resolved(): ResolvedSpeedAudioSettings {
        return ResolvedSpeedAudioSettings.from(this)
    }
}

/** Cached speed-audio settings for the audio worker. */
internal data class ResolvedSpeedAudioSettings(
    val cruisingGain: Float,
    val racingGain: Float,
    val modeBlendSeconds: Float,
    val speedGainCoefficient: Float,
) {
    fun modeGainOffset(racingBlend: Float): Float {
        return SpeedAudioGain.lerp(
            start = cruisingGain,
            end = racingGain,
            amount = racingBlend.coerceIn(0f, 1f),
        )
    }

    fun combinedMultiplier(racingBlend: Float, speedKmh: Double): Float {
        val modeOffset = modeGainOffset(racingBlend)
        val blend = racingBlend.coerceIn(0f, 1f)
        val speedBonus = SpeedAudioGainResolver.speedGainBonus(speedKmh, speedGainCoefficient) * blend

        return (1f + modeOffset + speedBonus).coerceAtLeast(0f)
    }

    companion object {
        fun from(settings: SpeedAudioSettings): ResolvedSpeedAudioSettings {
            val normalized = settings.normalized()

            return ResolvedSpeedAudioSettings(
                cruisingGain = normalized.cruisingGain,
                racingGain = normalized.racingGain,
                modeBlendSeconds = normalized.modeBlendSeconds,
                speedGainCoefficient = normalized.speedGainCoefficient,
            )
        }
    }
}

object SpeedAudioGainResolver {
    fun usesRacingGain(
        manualShiftEnabled: Boolean,
        automaticTransmissionMode: AutomaticTransmissionMode,
        racingReturnArmed: Boolean,
    ): Boolean {
        if (manualShiftEnabled) {
            return true
        }

        if (racingReturnArmed) {
            return false
        }

        return automaticTransmissionMode == AutomaticTransmissionMode.RACING
    }

    /** Speed-only bonus; never negative. */
    fun speedGainBonus(speedKmh: Double, coefficient: Float): Float {
        val normalizedCoefficient = SpeedAudioGain.normalizeSpeedCoefficient(coefficient)

        if (normalizedCoefficient <= 0f) {
            return 0f
        }

        val speed = speedKmh.coerceAtLeast(0.0)
        val normalizedSpeed = (speed / SpeedAudioGain.SPEED_REFERENCE_KMH).coerceIn(0.0, 1.0)

        return (normalizedCoefficient * normalizedSpeed).toFloat()
    }

    fun combinedGainOffset(
        usesRacingGain: Boolean,
        settings: SpeedAudioSettings,
    ): Float {
        val normalized = settings.normalized()

        if (usesRacingGain) {
            return normalized.racingGain
        }

        return normalized.cruisingGain
    }

    fun combinedGainOffset(
        speedKmh: Double,
        usesRacingGain: Boolean,
        settings: SpeedAudioSettings,
    ): Float {
        return combinedGainOffset(usesRacingGain, settings) +
            speedGainBonus(speedKmh, settings.normalized().speedGainCoefficient)
    }

    fun combinedMultiplier(
        racingBlend: Float,
        speedKmh: Double,
        settings: SpeedAudioSettings,
    ): Float {
        return settings.resolved().combinedMultiplier(
            racingBlend = racingBlend,
            speedKmh = speedKmh,
        )
    }

    internal fun combinedMultiplier(
        racingBlend: Float,
        speedKmh: Double,
        settings: ResolvedSpeedAudioSettings,
    ): Float {
        return settings.combinedMultiplier(
            racingBlend = racingBlend,
            speedKmh = speedKmh,
        )
    }
}
