package com.gabrielpc.enginesoundsimulator.audio

import android.content.Context
import android.media.AudioManager
import android.os.SystemClock
import com.gabrielpc.enginesoundsimulator.simulation.LoudnessCalibrationStimulus
import com.gabrielpc.enginesoundsimulator.simulation.LoudnessCalibrationStimulusFactory
import com.gabrielpc.enginesoundsimulator.simulation.nativeFmodSpatialCoordinates
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.LockSupport
import kotlin.math.abs

internal data class AcousticDiagnosticRunResult(
    val cancelled: Boolean = false,
    val interrupted: Boolean = false,
    val fatal: Boolean = false,
    val completedCount: Int = 0,
    val skippedCount: Int = 0,
    val failedCount: Int = 0,
    val lastError: String? = null,
)

internal class FmodAcousticDiagnosticRunner(
    context: Context,
    profiles: List<FmodBankProfile>,
    private val normalizationRepository: LoudnessNormalizationRepository,
    private val diagnosticRepository: AcousticDiagnosticRepository,
    private val meterRepository: IphoneAcousticMeterRepository,
    private val deviceAddress: String?,
    private val pairingCode: String?,
    private val resume: Boolean,
    private val cancellationRequested: AtomicBoolean,
    private val onProgress: (AcousticDiagnosticProgress) -> Unit,
    private val onRecordsChanged: () -> Unit,
) {
    private val appContext = context.applicationContext
    private val profiles = profiles.filter { it.packGroup == FmodBankProfiles.moddedCarsPackId }
    private val bankResolver = FmodBankResolver(appContext)
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val measurementSequence = AtomicLong(1L)

    fun run(): AcousticDiagnosticRunResult {
        if (profiles.isEmpty()) {
            return AcousticDiagnosticRunResult(fatal = true, lastError = "No installed cars are available.")
        }
        val targets = buildTargets()
        val total = profiles.size * EngineSoundPerspective.entries.size
        val skipped = total - targets.size
        val checkpoint = if (resume) {
            diagnosticRepository.checkpoint()
                ?: return AcousticDiagnosticRunResult(fatal = true, lastError = "No interrupted acoustic session exists.")
        } else {
            diagnosticRepository.beginCheckpoint()
        }
        val completedFingerprints = checkpoint.completedFingerprints
        val pending = targets.filter { target ->
            completedFingerprints[target.key] != target.diagnosticFingerprint
        }
        var completed = completedFingerprints.count { (key, fingerprint) ->
            targets.any { it.key == key && it.diagnosticFingerprint == fingerprint }
        }
        val targetFingerprints = targets.associate { it.key to it.diagnosticFingerprint }
        var failed = diagnosticRepository.records(checkpoint.sessionId).count { record ->
            !record.valid && completedFingerprints[record.key] == targetFingerprints[record.key]
        }
        var lastError: String? = null
        if (targets.isEmpty()) {
            diagnosticRepository.clearCheckpoint()
            return AcousticDiagnosticRunResult(
                fatal = true,
                skippedCount = skipped,
                lastError = "No car/perspective pair has a valid LUFS normalization.",
            )
        }
        if (pending.isEmpty()) {
            diagnosticRepository.completeSession(checkpoint.sessionId)
            onRecordsChanged()
            return AcousticDiagnosticRunResult(
                completedCount = completed,
                skippedCount = skipped,
                failedCount = diagnosticRepository.records(checkpoint.sessionId).count { !it.valid },
            )
        }
        onProgress(
            AcousticDiagnosticProgress(
                status = if (meterRepository.loadLink() == null && pairingCode != null) {
                    AcousticDiagnosticStatus.PAIRING
                } else {
                    AcousticDiagnosticStatus.CONNECTING
                },
                completedCount = completed,
                totalCount = total,
                skippedCount = skipped,
            ),
        )

        val bridge = NativeFmodBankBridge()
        val meter = IphoneAcousticMeterClient(appContext, meterRepository, cancellationRequested)
        var fmodInitialized = false
        var connectedMeter: ConnectedIphoneMeter? = null
        try {
            checkCancelled()
            connectedMeter = meter.connectAndAuthenticate(deviceAddress, pairingCode)
            onProgress(
                AcousticDiagnosticProgress(
                    status = AcousticDiagnosticStatus.PREPARING,
                    meterName = connectedMeter.model,
                    completedCount = completed,
                    totalCount = total,
                    skippedCount = skipped,
                ),
            )
            val shared = bankResolver.sharedBankFiles()
            try {
                org.fmod.FMOD.init(appContext)
                fmodInitialized = true
            } catch (error: Throwable) {
                throw AcousticDiagnosticFatalException(
                    "FMOD could not initialize for acoustic measurement.",
                    error,
                )
            }
            bridge.beginLoudnessCalibration(
                commonStringsBankPath = shared.commonStrings.absolutePath,
                commonBankPath = shared.common.absolutePath,
            )?.let { throw AcousticDiagnosticFatalException(it) }
            bridge.setMasterOutputGain(1f)
            bridge.setCalibrationOutputMuted(true)
            val latency = measureAcousticLatency(bridge, meter)
            val resumedVolume = diagnosticRepository.records(checkpoint.sessionId)
                .firstOrNull()?.androidMediaVolumeIndex
            val expectedMediaVolume = resumedVolume
                ?: audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            var setupLevelAccepted = completed > 0

            for ((profile, profileTargets) in pending.groupBy { it.profile }) {
                checkCancelled()
                val persistedTargets = mutableSetOf<LoudnessCalibrationKey>()
                try {
                    val files = bankResolver.bankFiles(profile)
                    val physics = bankResolver.physics(profile)
                    bridge.loadCalibrationCar(
                        carBankPath = files.car.absolutePath,
                        idleRpm = physics.engine.idleRpm.toFloat(),
                        limiterRpm = physics.engine.limiterRpm.toFloat(),
                        spatial = physics.nativeFmodSpatialCoordinates(),
                    )?.let(::error)
                    waitForSampleData(bridge)
                    val stimulus = LoudnessCalibrationStimulusFactory.create(physics)
                    profileTargets.forEach { target ->
                        checkCancelled()
                        onProgress(
                            AcousticDiagnosticProgress(
                                status = AcousticDiagnosticStatus.RUNNING,
                                meterName = connectedMeter.model,
                                activeCarName = profile.displayName,
                                perspective = target.key.perspective,
                                completedCount = completed,
                                totalCount = total,
                                skippedCount = skipped,
                                failedCount = failed,
                                lastError = lastError,
                            ),
                        )
                        val record = measureWithRetry(
                            bridge = bridge,
                            meter = meter,
                            meterInfo = connectedMeter,
                            sessionId = checkpoint.sessionId,
                            profile = profile,
                            target = target,
                            stimulus = stimulus,
                            latency = latency,
                            expectedMediaVolume = expectedMediaVolume,
                        )
                        if (!setupLevelAccepted) {
                            check(
                                record.valid && record.snrDb >= SETUP_MINIMUM_SNR_DB &&
                                    record.peakDbFs <= SETUP_SAFE_PEAK_DB_FS
                            ) {
                                "Adjust the vehicle media volume: the reference needs at least 15 dB SNR " +
                                    "and a peak no higher than -3 dBFS (measured %.1f dB SNR, %.1f dBFS)."
                                        .format(record.snrDb, record.peakDbFs)
                            }
                            setupLevelAccepted = true
                        }
                        diagnosticRepository.saveRecord(record)
                        persistedTargets += target.key
                        onRecordsChanged()
                        completed++
                        if (!record.valid) {
                            failed++
                            lastError = "${profile.displayName} ${target.key.perspective.name}: ${record.failureReason}"
                        }
                    }
                } catch (error: Throwable) {
                    if (error is AcousticDiagnosticCancelledException || Thread.currentThread().isInterrupted) {
                        throw error
                    }
                    val reason = error.message ?: error.javaClass.simpleName
                    profileTargets.filterNot { it.key in persistedTargets }.forEach { target ->
                        val record = failedRecord(
                            meterInfo = connectedMeter,
                            sessionId = checkpoint.sessionId,
                            target = target,
                            latency = latency,
                            reason = reason,
                        )
                        diagnosticRepository.saveRecord(record)
                        onRecordsChanged()
                        completed++
                        failed++
                    }
                    lastError = "${profile.displayName}: $reason"
                } finally {
                    bridge.setCalibrationOutputMuted(true)
                    bridge.unloadCalibrationCar()
                }
            }
            diagnosticRepository.completeSession(checkpoint.sessionId)
            onRecordsChanged()

            return AcousticDiagnosticRunResult(
                completedCount = completed,
                skippedCount = skipped,
                failedCount = failed,
                lastError = lastError,
            )
        } catch (_: AcousticDiagnosticCancelledException) {
            meter.cancelRemote()
            return AcousticDiagnosticRunResult(
                cancelled = true,
                completedCount = completed,
                skippedCount = skipped,
                failedCount = failed,
                lastError = lastError,
            )
        } catch (error: AcousticDiagnosticFatalException) {
            meter.cancelRemote()
            return AcousticDiagnosticRunResult(
                fatal = true,
                completedCount = completed,
                skippedCount = skipped,
                failedCount = failed,
                lastError = error.message ?: "FMOD acoustic measurement initialization failed.",
            )
        } catch (error: Throwable) {
            meter.cancelRemote()
            return AcousticDiagnosticRunResult(
                interrupted = true,
                completedCount = completed,
                skippedCount = skipped,
                failedCount = failed,
                lastError = error.message ?: error.javaClass.simpleName,
            )
        } finally {
            runCatching { bridge.setCalibrationOutputMuted(true) }
            runCatching(bridge::close)
            runCatching(meter::close)
            if (fmodInitialized) runCatching { org.fmod.FMOD.close() }
        }
    }

    private fun buildTargets(): List<Target> {
        val records = normalizationRepository.records().associateBy(LoudnessCalibrationRecord::key)
        return buildList {
            profiles.forEach { profile ->
                val fingerprint = runCatching { bankResolver.calibrationFingerprint(profile) }.getOrNull()
                    ?: return@forEach
                EngineSoundPerspective.entries.forEach { perspective ->
                    val key = LoudnessCalibrationKey(profile.id, profile.packGroup, perspective)
                    val record = records[key]
                    if (!LoudnessCalibrationRecordValidity.isValid(record, fingerprint)) return@forEach
                    val normalizationDb = requireNotNull(record).normalizationDb
                    add(
                        Target(
                            profile = profile,
                            key = key,
                            bankFingerprint = fingerprint,
                            normalizationDb = normalizationDb,
                            diagnosticFingerprint = AcousticDiagnosticRepository.diagnosticFingerprint(
                                fingerprint,
                                normalizationDb,
                            ),
                        ),
                    )
                }
            }
        }.sortedBy { abs(it.normalizationDb) }
    }

    private fun measureAcousticLatency(
        bridge: NativeFmodBankBridge,
        meter: IphoneAcousticMeterClient,
    ): AcousticLatencyMeasurement {
        val alignment = meter.synchronizeClocks()
        val measurementId = measurementSequence.getAndIncrement()
        val firstChirpAndroid = SystemClock.elapsedRealtimeNanos() + ARM_LEAD_NANOS
        val chirpAndroidTimes = LongArray(IphoneAcousticMeterProtocol.CHIRP_COUNT) { index ->
            firstChirpAndroid + index * CHIRP_INTERVAL_NANOS
        }
        val chirpIphoneTimes = chirpAndroidTimes.map(alignment::iphoneTime).toLongArray()
        meter.armLatency(
            IphoneAcousticMeterProtocol.ArmLatency(
                sequence = nextProtocolSequence(),
                measurementId = measurementId,
                chirpTimesNanos = chirpIphoneTimes,
            ),
        )
        bridge.setMasterOutputGain(1f)
        bridge.setCalibrationOutputMuted(false)
        try {
            chirpAndroidTimes.forEach { scheduled ->
                waitUntil(scheduled, meter)
                bridge.playAcousticLatencyChirp()?.let(::error)
                bridge.pumpLoudnessCalibration().takeIf { it != null && !it.contains("car is not loaded") }
                    ?.let(::error)
            }
            waitUntil(chirpAndroidTimes.last() + CHIRP_CAPTURE_TAIL_NANOS, meter)
        } finally {
            bridge.setCalibrationOutputMuted(true)
        }
        val result = meter.awaitLatency(measurementId)
        val latencies = result.detectedTimesNanos.zip(chirpIphoneTimes.toList()) { detected, scheduled ->
            detected - scheduled
        }.filter { it in MIN_ACOUSTIC_LATENCY_NANOS..MAX_ACOUSTIC_LATENCY_NANOS }
        val latency = medianLong(latencies)
            ?: error("The iPhone could not identify all three acoustic chirps.")
        if (result.confidence < MINIMUM_CHIRP_CONFIDENCE) {
            error("Acoustic latency chirps were too weak or noisy.")
        }

        return AcousticLatencyMeasurement(latency, result.confidence)
    }

    private fun measureWithRetry(
        bridge: NativeFmodBankBridge,
        meter: IphoneAcousticMeterClient,
        meterInfo: ConnectedIphoneMeter,
        sessionId: String,
        profile: FmodBankProfile,
        target: Target,
        stimulus: LoudnessCalibrationStimulus,
        latency: AcousticLatencyMeasurement,
        expectedMediaVolume: Int,
    ): AcousticDiagnosticRecord {
        var lastRecord: AcousticDiagnosticRecord? = null
        var lastFailure: Throwable? = null
        repeat(MEASUREMENT_ATTEMPTS) {
            checkCancelled()
            runCatching {
                measurePair(
                    bridge,
                    meter,
                    meterInfo,
                    sessionId,
                    profile,
                    target,
                    stimulus,
                    latency,
                    expectedMediaVolume,
                )
            }.onSuccess { record ->
                if (record.valid) return record
                lastRecord = record
            }.onFailure { error ->
                if (error is AcousticDiagnosticCancelledException) throw error
                lastFailure = error
            }
        }

        return lastRecord ?: failedRecord(
            meterInfo = meterInfo,
            sessionId = sessionId,
            target = target,
            latency = latency,
            reason = lastFailure?.message ?: "The acoustic measurement failed twice.",
        )
    }

    private fun measurePair(
        bridge: NativeFmodBankBridge,
        meter: IphoneAcousticMeterClient,
        meterInfo: ConnectedIphoneMeter,
        sessionId: String,
        profile: FmodBankProfile,
        target: Target,
        stimulus: LoudnessCalibrationStimulus,
        latency: AcousticLatencyMeasurement,
        expectedMediaVolume: Int,
    ): AcousticDiagnosticRecord {
        bridge.setCalibrationOutputMuted(true)
        bridge.setMasterOutputGain(LoudnessNormalizationMath.dbToLinear(target.normalizationDb).toFloat())
        bridge.beginEngineLoudnessMeasurement(target.key.perspective.ordinal)?.let(::error)
        bridge.updateEngineLoudnessMeasurement(
            stimulus.landingRpm.toFloat(),
            stimulus.drivetrainSpeedRadiansPerSecond(stimulus.landingRpm).toFloat(),
        )?.let(::error)
        val sourceSummary = bridge.calibrationSourceSummary()
        requireIsolatedSource(sourceSummary, target.key.perspective)

        val alignment = meter.synchronizeClocks()
        val volumeMonitor = MediaVolumeMonitor(audioManager, expectedMediaVolume)
        volumeMonitor.observe(force = true)
        val measurementId = measurementSequence.getAndIncrement()
        val ambientStartAndroid = SystemClock.elapsedRealtimeNanos() + ARM_LEAD_NANOS
        val engineStartAndroid = ambientStartAndroid + AMBIENT_DURATION_NANOS
        val sweepStartAndroid = engineStartAndroid + LANDING_DURATION_NANOS
        val sweepEndAndroid = sweepStartAndroid + SWEEP_DURATION_NANOS
        val engineEndAndroid = sweepEndAndroid + latency.latencyNanos
        val acousticShift = latency.latencyNanos
        val arm = IphoneAcousticMeterProtocol.ArmMeasurement(
            sequence = nextProtocolSequence(),
            measurementId = measurementId,
            ambientBeforeStartNanos = alignment.iphoneTime(ambientStartAndroid) + acousticShift,
            engineStartNanos = alignment.iphoneTime(engineStartAndroid) + acousticShift,
            sweepStartNanos = alignment.iphoneTime(sweepStartAndroid) + acousticShift,
            sweepEndNanos = alignment.iphoneTime(sweepEndAndroid) + acousticShift,
            engineEndNanos = alignment.iphoneTime(engineEndAndroid) + acousticShift,
            ambientAfterEndNanos = alignment.iphoneTime(engineEndAndroid) + acousticShift + AMBIENT_DURATION_NANOS,
            carName = profile.displayName,
            perspective = target.key.perspective,
        )
        meter.armMeasurement(arm)
        waitUntil(engineStartAndroid, meter, volumeMonitor)
        bridge.setCalibrationOutputMuted(false)
        try {
            runStimulus(
                bridge,
                meter,
                stimulus,
                startNanos = engineStartAndroid,
                endNanos = sweepStartAndroid,
                startRpm = stimulus.landingRpm,
                endRpm = stimulus.landingRpm,
                volumeMonitor = volumeMonitor,
            )
            runStimulus(
                bridge,
                meter,
                stimulus,
                startNanos = sweepStartAndroid,
                endNanos = sweepEndAndroid,
                startRpm = stimulus.landingRpm,
                endRpm = stimulus.limiterRpm,
                volumeMonitor = volumeMonitor,
            )
            runStimulus(
                bridge,
                meter,
                stimulus,
                startNanos = sweepEndAndroid,
                endNanos = engineEndAndroid,
                startRpm = stimulus.limiterRpm,
                endRpm = stimulus.limiterRpm,
                volumeMonitor = volumeMonitor,
            )
        } finally {
            bridge.setCalibrationOutputMuted(true)
        }
        waitUntil(
            engineEndAndroid + acousticShift + AMBIENT_DURATION_NANOS,
            meter,
            volumeMonitor,
        )
        val metrics = meter.awaitMeasurement(measurementId, MEASUREMENT_RESULT_TIMEOUT_SECONDS)
        val evaluated = AcousticDiagnosticMath.evaluate(metrics)
        val volume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val volumeMax = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        volumeMonitor.observe(force = true)
        val evaluation = if (!volumeMonitor.changed) {
            evaluated
        } else {
            evaluated.copy(
                valid = false,
                failureReason = "Android media volume changed during the batch " +
                    "(expected index $expectedMediaVolume; final index $volume).",
            )
        }

        return AcousticDiagnosticRecord(
            sessionId = sessionId,
            profileId = target.key.profileId,
            packGroup = target.key.packGroup,
            perspective = target.key.perspective,
            algorithmVersion = ACOUSTIC_DIAGNOSTIC_ALGORITHM_VERSION,
            bankFingerprint = target.bankFingerprint,
            normalizationDb = target.normalizationDb,
            meterInstanceId = meterInfo.instanceId,
            meterModel = meterInfo.model,
            correctedWeightedDb = evaluation.correctedWeightedDb,
            deltaFromMedianDb = 0.0,
            acousticAdjustmentDb = 0.0,
            snrDb = evaluation.snrDb,
            peakDbFs = evaluation.peakDbFs,
            presenceBalanceDb = evaluation.presenceBalanceDb,
            ambientBeforeDb = evaluation.ambientBeforeDb,
            ambientAfterDb = evaluation.ambientAfterDb,
            clockUncertaintyMs = alignment.uncertaintyNanos / 1e6,
            acousticLatencyMs = latency.latencyNanos / 1e6,
            androidMediaVolumeIndex = volume,
            androidMediaVolumeMax = volumeMax,
            sampleCount = metrics.sampleCount,
            sampleRateHz = metrics.sampleRateHz,
            channelCount = metrics.channelCount,
            channelBalanceDb = metrics.channelBalanceDb,
            valid = evaluation.valid,
            failureReason = evaluation.failureReason,
            measuredAtEpochMs = System.currentTimeMillis(),
        )
    }

    private fun failedRecord(
        meterInfo: ConnectedIphoneMeter,
        sessionId: String,
        target: Target,
        latency: AcousticLatencyMeasurement,
        reason: String,
    ): AcousticDiagnosticRecord = AcousticDiagnosticRecord(
        sessionId = sessionId,
        profileId = target.key.profileId,
        packGroup = target.key.packGroup,
        perspective = target.key.perspective,
        algorithmVersion = ACOUSTIC_DIAGNOSTIC_ALGORITHM_VERSION,
        bankFingerprint = target.bankFingerprint,
        normalizationDb = target.normalizationDb,
        meterInstanceId = meterInfo.instanceId,
        meterModel = meterInfo.model,
        correctedWeightedDb = -150.0,
        deltaFromMedianDb = 0.0,
        acousticAdjustmentDb = 0.0,
        snrDb = 0.0,
        peakDbFs = -150.0,
        presenceBalanceDb = 0.0,
        ambientBeforeDb = -150.0,
        ambientAfterDb = -150.0,
        clockUncertaintyMs = 0.0,
        acousticLatencyMs = latency.latencyNanos / 1e6,
        androidMediaVolumeIndex = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC),
        androidMediaVolumeMax = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC),
        sampleCount = 0L,
        sampleRateHz = 0.0,
        channelCount = 0,
        channelBalanceDb = null,
        valid = false,
        failureReason = reason,
        measuredAtEpochMs = System.currentTimeMillis(),
    )

    private fun requireIsolatedSource(summary: String, perspective: EngineSoundPerspective) {
        val expectedEvent = if (perspective == EngineSoundPerspective.EXTERIOR) "engine_ext" else "engine_int"
        val expectedPure = if (perspective == EngineSoundPerspective.EXTERIOR) "1" else "0"
        check(
            summary.contains("event=$expectedEvent") &&
                summary.contains("eventSlots=1") &&
                summary.contains("coreOverrideChannels=0") &&
                summary.contains("exteriorPure=$expectedPure")
        ) {
            "Acoustic source isolation failed: $summary"
        }
    }

    private fun waitForSampleData(bridge: NativeFmodBankBridge) {
        val deadline = SystemClock.elapsedRealtimeNanos() + SAMPLE_READY_TIMEOUT_NANOS
        while (!bridge.engineSampleDataReady()) {
            checkCancelled()
            bridge.pumpLoudnessCalibration()?.let(::error)
            if (SystemClock.elapsedRealtimeNanos() >= deadline) {
                error("Engine sample data did not become ready within 30 seconds.")
            }
            LockSupport.parkNanos(CONTROL_PERIOD_NANOS)
        }
    }

    private fun runStimulus(
        bridge: NativeFmodBankBridge,
        meter: IphoneAcousticMeterClient,
        stimulus: LoudnessCalibrationStimulus,
        startNanos: Long,
        endNanos: Long,
        startRpm: Double,
        endRpm: Double,
        volumeMonitor: MediaVolumeMonitor,
    ) {
        var nextTick = startNanos
        waitUntil(startNanos, meter, volumeMonitor)
        while (true) {
            checkCancelled()
            meter.ensureConnected()
            volumeMonitor.observe()
            val now = SystemClock.elapsedRealtimeNanos()
            val fraction = ((now - startNanos).toDouble() / (endNanos - startNanos).coerceAtLeast(1L))
                .coerceIn(0.0, 1.0)
            val rpm = startRpm + (endRpm - startRpm) * fraction
            bridge.updateEngineLoudnessMeasurement(
                rpm.toFloat(),
                stimulus.drivetrainSpeedRadiansPerSecond(rpm).toFloat(),
            )?.let(::error)
            if (now >= endNanos) return
            nextTick += CONTROL_PERIOD_NANOS
            LockSupport.parkNanos((nextTick - SystemClock.elapsedRealtimeNanos()).coerceAtLeast(0L))
        }
    }

    private fun waitUntil(
        targetNanos: Long,
        meter: IphoneAcousticMeterClient,
        volumeMonitor: MediaVolumeMonitor? = null,
    ) {
        while (true) {
            checkCancelled()
            meter.ensureConnected()
            volumeMonitor?.observe()
            val remaining = targetNanos - SystemClock.elapsedRealtimeNanos()
            if (remaining <= 0L) return
            LockSupport.parkNanos(remaining.coerceAtMost(CONTROL_PERIOD_NANOS))
        }
    }

    private fun checkCancelled() {
        if (cancellationRequested.get() || Thread.currentThread().isInterrupted) {
            throw AcousticDiagnosticCancelledException()
        }
    }

    private fun medianLong(values: List<Long>): Long? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()

        return sorted[sorted.size / 2]
    }

    private fun nextProtocolSequence(): Int = (measurementSequence.getAndIncrement() and 0xffff).toInt()

    private data class Target(
        val profile: FmodBankProfile,
        val key: LoudnessCalibrationKey,
        val bankFingerprint: String,
        val normalizationDb: Double,
        val diagnosticFingerprint: String,
    )

    private class MediaVolumeMonitor(
        private val audioManager: AudioManager,
        private val expectedIndex: Int,
    ) {
        var changed = false
            private set
        private var nextCheckNanos = 0L

        fun observe(force: Boolean = false) {
            val now = SystemClock.elapsedRealtimeNanos()
            if (!force && now < nextCheckNanos) return
            if (audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) != expectedIndex) changed = true
            nextCheckNanos = now + MEDIA_VOLUME_CHECK_PERIOD_NANOS
        }
    }

    private companion object {
        const val CONTROL_RATE_HZ = 60L
        const val CONTROL_PERIOD_NANOS = 1_000_000_000L / CONTROL_RATE_HZ
        const val AMBIENT_DURATION_NANOS = 1_000_000_000L
        const val LANDING_DURATION_NANOS = 1_000_000_000L
        const val SWEEP_DURATION_NANOS = 5_000_000_000L
        const val ARM_LEAD_NANOS = 2_000_000_000L
        const val CHIRP_INTERVAL_NANOS = 900_000_000L
        const val CHIRP_CAPTURE_TAIL_NANOS = 600_000_000L
        const val MIN_ACOUSTIC_LATENCY_NANOS = 5_000_000L
        const val MAX_ACOUSTIC_LATENCY_NANOS = 1_000_000_000L
        const val MINIMUM_CHIRP_CONFIDENCE = 0.55
        const val SAMPLE_READY_TIMEOUT_NANOS = 30_000_000_000L
        const val MEASUREMENT_RESULT_TIMEOUT_SECONDS = 15L
        const val MEASUREMENT_ATTEMPTS = 2
        const val MEDIA_VOLUME_CHECK_PERIOD_NANOS = 250_000_000L
        const val SETUP_MINIMUM_SNR_DB = 15.0
        const val SETUP_SAFE_PEAK_DB_FS = -3.0
    }
}

private class AcousticDiagnosticFatalException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
