package com.gabrielpc.enginesoundsimulator.drive

import kotlin.math.roundToInt

data class RpmGainCurvePoint(
    val rpm: Int,
    val gainOffset: Float,
)

/** Catmull-Rom spline through five RPM/gain points; O(1) segment lookup for audio. */
internal class RpmGainCurveEvaluator private constructor(
    private val points: List<RpmGainCurvePoint>,
) {
    fun eval(rpm: Double): Float {
        if (points.isEmpty()) {
            return 0f
        }

        if (points.size == 1) {
            return points[0].gainOffset.coerceIn(SpeedAudioGain.GAIN_MIN, SpeedAudioGain.GAIN_MAX)
        }

        val x = rpm

        if (x <= points.first().rpm) {
            return points.first().gainOffset.coerceIn(SpeedAudioGain.GAIN_MIN, SpeedAudioGain.GAIN_MAX)
        }

        if (x >= points.last().rpm) {
            return points.last().gainOffset.coerceIn(SpeedAudioGain.GAIN_MIN, SpeedAudioGain.GAIN_MAX)
        }

        for (segmentIndex in 0 until points.lastIndex) {
            val startRpm = points[segmentIndex].rpm.toDouble()
            val endRpm = points[segmentIndex + 1].rpm.toDouble()

            if (x < startRpm || x > endRpm) {
                continue
            }

            if (startRpm == endRpm) {
                return points[segmentIndex].gainOffset.coerceIn(SpeedAudioGain.GAIN_MIN, SpeedAudioGain.GAIN_MAX)
            }

            val p0 = points[(segmentIndex - 1).coerceAtLeast(0)]
            val p1 = points[segmentIndex]
            val p2 = points[segmentIndex + 1]
            val p3 = points[(segmentIndex + 2).coerceAtMost(points.lastIndex)]
            val t = ((x - startRpm) / (endRpm - startRpm)).coerceIn(0.0, 1.0)

            return catmullRom(
                y0 = p0.gainOffset,
                y1 = p1.gainOffset,
                y2 = p2.gainOffset,
                y3 = p3.gainOffset,
                t = t,
            ).coerceIn(SpeedAudioGain.GAIN_MIN, SpeedAudioGain.GAIN_MAX)
        }

        return points.last().gainOffset.coerceIn(SpeedAudioGain.GAIN_MIN, SpeedAudioGain.GAIN_MAX)
    }

    companion object {
        fun fromPoints(points: List<RpmGainCurvePoint>): RpmGainCurveEvaluator {
            require(points.size == SpeedAudioGain.CURVE_POINT_COUNT) {
                "RPM gain curve requires exactly ${SpeedAudioGain.CURVE_POINT_COUNT} points."
            }

            return RpmGainCurveEvaluator(points.sortedBy { point -> point.rpm })
        }

        private fun catmullRom(y0: Float, y1: Float, y2: Float, y3: Float, t: Double): Float {
            val t2 = t * t
            val t3 = t2 * t

            return (
                0.5 * (
                    (2.0 * y1) +
                        (-y0 + y2) * t +
                        (2.0 * y0 - 5.0 * y1 + 4.0 * y2 - y3) * t2 +
                        (-y0 + 3.0 * y1 - 3.0 * y2 + y3) * t3
                    )
                ).toFloat()
        }
    }
}

/** Additive gain offsets applied from an RPM curve and a speed-only bonus. */
object SpeedAudioGain {
    const val GAIN_MIN = -1.0f
    const val GAIN_MAX = 1.0f
    const val GAIN_STEP = 0.05f

    const val RPM_MIN = 0
    const val RPM_MAX = 12_000
    const val RPM_STEP = 100
    const val MIN_RPM_SPACING = 250
    const val CURVE_POINT_COUNT = 5

    const val SPEED_COEFFICIENT_MIN = 0.0f
    const val SPEED_COEFFICIENT_MAX = 2.0f
    const val SPEED_COEFFICIENT_STEP = 0.05f

    const val DEFAULT_SPEED_GAIN_COEFFICIENT = 0.0f

    /** Reference road speed for the speed coefficient (full bonus at this speed). */
    const val SPEED_REFERENCE_KMH = 190.0

    val DEFAULT_CURVE_POINTS: List<RpmGainCurvePoint> = listOf(
        RpmGainCurvePoint(rpm = 0, gainOffset = 0.0f),
        RpmGainCurvePoint(rpm = 2_000, gainOffset = 0.0f),
        RpmGainCurvePoint(rpm = 4_000, gainOffset = -0.25f),
        RpmGainCurvePoint(rpm = 7_000, gainOffset = 0.25f),
        RpmGainCurvePoint(rpm = RPM_MAX, gainOffset = 0.25f),
    )

    fun normalizeGain(value: Float): Float {
        val stepped = (value / GAIN_STEP).roundToInt() * GAIN_STEP
        return stepped.coerceIn(GAIN_MIN, GAIN_MAX)
    }

    fun normalizeRpm(value: Int): Int {
        val stepped = ((value.toFloat() / RPM_STEP).roundToInt() * RPM_STEP)
        return stepped.coerceIn(RPM_MIN, RPM_MAX)
    }

    fun normalizeSpeedCoefficient(value: Float): Float {
        val stepped = (value / SPEED_COEFFICIENT_STEP).roundToInt() * SPEED_COEFFICIENT_STEP
        return stepped.coerceIn(SPEED_COEFFICIENT_MIN, SPEED_COEFFICIENT_MAX)
    }

    fun migrateFromThreePointCurve(points: List<RpmGainCurvePoint>): List<RpmGainCurvePoint> {
        require(points.size == 3) { "Legacy RPM gain curve migration requires exactly 3 points." }

        val sorted = points.sortedBy { point -> point.rpm }

        return listOf(
            RpmGainCurvePoint(rpm = RPM_MIN, gainOffset = sorted[0].gainOffset),
            sorted[0],
            sorted[1],
            sorted[2],
            RpmGainCurvePoint(rpm = RPM_MAX, gainOffset = sorted[2].gainOffset),
        )
    }

    fun normalizeCurvePoints(points: List<RpmGainCurvePoint>): List<RpmGainCurvePoint> {
        if (points.size != CURVE_POINT_COUNT) {
            if (points.size == 3) {
                return normalizeCurvePoints(migrateFromThreePointCurve(points))
            }

            return DEFAULT_CURVE_POINTS
        }

        val sorted = points.sortedBy { point -> point.rpm }
        val normalizedGains = sorted.map { point ->
            point.copy(gainOffset = normalizeGain(point.gainOffset))
        }

        var p1Rpm = normalizeRpm(normalizedGains[1].rpm)
            .coerceIn(MIN_RPM_SPACING, RPM_MAX - (MIN_RPM_SPACING * 3))
        var p2Rpm = normalizeRpm(normalizedGains[2].rpm)
            .coerceIn(p1Rpm + MIN_RPM_SPACING, RPM_MAX - (MIN_RPM_SPACING * 2))
        var p3Rpm = normalizeRpm(normalizedGains[3].rpm)
            .coerceIn(p2Rpm + MIN_RPM_SPACING, RPM_MAX - MIN_RPM_SPACING)

        p1Rpm = p1Rpm.coerceAtMost(p2Rpm - MIN_RPM_SPACING)
        p3Rpm = p3Rpm.coerceAtLeast(p2Rpm + MIN_RPM_SPACING)
        p2Rpm = p2Rpm.coerceIn(p1Rpm + MIN_RPM_SPACING, p3Rpm - MIN_RPM_SPACING)

        return listOf(
            normalizedGains[0].copy(rpm = RPM_MIN),
            normalizedGains[1].copy(rpm = p1Rpm),
            normalizedGains[2].copy(rpm = p2Rpm),
            normalizedGains[3].copy(rpm = p3Rpm),
            normalizedGains[4].copy(rpm = RPM_MAX),
        )
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

    fun migrateFromLegacyBands(
        lowRangeMaxRpm: Int,
        midRangeMaxRpm: Int,
        lowRangeGain: Float,
        midRangeGain: Float,
        highRangeGain: Float,
    ): List<RpmGainCurvePoint> {
        val midHighRpm = normalizeRpm((midRangeMaxRpm + RPM_MAX) / 2)

        return normalizeCurvePoints(
            listOf(
                RpmGainCurvePoint(rpm = RPM_MIN, gainOffset = lowRangeGain),
                RpmGainCurvePoint(rpm = lowRangeMaxRpm, gainOffset = lowRangeGain),
                RpmGainCurvePoint(rpm = midRangeMaxRpm, gainOffset = midRangeGain),
                RpmGainCurvePoint(rpm = midHighRpm, gainOffset = highRangeGain),
                RpmGainCurvePoint(rpm = RPM_MAX, gainOffset = highRangeGain),
            ),
        )
    }
}

data class SpeedAudioSettings(
    val curvePoints: List<RpmGainCurvePoint> = SpeedAudioGain.DEFAULT_CURVE_POINTS,
    val speedGainCoefficient: Float = SpeedAudioGain.DEFAULT_SPEED_GAIN_COEFFICIENT,
) {
    fun normalized(): SpeedAudioSettings {
        return copy(
            curvePoints = SpeedAudioGain.normalizeCurvePoints(curvePoints),
            speedGainCoefficient = SpeedAudioGain.normalizeSpeedCoefficient(speedGainCoefficient),
        )
    }

    internal fun resolved(): ResolvedSpeedAudioSettings {
        return ResolvedSpeedAudioSettings.from(this)
    }
}

/** Cached curve evaluator for the audio worker; rebuilt only when settings change. */
internal data class ResolvedSpeedAudioSettings(
    val curvePoints: List<RpmGainCurvePoint>,
    val speedGainCoefficient: Float,
    private val curveEvaluator: RpmGainCurveEvaluator,
) {
    fun rpmGainOffset(rpm: Double): Float {
        return curveEvaluator.eval(rpm.coerceAtLeast(0.0))
    }

    fun combinedMultiplier(rpm: Double, speedKmh: Double): Float {
        val rpmOffset = rpmGainOffset(rpm)
        val speedBonus = SpeedAudioGainResolver.speedGainBonus(speedKmh, speedGainCoefficient)

        return (1f + rpmOffset + speedBonus).coerceAtLeast(0f)
    }

    companion object {
        fun from(settings: SpeedAudioSettings): ResolvedSpeedAudioSettings {
            val normalized = settings.normalized()

            return ResolvedSpeedAudioSettings(
                curvePoints = normalized.curvePoints,
                speedGainCoefficient = normalized.speedGainCoefficient,
                curveEvaluator = RpmGainCurveEvaluator.fromPoints(normalized.curvePoints),
            )
        }
    }
}

object SpeedAudioGainResolver {
    fun rpmGainOffset(rpm: Double, settings: SpeedAudioSettings): Float {
        return settings.resolved().rpmGainOffset(rpm)
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

    /** Additive RPM curve offset plus speed bonus at the current drivetrain state. */
    fun combinedGainOffset(rpm: Double, speedKmh: Double, settings: SpeedAudioSettings): Float {
        val normalized = settings.normalized()

        return rpmGainOffset(rpm, normalized) +
            speedGainBonus(speedKmh, normalized.speedGainCoefficient)
    }

    fun combinedMultiplier(
        rpm: Double,
        speedKmh: Double,
        settings: SpeedAudioSettings,
    ): Float {
        return settings.resolved().combinedMultiplier(
            rpm = rpm,
            speedKmh = speedKmh,
        )
    }

    internal fun combinedMultiplier(
        rpm: Double,
        speedKmh: Double,
        settings: ResolvedSpeedAudioSettings,
    ): Float {
        return settings.combinedMultiplier(
            rpm = rpm,
            speedKmh = speedKmh,
        )
    }
}
