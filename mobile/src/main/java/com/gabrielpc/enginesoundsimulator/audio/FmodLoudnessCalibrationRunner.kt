package com.gabrielpc.enginesoundsimulator.audio

import android.content.Context
import com.gabrielpc.enginesoundsimulator.BuildConfig
import com.gabrielpc.enginesoundsimulator.simulation.LoudnessCalibrationStimulus
import com.gabrielpc.enginesoundsimulator.simulation.LoudnessCalibrationStimulusFactory
import com.gabrielpc.enginesoundsimulator.simulation.nativeFmodSpatialCoordinates
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.LockSupport

internal data class LoudnessCalibrationRunResult(
    val cancelled: Boolean = false,
    val fatal: Boolean = false,
    val failedCount: Int = 0,
    val lastError: String? = null,
)

internal class FmodLoudnessCalibrationRunner(
    context: Context,
    private val profiles: List<FmodBankProfile>,
    private val repository: LoudnessNormalizationRepository,
    private val resume: Boolean,
    private val cancellationRequested: AtomicBoolean,
    private val onProgress: (LoudnessCalibrationProgress) -> Unit,
) {
    private val appContext = context.applicationContext
    private val bankResolver = FmodBankResolver(appContext)
    private val diagnosticFile = File(appContext.filesDir, DIAGNOSTIC_FILE_NAME)

    fun run(): LoudnessCalibrationRunResult {
        if (profiles.isEmpty()) {
            return LoudnessCalibrationRunResult(
                fatal = true,
                lastError = "No installed cars are available to calibrate.",
            )
        }

        val fingerprintCatalog = buildFingerprints()
        val fingerprints = fingerprintCatalog.fingerprints
        prepareDiagnosticFile()
        val checkpoint = repository.checkpoint().takeIf { resume }
        val resumable = LoudnessCalibrationRecordValidity.resumableCompleted(
            checkpoint = checkpoint,
            records = repository.records(),
            currentFingerprints = fingerprints,
        )
        var processed = resumable.size
        var failed = 0
        var lastError: String? = null
        val total = profiles.size * EngineSoundPerspective.entries.size
        onProgress(
            LoudnessCalibrationProgress(
                status = LoudnessCalibrationStatus.RUNNING,
                completedCount = processed,
                totalCount = total,
            ),
        )

        val bridge = NativeFmodBankBridge()
        var fmodInitialized = false
        try {
            checkCancelled()
            val shared = bankResolver.sharedBankFiles()
            org.fmod.FMOD.init(appContext)
            fmodInitialized = true
            bridge.beginLoudnessCalibration(
                commonStringsBankPath = shared.commonStrings.absolutePath,
                commonBankPath = shared.common.absolutePath,
            )?.let(::error)
            bridge.setMasterOutputGain(1f)
            bridge.setCalibrationOutputMuted(true)

            profiles.forEach { profile ->
                checkCancelled()
                fingerprintCatalog.errorsByProfile[profile.calibrationIdentity()]?.let { error ->
                    val perspectives = EngineSoundPerspective.entries
                    failed += perspectives.size
                    processed += perspectives.size
                    lastError = "${profile.displayName}: $error"
                    reportFailures(
                        profile,
                        perspectives,
                        processed,
                        total,
                        failed,
                        requireNotNull(lastError),
                    )
                    return@forEach
                }
                val pendingPerspectives = EngineSoundPerspective.entries.filter { perspective ->
                    val key = LoudnessCalibrationKey(profile.id, profile.packGroup, perspective)
                    resumable[key] != fingerprints[key]
                }
                if (pendingPerspectives.isEmpty()) return@forEach

                val bankFiles = runCatching { bankResolver.bankFiles(profile) }.getOrElse { error ->
                    val message = "${profile.displayName}: ${error.message ?: error.javaClass.simpleName}"
                    failed += pendingPerspectives.size
                    processed += pendingPerspectives.size
                    lastError = message
                    reportFailures(profile, pendingPerspectives, processed, total, failed, message)
                    return@forEach
                }
                val physics = runCatching { bankResolver.physics(profile) }.getOrElse { error ->
                    val message = "${profile.displayName}: ${error.message ?: error.javaClass.simpleName}"
                    failed += pendingPerspectives.size
                    processed += pendingPerspectives.size
                    lastError = message
                    reportFailures(profile, pendingPerspectives, processed, total, failed, message)
                    return@forEach
                }
                val loadError = bridge.loadCalibrationCar(
                    carBankPath = bankFiles.car.absolutePath,
                    idleRpm = physics.engine.idleRpm.toFloat(),
                    limiterRpm = physics.engine.limiterRpm.toFloat(),
                    spatial = physics.nativeFmodSpatialCoordinates(),
                )
                if (loadError != null) {
                    bridge.unloadCalibrationCar()
                    failed += pendingPerspectives.size
                    processed += pendingPerspectives.size
                    lastError = "${profile.displayName}: $loadError"
                    reportFailures(
                        profile,
                        pendingPerspectives,
                        processed,
                        total,
                        failed,
                        requireNotNull(lastError),
                    )
                    return@forEach
                }

                try {
                    waitForSampleData(bridge)
                    val stimulus = LoudnessCalibrationStimulusFactory.create(physics)
                    pendingPerspectives.forEach { perspective ->
                        checkCancelled()
                        onProgress(
                            LoudnessCalibrationProgress(
                                status = LoudnessCalibrationStatus.RUNNING,
                                activeProfileId = profile.id,
                                activeCarName = profile.displayName,
                                perspective = perspective,
                                completedCount = processed,
                                totalCount = total,
                                failedCount = failed,
                                lastError = lastError,
                            ),
                        )
                        val result = runCatching {
                            measurePairWithRetry(bridge, stimulus, perspective)
                        }
                        processed++
                        result.onSuccess { measurement ->
                            val key = LoudnessCalibrationKey(profile.id, profile.packGroup, perspective)
                            val fingerprint = requireNotNull(fingerprints[key])
                            repository.saveMeasurement(
                                key = key,
                                fingerprint = fingerprint,
                                integratedLufs = measurement.integratedLufs,
                                maxTruePeak = measurement.maximumTruePeak,
                                measuredAtEpochMs = System.currentTimeMillis(),
                                currentFingerprints = fingerprints,
                            )
                            repository.markCheckpointCompleted(key, fingerprint)
                            appendDiagnostic(
                                "profile=${profile.id};packGroup=${profile.packGroup};perspective=${perspective.name};" +
                                    "landingRpm=${stimulus.landingRpm};limiterRpm=${stimulus.limiterRpm};" +
                                    "gear=${stimulus.authoredGear};ratio=${stimulus.authoredRatio};" +
                                    "finalDrive=${stimulus.finalDrive};" +
                                    "${measurement.sourceSummary};lufs=${measurement.integratedLufs};" +
                                    "truePeak=${measurement.maximumTruePeak};result=ok",
                            )
                        }.onFailure { error ->
                            if (error is CalibrationCancelledException) throw error
                            failed++
                            lastError = "${profile.displayName} ${perspective.name}: " +
                                (error.message ?: error.javaClass.simpleName)
                            appendDiagnostic(
                                "profile=${profile.id};packGroup=${profile.packGroup};perspective=${perspective.name};" +
                                    "result=failed;error=${lastError.orEmpty().replace(';', ',')}",
                            )
                        }
                        onProgress(
                            LoudnessCalibrationProgress(
                                status = LoudnessCalibrationStatus.RUNNING,
                                activeProfileId = profile.id,
                                activeCarName = profile.displayName,
                                perspective = perspective,
                                completedCount = processed,
                                totalCount = total,
                                failedCount = failed,
                                lastError = lastError,
                            ),
                        )
                    }
                } catch (cancelled: CalibrationCancelledException) {
                    throw cancelled
                } catch (error: Throwable) {
                    val unprocessed = pendingPerspectives.size
                    failed += unprocessed
                    processed += unprocessed
                    lastError = "${profile.displayName}: ${error.message ?: error.javaClass.simpleName}"
                    reportFailures(
                        profile,
                        pendingPerspectives,
                        processed,
                        total,
                        failed,
                        requireNotNull(lastError),
                    )
                } finally {
                    bridge.unloadCalibrationCar()
                }
            }
            repository.recomputeNormalizations(fingerprints)

            return LoudnessCalibrationRunResult(failedCount = failed, lastError = lastError)
        } catch (_: CalibrationCancelledException) {
            return LoudnessCalibrationRunResult(cancelled = true, failedCount = failed, lastError = lastError)
        } catch (error: Throwable) {
            return LoudnessCalibrationRunResult(
                fatal = true,
                failedCount = failed,
                lastError = error.message ?: error.javaClass.simpleName,
            )
        } finally {
            runCatching(bridge::close)
            if (fmodInitialized) runCatching { org.fmod.FMOD.close() }
        }
    }

    private fun buildFingerprints(): FingerprintCatalog {
        val fingerprints = linkedMapOf<LoudnessCalibrationKey, String>()
        val errors = linkedMapOf<CalibrationProfileIdentity, String>()
        profiles.forEach { profile ->
            runCatching { bankResolver.calibrationFingerprint(profile) }
                .onSuccess { fingerprint ->
                    EngineSoundPerspective.entries.forEach { perspective ->
                        fingerprints[LoudnessCalibrationKey(profile.id, profile.packGroup, perspective)] = fingerprint
                    }
                }
                .onFailure { error ->
                    errors[profile.calibrationIdentity()] = error.message ?: error.javaClass.simpleName
                }
        }

        return FingerprintCatalog(
            fingerprints = fingerprints,
            errorsByProfile = errors,
        )
    }

    private data class FingerprintCatalog(
        val fingerprints: Map<LoudnessCalibrationKey, String>,
        val errorsByProfile: Map<CalibrationProfileIdentity, String>,
    )

    private data class CalibrationProfileIdentity(
        val profileId: String,
        val packGroup: String,
    )

    private fun FmodBankProfile.calibrationIdentity(): CalibrationProfileIdentity =
        CalibrationProfileIdentity(id, packGroup)

    private fun waitForSampleData(bridge: NativeFmodBankBridge) {
        val deadline = System.nanoTime() + SAMPLE_READY_TIMEOUT_NANOS
        while (!bridge.engineSampleDataReady()) {
            checkCancelled()
            bridge.pumpLoudnessCalibration()?.let(::error)
            if (System.nanoTime() >= deadline) {
                error("Engine sample data did not become ready within 30 seconds.")
            }
            LockSupport.parkNanos(CONTROL_PERIOD_NANOS)
        }
    }

    private fun measurePairWithRetry(
        bridge: NativeFmodBankBridge,
        stimulus: LoudnessCalibrationStimulus,
        perspective: EngineSoundPerspective,
    ): PairMeasurement {
        var lastFailure: Throwable? = null
        repeat(MEASUREMENT_ATTEMPTS) {
            checkCancelled()
            runCatching { measurePair(bridge, stimulus, perspective) }
                .onSuccess { return it }
                .onFailure { error ->
                    if (error is CalibrationCancelledException) throw error
                    lastFailure = error
                }
        }

        throw requireNotNull(lastFailure)
    }

    private fun measurePair(
        bridge: NativeFmodBankBridge,
        stimulus: LoudnessCalibrationStimulus,
        perspective: EngineSoundPerspective,
    ): PairMeasurement {
        bridge.beginEngineLoudnessMeasurement(perspective.ordinal)?.let(::error)
        runStimulus(
            bridge = bridge,
            stimulus = stimulus,
            durationNanos = SETTLE_DURATION_NANOS,
            startRpm = stimulus.landingRpm,
            endRpm = stimulus.landingRpm,
        )
        bridge.resetEngineLoudnessMeasurement()
        runStimulus(
            bridge = bridge,
            stimulus = stimulus,
            durationNanos = SWEEP_DURATION_NANOS,
            startRpm = stimulus.landingRpm,
            endRpm = stimulus.limiterRpm,
        )
        val sourceSummary = bridge.calibrationSourceSummary()
        val expectedEvent = if (perspective == EngineSoundPerspective.EXTERIOR) "engine_ext" else "engine_int"
        val expectedPure = if (perspective == EngineSoundPerspective.EXTERIOR) "1" else "0"
        check(
            sourceSummary.contains("event=$expectedEvent") &&
                sourceSummary.contains("eventSlots=1") &&
                sourceSummary.contains("coreOverrideChannels=0") &&
                sourceSummary.contains("masterGain=1.000000") &&
                sourceSummary.contains("outputMuted=1") &&
                sourceSummary.contains("exteriorPure=$expectedPure")
        ) {
            "Calibration source isolation failed: $sourceSummary"
        }
        val values = bridge.engineLoudnessMeasurement()
        check(values.size >= 3 && values[2] > 0.5) {
            "FMOD returned no valid integrated loudness measurement " +
                "(LUFS=${values.getOrNull(0)}, dBTP=${values.getOrNull(1)}; $sourceSummary)."
        }

        return PairMeasurement(
            integratedLufs = values[0],
            maximumTruePeak = values[1],
            sourceSummary = sourceSummary,
        )
    }

    private fun runStimulus(
        bridge: NativeFmodBankBridge,
        stimulus: LoudnessCalibrationStimulus,
        durationNanos: Long,
        startRpm: Double,
        endRpm: Double,
    ) {
        val started = System.nanoTime()
        var nextTick = started
        while (true) {
            checkCancelled()
            val elapsed = System.nanoTime() - started
            val fraction = (elapsed.toDouble() / durationNanos).coerceIn(0.0, 1.0)
            val rpm = startRpm + (endRpm - startRpm) * fraction
            bridge.updateEngineLoudnessMeasurement(
                rpm = rpm.toFloat(),
                drivetrainSpeed = stimulus.drivetrainSpeedRadiansPerSecond(rpm).toFloat(),
            )?.let(::error)
            if (elapsed >= durationNanos) return
            nextTick += CONTROL_PERIOD_NANOS
            LockSupport.parkNanos((nextTick - System.nanoTime()).coerceAtLeast(0L))
        }
    }

    private fun reportFailures(
        profile: FmodBankProfile,
        perspectives: List<EngineSoundPerspective>,
        processed: Int,
        total: Int,
        failed: Int,
        message: String,
    ) {
        perspectives.forEach { perspective ->
            appendDiagnostic(
                "profile=${profile.id};packGroup=${profile.packGroup};perspective=${perspective.name};result=failed;" +
                    "error=${message.replace(';', ',')}",
            )
        }
        onProgress(
            LoudnessCalibrationProgress(
                status = LoudnessCalibrationStatus.RUNNING,
                activeProfileId = profile.id,
                activeCarName = profile.displayName,
                perspective = perspectives.lastOrNull(),
                completedCount = processed,
                totalCount = total,
                failedCount = failed,
                lastError = message,
            ),
        )
    }

    private fun checkCancelled() {
        if (cancellationRequested.get() || Thread.currentThread().isInterrupted) {
            throw CalibrationCancelledException()
        }
    }

    private fun appendDiagnostic(line: String) {
        if (!BuildConfig.DEBUG) return
        runCatching { diagnosticFile.appendText("${System.currentTimeMillis()};$line\n") }
    }

    private fun prepareDiagnosticFile() {
        if (!BuildConfig.DEBUG || resume) return

        runCatching {
            diagnosticFile.writeText(
                "${System.currentTimeMillis()};batch=start;profiles=${profiles.size};" +
                    "pairs=${profiles.size * EngineSoundPerspective.entries.size}\n",
            )
        }
    }

    private data class PairMeasurement(
        val integratedLufs: Double,
        val maximumTruePeak: Double,
        val sourceSummary: String,
    )

    private class CalibrationCancelledException : RuntimeException()

    private companion object {
        const val DIAGNOSTIC_FILE_NAME = "loudness-calibration-debug.log"
        const val CONTROL_RATE_HZ = 60L
        const val CONTROL_PERIOD_NANOS = 1_000_000_000L / CONTROL_RATE_HZ
        const val SETTLE_DURATION_NANOS = 1_000_000_000L
        const val SWEEP_DURATION_NANOS = 5_000_000_000L
        const val SAMPLE_READY_TIMEOUT_NANOS = 30_000_000_000L
        const val MEASUREMENT_ATTEMPTS = 2
    }
}
