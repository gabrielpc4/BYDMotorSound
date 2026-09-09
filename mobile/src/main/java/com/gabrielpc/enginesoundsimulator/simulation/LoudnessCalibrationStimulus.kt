package com.gabrielpc.enginesoundsimulator.simulation

import kotlin.math.PI
import kotlin.math.abs

internal data class LoudnessCalibrationStimulus(
    val landingRpm: Double,
    val limiterRpm: Double,
    val authoredGear: Int,
    val authoredRatio: Double?,
    val finalDrive: Double,
) {
    fun drivetrainSpeedRadiansPerSecond(rpm: Double): Double {
        val ratio = authoredRatio?.takeIf { it.isFinite() && abs(it) > MIN_RATIO } ?: return 0.0
        val drive = finalDrive.takeIf { it.isFinite() && abs(it) > MIN_RATIO } ?: return 0.0
        val engineRadiansPerSecond = rpm.coerceAtLeast(0.0) * 2.0 * PI / 60.0

        return engineRadiansPerSecond / abs(ratio * drive)
    }

    private companion object {
        const val MIN_RATIO = 1e-9
    }
}

internal object LoudnessCalibrationStimulusFactory {
    fun create(physics: AssettoPhysics): LoudnessCalibrationStimulus {
        return create(
            idleRpm = physics.engine.idleRpm,
            limiterRpm = physics.engine.limiterRpm,
            automaticUpshiftRpm = physics.drivetrain.automaticUpshiftRpm,
            automaticDownshiftRpm = physics.drivetrain.automaticDownshiftRpm,
            forwardRatios = physics.drivetrain.forwardRatios,
            finalDrive = physics.drivetrain.finalDrive,
        )
    }

    fun create(
        idleRpm: Double,
        limiterRpm: Double,
        automaticUpshiftRpm: Int,
        automaticDownshiftRpm: Int,
        forwardRatios: List<Double>,
        finalDrive: Double,
    ): LoudnessCalibrationStimulus {
        val idle = idleRpm.coerceAtLeast(1.0)
        val limiter = limiterRpm.coerceAtLeast(idle + 1.0)
        val thresholds = AutomaticTransmissionPolicy.relocatedShiftThresholds(
            authoredUpshiftRpm = automaticUpshiftRpm,
            authoredDownshiftRpm = automaticDownshiftRpm,
            limiterRpm = limiter,
            idleRpm = idle,
        )
        val ratios = forwardRatios
        val hasTwoRatios = ratios.size >= 2 && ratios[0].isFinite() && ratios[1].isFinite() &&
            abs(ratios[0]) > MIN_RATIO && abs(ratios[1]) > MIN_RATIO
        val landing = if (hasTwoRatios) {
            thresholds.upshiftRpm * abs(ratios[1] / ratios[0])
        } else {
            thresholds.downshiftRpm
        }.coerceIn(idle, thresholds.upshiftRpm)
        val gear = if (hasTwoRatios) 2 else 1
        val ratio = ratios.getOrNull(gear - 1)?.takeIf { it.isFinite() && abs(it) > MIN_RATIO }

        return LoudnessCalibrationStimulus(
            landingRpm = landing,
            limiterRpm = limiter,
            authoredGear = gear,
            authoredRatio = ratio,
            finalDrive = finalDrive,
        )
    }

    private const val MIN_RATIO = 1e-9
}
