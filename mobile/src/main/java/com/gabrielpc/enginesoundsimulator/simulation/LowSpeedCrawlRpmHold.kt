package com.gabrielpc.enginesoundsimulator.simulation

import kotlin.math.abs

/**
 * Low-speed crawl needle: when normal drivetrain logic would sit below 3,000 RPM, throttle above
 * 1% glides the tach toward 3,000 RPM; lifting the throttle or braking glides back to idle.
 * Once normal logic would reach 3,000 RPM or higher, glide into the mapped RPM instead of
 * snapping to it.
 */
internal class LowSpeedCrawlRpmHold {
    enum class Phase {
        INACTIVE,
        APPROACHING_HOLD,
        HOLDING,
        HANDOFF_TO_BASELINE,
        RETURNING_TO_IDLE,
    }

    var phase: Phase = Phase.INACTIVE
        private set

    val isActive: Boolean
        get() = phase != Phase.INACTIVE

    fun clear() {
        phase = Phase.INACTIVE
    }

    fun step(
        dt: Double,
        enabled: Boolean,
        brake: Double,
        throttle: Double,
        idleRpm: Double,
        currentRpm: Double,
        baselineRpm: Double,
        launchControlActive: Boolean,
        cruisingReturnActive: Boolean,
        inDrive: Boolean,
    ): Double? {
        if (
            !enabled ||
            !inDrive ||
            launchControlActive ||
            cruisingReturnActive
        ) {
            clear()
            return null
        }

        if (baselineRpm >= HOLD_RPM) {
            if (phase == Phase.APPROACHING_HOLD || phase == Phase.HOLDING) {
                phase = Phase.HANDOFF_TO_BASELINE
            } else if (phase != Phase.HANDOFF_TO_BASELINE) {
                clear()
                return null
            }
        }

        val brakeApplied = brake >= BRAKE_THRESHOLD
        val throttleEngaged = throttle > THROTTLE_ENGAGE_THRESHOLD
        val driverRequestsHold = throttleEngaged && !brakeApplied

        if (driverRequestsHold) {
            if (phase == Phase.INACTIVE || phase == Phase.RETURNING_TO_IDLE) {
                phase = Phase.APPROACHING_HOLD
            }
        } else if (phase == Phase.APPROACHING_HOLD || phase == Phase.HOLDING) {
            phase = Phase.RETURNING_TO_IDLE
        }

        return when (phase) {
            Phase.INACTIVE -> null

            Phase.APPROACHING_HOLD -> {
                val nextRpm = approachRpm(
                    currentRpm = currentRpm,
                    targetRpm = HOLD_RPM,
                    responseSeconds = APPROACH_RESPONSE_SECONDS,
                    dt = dt,
                    idleRpm = idleRpm,
                    limiterRpm = Double.MAX_VALUE,
                )
                if (abs(nextRpm - HOLD_RPM) <= HOLD_SETTLE_RPM) {
                    phase = Phase.HOLDING
                    HOLD_RPM
                } else {
                    nextRpm
                }
            }

            Phase.HOLDING -> HOLD_RPM

            Phase.HANDOFF_TO_BASELINE -> {
                val nextRpm = approachRpm(
                    currentRpm = currentRpm,
                    targetRpm = baselineRpm,
                    responseSeconds = HANDOFF_RESPONSE_SECONDS,
                    dt = dt,
                    idleRpm = idleRpm,
                    limiterRpm = Double.MAX_VALUE,
                )
                if (abs(nextRpm - baselineRpm) <= HANDOFF_SETTLE_RPM) {
                    clear()
                    baselineRpm
                } else {
                    nextRpm
                }
            }

            Phase.RETURNING_TO_IDLE -> {
                val nextRpm = approachRpm(
                    currentRpm = currentRpm,
                    targetRpm = idleRpm,
                    responseSeconds = RETURN_TO_IDLE_RESPONSE_SECONDS,
                    dt = dt,
                    idleRpm = idleRpm,
                    limiterRpm = Double.MAX_VALUE,
                )
                if (abs(nextRpm - idleRpm) <= RETURN_SETTLE_RPM) {
                    phase = Phase.INACTIVE
                    idleRpm
                } else {
                    nextRpm
                }
            }
        }
    }

    private fun approachRpm(
        currentRpm: Double,
        targetRpm: Double,
        responseSeconds: Double,
        dt: Double,
        idleRpm: Double,
        limiterRpm: Double,
    ): Double {
        val response = responseSeconds.coerceAtLeast(0.001)
        val alpha = (1.0 - kotlin.math.exp(-dt / response)).coerceIn(0.0, 1.0)
        return (currentRpm + (targetRpm - currentRpm) * alpha)
            .coerceIn(idleRpm, limiterRpm)
    }

    companion object {
        const val HOLD_RPM = 3_000.0
        const val THROTTLE_ENGAGE_THRESHOLD = 0.01
        const val BRAKE_THRESHOLD = 0.05
        const val APPROACH_RESPONSE_SECONDS = 1.8
        const val HANDOFF_RESPONSE_SECONDS = 1.1
        const val RETURN_TO_IDLE_RESPONSE_SECONDS = 1.35
        const val HOLD_SETTLE_RPM = 35.0
        const val HANDOFF_SETTLE_RPM = 45.0
        const val RETURN_SETTLE_RPM = 40.0
    }
}
