package com.gabrielpc.enginesoundsimulator.simulation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.max

class CruisingReturnTransitionTest {
    @Test
    fun ninthNearRedlinePicksATallerCruisingGear() {
        val target = CruisingReturn.targetGear(
            currentGear = 9,
            topGear = 10,
            cruisingUpshiftRpm = CRUISING_UPSHIFT_RPM,
            coupledRpmForGear = ::coupledAtHighSpeed,
        )

        assertEquals(10, target)
    }

    @Test
    fun alreadyInCruisingBandKeepsCurrentGear() {
        val target = CruisingReturn.targetGear(
            currentGear = 10,
            topGear = 10,
            cruisingUpshiftRpm = CRUISING_UPSHIFT_RPM,
            coupledRpmForGear = { gear ->
                if (gear >= 10) 3_800.0 else 8_700.0
            },
        )

        assertEquals(10, target)
    }

    @Test
    fun lightThrottleInNinthDropsRpmInsteadOfClimbing() {
        val transition = CruisingReturnTransition()
        var rpm = NINTH_REDLINE_RPM
        var gear = 9
        var speed = 1.0
        var shifting = false
        var shiftFrames = 0
        transition.begin(
            currentRpm = rpm,
            currentGear = gear,
            computedTargetGear = 10,
            initialLiveTargetRpm = tenthCoupled(speed),
        )

        val history = mutableListOf<Double>()
        repeat(180) {
            speed += 0.012
            val liveTarget = tenthCoupled(speed)
            val nextGear = if (gear < 10) tenthCoupled(speed) else null
            if (shifting) {
                shiftFrames -= 1
                if (shiftFrames <= 0) {
                    gear += 1
                    shifting = false
                }
            }
            val step = transition.step(
                dt = DT,
                currentRpm = rpm,
                currentGear = gear,
                shifting = shifting,
                liveTargetRpm = liveTarget,
                computedTargetGear = if (ninthCoupled(speed) > CRUISING_UPSHIFT_RPM) 10 else gear,
                nextGearCoupledRpm = nextGear,
            )
            rpm = step.rpm
            history.add(rpm)
            if (step.requestUpshift) {
                shifting = true
                shiftFrames = 3
            }
        }

        assertTrue(rpm < NINTH_REDLINE_RPM - 1_000.0)
        assertTrue(gear >= 10)
        assertFalse(transition.active)
        assertFalse(climbedWhileAboveTarget(history, startRpm = NINTH_REDLINE_RPM))
    }

    @Test
    fun doesNotFinishAfterOneSecondWhenStillAboveTarget() {
        val transition = CruisingReturnTransition()
        var rpm = NINTH_REDLINE_RPM
        transition.begin(
            currentRpm = rpm,
            currentGear = 9,
            computedTargetGear = 10,
            initialLiveTargetRpm = 3_800.0,
        )

        repeat(60) {
            val step = transition.step(
                dt = DT,
                currentRpm = rpm,
                currentGear = 9,
                shifting = false,
                liveTargetRpm = 1_800.0,
                computedTargetGear = 10,
                nextGearCoupledRpm = 1_800.0,
            )
            rpm = step.rpm
        }

        assertTrue(transition.active)
        assertTrue(rpm > 1_800.0 + CruisingReturn.RPM_TOLERANCE)
    }

    @Test
    fun keepsGlidingUntilNeedleAndGearSettle() {
        val transition = CruisingReturnTransition()
        var rpm = NINTH_REDLINE_RPM
        var gear = 9
        var shifting = false
        var shiftFrames = 0
        transition.begin(
            currentRpm = rpm,
            currentGear = gear,
            computedTargetGear = 10,
            initialLiveTargetRpm = 3_800.0,
        )

        var finished = false
        var frames = 0
        while (frames < 400 && !finished) {
            if (shifting) {
                shiftFrames -= 1
                if (shiftFrames <= 0) {
                    gear = 10
                    shifting = false
                }
            }
            val step = transition.step(
                dt = DT,
                currentRpm = rpm,
                currentGear = gear,
                shifting = shifting,
                liveTargetRpm = 3_800.0,
                computedTargetGear = 10,
                nextGearCoupledRpm = if (gear < 10) 3_800.0 else null,
            )
            rpm = step.rpm
            finished = step.finished
            if (step.requestUpshift) {
                shifting = true
                shiftFrames = 3
            }
            frames++
        }

        assertTrue(finished)
        assertEquals(10, gear)
        assertEquals(3_800.0, rpm, CruisingReturn.RPM_TOLERANCE)
        assertTrue(frames > 60)
    }

    @Test
    fun throttlePumpsDoNotJumpTheNeedle() {
        val transition = CruisingReturnTransition()
        var rpm = NINTH_REDLINE_RPM
        transition.begin(
            currentRpm = rpm,
            currentGear = 9,
            computedTargetGear = 10,
            initialLiveTargetRpm = 3_800.0,
        )

        val maxStep = max(
            (NINTH_REDLINE_RPM - 3_800.0) / CruisingReturn.REFERENCE_SECONDS,
            CruisingReturn.MIN_GLIDE_RPM_PER_SECOND,
        ) * DT + 1.0

        repeat(80) { frame ->
            val liveTarget = if (frame % 2 == 0) 6_400.0 else 3_600.0
            val previous = rpm
            val step = transition.step(
                dt = DT,
                currentRpm = rpm,
                currentGear = 9,
                shifting = false,
                liveTargetRpm = liveTarget,
                computedTargetGear = 10,
                nextGearCoupledRpm = liveTarget,
            )
            rpm = step.rpm
            assertTrue(
                "frame $frame jumped ${abs(rpm - previous)} rpm",
                abs(rpm - previous) <= maxStep,
            )
        }
    }

    @Test
    fun secondBeginDoesNotRestartTheGlide() {
        val transition = CruisingReturnTransition()
        transition.begin(
            currentRpm = NINTH_REDLINE_RPM,
            currentGear = 9,
            computedTargetGear = 10,
            initialLiveTargetRpm = 3_800.0,
        )
        val afterStart = transition.step(
            dt = DT,
            currentRpm = NINTH_REDLINE_RPM,
            currentGear = 9,
            shifting = false,
            liveTargetRpm = 3_800.0,
            computedTargetGear = 10,
            nextGearCoupledRpm = 3_800.0,
        )
        transition.begin(
            currentRpm = afterStart.rpm,
            currentGear = 9,
            computedTargetGear = 10,
            initialLiveTargetRpm = afterStart.rpm,
        )
        val afterRestart = transition.step(
            dt = DT,
            currentRpm = afterStart.rpm,
            currentGear = 9,
            shifting = false,
            liveTargetRpm = 3_800.0,
            computedTargetGear = 10,
            nextGearCoupledRpm = 3_800.0,
        )

        assertTrue(afterRestart.rpm < afterStart.rpm)
        assertTrue(afterStart.rpm - afterRestart.rpm > 20.0)
    }

    private fun climbedWhileAboveTarget(history: List<Double>, startRpm: Double): Boolean {
        history.zipWithNext().forEach { (previous, next) ->
            if (previous > CRUISING_UPSHIFT_RPM + 200.0 && next > previous + 8.0) {
                return true
            }
        }

        return history.last() > startRpm
    }

    private fun ninthCoupled(speed: Double): Double {
        return (8_200.0 + speed * 500.0).coerceAtMost(8_850.0)
    }

    private fun tenthCoupled(speed: Double): Double {
        return 3_800.0 + speed * 180.0
    }

    private fun coupledAtHighSpeed(gear: Int): Double {
        return when (gear) {
            9 -> NINTH_REDLINE_RPM
            else -> 3_900.0
        }
    }

    private companion object {
        const val DT = 1.0 / 60.0
        const val NINTH_REDLINE_RPM = 8_700.0
        const val CRUISING_UPSHIFT_RPM = 4_850.0
    }
}
