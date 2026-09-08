package com.gabrielpc.enginesoundsimulator.simulation

/**
 * Turns a car's authored `shift_lights` entries into the five RPM stops a dashboard can light.
 *
 * Assetto Corsa cars do not all author shift lights, so cars without them still get a usable
 * ramp derived from the limiter instead of a dead indicator row.
 */
object ShiftLightThresholds {
    const val STOP_COUNT = 5

    /** How far below the limiter the derived ramp starts, and the gap between derived stops. */
    private const val DERIVED_STOP_INTERVAL_RPM = 100.0

    /** The last stop blinks once the engine is this close to the limiter. */
    private const val BLINK_MARGIN_RPM = 200.0

    const val BLINK_HZ = 5.0

    fun resolve(authored: List<Double>, limiterRpm: Double): List<Double> {
        val usable = authored.filter { it > 0.0 }.sorted()
        if (usable.isNotEmpty()) {
            return usable
        }

        return derive(limiterRpm)
    }

    /** RPM at which the lit stops start blinking, mirroring the reference dashboard. */
    fun blinkRpm(limiterRpm: Double): Double {
        return (limiterRpm - BLINK_MARGIN_RPM).coerceAtLeast(0.0)
    }

    private fun derive(limiterRpm: Double): List<Double> {
        if (limiterRpm <= 0.0) {
            return emptyList()
        }

        val lowestStop = limiterRpm - DERIVED_STOP_INTERVAL_RPM * (STOP_COUNT - 1)
        if (lowestStop <= 0.0) {
            return emptyList()
        }

        return List(STOP_COUNT) { index ->
            lowestStop + DERIVED_STOP_INTERVAL_RPM * index
        }
    }
}
