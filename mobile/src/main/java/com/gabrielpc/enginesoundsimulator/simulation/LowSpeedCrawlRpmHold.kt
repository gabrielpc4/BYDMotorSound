package com.gabrielpc.enginesoundsimulator.simulation

import kotlin.math.abs

/**
 * Low-speed crawl needle: when normal drivetrain logic would sit below 3,000 RPM, throttle above
 * 1% glides the tach toward 3,000 RPM. With throttle at or below 1% and no brake, the needle
 * follows mapped road-speed RPM instead of gliding to idle. Once normal logic would reach 3,000
 * RPM or higher, glide into the mapped RPM instead of snapping to it.
 */
internal class LowSpeedCrawlRpmHold {
    enum class Phase {
        INACTIVE,
        APPROACHING_HOLD,
        HOLDING,
        HANDOFF_TO_BASELINE,
        FOLLOWING_BASELINE,
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
            if (
                phase == Phase.INACTIVE ||
                phase == Phase.RETURNING_TO_IDLE ||
                phase == Phase.FOLLOWING_BASELINE
            ) {
                phase = Phase.APPROACHING_HOLD
            }
        } else if (brakeApplied) {
            if (phase != Phase.INACTIVE && phase != Phase.RETURNING_TO_IDLE) {
                phase = Phase.RETURNING_TO_IDLE
            }
        } else if (phase == Phase.APPROACHING_HOLD || phase == Phase.HOLDING) {
            phase = if (shouldFollowBaseline(idleRpm, baselineRpm)) {
                Phase.FOLLOWING_BASELINE
            } else {
                Phase.RETURNING_TO_IDLE
            }
        } else if (
            phase == Phase.INACTIVE &&
            shouldFollowBaseline(idleRpm, baselineRpm)
        ) {
            phase = Phase.FOLLOWING_BASELINE
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
                glideTowardBaseline(
                    currentRpm = currentRpm,
                    baselineRpm = baselineRpm,
                    responseSeconds = HANDOFF_RESPONSE_SECONDS,
                    settleRpm = HANDOFF_SETTLE_RPM,
                    dt = dt,
                    idleRpm = idleRpm,
                )
            }

            Phase.FOLLOWING_BASELINE -> {
                if (!shouldFollowBaseline(idleRpm, baselineRpm)) {
                    phase = Phase.RETURNING_TO_IDLE
                    return approachRpm(
                        currentRpm = currentRpm,
                        targetRpm = idleRpm,
                        responseSeconds = RETURN_TO_IDLE_RESPONSE_SECONDS,
                        dt = dt,
                        idleRpm = idleRpm,
                        limiterRpm = Double.MAX_VALUE,
                    )
                }

                glideTowardBaseline(
                    currentRpm = currentRpm,
                    baselineRpm = baselineRpm,
                    responseSeconds = FOLLOW_BASELINE_RESPONSE_SECONDS,
                    settleRpm = FOLLOW_BASELINE_SETTLE_RPM,
                    dt = dt,
                    idleRpm = idleRpm,
                )
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

    private fun shouldFollowBaseline(idleRpm: Double, baselineRpm: Double): Boolean {
        return baselineRpm > idleRpm + RETURN_SETTLE_RPM && baselineRpm < HOLD_RPM
    }

    private fun glideTowardBaseline(
        currentRpm: Double,
        baselineRpm: Double,
        responseSeconds: Double,
        settleRpm: Double,
        dt: Double,
        idleRpm: Double,
    ): Double {
        val nextRpm = approachRpm(
            currentRpm = currentRpm,
            targetRpm = baselineRpm,
            responseSeconds = responseSeconds,
            dt = dt,
            idleRpm = idleRpm,
            limiterRpm = Double.MAX_VALUE,
        )
        if (abs(nextRpm - baselineRpm) <= settleRpm) {
            clear()
            return baselineRpm
        }

        return nextRpm
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
        const val FOLLOW_BASELINE_RESPONSE_SECONDS = 0.85
        const val RETURN_TO_IDLE_RESPONSE_SECONDS = 1.35
        const val HOLD_SETTLE_RPM = 35.0
        const val HANDOFF_SETTLE_RPM = 45.0
        const val FOLLOW_BASELINE_SETTLE_RPM = 35.0
        const val RETURN_SETTLE_RPM = 40.0
    }
}
