package com.gabrielpc.enginesoundsimulator.simulation

import kotlin.math.PI
import org.junit.Assert.assertEquals
import org.junit.Test

class LoudnessCalibrationStimulusTest {
    @Test
    fun landingRpmUsesRelocatedUpshiftAndAuthoredSecondToFirstRatio() {
        val stimulus = LoudnessCalibrationStimulusFactory.create(
            idleRpm = 1_000.0,
            limiterRpm = 8_000.0,
            automaticUpshiftRpm = 7_000,
            automaticDownshiftRpm = 4_500,
            forwardRatios = listOf(3.2, 2.0, 1.4),
            finalDrive = 4.0,
        )

        assertEquals(4_906.25, stimulus.landingRpm, 0.0001)
        assertEquals(2, stimulus.authoredGear)
        val expectedSpeed = 4_906.25 * 2.0 * PI / 60.0 / (2.0 * 4.0)
        assertEquals(expectedSpeed, stimulus.drivetrainSpeedRadiansPerSecond(stimulus.landingRpm), 0.0001)
    }

    @Test
    fun singleRatioFallsBackToRelocatedDownshiftAndFirstGear() {
        val stimulus = LoudnessCalibrationStimulusFactory.create(
            idleRpm = 1_000.0,
            limiterRpm = 8_000.0,
            automaticUpshiftRpm = 7_000,
            automaticDownshiftRpm = 4_500,
            forwardRatios = listOf(3.2),
            finalDrive = 4.0,
        )

        assertEquals(5_350.0, stimulus.landingRpm, 0.0001)
        assertEquals(1, stimulus.authoredGear)
    }

    @Test
    fun invalidRatiosOrFinalDriveProduceZeroDrivetrainSpeed() {
        val invalidRatio = LoudnessCalibrationStimulusFactory.create(
            idleRpm = 1_000.0,
            limiterRpm = 8_000.0,
            automaticUpshiftRpm = 7_000,
            automaticDownshiftRpm = 4_500,
            forwardRatios = listOf(0.0),
            finalDrive = 4.0,
        )
        val invalidDrive = invalidRatio.copy(authoredRatio = 3.2, finalDrive = 0.0)

        assertEquals(0.0, invalidRatio.drivetrainSpeedRadiansPerSecond(5_000.0), 0.0)
        assertEquals(0.0, invalidDrive.drivetrainSpeedRadiansPerSecond(5_000.0), 0.0)
    }
}
