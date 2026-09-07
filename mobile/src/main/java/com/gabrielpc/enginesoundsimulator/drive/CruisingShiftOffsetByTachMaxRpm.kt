package com.gabrielpc.enginesoundsimulator.drive

import kotlin.math.roundToInt

/** Cruising shift offset stored per authored tachometer maximum RPM tier. */
internal object CruisingShiftOffsetByTachMaxRpm {
    const val MIN = 0
    const val MAX = 5_000
    const val STEP = 1_000

    val TIERS: List<Int> = listOf(7_000, 8_000, 9_000, 10_000, 12_000, 20_000)

    private val DEFAULT_OFFSETS: Map<Int, Int> = mapOf(
        7_000 to 2_000,
        8_000 to 2_000,
        9_000 to 3_000,
        10_000 to 3_000,
        12_000 to 4_000,
        20_000 to 5_000,
    )

    fun defaultOffsets(): Map<Int, Int> {
        return DEFAULT_OFFSETS.mapValues { (_, value) -> normalize(value) }
    }

    fun normalize(value: Int): Int {
        val stepped = ((value.toFloat() / STEP).roundToInt() * STEP)
        return stepped.coerceIn(MIN, MAX)
    }

    fun normalizeMap(offsets: Map<Int, Int>): Map<Int, Int> {
        return TIERS.associateWith { tier ->
            normalize(offsets[tier] ?: DEFAULT_OFFSETS[tier] ?: DEFAULT_OFFSETS.getValue(7_000))
        }
    }

    /** Maps the car's authored tachometer maximum to the next configured tier at or above it. */
    fun resolveTier(tachometerMaximumRpm: Double): Int {
        val rpm = tachometerMaximumRpm.roundToInt().coerceAtLeast(1)
        return TIERS.firstOrNull { tier -> tier >= rpm } ?: TIERS.last()
    }

    fun resolveOffset(offsets: Map<Int, Int>, tachometerMaximumRpm: Double): Int {
        val tier = resolveTier(tachometerMaximumRpm)
        return normalize(offsets[tier] ?: DEFAULT_OFFSETS[tier] ?: DEFAULT_OFFSETS.getValue(7_000))
    }

    fun preferenceKey(tierRpm: Int): String {
        return "cruising_shift_offset_rpm_$tierRpm"
    }

    fun formatOffsetLabel(offsetRpm: Int): String {
        return "-${offsetRpm}RPM"
    }

    /** Slider position is inverted so dragging left increases the offset magnitude. */
    fun sliderValueFromOffset(offsetRpm: Int): Float {
        return (MAX - normalize(offsetRpm)).toFloat()
    }

    fun offsetFromSliderValue(sliderValue: Float): Int {
        return normalize((MAX - sliderValue).roundToInt())
    }
}
