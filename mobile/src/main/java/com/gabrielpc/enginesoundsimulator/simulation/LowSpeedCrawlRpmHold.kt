package com.gabrielpc.enginesoundsimulator.simulation

import kotlin.math.abs

/**
 * Low-speed crawl needle: from a standstill, glide the tach toward 3,000 RPM and hold until
 * road speed passes 20 km/h or the driver brakes.
 */
internal class LowSpeedCrawlRpmHold {
    enum class Phase {
        INACTIVE,
        APPROACHING_HOLD,
        HOLDING,
        RETURNING_TO_IDLE,
    }

    var phase: Phase = Phase.INACTIVE
        private set

    val isActive: Boolean
        get() = phase != Phase.INACTIVE

    private var previousSpeedKmh: Double = 0.0
    private var rearmEligible: Boolean = false

    fun clear() {
        phase = Phase.INACTIVE
        previousSpeedKmh = 0.0
        rearmEligible = false
    }

    fun step(
        dt: Double,
        enabled: Boolean,
        speedKmh: Double,
        brake: Double,
        throttle: Double,
        idleRpm: Double,
        currentRpm: Double,
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

        if (speedKmh >= RELEASE_SPEED_KMH) {
            phase = Phase.INACTIVE
            rearmEligible = false
            previousSpeedKmh = speedKmh
            return null
        }

        val brakeApplied = brake >= BRAKE_THRESHOLD
        val gainingSpeed = speedKmh > previousSpeedKmh + GAIN_SPEED_DELTA_KMH
        val leftStandstill = previousSpeedKmh <= STANDSTILL_SPEED_KMH && speedKmh > STANDSTILL_SPEED_KMH
        val driverRequestsMotion = throttle >= THROTTLE_INTENT_THRESHOLD || gainingSpeed

        if (brakeApplied) {
            phase = Phase.RETURNING_TO_IDLE
            rearmEligible = true
        } else if (phase == Phase.INACTIVE) {
            val startedFromStandstill = leftStandstill
            val resumedAfterBrake = rearmEligible &&
                driverRequestsMotion &&
                speedKmh > STANDSTILL_SPEED_KMH
            if (startedFromStandstill || resumedAfterBrake) {
                phase = Phase.APPROACHING_HOLD
                rearmEligible = false
            }
        } else if (phase == Phase.RETURNING_TO_IDLE) {
            if (driverRequestsMotion && speedKmh > STANDSTILL_SPEED_KMH) {
                phase = Phase.APPROACHING_HOLD
                rearmEligible = false
            }
        }

        previousSpeedKmh = speedKmh

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
        const val RELEASE_SPEED_KMH = 20.0
        const val STANDSTILL_SPEED_KMH = 0.5
        const val GAIN_SPEED_DELTA_KMH = 0.15
        const val THROTTLE_INTENT_THRESHOLD = 0.05
        const val BRAKE_THRESHOLD = 0.05
        const val APPROACH_RESPONSE_SECONDS = 1.8
        const val RETURN_TO_IDLE_RESPONSE_SECONDS = 1.35
        const val HOLD_SETTLE_RPM = 35.0
        const val RETURN_SETTLE_RPM = 40.0
    }
}
