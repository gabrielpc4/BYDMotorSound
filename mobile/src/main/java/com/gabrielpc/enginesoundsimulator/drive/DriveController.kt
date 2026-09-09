package com.gabrielpc.enginesoundsimulator.drive

import android.content.Context
import com.gabrielpc.enginesoundsimulator.RuntimeFeatureFlags
import android.os.Debug
import android.os.Process
import android.os.SystemClock
import com.gabrielpc.enginesoundsimulator.audio.AudioFocusEvent
import com.gabrielpc.enginesoundsimulator.audio.EngineAudioEngine
import com.gabrielpc.enginesoundsimulator.audio.EngineAudioFrame
import com.gabrielpc.enginesoundsimulator.audio.EngineSoundPerspective
import com.gabrielpc.enginesoundsimulator.audio.EngineSoundPerspectiveRepository
import com.gabrielpc.enginesoundsimulator.audio.FmodBankProfile
import com.gabrielpc.enginesoundsimulator.audio.FmodBankProfiles
import com.gabrielpc.enginesoundsimulator.audio.FmodBankResolver
import com.gabrielpc.enginesoundsimulator.audio.FmodBankImportResult
import com.gabrielpc.enginesoundsimulator.audio.FmodSourceState
import com.gabrielpc.enginesoundsimulator.audio.FmodUpdateRate
import com.gabrielpc.enginesoundsimulator.audio.FmodUpdateRateRepository
import com.gabrielpc.enginesoundsimulator.audio.ExteriorAudioModeRepository
import com.gabrielpc.enginesoundsimulator.audio.MediaShiftButtonCoordinator
import com.gabrielpc.enginesoundsimulator.audio.AudioMixGains
import com.gabrielpc.enginesoundsimulator.audio.MixerGlobalGainRepository
import com.gabrielpc.enginesoundsimulator.audio.MixerCarSpecificGainRepository
import com.gabrielpc.enginesoundsimulator.diagnostics.DriveSessionCapture
import com.gabrielpc.enginesoundsimulator.audio.MixerCarSpecificGains
import com.gabrielpc.enginesoundsimulator.audio.MixerGlobalGains
import com.gabrielpc.enginesoundsimulator.audio.AppVolumeRepository
import com.gabrielpc.enginesoundsimulator.audio.AppVolumeSettings
import com.gabrielpc.enginesoundsimulator.audio.LoudnessCalibrationKey
import com.gabrielpc.enginesoundsimulator.audio.LoudnessCalibrationProgress
import com.gabrielpc.enginesoundsimulator.audio.LoudnessCalibrationStatus
import com.gabrielpc.enginesoundsimulator.audio.LoudnessNormalizationRepository
import com.gabrielpc.enginesoundsimulator.audio.LoudnessNormalizationState
import com.gabrielpc.enginesoundsimulator.audio.LoudnessNormalizationSummary
import com.gabrielpc.enginesoundsimulator.audio.composeMasterOutputGain
import com.gabrielpc.enginesoundsimulator.audio.effectiveCategoryGains
import com.gabrielpc.enginesoundsimulator.audio.effectiveEffectsHostForOverrides
import com.gabrielpc.enginesoundsimulator.audio.effectiveEngineIdleGain
import com.gabrielpc.enginesoundsimulator.audio.effectiveHostGains
import com.gabrielpc.enginesoundsimulator.drive.effectiveEffectSoundOverrideGains
import com.gabrielpc.enginesoundsimulator.AppPreferenceStores
import com.gabrielpc.enginesoundsimulator.audio.SelectedCarRepository
import com.gabrielpc.enginesoundsimulator.diagnostics.DebugScenarioOverride
import com.gabrielpc.enginesoundsimulator.diagnostics.DebugTelemetry
import com.gabrielpc.enginesoundsimulator.simulation.AssettoPhysics
import com.gabrielpc.enginesoundsimulator.simulation.DriverInput
import com.gabrielpc.enginesoundsimulator.simulation.DrivetrainState
import com.gabrielpc.enginesoundsimulator.simulation.EngineSimulation
import com.gabrielpc.enginesoundsimulator.simulation.ShiftDirection
import com.gabrielpc.enginesoundsimulator.simulation.SimulationMotionContinuity
import com.gabrielpc.enginesoundsimulator.simulation.AutomaticTransmissionMode
import com.gabrielpc.enginesoundsimulator.simulation.TransmissionPosition
import com.gabrielpc.enginesoundsimulator.simulation.VirtualGearProfile
import com.gabrielpc.enginesoundsimulator.simulation.resolveDriveInput
import com.gabrielpc.enginesoundsimulator.telemetry.BydSpeedReader
import com.gabrielpc.enginesoundsimulator.telemetry.TelemetrySnapshot
import com.gabrielpc.enginesoundsimulator.telemetry.ResolvedTransmissionControl
import com.gabrielpc.enginesoundsimulator.telemetry.resolveTransmissionControl
import com.gabrielpc.enginesoundsimulator.telemetry.vehicleDriveSignalsAvailable
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.LockSupport

enum class InputMode(val primaryLabel: String, val secondaryLabel: String = "PEDALS") {
    RealPedals("REAL"),
    SimulatedPedals("SIMULATED"),
    ;

    val displayName: String get() = "$primaryLabel $secondaryLabel"
}

// In Hold Pedals mode, the bottom part of the touch track is an explicit release gesture. This
// keeps a latched value from requiring pixel-perfect travel back to zero before disengaging.
private const val HELD_PEDAL_RELEASE_THRESHOLD = 0.10

data class DriveSnapshot(
    val drivetrain: DrivetrainState,
    val inputSourcePrimary: String,
    val inputSourceSecondary: String,
    val inputSourceIsRealPedals: Boolean,
    /** True when simulated pedal percentages remain latched after the pointer is released. */
    val simulatedPedalsLatched: Boolean = false,
    val inputSourceFaded: Boolean,
    val throttle: Double,
    val brake: Double,
    val simulatedRegen: Double = 1.0,
    val transmissionPosition: TransmissionPosition,
    val engineSoundEnabled: Boolean,
    val audioMuted: Boolean = false,
    val selectedCarId: String,
    val selectedCarName: String,
    val selectedCarPreviewAsset: String,
    val selectedCarIndex: Int,
    val availableCarCount: Int,
    val fmodSources: List<FmodSourceState> = emptyList(),
    /** Final FMOD mix level used for the dashboard output meter (linear 0..1). */
    val masterOutputLinear: Float = 0f,
    /** Host-level engine trim applied before category routing. */
    val shiftOverrideGain: Float = 1.0f,
    val backfireOverrideGain: Float = 1.0f,
    val mixerGlobalGains: MixerGlobalGains = MixerGlobalGains(),
    val mixerCarSpecificGains: MixerCarSpecificGains = MixerCarSpecificGains(),
    val appVolumeSettings: AppVolumeSettings = AppVolumeSettings(),
    val currentLoudnessNormalization: LoudnessNormalizationState = LoudnessNormalizationState(),
    val loudnessNormalizationSummary: LoudnessNormalizationSummary = LoudnessNormalizationSummary(),
    val loudnessCalibrationProgress: LoudnessCalibrationProgress = LoudnessCalibrationProgress(),
    /** Global backfire policy, deliberately independent of each car bank's authored thresholds. */
    val backfireSettings: BackfireSettings = BackfireSettings(),
    val popsAndBangsOverride: Boolean = false,
    val shiftSoundsOverride: Boolean = false,
    val hasTurbo: Boolean = false,
    val hasSupercharger: Boolean = false,
    val soundPerspective: EngineSoundPerspective = EngineSoundPerspective.CABIN,
    val transmissionLockedToVehicle: Boolean = false,
    val carAudioReady: Boolean = false,
    val manualShiftModeEnabled: Boolean = false,
    val fmodUpdateRateHz: Int = FmodUpdateRate.DEFAULT_HZ,
    val gearProfileSelection: GearProfileSelection = GearProfileSelection.virtual(VirtualGearProfile.DEFAULT_VIRTUAL_GEARS),
    val virtualForwardGearCount: Int = VirtualGearProfile.DEFAULT_VIRTUAL_GEARS,
    val virtualGearSpeedBoundaries: VirtualGearSpeedBoundariesSettings = VirtualGearSpeedBoundariesSettings(),
    val exteriorPureAudio: Boolean = false,
    val minimumAudioThrottle: Float = MinimumAudioThrottle.DEFAULT,
    val speedAudioSettings: SpeedAudioSettings = SpeedAudioSettings(),
    val cruisingLogicEnabled: Boolean = true,
    val allowManualOnLaunchEnabled: Boolean = false,
    val manualTransmissionKickdownEnabled: Boolean = true,
    val pedalAudioThrottleRampUpMilliseconds: Int = PedalAudioThrottleRampMilliseconds.DEFAULT,
    val pedalAudioThrottleRampDownMilliseconds: Int = PedalAudioThrottleRampMilliseconds.DEFAULT,
    val cruisingShiftOffsetTachMaxRpm: Int = CruisingShiftOffsetByTachMaxRpm.TIERS.first(),
    val cruisingShiftOffsetRpm: Int = CruisingShiftOffsetByTachMaxRpm.defaultOffsets().getValue(7_000),
    val cruisingShiftOffsetsByTachMaxRpm: Map<Int, Int> = CruisingShiftOffsetByTachMaxRpm.defaultOffsets(),
    val racingReturnThrottlePercent: Int = RacingReturnThrottlePercent.DEFAULT,
    val racingEnterMinThrottlePercent: Int = RacingEnterMinThrottlePercent.DEFAULT,
    val kickdownStompDeltaPercent: Int = KickdownStompDeltaPercent.DEFAULT,
    val kickdownStompMinThrottlePercent: Int = KickdownStompMinThrottlePercent.DEFAULT,
    val racingEnterDelayMilliseconds: Int = RacingEnterDelayMilliseconds.DEFAULT,
    val automaticUpshiftMilliseconds: Int = AutomaticUpshiftMilliseconds.DEFAULT,
    val automaticDownshiftMilliseconds: Int = AutomaticDownshiftMilliseconds.DEFAULT,
    val racingReturnHoldSeconds: Int = RacingReturnHoldSeconds.DEFAULT,
    val manualRedlineHoldSeconds: Int = ManualRedlineHoldSeconds.DEFAULT,
    val manualAutodownshiftRpm: Int = ManualAutodownshiftRpm.DEFAULT,
    val tachometerCruisingShiftRangeOverlayEnabled: Boolean = true,
    val lowSpeedCrawlRpmHoldEnabled: Boolean = true,
    val favoriteCarIds: Set<String> = emptySet(),
    val userMessage: UserVisibleMessage? = null,
)

/** Runtime-only selection restored once an ADB diagnostic scenario ends. */
private data class DebugScenarioBaseline(
    val profile: FmodBankProfile,
    val perspective: EngineSoundPerspective,
)

/** Coordinates read-only inputs, the authored Assetto drivetrain, and FMOD. */
class DriveController(context: Context) {
    private val appContext = context.applicationContext
    private val selectedCarRepository = SelectedCarRepository(appContext)
    private val carFavoritesRepository = CarFavoritesRepository(appContext)
    private val bankResolver = FmodBankResolver(appContext)
    // Package manifests are immutable while this controller is running. Keeping the installed
    // catalog out of the fixed-step simulation prevents disk reads and JSON parses on every
    // physical frame, which otherwise makes simulated acceleration run behind wall-clock time.
    private val installedProfileCache = AtomicReference(
        FmodBankProfiles.all.filter(bankResolver::isInstalled),
    )
    private val calibrationFingerprintCache = AtomicReference<Map<LoudnessCalibrationKey, String>>(emptyMap())
    private val currentLoudnessNormalization = AtomicReference(LoudnessNormalizationState())
    private val loudnessNormalizationSummary = AtomicReference(LoudnessNormalizationSummary())
    private val observedCalibrationProgress = AtomicLong(Long.MIN_VALUE)
    private val shiftModeRepository = ShiftModeRepository(appContext)
    private val soundPerspectiveRepository = EngineSoundPerspectiveRepository(appContext)
    private val effectSoundOverrideGainRepository = EffectSoundOverrideGainRepository(appContext)
    private val mixerGlobalGainRepository = MixerGlobalGainRepository(appContext)
    private val mixerCarSpecificGainRepository = MixerCarSpecificGainRepository(appContext)
    private val appVolumeRepository = AppVolumeRepository(appContext)
    private val loudnessNormalizationRepository = LoudnessNormalizationRepository(appContext)
    private val fmodUpdateRateRepository = FmodUpdateRateRepository(appContext)
    private val exteriorAudioModeRepository = ExteriorAudioModeRepository(appContext)
    private val backfireSettingsRepository = BackfireSettingsRepository(appContext)
    private val effectSoundOverrideRepository = EffectSoundOverrideRepository(appContext)
    private val gearProfileSelectionRepository = GearProfileSelectionRepository(appContext)
    private val virtualGearSpeedBoundariesRepository = VirtualGearSpeedBoundariesRepository(appContext)
    private val minimumAudioThrottleRepository = MinimumAudioThrottleRepository(appContext)
    private val speedAudioSettingsRepository = SpeedAudioSettingsRepository(appContext)
    private val automaticTransmissionSettingsRepository = AutomaticTransmissionSettingsRepository(appContext)
    private val selectedProfile = AtomicReference(resolveInitialProfile())
    private val selectedPerspective = AtomicReference(soundPerspectiveRepository.load(selectedProfile.get()))
    private val manualShiftEnabled = AtomicBoolean(shiftModeRepository.isManualEnabled())
    private val mediaShiftButtonCoordinator = MediaShiftButtonCoordinator(appContext) { keyCode ->
        handleMediaShiftButton(keyCode)
    }
    private val activePhysics = AtomicReference<AssettoPhysics?>(null)
    private val simulation = EngineSimulation()
    private val sessionCapture = DriveSessionCapture(appContext)
    private val vehicleReader = BydSpeedReader(appContext)
    private val audioEngine = EngineAudioEngine(appContext)
    private val lifecycleLock = Any()
    private val running = AtomicBoolean(false)
    private val generation = AtomicLong(0L)
    private val simulatedPedals = AtomicReference(SimulatedPedalInput())
    private val simulatedPedalsLatched = AtomicBoolean(false)
    private val simulatedRegen = AtomicReference(1.0)
    private val inputMode = AtomicReference(InputMode.RealPedals)
    private val transmissionPosition = AtomicReference(TransmissionPosition.DRIVE)
    private val uiActive = AtomicBoolean(false)
    private val audioInterrupted = AtomicBoolean(false)
    private val bankRescanRunning = AtomicBoolean(false)
    private val stagedBankImportRunning = AtomicBoolean(false)
    private val audioMuted = AtomicBoolean(false)
    // Deliberately session-only: this diagnostic/listening mode must never become a car preference.
    private val backfireSettings = AtomicReference(BackfireSettings())
    private val effectSoundOverrides = AtomicReference(EffectSoundOverrideSettings())
    private val effectSoundOverrideGains = AtomicReference(EffectSoundOverrideGains())
    private val mixerGlobalGains = AtomicReference(MixerGlobalGains())
    private val mixerCarSpecificGains = AtomicReference(MixerCarSpecificGains())
    private val appVolumeSettings = AtomicReference(AppVolumeSettings())
    private val fmodUpdateRateHz = AtomicInteger(fmodUpdateRateRepository.load())
    private val gearProfileSelection = AtomicReference(gearProfileSelectionRepository.load())
    private val virtualGearSpeedBoundaries = AtomicReference(virtualGearSpeedBoundariesRepository.load())
    private val exteriorPureAudio = AtomicBoolean(false)
    private val minimumAudioThrottleSettings = AtomicReference(minimumAudioThrottleRepository.load())
    private val speedAudioSettings = AtomicReference(speedAudioSettingsRepository.load())
    private val automaticTransmissionSettings = AtomicReference(automaticTransmissionSettingsRepository.load())
    private val favoriteCarIds = AtomicReference(carFavoritesRepository.load())
    /** Monotonic across the controller lifetime so audio-worker skips/repeats are measurable. */
    private val simulationFrameSerial = AtomicLong(0L)
    private var consumedDebugScenarioShiftSerial = 0L
    private var activeDebugScenarioId = 0L
    private var debugScenarioBaseline: DebugScenarioBaseline? = null
    /** Back stack of car profile ids; [carNavigationIndex] is the current selection. */
    private val carNavigationHistory = mutableListOf<String>()
    private var carNavigationIndex = 0
    /** Cars already picked by Next in the current random cycle. */
    private val randomCycleVisitedCarIds = mutableSetOf<String>()

    @Volatile private var loopThread: Thread? = null
    @Volatile private var userMessage: UserVisibleMessage? = null
    @Volatile private var lastVehicleTransmissionPosition: TransmissionPosition? = null
    private var nextUiSnapshotNanos = 0L
    @Volatile private var latest = DriveSnapshot(
        drivetrain = simulation.state,
        inputSourcePrimary = InputMode.SimulatedPedals.primaryLabel,
        inputSourceSecondary = InputMode.SimulatedPedals.secondaryLabel,
        inputSourceIsRealPedals = false,
        simulatedPedalsLatched = false,
        inputSourceFaded = false,
        throttle = 0.0,
        brake = 0.0,
        simulatedRegen = 1.0,
        transmissionPosition = TransmissionPosition.DRIVE,
        engineSoundEnabled = false,
        selectedCarId = selectedProfile.get().id,
        selectedCarName = selectedProfile.get().displayName,
        selectedCarPreviewAsset = selectedProfile.get().previewAssetName,
        selectedCarIndex = installedProfiles().indexOf(selectedProfile.get()),
        availableCarCount = installedProfiles().size,
        soundPerspective = selectedPerspective.get(),
    )

    init {
        // Gain semantics changed from percentage-like 0..2 values to 1..10x. Deliberately discard
        // the old preference namespace rather than migrating values into the new scale.
        appContext.getSharedPreferences(AppPreferenceStores.AUDIO_MIX_GAINS_LEGACY, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
        loadPhysics(selectedProfile.get())
        simulation.manualShiftEnabled = manualShiftEnabled.get()
        mixerGlobalGains.set(mixerGlobalGainRepository.load())
        appVolumeSettings.set(appVolumeRepository.load())
        refreshCalibrationFingerprintCache()
        loudnessNormalizationRepository.recomputeNormalizations(currentCalibrationFingerprints())
        refreshLoudnessNormalizationSummary()
        audioEngine.setFocusChangeListener(::handleAudioFocusChange)
        audioEngine.setFmodUpdateRateHz(fmodUpdateRateHz.get())
        applyCarAudioPreferences(selectedProfile.get())
        audioEngine.setSoundProgram(selectedProfile.get(), selectedPerspective.get())
        backfireSettings.set(backfireSettingsRepository.load())
        effectSoundOverrides.set(effectSoundOverrideRepository.load())
        simulation.updateBackfireSettings(backfireSettings.get())
        applyEffectSoundOverrides()
        simulation.updateGearProfileSelection(gearProfileSelection.get())
        simulation.updateVirtualGearSpeedBoundaries(virtualGearSpeedBoundaries.get())
        audioEngine.setBackfireAllowedSamples(backfireSettings.get().allowedSamples)
        applyMinimumAudioThrottleSettings(minimumAudioThrottleSettings.get())
        applySpeedAudioSettings(speedAudioSettings.get())
        applyManualShiftSoundOverrideCoupling(manualShiftEnabled.get())
        simulation.updateAutomaticTransmissionSettings(automaticTransmissionSettings.get())
        initializeCarNavigation(selectedProfile.get().id)
    }

    fun isRunning(): Boolean = running.get()

    fun setUiActive(active: Boolean) { uiActive.set(active) }

    fun setMixerDiagnosticsActive(active: Boolean) {
        audioEngine.setMixerDiagnosticsActive(active)
    }

    fun snapshot(): DriveSnapshot {
        val base = latest
        val selected = selectedProfile.get()
        val calibrationProgress = audioEngine.loudnessCalibrationProgress()
        val progressMarker = calibrationProgress.status.ordinal.toLong().shl(32) or
            calibrationProgress.completedCount.toLong().and(0xffffffffL)
        if (observedCalibrationProgress.getAndSet(progressMarker) != progressMarker) {
            refreshLoudnessNormalizationSummary()
            if (!calibrationProgress.isRunning) {
                syncMasterOutputGainToAudioEngine()
            }
        }

        return base.copy(
            engineSoundEnabled = audioEngine.isAudioActive(),
            audioMuted = audioMuted.get(),
            manualShiftModeEnabled = manualShiftEnabled.get(),
            fmodSources = if (uiActive.get() && audioEngine.isMixerDiagnosticsActive()) {
                audioEngine.sourceSnapshots()
            } else {
                emptyList()
            },
            masterOutputLinear = if (audioEngine.isAudioActive() && !audioMuted.get()) {
                audioEngine.masterOutputLinear()
            } else {
                0f
            },
            selectedCarId = selected.id,
            selectedCarName = selected.displayName,
            selectedCarPreviewAsset = selected.previewAssetName,
            selectedCarIndex = installedProfiles().indexOf(selected),
            availableCarCount = installedProfiles().size,
            soundPerspective = selectedPerspective.get(),
            shiftOverrideGain = effectSoundOverrideGains.get().shiftGain,
            backfireOverrideGain = effectSoundOverrideGains.get().backfireGain,
            mixerGlobalGains = mixerGlobalGains.get(),
            mixerCarSpecificGains = mixerCarSpecificGains.get(),
            appVolumeSettings = appVolumeSettings.get(),
            currentLoudnessNormalization = currentLoudnessNormalization.get(),
            loudnessNormalizationSummary = loudnessNormalizationSummary.get(),
            loudnessCalibrationProgress = calibrationProgress,
            backfireSettings = backfireSettings.get(),
            popsAndBangsOverride = effectSoundOverrides.get().popsAndBangsOverride,
            shiftSoundsOverride = effectSoundOverrides.get().shiftSoundsOverride,
            hasTurbo = activePhysics.get()?.engine?.turbos?.isNotEmpty() == true,
            hasSupercharger = resolveHasSupercharger(),
            fmodUpdateRateHz = fmodUpdateRateHz.get(),
            gearProfileSelection = gearProfileSelection.get(),
            virtualForwardGearCount = simulation.effectiveForwardGearCount(),
            virtualGearSpeedBoundaries = virtualGearSpeedBoundaries.get(),
            exteriorPureAudio = exteriorPureAudio.get(),
            minimumAudioThrottle = minimumAudioThrottleSettings.get().minimum,
            speedAudioSettings = speedAudioSettings.get(),
            pedalAudioThrottleRampUpMilliseconds = minimumAudioThrottleSettings.get().rampUpMilliseconds,
            pedalAudioThrottleRampDownMilliseconds = minimumAudioThrottleSettings.get().rampDownMilliseconds,
            favoriteCarIds = favoriteCarIds.get(),
            carAudioReady = isSelectedCarAudioReady(selected.id),
            userMessage = userMessage,
        )
    }

    fun start() {
        synchronized(lifecycleLock) {
            if (running.get() && loopThread?.isAlive == true) {
                // Android can retain the driving service while its activity is closed. A user may
                // copy packs through the file manager in that interval, then reopen the app; do
                // not require a process restart before the staged files can be discovered.
                if (bankResolver.hasStagedPacks()) importStagedBankPacksAsync()
                return
            }
            refreshInstalledProfileCache()
            val stagedPacksPending = bankResolver.hasStagedPacks()
            loopThread?.let { thread ->
                thread.interrupt()
                joinLoop(thread)
            }
            val runId = generation.incrementAndGet()
            running.set(true)
            val thread = Thread({ runLoop(runId) }, "drivetrain-simulation").apply { isDaemon = true }
            loopThread = thread
            try {
                vehicleReader.start()
                // Starting FMOD before a staged shared bank is published makes the control worker
                // stop permanently on its first missing-bank error. Let the background importer
                // finish first, then start FMOD from completeStagedBankImport.
                if (!audioMuted.get() && !stagedPacksPending) audioEngine.start()
                mediaShiftButtonCoordinator.start()
                thread.start()
                if (stagedPacksPending) importStagedBankPacksAsync()
            } catch (error: Throwable) {
                running.set(false)
                generation.incrementAndGet()
                vehicleReader.stop()
                audioEngine.stop()
                throw error
            }
        }
    }

    fun stop() {
        synchronized(lifecycleLock) {
            running.set(false)
            generation.incrementAndGet()
            loopThread?.interrupt()
            loopThread?.let(::joinLoop)
            loopThread = null
            vehicleReader.stop()
            audioEngine.stop()
            mediaShiftButtonCoordinator.stop()
            simulatedPedals.set(SimulatedPedalInput())
            simulatedRegen.set(1.0)
            simulatedPedalsLatched.set(false)
        }
    }

    fun setSimulatedPedalsLatched(enabled: Boolean) {
        simulatedPedalsLatched.set(enabled)
        if (!enabled) simulatedPedals.set(SimulatedPedalInput())
    }

    fun setSimulatedPedalThrottle(value: Double) {
        val clamped = value.coerceIn(0.0, 1.0)
        // PedalControl emits an exact zero from its pointer-release callback. In Hold Pedals
        // mode that callback must not clear the latched value; only an intentional low travel
        // sample (0 < value <= 10%) is the explicit release gesture.
        if (simulatedPedalsLatched.get() && clamped == 0.0) return
        val effective = if (simulatedPedalsLatched.get() && clamped <= HELD_PEDAL_RELEASE_THRESHOLD) {
            0.0
        } else {
            clamped
        }
        simulatedPedals.updateAndGet { it.copy(throttle = effective) }
    }

    fun setSimulatedPedalBrake(value: Double) {
        val clamped = value.coerceIn(0.0, 1.0)
        // See throttle above: distinguish the UI's pointer-release callback from low travel.
        if (simulatedPedalsLatched.get() && clamped == 0.0) return
        val effective = if (simulatedPedalsLatched.get() && clamped <= HELD_PEDAL_RELEASE_THRESHOLD) {
            0.0
        } else {
            clamped
        }
        simulatedPedals.updateAndGet { it.copy(brake = effective) }
    }

    fun setSimulatedRegen(value: Double) { simulatedRegen.set(value.coerceIn(0.0, 1.0)) }

    fun setFmodUpdateRateHz(rateHz: Int) {
        if (audioEngine.isLoudnessCalibrationRunning()) return
        val normalized = FmodUpdateRate.normalize(rateHz)
        fmodUpdateRateHz.set(normalized)
        fmodUpdateRateRepository.save(normalized)
        audioEngine.setFmodUpdateRateHz(normalized)
    }

    fun setGearProfileSelection(selection: GearProfileSelection) {
        gearProfileSelection.set(selection)
        gearProfileSelectionRepository.save(selection)
        simulation.updateGearProfileSelection(selection)
    }

    fun setVirtualForwardGearCount(count: Int) {
        setGearProfileSelection(GearProfileSelection.virtual(count))
    }

    fun setVirtualGearSpeedBoundary(preset: Int, boundaryIndex: Int, speedKmh: Int) {
        val gearCount = VirtualGearSpeedBoundaries.coerceVirtualPreset(preset)
        val current = virtualGearSpeedBoundaries.get()
        val updatedBoundaries = VirtualGearSpeedBoundaries.withBoundaryAtIndex(
            boundaries = current.boundariesFor(gearCount),
            gearCount = gearCount,
            boundaryIndex = boundaryIndex,
            speedKmh = speedKmh,
        )
        applyVirtualGearSpeedBoundaries(current.withPresetBoundaries(gearCount, updatedBoundaries))
    }

    fun restoreVirtualGearSpeedBoundaries(preset: Int) {
        applyVirtualGearSpeedBoundaries(
            virtualGearSpeedBoundaries.get().withRestoredPreset(preset),
        )
    }

    private fun applyVirtualGearSpeedBoundaries(settings: VirtualGearSpeedBoundariesSettings) {
        val normalized = settings.normalized()
        virtualGearSpeedBoundaries.set(normalized)
        virtualGearSpeedBoundariesRepository.save(normalized)
        simulation.updateVirtualGearSpeedBoundaries(normalized)
    }

    fun setCruisingShiftOffsetForTachMaxRpm(tachMaxRpmTier: Int, offsetRpm: Int) {
        if (tachMaxRpmTier !in CruisingShiftOffsetByTachMaxRpm.TIERS) {
            return
        }

        updateAutomaticTransmissionSettings { settings ->
            val normalized = CruisingShiftOffsetByTachMaxRpm.normalize(offsetRpm)
            val updatedOffsets = settings.cruisingShiftOffsetsByTachMaxRpm.toMutableMap()
            updatedOffsets[tachMaxRpmTier] = normalized
            settings.copy(
                cruisingShiftOffsetsByTachMaxRpm = CruisingShiftOffsetByTachMaxRpm.normalizeMap(updatedOffsets),
            )
        }
    }

    fun startDriveCapture(): String {
        return sessionCapture.start().absolutePath
    }

    fun stopDriveCapture(): String? {
        return sessionCapture.stop()?.absolutePath
    }

    fun isDriveCapturing(): Boolean {
        return sessionCapture.isCapturing
    }

    fun setRacingReturnThrottlePercent(percent: Int) {
        updateAutomaticTransmissionSettings {
            it.copy(racingReturnThrottlePercent = RacingReturnThrottlePercent.normalize(percent))
        }
    }

    fun setRacingEnterMinThrottlePercent(percent: Int) {
        updateAutomaticTransmissionSettings {
            it.copy(racingEnterMinThrottlePercent = RacingEnterMinThrottlePercent.normalize(percent))
        }
    }

    fun setKickdownStompDeltaPercent(percent: Int) {
        updateAutomaticTransmissionSettings {
            it.copy(kickdownStompDeltaPercent = KickdownStompDeltaPercent.normalize(percent))
        }
    }

    fun setKickdownStompMinThrottlePercent(percent: Int) {
        updateAutomaticTransmissionSettings {
            it.copy(kickdownStompMinThrottlePercent = KickdownStompMinThrottlePercent.normalize(percent))
        }
    }

    fun setRacingReturnHoldSeconds(seconds: Int) {
        updateAutomaticTransmissionSettings {
            it.copy(racingReturnHoldSeconds = RacingReturnHoldSeconds.normalize(seconds))
        }
    }

    fun setRacingEnterDelayMilliseconds(milliseconds: Int) {
        updateAutomaticTransmissionSettings {
            it.copy(racingEnterDelayMilliseconds = RacingEnterDelayMilliseconds.normalize(milliseconds))
        }
    }

    fun setAutomaticUpshiftMilliseconds(milliseconds: Int) {
        updateAutomaticTransmissionSettings {
            it.copy(automaticUpshiftMilliseconds = AutomaticUpshiftMilliseconds.normalize(milliseconds))
        }
    }

    fun setAutomaticDownshiftMilliseconds(milliseconds: Int) {
        updateAutomaticTransmissionSettings {
            it.copy(automaticDownshiftMilliseconds = AutomaticDownshiftMilliseconds.normalize(milliseconds))
        }
    }

    fun setManualRedlineHoldSeconds(seconds: Int) {
        updateAutomaticTransmissionSettings {
            it.copy(manualRedlineHoldSeconds = ManualRedlineHoldSeconds.normalize(seconds))
        }
    }

    fun setManualAutodownshiftRpm(rpm: Int) {
        updateAutomaticTransmissionSettings {
            it.copy(manualAutodownshiftRpm = ManualAutodownshiftRpm.normalize(rpm))
        }
    }

    fun setCruisingLogicEnabled(enabled: Boolean) {
        updateAutomaticTransmissionSettings {
            it.copy(cruisingLogicEnabled = enabled)
        }
    }

    fun setAllowManualOnLaunchEnabled(enabled: Boolean) {
        updateAutomaticTransmissionSettings {
            it.copy(allowManualOnLaunchEnabled = enabled)
        }
    }

    fun setManualTransmissionKickdownEnabled(enabled: Boolean) {
        updateAutomaticTransmissionSettings {
            it.copy(manualTransmissionKickdownEnabled = enabled)
        }
    }

    fun setTachometerCruisingShiftRangeOverlayEnabled(enabled: Boolean) {
        updateAutomaticTransmissionSettings {
            it.copy(tachometerCruisingShiftRangeOverlayEnabled = enabled)
        }
    }

    fun setLowSpeedCrawlRpmHoldEnabled(enabled: Boolean) {
        updateAutomaticTransmissionSettings {
            it.copy(lowSpeedCrawlRpmHoldEnabled = enabled)
        }
    }

    private fun updateAutomaticTransmissionSettings(
        transform: (AutomaticTransmissionSettings) -> AutomaticTransmissionSettings,
    ) {
        val updated = transform(automaticTransmissionSettings.get())
        automaticTransmissionSettings.set(updated)
        automaticTransmissionSettingsRepository.save(updated)
        simulation.updateAutomaticTransmissionSettings(updated)
    }

    fun setMixerGlobalGains(updated: MixerGlobalGains) {
        if (audioEngine.isLoudnessCalibrationRunning()) return
        val normalized = updated.normalized()
        mixerGlobalGains.set(normalized)
        mixerGlobalGainRepository.save(normalized)
        syncEffectiveMixGainsToAudioEngine()
    }

    fun setAppVolumePercent(percent: Int) {
        if (audioEngine.isLoudnessCalibrationRunning()) return
        val settings = AppVolumeSettings(percent).normalized()
        appVolumeSettings.set(settings)
        appVolumeRepository.save(settings)
        syncMasterOutputGainToAudioEngine()
    }

    fun setMixerCarSpecificGains(updated: MixerCarSpecificGains) {
        if (audioEngine.isLoudnessCalibrationRunning()) return
        val normalized = updated.normalized()
        mixerCarSpecificGains.set(normalized)
        mixerCarSpecificGainRepository.save(
            profile = selectedProfile.get(),
            perspective = selectedPerspective.get(),
            gains = normalized,
        )
        syncEffectiveMixGainsToAudioEngine()
    }

    fun resetMixerCarSpecificGainsForCurrentSelection() {
        if (audioEngine.isLoudnessCalibrationRunning()) return
        val profile = selectedProfile.get()
        val perspective = selectedPerspective.get()
        mixerCarSpecificGainRepository.reset(profile, perspective)
        val defaults = MixerCarSpecificGains()
        mixerCarSpecificGains.set(defaults)
        syncEffectiveMixGainsToAudioEngine()
    }

    private fun resolveHasSupercharger(): Boolean {
        if (!RuntimeFeatureFlags.MIX_SUPERCHARGER) {
            return false
        }

        if (audioEngine.hasEmbeddedSupercharger()) {
            return true
        }

        if (!uiActive.get() || !audioEngine.isMixerDiagnosticsActive()) {
            return false
        }

        return audioEngine.sourceSnapshots().any { source ->
            (source.eventName == "engine_int" || source.eventName == "engine_ext") &&
                source.soundName.endsWith("_supercharger", ignoreCase = true)
        }
    }

    private fun syncEffectiveMixGainsToAudioEngine() {
        val global = mixerGlobalGains.get()
        val specific = mixerCarSpecificGains.get()
        val host = effectiveHostGains(global, specific)
        val categories = effectiveCategoryGains(global, specific)
        audioEngine.setHostGains(host.engineInterior, host.engineExterior, host.effectsHost)
        audioEngine.setOverrideEffectsHostGain(
            effectiveEffectsHostForOverrides(
                mixerGlobal = global,
                mixerSpecific = specific,
            ),
        )
        audioEngine.setCategoryGains(categories)
        audioEngine.setEngineIdleGain(
            effectiveEngineIdleGain(
                mixerSpecific = specific,
            ),
        )
        syncMasterOutputGainToAudioEngine()
        syncEffectSoundOverrideGainsToAudioEngine()
    }

    private fun syncMasterOutputGainToAudioEngine() {
        val profile = selectedProfile.get()
        val perspective = selectedPerspective.get()
        val fingerprint = runCatching { bankResolver.calibrationFingerprint(profile) }.getOrNull()
        val normalization = loudnessNormalizationRepository.normalizationState(
            key = LoudnessCalibrationKey(profile.id, profile.packGroup, perspective),
            fingerprint = fingerprint,
        )
        currentLoudnessNormalization.set(normalization)
        audioEngine.setMasterOutputGain(
            composeMasterOutputGain(
                appVolumeLinear = appVolumeSettings.get().linear,
                normalizationLinear = normalization.linear,
                carSpecificOverallLinear = mixerCarSpecificGains.get().overall,
            ),
        )
    }

    private fun syncEffectSoundOverrideGainsToAudioEngine() {
        val effective = effectiveEffectSoundOverrideGains(
            mixerGlobal = mixerGlobalGains.get(),
            local = effectSoundOverrideGains.get(),
        )
        audioEngine.setEffectSoundOverrideGains(
            shiftOverrideGain = effective.shiftGain,
            backfireOverrideGain = effective.backfireGain,
        )
    }

    fun setFmodHostGains(engineInterior: Float, engineExterior: Float, effects: Float) {
        setMixerGlobalGains(
            mixerGlobalGains.get().copy(
                engineInterior = engineInterior.coerceIn(MixerGlobalGains.MIN, MixerGlobalGains.MAX),
                engineExterior = engineExterior.coerceIn(MixerGlobalGains.MIN, MixerGlobalGains.MAX),
                effectsHost = effects.coerceIn(MixerGlobalGains.MIN, MixerGlobalGains.MAX),
            ),
        )
    }

    fun setMinimumAudioThrottle(minimum: Float) {
        updateMinimumAudioThrottleSettings {
            it.copy(minimum = MinimumAudioThrottle.normalize(minimum))
        }
    }

    fun setPedalAudioThrottleRampUpMilliseconds(milliseconds: Int) {
        updateMinimumAudioThrottleSettings {
            it.copy(rampUpMilliseconds = PedalAudioThrottleRampMilliseconds.normalize(milliseconds))
        }
    }

    fun setPedalAudioThrottleRampDownMilliseconds(milliseconds: Int) {
        updateMinimumAudioThrottleSettings {
            it.copy(rampDownMilliseconds = PedalAudioThrottleRampMilliseconds.normalize(milliseconds))
        }
    }

    fun setSpeedAudioSettings(updated: SpeedAudioSettings) {
        val normalized = updated.normalized()
        speedAudioSettings.set(normalized)
        speedAudioSettingsRepository.save(normalized)
        applySpeedAudioSettings(normalized)
    }

    private fun applySpeedAudioSettings(settings: SpeedAudioSettings) {
        audioEngine.setSpeedAudioSettings(settings)
    }

    private fun updateMinimumAudioThrottleSettings(
        transform: (MinimumAudioThrottleSettings) -> MinimumAudioThrottleSettings,
    ) {
        val updated = transform(minimumAudioThrottleSettings.get())
        minimumAudioThrottleSettings.set(updated)
        minimumAudioThrottleRepository.save(updated)
        applyMinimumAudioThrottleSettings(updated)
    }

    private fun applyMinimumAudioThrottleSettings(settings: MinimumAudioThrottleSettings) {
        audioEngine.setMinimumAudioThrottle(settings.minimum)
        audioEngine.setPedalAudioThrottleRampMilliseconds(
            settings.rampUpMilliseconds,
            settings.rampDownMilliseconds,
        )
    }

    fun setExteriorPureAudio(enabled: Boolean) {
        if (audioEngine.isLoudnessCalibrationRunning()) return
        val profile = selectedProfile.get()
        if (!enabled) {
            exteriorAudioModeRepository.save(profile, false)
            exteriorPureAudio.set(false)
            audioEngine.setExteriorPureAudio(false)
            return
        }

        if (selectedPerspective.get() != EngineSoundPerspective.EXTERIOR) {
            setSoundPerspective(EngineSoundPerspective.EXTERIOR)
        }

        exteriorAudioModeRepository.save(profile, true)
        exteriorPureAudio.set(true)
        audioEngine.setExteriorPureAudio(true)
    }

    fun setEffectOverride(kind: EffectSoundKind, override: Boolean) {
        if (audioEngine.isLoudnessCalibrationRunning()) return
        val updated = effectSoundOverrides.get().withOverride(kind, override)
        effectSoundOverrides.set(updated)
        effectSoundOverrideRepository.save(updated)
        applyEffectSoundOverride(kind, updated)
    }

    private fun applyEffectSoundOverrides() {
        val overrides = effectSoundOverrides.get()
        applyEffectSoundOverride(EffectSoundKind.POPS_AND_BANGS, overrides)
        applyEffectSoundOverride(EffectSoundKind.SHIFT, overrides)
    }

    private fun applyEffectSoundOverride(kind: EffectSoundKind, overrides: EffectSoundOverrideSettings) {
        when (kind) {
            EffectSoundKind.POPS_AND_BANGS -> {
                val override = overrides.popsAndBangsOverride
                audioEngine.setBackfireUseOriginal(!override)
                simulation.setUseOriginalBackfire(!override)
            }
            EffectSoundKind.SHIFT -> {
                audioEngine.setShiftSoundOverride(overrides.shiftSoundsOverride)
            }
            EffectSoundKind.TRANSMISSION, EffectSoundKind.TURBO -> Unit
        }
    }
    fun setEffectSoundOverrideGain(kind: EffectSoundKind, gain: Float) {
        if (audioEngine.isLoudnessCalibrationRunning()) return
        val normalized = normalizePresetGain(gain)
        val current = effectSoundOverrideGains.get()
        val updated = when (kind) {
            EffectSoundKind.SHIFT -> current.copy(shiftGain = normalized)
            EffectSoundKind.POPS_AND_BANGS -> current.copy(backfireGain = normalized)
            EffectSoundKind.TRANSMISSION, EffectSoundKind.TURBO -> return
        }
        effectSoundOverrideGains.set(updated)
        effectSoundOverrideGainRepository.save(selectedProfile.get(), updated)
        syncEffectSoundOverrideGainsToAudioEngine()
    }

    fun setBackfireSettings(updated: BackfireSettings) {
        if (audioEngine.isLoudnessCalibrationRunning()) return
        val normalized = updated.normalized()
        backfireSettings.set(normalized)
        backfireSettingsRepository.save(normalized)
        simulation.updateBackfireSettings(normalized)
        audioEngine.setBackfireAllowedSamples(normalized.allowedSamples)
        audioEngine.setBackfireAudioEnabled(true)
    }

    fun exportAllPreferences() {
        try {
            val path = SettingsExporter.export(appContext)
            userMessage = UserVisibleMessage(
                id = SystemClock.elapsedRealtime(),
                title = "Settings exported",
                detail = "Saved to $path",
                severity = UserVisibleMessageSeverity.INFO,
            )
        } catch (error: Exception) {
            userMessage = UserVisibleMessage(
                id = SystemClock.elapsedRealtime(),
                title = "Settings export failed",
                detail = error.message ?: error.javaClass.simpleName,
                severity = UserVisibleMessageSeverity.ERROR,
            )
        }
    }

    fun resetAllPreferences() {
        if (audioEngine.isLoudnessCalibrationRunning()) return
        effectSoundOverrideGainRepository.resetAll()
        appContext.getSharedPreferences(AppPreferenceStores.SHIFT_MODE, Context.MODE_PRIVATE).edit().clear().apply()
        appContext.getSharedPreferences(AppPreferenceStores.ENGINE_SOUND_PERSPECTIVE, Context.MODE_PRIVATE).edit().clear().apply()
        appContext.getSharedPreferences(AppPreferenceStores.CAR_PICKER_GROUP, Context.MODE_PRIVATE).edit().clear().apply()
        backfireSettingsRepository.reset()
        effectSoundOverrideRepository.reset()
        gearProfileSelectionRepository.reset()
        virtualGearSpeedBoundariesRepository.reset()
        minimumAudioThrottleRepository.reset()
        speedAudioSettingsRepository.reset()
        automaticTransmissionSettingsRepository.reset()
        fmodUpdateRateRepository.reset()
        exteriorAudioModeRepository.reset()
        mixerGlobalGainRepository.resetAll()
        mixerCarSpecificGainRepository.resetAll()
        appVolumeRepository.reset()
        loudnessNormalizationRepository.clearAll()
        refreshLoudnessNormalizationSummary()
        audioEngine.resetLoudnessCalibrationProgress()
        effectSoundOverrideGains.set(EffectSoundOverrideGains())
        mixerGlobalGains.set(MixerGlobalGains())
        mixerCarSpecificGains.set(MixerCarSpecificGains())
        appVolumeSettings.set(AppVolumeSettings())
        fmodUpdateRateHz.set(FmodUpdateRate.DEFAULT_HZ)
        exteriorPureAudio.set(false)
        backfireSettings.set(BackfireSettings())
        effectSoundOverrides.set(EffectSoundOverrideSettings())
        gearProfileSelection.set(GearProfileSelection.virtual(VirtualGearProfile.DEFAULT_VIRTUAL_GEARS))
        virtualGearSpeedBoundaries.set(VirtualGearSpeedBoundariesSettings())
        minimumAudioThrottleSettings.set(MinimumAudioThrottleSettings())
        speedAudioSettings.set(SpeedAudioSettings())
        automaticTransmissionSettings.set(AutomaticTransmissionSettings())
        simulation.updateGearProfileSelection(GearProfileSelection.virtual(VirtualGearProfile.DEFAULT_VIRTUAL_GEARS))
        simulation.updateVirtualGearSpeedBoundaries(VirtualGearSpeedBoundariesSettings())
        simulation.updateAutomaticTransmissionSettings(AutomaticTransmissionSettings())
        simulation.updateBackfireSettings(backfireSettings.get())
        audioEngine.setBackfireAudioEnabled(true)
        audioEngine.setShiftSoundEnabled(true)
        applyEffectSoundOverrides()
        audioEngine.setTransmissionAudioEnabled(true)
        audioEngine.setTurboAudioEnabled(true)
        selectedProfile.set(defaultInstalledProfile())
        initializeCarNavigation(selectedProfile.get().id)
        selectedPerspective.set(EngineSoundPerspective.CABIN)
        audioEngine.setFmodUpdateRateHz(FmodUpdateRate.DEFAULT_HZ)
        audioEngine.setExteriorPureAudio(false)
        applyMinimumAudioThrottleSettings(MinimumAudioThrottleSettings())
        applySpeedAudioSettings(SpeedAudioSettings())
        syncEffectiveMixGainsToAudioEngine()
        simulation.reset()
        audioEngine.setSoundProgram(selectedProfile.get(), selectedPerspective.get())
    }
    fun setFmodEventMute(eventName: String, muted: Boolean) {
        if (!audioEngine.isLoudnessCalibrationRunning()) audioEngine.setEventMute(eventName, muted)
    }

    fun setFmodEventSolo(eventName: String, solo: Boolean) {
        if (!audioEngine.isLoudnessCalibrationRunning()) audioEngine.setEventSolo(eventName, solo)
    }
    fun setInputMode(mode: InputMode) { inputMode.set(mode) }

    /**
     * Muting stops FMOD completely. Unmuting deliberately performs a full stop/start cycle so
     * stale event instances, voices, and decoder state cannot survive the user's reset gesture.
     */
    fun toggleAudioMute(): Boolean = synchronized(lifecycleLock) {
        if (audioEngine.isLoudnessCalibrationRunning()) return@synchronized audioMuted.get()
        val shouldMute = !audioMuted.get()
        audioMuted.set(shouldMute)
        if (shouldMute) {
            audioEngine.stop()
        } else if (running.get() && !audioInterrupted.get()) {
            audioEngine.stop()
            audioEngine.start()
        }
        shouldMute
    }
    fun selectSimulatedPedals() {
        inputMode.set(InputMode.SimulatedPedals)
        lastVehicleTransmissionPosition = null
    }

    fun setTransmissionPosition(position: TransmissionPosition) { transmissionPosition.set(position) }

    fun selectRealPedals() {
        if (vehicleReader.snapshot().vehicleDriveSignalsAvailable()) {
            inputMode.set(InputMode.RealPedals)
            lastVehicleTransmissionPosition = null
        }
    }

    fun toggleInputSource() {
        if (inputMode.get() == InputMode.RealPedals) {
            inputMode.set(InputMode.SimulatedPedals)
            lastVehicleTransmissionPosition = null
        } else if (vehicleReader.snapshot().vehicleDriveSignalsAvailable()) {
            inputMode.set(InputMode.RealPedals)
            lastVehicleTransmissionPosition = null
        }
    }

    fun setSoundPerspective(perspective: EngineSoundPerspective) {
        if (audioEngine.isLoudnessCalibrationRunning()) return
        if (perspective != EngineSoundPerspective.EXTERIOR && exteriorPureAudio.get()) {
            setExteriorPureAudio(false)
        }

        val profile = selectedProfile.get()
        selectedPerspective.set(soundPerspectiveRepository.save(profile, perspective))
        mixerCarSpecificGains.set(mixerCarSpecificGainRepository.load(profile, perspective))
        syncEffectiveMixGainsToAudioEngine()
        audioEngine.setSoundProgram(profile, perspective)
    }

    fun startLoudnessCalibration(resume: Boolean = false): Boolean = synchronized(lifecycleLock) {
        if (bankRescanRunning.get() || stagedBankImportRunning.get()) return@synchronized false
        refreshInstalledProfileCache()

        audioEngine.startLoudnessCalibration(
            profiles = installedProfiles(),
            resume = resume,
            onRecordsChanged = ::syncMasterOutputGainToAudioEngine,
        )
    }

    fun cancelLoudnessCalibration() {
        audioEngine.cancelLoudnessCalibration()
    }

    fun selectPreviousCar() {
        synchronized(lifecycleLock) {
            if (audioEngine.isLoudnessCalibrationRunning()) return
            if (carNavigationIndex > 0) {
                carNavigationIndex--
                val profileId = carNavigationHistory[carNavigationIndex]
                installedProfiles().firstOrNull { it.id == profileId }?.let(::applySelectedCar)
                return
            }

            val installed = installedProfiles()
            if (installed.size <= 1) {
                return
            }

            val currentId = selectedProfile.get().id
            val currentIndex = installed.indexOfFirst { profile -> profile.id == currentId }.coerceAtLeast(0)
            val previousProfile = installed[(currentIndex - 1 + installed.size) % installed.size]

            if (carNavigationHistory.firstOrNull() != previousProfile.id) {
                carNavigationHistory.add(0, previousProfile.id)
            }
            carNavigationIndex = 0
            randomCycleVisitedCarIds.add(previousProfile.id)
            applySelectedCar(previousProfile)
        }
    }

    fun selectNextCar() {
        synchronized(lifecycleLock) {
            if (audioEngine.isLoudnessCalibrationRunning()) return
            val installed = installedProfiles()
            if (installed.isEmpty()) {
                return
            }

            val currentId = selectedProfile.get().id
            val currentIndex = installed.indexOfFirst { profile -> profile.id == currentId }.coerceAtLeast(0)
            val nextProfile = installed[(currentIndex + 1) % installed.size]

            truncateCarNavigationForwardHistory()
            if (carNavigationHistory[carNavigationIndex] != nextProfile.id) {
                carNavigationHistory.add(nextProfile.id)
                carNavigationIndex = carNavigationHistory.lastIndex
            }
            randomCycleVisitedCarIds.add(nextProfile.id)
            applySelectedCar(nextProfile)
        }
    }

    fun selectShuffleCar() {
        synchronized(lifecycleLock) {
            if (audioEngine.isLoudnessCalibrationRunning()) return
            val installed = installedProfiles()
            if (installed.isEmpty()) {
                return
            }

            truncateCarNavigationForwardHistory()
            val currentId = selectedProfile.get().id
            val installedIds = installed.map { it.id }.toSet()
            randomCycleVisitedCarIds.retainAll(installedIds)
            if (randomCycleVisitedCarIds.isEmpty()) {
                randomCycleVisitedCarIds.add(currentId)
            }

            val nextProfile = pickRandomNextCarProfile(
                installed = installed,
                currentId = currentId,
            ) ?: return

            randomCycleVisitedCarIds.add(nextProfile.id)
            carNavigationHistory.add(nextProfile.id)
            carNavigationIndex = carNavigationHistory.lastIndex
            applySelectedCar(nextProfile)
        }
    }

    fun selectCar(profileId: String) {
        synchronized(lifecycleLock) {
            if (audioEngine.isLoudnessCalibrationRunning()) return
            FmodBankProfiles.find(profileId).takeIf(bankResolver::isInstalled)?.let { profile ->
                truncateCarNavigationForwardHistory()
                if (carNavigationHistory[carNavigationIndex] != profile.id) {
                    carNavigationHistory.add(profile.id)
                    carNavigationIndex = carNavigationHistory.lastIndex
                }
                randomCycleVisitedCarIds.add(profile.id)
                applySelectedCar(profile)
            }
        }
    }

    fun toggleCarFavorite(profileId: String) {
        synchronized(lifecycleLock) {
            if (audioEngine.isLoudnessCalibrationRunning()) return
            val updated = carFavoritesRepository.toggle(profileId)
            favoriteCarIds.set(updated)
            latest = latest.copy(favoriteCarIds = updated)
        }
    }

    fun toggleManualShiftMode() {
        setManualShiftMode(!manualShiftEnabled.get())
    }

    fun setManualShiftMode(enabled: Boolean) {
        val currentlyEnabled = manualShiftEnabled.get()
        if (currentlyEnabled == enabled) {
            return
        }

        shiftModeRepository.setManualEnabled(enabled)
        manualShiftEnabled.set(enabled)
        simulation.manualShiftEnabled = enabled
        applyManualShiftSoundOverrideCoupling(enabled)
    }

    private fun applyManualShiftSoundOverrideCoupling(manualEnabled: Boolean) {
        setEffectOverride(EffectSoundKind.SHIFT, manualEnabled)
    }

    fun handleMediaShiftButton(keyCode: Int): Boolean {
        if (!MediaShiftButtonCoordinator.isMediaShiftKeyCode(keyCode)) {
            return false
        }
        if (audioEngine.isLoudnessCalibrationRunning()) return true

        synchronized(lifecycleLock) {
            if (transmissionPosition.get() != TransmissionPosition.DRIVE) {
                return false
            }

            if (!manualShiftEnabled.get()) {
                setManualShiftMode(enabled = true)
            }

            return when (keyCode) {
                android.view.KeyEvent.KEYCODE_MEDIA_NEXT,
                android.view.KeyEvent.KEYCODE_DPAD_RIGHT,
                -> simulation.requestManualUpshift()
                android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS,
                android.view.KeyEvent.KEYCODE_DPAD_LEFT,
                -> simulation.requestManualDownshift()
                else -> false
            }
        }
    }

    fun handleShiftKey(keyCode: Int): Boolean = handleMediaShiftButton(keyCode)

    fun requestManualUpshift(): Boolean = synchronized(lifecycleLock) {
        if (transmissionPosition.get() != TransmissionPosition.DRIVE) {
            false
        } else {
            simulation.requestManualUpshift()
        }
    }

    fun requestManualDownshift(): Boolean = synchronized(lifecycleLock) {
        if (transmissionPosition.get() != TransmissionPosition.DRIVE) {
            false
        } else {
            simulation.requestManualDownshift()
        }
    }

    fun rescanBanks() {
        val shouldStart = synchronized(lifecycleLock) {
            !audioEngine.isLoudnessCalibrationRunning() &&
                bankRescanRunning.compareAndSet(false, true)
        }
        if (!shouldStart) return

        Thread({
            try {
                synchronized(lifecycleLock) {
                    refreshInstalledProfileCache()
                    val installed = installedProfiles()
                    val current = selectedProfile.get()
                    val target = installed.firstOrNull { profile ->
                        profile.id == current.id
                    } ?: defaultInstalledProfile()

                    if (installed.isEmpty()) {
                        userMessage = UserVisibleMessage(
                            id = SystemClock.elapsedRealtime(),
                            title = "No valid FMOD banks found",
                            detail = "Open Settings > BANK IMPORT to inspect paths and pack errors.",
                        )
                        return@synchronized
                    }

                    reconcileCarNavigationState()
                    applySelectedCar(
                        profile = target,
                        forceAudioReload = true,
                    )
                    if (!audioMuted.get() && !audioEngine.isAudioActive()) {
                        audioEngine.start()
                    }
                    userMessage = UserVisibleMessage(
                        id = SystemClock.elapsedRealtime(),
                        title = "FMOD banks rescanned",
                        detail = "Recognized ${installed.size} car(s) from the available bank folders.",
                        severity = UserVisibleMessageSeverity.INFO,
                    )
                }
            } finally {
                bankRescanRunning.set(false)
            }
        }, "fmod-bank-rescan").apply {
            isDaemon = true
            start()
        }
    }

    fun dismissUserMessage() { userMessage = null }

    private fun initializeCarNavigation(initialCarId: String) {
        carNavigationHistory.clear()
        carNavigationHistory.add(initialCarId)
        carNavigationIndex = 0
        randomCycleVisitedCarIds.clear()
        randomCycleVisitedCarIds.add(initialCarId)
    }

    private fun truncateCarNavigationForwardHistory() {
        if (carNavigationIndex >= carNavigationHistory.lastIndex) {
            return
        }

        val retained = carNavigationHistory.subList(0, carNavigationIndex + 1).toMutableList()
        carNavigationHistory.clear()
        carNavigationHistory.addAll(retained)
    }

    private fun pickRandomNextCarProfile(
        installed: List<FmodBankProfile>,
        currentId: String,
    ): FmodBankProfile? {
        if (installed.size == 1) {
            return installed.first()
        }

        val unvisited = installed.filter { it.id !in randomCycleVisitedCarIds }
        val pool = if (unvisited.isNotEmpty()) {
            unvisited.filter { it.id != currentId }.ifEmpty { unvisited }
        } else {
            randomCycleVisitedCarIds.clear()
            randomCycleVisitedCarIds.add(currentId)
            installed.filter { it.id != currentId }.ifEmpty { installed }
        }

        return pool.randomOrNull()
    }

    private fun reconcileCarNavigationState() {
        val installedIds = installedProfiles().map { it.id }.toSet()
        if (installedIds.isEmpty()) {
            return
        }

        randomCycleVisitedCarIds.retainAll(installedIds)
        val currentId = selectedProfile.get().id
        if (randomCycleVisitedCarIds.isEmpty()) {
            randomCycleVisitedCarIds.add(currentId)
        }

        val trimmed = carNavigationHistory.filter { it in installedIds }.toMutableList()
        if (trimmed.isEmpty()) {
            initializeCarNavigation(currentId)
            return
        }

        carNavigationHistory.clear()
        carNavigationHistory.addAll(trimmed)
        val currentIndex = trimmed.indexOf(currentId)
        carNavigationIndex = if (currentIndex >= 0) {
            currentIndex
        } else {
            carNavigationHistory.add(currentId)
            carNavigationHistory.lastIndex
        }
    }

    private fun applySelectedCar(
        profile: FmodBankProfile,
        forceAudioReload: Boolean = false,
    ) {
        if (audioEngine.isLoudnessCalibrationRunning()) return
        synchronized(lifecycleLock) {
            selectedProfile.set(profile)
            selectedCarRepository.save(profile)
            applyCarAudioPreferences(profile)
            val telemetry = vehicleReader.snapshot()
            val driveInput = resolveDriveInput(
                mode = inputMode.get(),
                telemetry = telemetry,
                simulatedPedalThrottle = simulatedPedals.get().throttle,
                simulatedPedalBrake = simulatedPedals.get().brake,
            )
            val preserveMotion = simulation.captureMotionContinuity(
                usesSimulatedPedals = driveInput.usesSimulatedPedals,
                transmissionPosition = transmissionPosition.get(),
            )
            loadPhysics(profile, preserveMotion)
            audioEngine.setSoundProgram(
                profile = profile,
                perspective = selectedPerspective.get(),
                forceReload = forceAudioReload,
            )
        }
    }

    /**
     * Reloads per-car dashboard controls before FMOD switches banks. Global effect overrides are
     * intentionally excluded so they survive car changes.
     */
    private fun applyCarAudioPreferences(
        profile: FmodBankProfile,
        perspectiveOverride: EngineSoundPerspective? = null,
    ) {
        val perspective = perspectiveOverride ?: soundPerspectiveRepository.load(profile)
        selectedPerspective.set(perspective)

        val savedPure = exteriorAudioModeRepository.load(profile)
        val effectivePure = savedPure && perspective == EngineSoundPerspective.EXTERIOR
        if (savedPure && !effectivePure) {
            exteriorAudioModeRepository.save(profile, false)
        }
        exteriorPureAudio.set(effectivePure)
        audioEngine.setExteriorPureAudio(effectivePure)

        mixerCarSpecificGains.set(mixerCarSpecificGainRepository.load(profile, perspective))
        effectSoundOverrideGains.set(effectSoundOverrideGainRepository.load(profile))
        syncEffectiveMixGainsToAudioEngine()

        audioEngine.setBackfireAudioEnabled(true)
        audioEngine.setShiftSoundEnabled(true)
        audioEngine.setTransmissionAudioEnabled(true)
        audioEngine.setTurboAudioEnabled(true)
    }

    private fun loadPhysics(
        profile: FmodBankProfile,
        preserveMotion: SimulationMotionContinuity? = null,
    ) {
        val physics = runCatching { bankResolver.physics(profile) }.getOrNull()
        activePhysics.set(physics)
        if (physics != null) {
            simulation.updateAssettoPhysics(physics, preserveMotion)
            simulation.updateBackfireSettings(backfireSettings.get())
        } else userMessage = UserVisibleMessage(
            id = SystemClock.elapsedRealtime(),
            title = "Car audio is not installed",
            detail = "Install the matching bank installer APK, or copy its bank package to Internal storage/Android/data/${appContext.packageName}/files/fmod-bank-import/ and reopen.",
        )
    }

    /**
     * File-manager bank imports run off the UI and audio-control threads because a complete car
     * catalog is multi-gigabyte. Importing uses the same checksum and atomic publication path as
     * the retired companion installer, then refreshes the selectable catalog only once it ends.
     */
    private fun importStagedBankPacksAsync() {
        val shouldStart = synchronized(lifecycleLock) {
            !audioEngine.isLoudnessCalibrationRunning() &&
                stagedBankImportRunning.compareAndSet(false, true)
        }
        if (!shouldStart) return
        Thread({
            val result = bankResolver.importStagedPacks()
            synchronized(lifecycleLock) {
                stagedBankImportRunning.set(false)
                if (!running.get()) return@synchronized
                completeStagedBankImport(result)
            }
        }, "fmod-bank-file-import").apply {
            isDaemon = true
            start()
        }
    }

    private fun completeStagedBankImport(result: FmodBankImportResult) {
        if (!result.foundPacks) return
        refreshInstalledProfileCache()
        reconcileCarNavigationState()
        val selected = selectedProfile.get()
        val target = installedProfiles().firstOrNull { it.id == selected.id }
            ?: installedProfiles().firstOrNull()
        // A staged package may replace the currently selected car or a shared bank without
        // changing its profile ID. Force a fresh FMOD load so the audio worker cannot retain the
        // old file handles after verified publication to private storage.
        target?.let { profile ->
            applySelectedCar(
                profile = profile,
                forceAudioReload = result.importedPackCount > 0,
            )
        }
        if (target != null && !audioMuted.get() && !audioEngine.isAudioActive()) {
            audioEngine.start()
        }
        userMessage = UserVisibleMessage(
            id = SystemClock.elapsedRealtime(),
            title = if (result.failures.isEmpty()) "Car audio import complete" else "Car audio import needs attention",
            detail = buildString {
                append("Imported ${result.importedPackCount} package(s)")
                if (result.alreadyInstalledPackCount > 0) {
                    append("; ${result.alreadyInstalledPackCount} already matched")
                }
                result.failures.firstOrNull()?.let { failure ->
                    append(". First error: $failure")
                }
            },
            severity = if (result.failures.isEmpty()) {
                UserVisibleMessageSeverity.INFO
            } else {
                UserVisibleMessageSeverity.ERROR
            },
        )
    }

    private fun isSelectedCarAudioReady(profileId: String): Boolean {
        // Muting stops FMOD and clears native load state. That is intentional silence, not a car
        // bank still loading, so the dashboard must not show the engine loading overlay.
        if (audioMuted.get()) {
            return true
        }

        return audioEngine.loadedBankProfileId() == profileId && audioEngine.engineSampleDataReady()
    }

    private fun installedProfiles(): List<FmodBankProfile> =
        installedProfileCache.get()

    private fun resolveInitialProfile(): FmodBankProfile {
        refreshInstalledProfileCache()
        return defaultInstalledProfile()
    }

    private fun defaultInstalledProfile(): FmodBankProfile {
        val installed = installedProfiles()
        val saved = selectedCarRepository.load()
        if (installed.any { it.id == saved.id }) {
            return saved
        }

        FmodBankProfiles.catalogGroup?.let { group ->
            installed.firstOrNull { it.packGroup == group }?.let { return it }
        }

        return installed.firstOrNull { it.packGroup == FmodBankProfiles.moddedCarsPackId }
            ?: installed.firstOrNull()
            ?: FmodBankProfiles.default
    }

    private fun refreshInstalledProfileCache() {
        installedProfileCache.set(FmodBankProfiles.all.filter(bankResolver::isInstalled))
        refreshCalibrationFingerprintCache()
        loudnessNormalizationRepository.recomputeNormalizations(currentCalibrationFingerprints())
        refreshLoudnessNormalizationSummary()
    }

    private fun currentCalibrationFingerprints(): Map<LoudnessCalibrationKey, String> =
        calibrationFingerprintCache.get()

    private fun refreshCalibrationFingerprintCache() {
        calibrationFingerprintCache.set(
            buildMap {
                installedProfiles().forEach { profile ->
                    val fingerprint = runCatching { bankResolver.calibrationFingerprint(profile) }.getOrNull()
                        ?: return@forEach
                    EngineSoundPerspective.entries.forEach { perspective ->
                        put(LoudnessCalibrationKey(profile.id, profile.packGroup, perspective), fingerprint)
                    }
                }
            }
        )
    }

    private fun refreshLoudnessNormalizationSummary() {
        loudnessNormalizationSummary.set(
            loudnessNormalizationRepository.summary(currentCalibrationFingerprints()),
        )
    }

    private fun runLoop(runId: Long) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_MORE_FAVORABLE)
        var previousNanos = SystemClock.elapsedRealtimeNanos()
        var accumulatorSeconds = 0.0
        while (isCurrent(runId)) {
            if (audioInterrupted.get()) {
                LockSupport.parkNanos(INTERRUPTED_IDLE_NANOS)
                continue
            }
            val now = SystemClock.elapsedRealtimeNanos()
            val elapsed = ((now - previousNanos) / 1_000_000_000.0).coerceIn(0.0, 0.050)
            previousNanos = now
            accumulatorSeconds += elapsed
            val simulationRateHz = fmodUpdateRateHz.get()
            val simulationStepSeconds = FmodUpdateRate.stepSeconds(simulationRateHz)
            val simulationStepNanos = FmodUpdateRate.periodNanos(simulationRateHz)
            while (accumulatorSeconds >= simulationStepSeconds && isCurrent(runId)) {
                step(simulationStepSeconds)
                accumulatorSeconds -= simulationStepSeconds
            }
            val remaining = simulationStepNanos - (SystemClock.elapsedRealtimeNanos() - now)
            if (remaining > 0) LockSupport.parkNanos(remaining)
        }
    }

    private fun step(dt: Double) {
        val telemetry = vehicleReader.snapshot()
        val scenario = DebugTelemetry.scenarioOverride(SystemClock.elapsedRealtimeNanos())
        if (scenario != null) applyDebugScenario(scenario)
        else restoreDebugScenarioIfNeeded()
        val mode = scenario?.inputModeOrdinal
            ?.let { InputMode.entries.getOrNull(it) }
            ?: inputMode.get()
        val pedals = simulatedPedals.get()
        val input = resolveDriveInput(
            mode,
            telemetry,
            scenario?.throttle ?: pedals.throttle,
            scenario?.brake ?: pedals.brake,
        )
        val selectedTransmissionPosition = scenario?.transmissionPositionOrdinal
            ?.let { TransmissionPosition.entries.getOrNull(it) }
            ?: transmissionPosition.get()
        val transmission = if (scenario != null) {
            ResolvedTransmissionControl(
                position = selectedTransmissionPosition,
                lockedToVehicle = false,
                lastVehiclePosition = lastVehicleTransmissionPosition,
                syncManualPosition = false,
            )
        } else {
            resolveTransmissionControl(
                mode = mode,
                telemetry = telemetry,
                manualPosition = selectedTransmissionPosition,
                lastVehiclePosition = lastVehicleTransmissionPosition,
            )
        }
        lastVehicleTransmissionPosition = transmission.lastVehiclePosition
        if (transmission.syncManualPosition) {
            transmissionPosition.set(transmission.position)
        }
        simulation.manualShiftEnabled = scenario?.manualModeEnabled ?: manualShiftEnabled.get()
        if (
            scenario != null &&
            scenario.manualShiftSerial != 0L &&
            scenario.manualShiftSerial != consumedDebugScenarioShiftSerial
        ) {
            consumedDebugScenarioShiftSerial = scenario.manualShiftSerial
            when (scenario.manualShiftDirection) {
                1 -> simulation.requestManualUpshift()
                -1 -> simulation.requestManualDownshift()
            }
        }
        val measurePerformance = DebugTelemetry.performanceEnabled()
        val simulationWallStartedNanos = if (measurePerformance) System.nanoTime() else 0L
        val simulationCpuStartedNanos = if (measurePerformance) Debug.threadCpuTimeNanos() else 0L
        val drivetrain = simulation.update(
            DriverInput(
                throttle = input.throttle,
                brake = input.brake,
                simulatedPedals = input.usesSimulatedPedals,
                realReportedRawSpeedKmh = input.realReportedRawSpeedKmh,
                transmissionPosition = transmission.position,
                simulatedRegen = simulatedRegen.get(),
            ),
            dt,
        )
        sessionCapture.record(
            throttle = input.throttle,
            brake = input.brake,
            drivetrain = drivetrain,
        )
        if (drivetrain.requestAutomaticShiftMode) {
            setManualShiftMode(enabled = false)
        }
        if (measurePerformance) {
            DebugTelemetry.recordSimulationPerformance(
                cpuNanos = Debug.threadCpuTimeNanos() - simulationCpuStartedNanos,
                wallNanos = System.nanoTime() - simulationWallStartedNanos,
            )
        }
        val simulationFrameId = simulationFrameSerial.incrementAndGet()
        val shiftDirection = when (drivetrain.shiftDirection) {
            ShiftDirection.UP -> 1
            ShiftDirection.DOWN -> -1
            ShiftDirection.NONE -> 0
        }
        val frameTimestampNanos = SystemClock.elapsedRealtimeNanos()
        DebugTelemetry.recordSimulation(
            timestampNanos = frameTimestampNanos,
            simulationFrameId = simulationFrameId,
            profileId = selectedProfile.get().id,
            inputMode = mode.name,
            perspectiveOrdinal = selectedPerspective.get().ordinal,
            rawSpeedKmh = drivetrain.realOrDocumentedRawSpeedKmh,
            presentationSpeedKmh = drivetrain.presentationSpeedKmh,
            presentationAccelerationKmhPerSecond = drivetrain.presentationAccelerationKmhPerSecond,
            fmodDrivetrainSpeedKmh = drivetrain.fmodDrivetrainSpeedKmh,
            rpm = drivetrain.rpm,
            gear = drivetrain.gear,
            clutch = drivetrain.clutch,
            transmissionPosition = transmission.position.ordinal,
            throttle = input.throttle,
            brake = input.brake,
            boost = drivetrain.boost,
            bov = drivetrain.bov,
            bovDecaySeconds = drivetrain.bovDecaySeconds,
            isShifting = drivetrain.isShifting,
            shiftProgress = drivetrain.shiftProgress,
            shiftSerial = drivetrain.shiftSerial,
            shiftDirection = shiftDirection,
            limiterPulse = drivetrain.limiterPulse,
            backfireTriggered = drivetrain.backfireTriggered,
            tractionLimitActive = drivetrain.tractionLimitActive,
            tractionLimitPulse = drivetrain.tractionLimitPulse,
        )
        audioEngine.update(
            EngineAudioFrame(
                simulationFrameId = simulationFrameId,
                rpm = drivetrain.rpm,
                throttle = drivetrain.audioThrottle,
                rawSpeedKmh = drivetrain.realOrDocumentedRawSpeedKmh,
                presentationSpeedKmh = drivetrain.presentationSpeedKmh,
                presentationAccelerationKmhPerSecond = drivetrain.presentationAccelerationKmhPerSecond,
                brake = drivetrain.smoothedBrake,
                clutch = drivetrain.clutch,
                transmissionPosition = transmission.position.ordinal,
                gear = drivetrain.gear,
                isShifting = drivetrain.isShifting,
                shiftProgress = drivetrain.shiftProgress,
                shiftSerial = drivetrain.shiftSerial,
                shiftDirection = shiftDirection,
                limiterPulse = drivetrain.limiterPulse,
                backfireTriggered = drivetrain.backfireTriggered,
                backfireSampleIndex = drivetrain.backfireSampleIndex,
                shiftRejected = drivetrain.shiftRejected,
                suppressShiftSoundOverride = drivetrain.suppressShiftSoundOverride,
                tractionLimitActive = drivetrain.tractionLimitActive,
                tractionLimitPulse = drivetrain.tractionLimitPulse,
                drivetrainSpeedRadiansPerSecond = drivetrain.drivetrainSpeedRadiansPerSecond,
                boost = drivetrain.boost,
                maximumBoost = activePhysics.get()?.engine?.turbos?.sumOf { it.maximumBoost } ?: 0.0,
                bov = drivetrain.bov,
                bovDecaySeconds = drivetrain.bovDecaySeconds,
                perspective = selectedPerspective.get(),
                suppressEffectsLoad = transmission.position == TransmissionPosition.DRIVE &&
                    !manualShiftEnabled.get() &&
                    drivetrain.automaticTransmissionMode == AutomaticTransmissionMode.CRUISING,
                effectsLoadFromThrottle = RuntimeFeatureFlags.MIX_SUPERCHARGER &&
                    (
                        manualShiftEnabled.get() ||
                            (
                                transmission.position == TransmissionPosition.DRIVE &&
                                    !manualShiftEnabled.get() &&
                                    drivetrain.automaticTransmissionMode == AutomaticTransmissionMode.RACING
                                )
                        ),
                usesRacingSpeedAudioGain = SpeedAudioGainResolver.usesRacingGain(
                    manualShiftEnabled = manualShiftEnabled.get(),
                    automaticTransmissionMode = drivetrain.automaticTransmissionMode,
                    racingReturnArmed = drivetrain.racingReturnArmed,
                ),
            ),
        )
        val selected = selectedProfile.get()
        val sourceUi = resolveInputSourceUi(mode, telemetry.vehicleDriveSignalsAvailable())
        if (uiActive.get() && frameTimestampNanos >= nextUiSnapshotNanos) {
            latest = DriveSnapshot(
                drivetrain = drivetrain,
                inputSourcePrimary = sourceUi.primaryLabel,
                inputSourceSecondary = sourceUi.secondaryLabel,
                inputSourceIsRealPedals = sourceUi.isRealPedals,
                simulatedPedalsLatched = simulatedPedalsLatched.get(),
                inputSourceFaded = sourceUi.faded,
                throttle = input.throttle,
                brake = input.brake,
                simulatedRegen = simulatedRegen.get(),
                transmissionPosition = transmission.position,
                engineSoundEnabled = audioEngine.isAudioActive(),
                audioMuted = audioMuted.get(),
                selectedCarId = selected.id,
                selectedCarName = selected.displayName,
                selectedCarPreviewAsset = selected.previewAssetName,
                selectedCarIndex = installedProfiles().indexOf(selected),
                availableCarCount = installedProfiles().size,
                soundPerspective = selectedPerspective.get(),
                gearProfileSelection = gearProfileSelection.get(),
                virtualForwardGearCount = simulation.effectiveForwardGearCount(),
                virtualGearSpeedBoundaries = virtualGearSpeedBoundaries.get(),
                cruisingLogicEnabled = automaticTransmissionSettings.get().cruisingLogicEnabled,
                allowManualOnLaunchEnabled = automaticTransmissionSettings.get().allowManualOnLaunchEnabled,
                manualTransmissionKickdownEnabled =
                    automaticTransmissionSettings.get().manualTransmissionKickdownEnabled,
                cruisingShiftOffsetTachMaxRpm = CruisingShiftOffsetByTachMaxRpm.resolveTier(drivetrain.tachometerMaximumRpm),
                cruisingShiftOffsetRpm = CruisingShiftOffsetByTachMaxRpm.resolveOffset(
                    offsets = automaticTransmissionSettings.get().cruisingShiftOffsetsByTachMaxRpm,
                    tachometerMaximumRpm = drivetrain.tachometerMaximumRpm,
                ),
                cruisingShiftOffsetsByTachMaxRpm = automaticTransmissionSettings.get().cruisingShiftOffsetsByTachMaxRpm,
                racingReturnThrottlePercent = automaticTransmissionSettings.get().racingReturnThrottlePercent,
                racingEnterMinThrottlePercent = automaticTransmissionSettings.get().racingEnterMinThrottlePercent,
                kickdownStompDeltaPercent = automaticTransmissionSettings.get().kickdownStompDeltaPercent,
                kickdownStompMinThrottlePercent = automaticTransmissionSettings.get().kickdownStompMinThrottlePercent,
                racingEnterDelayMilliseconds = automaticTransmissionSettings.get().racingEnterDelayMilliseconds,
                automaticUpshiftMilliseconds = automaticTransmissionSettings.get().automaticUpshiftMilliseconds,
                automaticDownshiftMilliseconds = automaticTransmissionSettings.get().automaticDownshiftMilliseconds,
                racingReturnHoldSeconds = automaticTransmissionSettings.get().racingReturnHoldSeconds,
                manualRedlineHoldSeconds = automaticTransmissionSettings.get().manualRedlineHoldSeconds,
                manualAutodownshiftRpm = automaticTransmissionSettings.get().manualAutodownshiftRpm,
                tachometerCruisingShiftRangeOverlayEnabled =
                    automaticTransmissionSettings.get().tachometerCruisingShiftRangeOverlayEnabled,
                lowSpeedCrawlRpmHoldEnabled =
                    automaticTransmissionSettings.get().lowSpeedCrawlRpmHoldEnabled,
                transmissionLockedToVehicle = transmission.lockedToVehicle,
                carAudioReady = isSelectedCarAudioReady(selected.id),
                masterOutputLinear = if (audioEngine.isAudioActive() && !audioMuted.get()) {
                    audioEngine.masterOutputLinear()
                } else {
                    0f
                },
                favoriteCarIds = favoriteCarIds.get(),
                userMessage = userMessage,
            )
            nextUiSnapshotNanos = frameTimestampNanos + UI_SNAPSHOT_PERIOD_NANOS
        }
        handleAudioLoadFailures()
    }

    /**
     * The debug scenario must never modify normal selections or saved preferences. It is only an
     * ADB-driven input source used to make repeated bank audits reproducible on the same APK.
     */
    private fun applyDebugScenario(scenario: DebugScenarioOverride) {
        if (activeDebugScenarioId != scenario.scenarioId) {
            // The ADB runner is allowed to change in-memory runtime selection for a repeatable
            // audit, but it must leave the driver's saved car and listener choice untouched.
            debugScenarioBaseline = DebugScenarioBaseline(
                profile = selectedProfile.get(),
                perspective = selectedPerspective.get(),
            )
            activeDebugScenarioId = scenario.scenarioId
        }
        val requestedProfile = FmodBankProfiles.find(scenario.profileId)
            ?.takeIf(bankResolver::isInstalled)
        if (requestedProfile != null && requestedProfile.id != selectedProfile.get().id) {
            synchronized(lifecycleLock) {
                if (requestedProfile.id != selectedProfile.get().id) {
                    selectedProfile.set(requestedProfile)
                    applyCarAudioPreferences(requestedProfile)
                    loadPhysics(requestedProfile)
                    simulation.reset()
                    consumedDebugScenarioShiftSerial = 0L
                    audioEngine.setSoundProgram(requestedProfile, selectedPerspective.get())
                }
            }
        }
        val requestedPerspective = EngineSoundPerspective.entries.getOrNull(scenario.perspectiveOrdinal)
            ?: EngineSoundPerspective.CABIN
        if (requestedPerspective != selectedPerspective.get()) {
            selectedPerspective.set(requestedPerspective)
            val pure = exteriorAudioModeRepository.load(selectedProfile.get()) &&
                requestedPerspective == EngineSoundPerspective.EXTERIOR
            exteriorPureAudio.set(pure)
            audioEngine.setExteriorPureAudio(pure)
            audioEngine.setSoundProgram(selectedProfile.get(), requestedPerspective)
        }
        if (scenario.forceAuthoredBankEffects) {
            // The modded-bank audit must exercise authored gear/backfire events rather than the
            // driver's persistent replacement samples. This is debug-scenario-only and the
            // saved per-car controls are restored as soon as the scenario finishes.
            audioEngine.setBackfireAudioEnabled(true)
            audioEngine.setBackfireUseOriginal(true)
            audioEngine.setShiftSoundEnabled(true)
            audioEngine.setShiftSoundOverride(false)
            audioEngine.setTransmissionAudioEnabled(true)
            audioEngine.setTurboAudioEnabled(true)
        }
    }

    private fun restoreDebugScenarioIfNeeded() {
        val baseline = debugScenarioBaseline ?: return
        debugScenarioBaseline = null
        activeDebugScenarioId = 0L
        consumedDebugScenarioShiftSerial = 0L

        synchronized(lifecycleLock) {
            if (baseline.profile.id != selectedProfile.get().id) {
                selectedProfile.set(baseline.profile)
                loadPhysics(baseline.profile)
                simulation.reset()
            }
            applyCarAudioPreferences(
                profile = baseline.profile,
                perspectiveOverride = baseline.perspective,
            )
            applyEffectSoundOverrides()
            audioEngine.setSoundProgram(selectedProfile.get(), selectedPerspective.get())
        }
    }

    private fun handleAudioLoadFailures() {
        val failure = audioEngine.consumeLoadFailure() ?: return
        if (failure.profileId == selectedProfile.get().id) {
            userMessage = UserVisibleMessage(
                id = SystemClock.elapsedRealtime(),
                title = "Engine audio failed to load",
                detail = "${selectedProfile.get().displayName}: ${failure.detail}",
            )
        }
    }

    private fun handleAudioFocusChange(event: AudioFocusEvent) {
        if (audioEngine.isLoudnessCalibrationRunning()) return

        when (event) {
            AudioFocusEvent.TRANSIENT_LOSS, AudioFocusEvent.TRANSIENT_DUCK -> {
                audioInterrupted.set(true)
                audioEngine.stop()
            }
            AudioFocusEvent.TRANSIENT_GAIN -> {
                audioInterrupted.set(false)
                if (running.get() && !audioMuted.get()) audioEngine.start()
            }
            AudioFocusEvent.PERMANENT_LOSS -> {
                audioInterrupted.set(true)
                audioEngine.stop()
            }
        }
    }

    private fun isCurrent(runId: Long): Boolean = running.get() && generation.get() == runId
    private fun joinLoop(thread: Thread) { if (thread !== Thread.currentThread()) runCatching { thread.join(500L) } }

    private data class SimulatedPedalInput(val throttle: Double = 0.0, val brake: Double = 0.0)

    private companion object {
        const val UI_SNAPSHOT_PERIOD_NANOS = 16_666_667L
        const val INTERRUPTED_IDLE_NANOS = 50_000_000L
    }
}

internal fun resolveInputSourceUi(selectedMode: InputMode, vehicleAvailable: Boolean): InputSourceUiState {
    val activeMode = if (selectedMode == InputMode.RealPedals && vehicleAvailable) selectedMode else InputMode.SimulatedPedals
    return InputSourceUiState(
        primaryLabel = activeMode.primaryLabel,
        secondaryLabel = activeMode.secondaryLabel,
        isRealPedals = activeMode == InputMode.RealPedals,
        faded = !vehicleAvailable,
    )
}

internal data class InputSourceUiState(
    val primaryLabel: String,
    val secondaryLabel: String,
    val isRealPedals: Boolean,
    val faded: Boolean,
)
