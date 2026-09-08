package com.gabrielpc.enginesoundsimulator.drive

import kotlin.math.roundToInt

/** Additive gain offsets applied from RPM bands and a speed-only bonus. */
object SpeedAudioGain {
    const val MIN = -1.0f
    const val MAX = 1.0f
    const val STEP = 0.05f

    const val SPEED_COEFFICIENT_MIN = 0.0f
    const val SPEED_COEFFICIENT_MAX = 2.0f
    const val SPEED_COEFFICIENT_STEP = 0.05f

    const val DEFAULT_LOW_RANGE_MAX_RPM = 3_000
    const val DEFAULT_MID_RANGE_MAX_RPM = 5_000
    const val DEFAULT_LOW_RANGE_GAIN = 0.0f
    const val DEFAULT_MID_RANGE_GAIN = -0.25f
    const val DEFAULT_HIGH_RANGE_GAIN = 0.25f
    const val DEFAULT_SPEED_GAIN_COEFFICIENT = 0.0f

    /** Reference road speed for the speed coefficient (full bonus at this speed). */
    const val SPEED_REFERENCE_KMH = 190.0

    fun normalizeGain(value: Float): Float {
        val stepped = (value / STEP).roundToInt() * STEP
        return stepped.coerceIn(MIN, MAX)
    }

    fun normalizeSpeedCoefficient(value: Float): Float {
        val stepped = (value / SPEED_COEFFICIENT_STEP).roundToInt() * SPEED_COEFFICIENT_STEP
        return stepped.coerceIn(SPEED_COEFFICIENT_MIN, SPEED_COEFFICIENT_MAX)
    }

    fun normalizeBoundaryRpm(value: Int): Int {
        return value.coerceIn(MIN_BOUNDARY_RPM, MAX_BOUNDARY_RPM)
    }

    fun gainSliderSteps(): Int {
        return ((MAX - MIN) / STEP).roundToInt() - 1
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

    fun formatSpeedCoefficient(value: Float): String {
        return String.format(java.util.Locale.US, "%.2fx", normalizeSpeedCoefficient(value))
    }

    private const val MIN_BOUNDARY_RPM = 500
    private const val MAX_BOUNDARY_RPM = 12_000
}

data class SpeedAudioSettings(
    val lowRangeMaxRpm: Int = SpeedAudioGain.DEFAULT_LOW_RANGE_MAX_RPM,
    val midRangeMaxRpm: Int = SpeedAudioGain.DEFAULT_MID_RANGE_MAX_RPM,
    val lowRangeGain: Float = SpeedAudioGain.DEFAULT_LOW_RANGE_GAIN,
    val midRangeGain: Float = SpeedAudioGain.DEFAULT_MID_RANGE_GAIN,
    val highRangeGain: Float = SpeedAudioGain.DEFAULT_HIGH_RANGE_GAIN,
    val speedGainCoefficient: Float = SpeedAudioGain.DEFAULT_SPEED_GAIN_COEFFICIENT,
) {
    fun normalized(): SpeedAudioSettings {
        val lowBoundary = SpeedAudioGain.normalizeBoundaryRpm(lowRangeMaxRpm)
        val midBoundary = SpeedAudioGain.normalizeBoundaryRpm(midRangeMaxRpm.coerceAtLeast(lowBoundary + 100))

        return copy(
            lowRangeMaxRpm = lowBoundary,
            midRangeMaxRpm = midBoundary,
            lowRangeGain = SpeedAudioGain.normalizeGain(lowRangeGain),
            midRangeGain = SpeedAudioGain.normalizeGain(midRangeGain),
            highRangeGain = SpeedAudioGain.normalizeGain(highRangeGain),
            speedGainCoefficient = SpeedAudioGain.normalizeSpeedCoefficient(speedGainCoefficient),
        )
    }
}

object SpeedAudioGainResolver {
    fun rpmGainOffset(rpm: Double, settings: SpeedAudioSettings): Float {
        val normalized = settings.normalized()
        val rpmValue = rpm.coerceAtLeast(0.0)

        if (rpmValue < normalized.lowRangeMaxRpm) {
            return normalized.lowRangeGain
        }

        if (rpmValue <= normalized.midRangeMaxRpm) {
            return normalized.midRangeGain
        }

        return normalized.highRangeGain
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

    fun combinedMultiplier(
        rpm: Double,
        speedKmh: Double,
        settings: SpeedAudioSettings,
    ): Float {
        val normalized = settings.normalized()
        val rpmOffset = rpmGainOffset(rpm, normalized)
        val speedBonus = speedGainBonus(speedKmh, normalized.speedGainCoefficient)

        return (1f + rpmOffset + speedBonus).coerceAtLeast(0f)
    }
}
