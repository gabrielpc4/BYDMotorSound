package com.gabrielpc.enginesoundsimulator.audio

import com.gabrielpc.enginesoundsimulator.simulation.LoudnessCalibrationStimulus
import com.gabrielpc.enginesoundsimulator.simulation.LoudnessCalibrationStimulusFactory
import com.gabrielpc.enginesoundsimulator.simulation.nativeFmodSpatialCoordinates
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.LockSupport

internal object LoudnessCalibrationStimulusPlayer {
    private const val CONTROL_RATE_HZ = 60L
    private const val CONTROL_PERIOD_NANOS = 1_000_000_000L / CONTROL_RATE_HZ
    private const val SETTLE_DURATION_NANOS = 1_000_000_000L
    private const val SWEEP_DURATION_NANOS = 5_000_000_000L
    private const val SAMPLE_READY_TIMEOUT_NANOS = 30_000_000_000L

    fun playCalibrationSweep(
        bridge: NativeFmodBankBridge,
        bankResolver: FmodBankResolver,
        profile: FmodBankProfile,
        perspective: EngineSoundPerspective,
        masterGainLinear: Float,
        cancellationRequested: AtomicBoolean,
        masterGainProvider: (() -> Float)? = null,
    ) {
        checkCancelled(cancellationRequested)
        val bankFiles = bankResolver.bankFiles(profile)
        val physics = bankResolver.physics(profile)
        bridge.loadCalibrationCar(
            carBankPath = bankFiles.car.absolutePath,
            idleRpm = physics.engine.idleRpm.toFloat(),
            limiterRpm = physics.engine.limiterRpm.toFloat(),
            spatial = physics.nativeFmodSpatialCoordinates(),
        )?.let(::error)
        waitForSampleData(bridge, cancellationRequested)
        bridge.setMasterOutputGain(masterGainLinear)
        bridge.setCalibrationOutputMuted(false)
        val stimulus = LoudnessCalibrationStimulusFactory.create(physics)
        try {
            bridge.beginEngineLoudnessMeasurement(perspective.ordinal)?.let(::error)
            runStimulus(
                bridge = bridge,
                stimulus = stimulus,
                durationNanos = SETTLE_DURATION_NANOS,
                startRpm = stimulus.landingRpm,
                endRpm = stimulus.landingRpm,
                cancellationRequested = cancellationRequested,
                masterGainProvider = masterGainProvider,
            )
            bridge.resetEngineLoudnessMeasurement()
            runStimulus(
                bridge = bridge,
                stimulus = stimulus,
                durationNanos = SWEEP_DURATION_NANOS,
                startRpm = stimulus.landingRpm,
                endRpm = stimulus.limiterRpm,
                cancellationRequested = cancellationRequested,
                masterGainProvider = masterGainProvider,
            )
        } finally {
            bridge.setCalibrationOutputMuted(true)
            bridge.unloadCalibrationCar()
        }
    }

    private fun waitForSampleData(
        bridge: NativeFmodBankBridge,
        cancellationRequested: AtomicBoolean,
    ) {
        val deadline = System.nanoTime() + SAMPLE_READY_TIMEOUT_NANOS
        while (!bridge.engineSampleDataReady()) {
            checkCancelled(cancellationRequested)
            bridge.pumpLoudnessCalibration()?.let(::error)
            if (System.nanoTime() >= deadline) {
                error("Engine sample data did not become ready within 30 seconds.")
            }
            LockSupport.parkNanos(CONTROL_PERIOD_NANOS)
        }
    }

    private fun runStimulus(
        bridge: NativeFmodBankBridge,
        stimulus: LoudnessCalibrationStimulus,
        durationNanos: Long,
        startRpm: Double,
        endRpm: Double,
        cancellationRequested: AtomicBoolean,
        masterGainProvider: (() -> Float)? = null,
    ) {
        val started = System.nanoTime()
        var nextTick = started
        while (true) {
            checkCancelled(cancellationRequested)
            masterGainProvider?.let { provider ->
                bridge.setMasterOutputGain(provider())
            }
            val elapsed = System.nanoTime() - started
            val fraction = (elapsed.toDouble() / durationNanos).coerceIn(0.0, 1.0)
            val rpm = startRpm + (endRpm - startRpm) * fraction
            bridge.updateEngineLoudnessMeasurement(
                rpm = rpm.toFloat(),
                drivetrainSpeed = stimulus.drivetrainSpeedRadiansPerSecond(rpm).toFloat(),
            )?.let(::error)
            bridge.pumpLoudnessCalibration()?.let(::error)
            if (elapsed >= durationNanos) {
                return
            }
            nextTick += CONTROL_PERIOD_NANOS
            LockSupport.parkNanos((nextTick - System.nanoTime()).coerceAtLeast(0L))
        }
    }

    private fun checkCancelled(cancellationRequested: AtomicBoolean) {
        if (cancellationRequested.get() || Thread.currentThread().isInterrupted) {
            throw ManualLoudnessPreviewCancelledException()
        }
    }
}

internal class ManualLoudnessPreviewCancelledException : RuntimeException()
