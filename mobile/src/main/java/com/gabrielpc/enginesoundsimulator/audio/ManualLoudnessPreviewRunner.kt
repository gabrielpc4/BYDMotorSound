package com.gabrielpc.enginesoundsimulator.audio

import android.content.Context
import java.util.concurrent.atomic.AtomicBoolean

internal class ManualLoudnessPreviewRunner(
    context: Context,
    private val profilesById: Map<String, FmodBankProfile>,
    private val perspectiveForProfileId: (String) -> EngineSoundPerspective,
    private val activeProfileIds: () -> Set<String>,
    private val adjustmentDbFor: (FmodBankProfile, EngineSoundPerspective) -> Double,
    private val appVolumeLinearProvider: () -> Float,
    private val cancellationRequested: AtomicBoolean,
    private val onProfileSweepCompleted: (String) -> Unit,
) {
    private val appContext = context.applicationContext
    private val bankResolver = FmodBankResolver(appContext)

    fun run() {
        if (profilesById.isEmpty()) {
            return
        }

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

            while (!cancellationRequested.get()) {
                val activeIds = activeProfileIds().toList()
                if (activeIds.isEmpty()) {
                    return
                }
                activeIds.forEach { profileId ->
                    checkCancelled()
                    if (!activeProfileIds().contains(profileId)) {
                        return@forEach
                    }
                    val profile = profilesById[profileId] ?: run {
                        onProfileSweepCompleted(profileId)
                        return@forEach
                    }
                    val perspective = perspectiveForProfileId(profileId)
                    if (!LoudnessCalibrationPolicy.shouldMeasure(profile.id, perspective)) {
                        onProfileSweepCompleted(profileId)
                        return@forEach
                    }
                    val masterGainProvider = {
                        val manualLinear = LoudnessNormalizationMath
                            .dbToLinear(adjustmentDbFor(profile, perspective))
                            .toFloat()
                        manualLinear * appVolumeLinearProvider()
                    }
                    LoudnessCalibrationStimulusPlayer.playCalibrationSweep(
                        bridge = bridge,
                        bankResolver = bankResolver,
                        profile = profile,
                        perspective = perspective,
                        masterGainLinear = masterGainProvider(),
                        cancellationRequested = cancellationRequested,
                        masterGainProvider = masterGainProvider,
                    )
                    onProfileSweepCompleted(profileId)
                }
            }
        } finally {
            runCatching { bridge.setCalibrationOutputMuted(true) }
            runCatching { bridge.unloadCalibrationCar() }
            if (fmodInitialized) {
                runCatching { org.fmod.FMOD.close() }
            }
        }
    }

    private fun checkCancelled() {
        if (cancellationRequested.get() || Thread.currentThread().isInterrupted) {
            throw ManualLoudnessPreviewCancelledException()
        }
    }
}
