package com.gabrielpc.enginesoundsimulator.simulation

enum class AutomaticTransmissionMode {
    CRUISING,
    RACING,
}

/**
 * Automatic shift behavior layered on top of each bank's authored thresholds.
 *
 * Racing upshift/downshift triggers are relocated near the limiter instead of using the bank's
 * absolute auto_up/auto_down RPM values. Cruising then lowers those relocated triggers further.
 */
internal object AutomaticTransmissionPolicy {
    /** Throttle below this after kickdown cancels the one-shot automatic upshift follow-up. */
    const val MANUAL_KICKDOWN_CANCEL_MAX_THROTTLE = 0.12
    /** Emergency upshift once after holding the limiter this long, unless already in top gear. */
    const val EMERGENCY_UPSHIFT_HOLD_SECONDS = 1.5
    /** Complete throttle release prepares a racing → cruising return without switching immediately. */
    const val RACING_RETURN_FULL_LIFT_MAX_THROTTLE = 0.0
    /** Automatic transmission returns to cruising below this road speed in D. */
    const val CRUISING_RETURN_MAX_SPEED_MPS = 1.0
    /** Racing upshift sits this many RPM below the authored limiter. */
    const val UPSHIFT_MARGIN_BELOW_LIMITER_RPM = 150.0
    private const val MINIMUM_THRESHOLD_ABOVE_IDLE_RPM = 250.0
    private const val MINIMUM_UPSHIFT_DOWNSHIFT_SPREAD_RPM = 500.0

    data class RelocatedShiftThresholds(
        val upshiftRpm: Double,
        val downshiftRpm: Double,
    )

    /**
     * Anchor automatic upshift just below the limiter and preserve the bank's up/down spread
     * so downshift hysteresis moves with it instead of staying at the authored absolute RPMs.
     */
    fun relocatedShiftThresholds(
        authoredUpshiftRpm: Int,
        authoredDownshiftRpm: Int,
        limiterRpm: Double,
        idleRpm: Double,
    ): RelocatedShiftThresholds {
        val minimumRpm = idleRpm + MINIMUM_THRESHOLD_ABOVE_IDLE_RPM
        val authoredSpread = (authoredUpshiftRpm - authoredDownshiftRpm)
            .toDouble()
            .coerceAtLeast(MINIMUM_UPSHIFT_DOWNSHIFT_SPREAD_RPM)

        val upshiftRpm = if (limiterRpm > 0.0) {
            (limiterRpm - UPSHIFT_MARGIN_BELOW_LIMITER_RPM).coerceAtLeast(minimumRpm)
        } else {
            authoredUpshiftRpm.toDouble().coerceAtLeast(minimumRpm)
        }

        val downshiftRpm = (upshiftRpm - authoredSpread).coerceAtLeast(minimumRpm)

        return RelocatedShiftThresholds(
            upshiftRpm = upshiftRpm,
            downshiftRpm = downshiftRpm,
        )
    }

    fun kickdownStompDetected(
        previousThrottle: Double,
        currentThrottle: Double,
        minDelta: Double,
        minCurrentThrottle: Double,
    ): Boolean {
        val delta = currentThrottle - previousThrottle
        if (delta < minDelta) {
            return false
        }

        if (currentThrottle < minCurrentThrottle) {
            return false
        }

        return true
    }

    fun manualKickdownStompDetected(
        previousThrottle: Double,
        currentThrottle: Double,
        minDelta: Double,
        minCurrentThrottle: Double,
    ): Boolean {
        return kickdownStompDetected(
            previousThrottle = previousThrottle,
            currentThrottle = currentThrottle,
            minDelta = minDelta,
            minCurrentThrottle = minCurrentThrottle,
        )
    }

    data class RacingReturnStepResult(
        val armed: Boolean,
        val lightBrakeHoldSeconds: Double,
        val returnToCruising: Boolean,
    )

    /**
     * Racing-mode return logic: brake returns to cruising immediately; lift-off prepares
     * P-CRUISING; gentle re-acceleration after lift-off completes the return.
     */
    fun stepRacingReturn(
        armed: Boolean,
        rawGas: Double,
        brake: Double,
        previousThrottle: Double,
        racingReturnMaxThrottle: Double,
        kickdownMinDelta: Double,
        kickdownMinCurrentThrottle: Double,
    ): RacingReturnStepResult {
        if (brake > 0.0) {
            return RacingReturnStepResult(
                armed = false,
                lightBrakeHoldSeconds = 0.0,
                returnToCruising = true,
            )
        }

        var nextArmed = armed
        var returnToCruising = false

        val fullLift = rawGas <= RACING_RETURN_FULL_LIFT_MAX_THROTTLE

        if (fullLift) {
            nextArmed = true
        }

        if (!returnToCruising && nextArmed && rawGas > 0.0) {
            val stomp = kickdownStompDetected(
                previousThrottle = previousThrottle,
                currentThrottle = rawGas,
                minDelta = kickdownMinDelta,
                minCurrentThrottle = kickdownMinCurrentThrottle,
            )

            if (stomp || rawGas > racingReturnMaxThrottle) {
                nextArmed = false
            } else {
                returnToCruising = true
            }
        }

        if (returnToCruising) {
            nextArmed = false
        }

        return RacingReturnStepResult(
            armed = nextArmed,
            lightBrakeHoldSeconds = 0.0,
            returnToCruising = returnToCruising,
        )
    }

    fun shouldEnterRacingFromCruisingKickdown(
        mode: AutomaticTransmissionMode,
        previousThrottle: Double,
        currentThrottle: Double,
        kickdownMinDelta: Double,
        kickdownMinCurrentThrottle: Double,
    ): Boolean {
        if (mode != AutomaticTransmissionMode.CRUISING) {
            return false
        }

        return kickdownStompDetected(
            previousThrottle = previousThrottle,
            currentThrottle = currentThrottle,
            minDelta = kickdownMinDelta,
            minCurrentThrottle = kickdownMinCurrentThrottle,
        )
    }

    fun applyCruisingOffset(
        baseRpm: Double,
        offsetRpm: Int,
        idleRpm: Double,
    ): Double {
        if (offsetRpm <= 0) {
            return baseRpm
        }

        return (baseRpm - offsetRpm).coerceAtLeast(idleRpm + MINIMUM_THRESHOLD_ABOVE_IDLE_RPM)
    }
}
