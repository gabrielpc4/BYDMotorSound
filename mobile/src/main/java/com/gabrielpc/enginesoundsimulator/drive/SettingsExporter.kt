package com.gabrielpc.enginesoundsimulator.drive

import android.content.Context
import com.gabrielpc.enginesoundsimulator.AppBuildInfo
import com.gabrielpc.enginesoundsimulator.AppPreferenceStores
import com.gabrielpc.enginesoundsimulator.audio.EngineSoundPerspective
import com.gabrielpc.enginesoundsimulator.audio.EngineSoundPerspectiveRepository
import com.gabrielpc.enginesoundsimulator.audio.ExteriorAudioModeRepository
import com.gabrielpc.enginesoundsimulator.audio.FmodBankProfiles
import com.gabrielpc.enginesoundsimulator.audio.FmodBankResolver
import com.gabrielpc.enginesoundsimulator.audio.FmodUpdateRateRepository
import com.gabrielpc.enginesoundsimulator.audio.MixerCarSpecificGainRepository
import com.gabrielpc.enginesoundsimulator.audio.MixerCarSpecificGains
import com.gabrielpc.enginesoundsimulator.audio.MixerGlobalGainRepository
import com.gabrielpc.enginesoundsimulator.audio.MixerGlobalGains
import com.gabrielpc.enginesoundsimulator.audio.SelectedCarRepository
import org.json.JSONArray
import org.json.JSONObject
import com.gabrielpc.enginesoundsimulator.simulation.VirtualGearProfile
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Writes a JSON snapshot of every persisted app preference to internal app storage. */
internal object SettingsExporter {
    private const val EXPORT_VERSION = 1
    private const val EXPORT_DIR = "settings_exports"

    fun export(context: Context): String {
        val appContext = context.applicationContext
        val bankResolver = FmodBankResolver(appContext)
        val installedProfiles = FmodBankProfiles.all.filter(bankResolver::isInstalled)

        val selectedCarRepository = SelectedCarRepository(appContext)
        val shiftModeRepository = ShiftModeRepository(appContext)
        val fmodUpdateRateRepository = FmodUpdateRateRepository(appContext)
        val gearProfileSelectionRepository = GearProfileSelectionRepository(appContext)
        val virtualGearSpeedBoundariesRepository = VirtualGearSpeedBoundariesRepository(appContext)
        val speedAudioSettingsRepository = SpeedAudioSettingsRepository(appContext)
        val minimumAudioThrottleRepository = MinimumAudioThrottleRepository(appContext)
        val automaticTransmissionSettingsRepository = AutomaticTransmissionSettingsRepository(appContext)
        val backfireSettingsRepository = BackfireSettingsRepository(appContext)
        val effectSoundOverrideRepository = EffectSoundOverrideRepository(appContext)
        val mixerGlobalGainRepository = MixerGlobalGainRepository(appContext)
        val effectSoundOverrideGainRepository = EffectSoundOverrideGainRepository(appContext)
        val mixerCarSpecificGainRepository = MixerCarSpecificGainRepository(appContext)
        val soundPerspectiveRepository = EngineSoundPerspectiveRepository(appContext)
        val exteriorAudioModeRepository = ExteriorAudioModeRepository(appContext)
        val carFavoritesRepository = CarFavoritesRepository(appContext)

        val carPickerGroup = appContext
            .getSharedPreferences(AppPreferenceStores.CAR_PICKER_GROUP, Context.MODE_PRIVATE)
            .getString("selected", null)

        val root = JSONObject()
        root.put("exportVersion", EXPORT_VERSION)
        root.put("exportedAtEpochMs", System.currentTimeMillis())
        root.put("buildNumber", AppBuildInfo.buildNumber)
        root.put(
            "global",
            JSONObject().apply {
                put("selectedCarId", selectedCarRepository.load().id)
                put("manualShiftEnabled", shiftModeRepository.isManualEnabled())
                put("fmodUpdateRateHz", fmodUpdateRateRepository.load())
                put("gearProfileSelection", GearProfileSelection.toPersisted(gearProfileSelectionRepository.load()))
                put("virtualGearSpeedBoundaries", virtualGearSpeedBoundariesToJson(virtualGearSpeedBoundariesRepository.load()))
                put(
                    "virtualForwardGearCount",
                    gearProfileSelectionRepository.load().virtualCountOrNull()
                        ?: VirtualGearProfile.DEFAULT_VIRTUAL_GEARS,
                )
                put("carPickerGroup", carPickerGroup)
                put("favoriteCarIds", JSONArray(carFavoritesRepository.load().sorted()))
                put("minimumAudioThrottle", minimumAudioThrottleToJson(minimumAudioThrottleRepository.load()))
                put("speedAudio", speedAudioToJson(speedAudioSettingsRepository.load()))
                put("automaticTransmission", automaticTransmissionToJson(automaticTransmissionSettingsRepository.load()))
                put("backfire", backfireToJson(backfireSettingsRepository.load()))
                put("effectSoundOverrides", effectSoundOverridesToJson(effectSoundOverrideRepository.load()))
                put("mixerGlobalGains", mixerGlobalGainsToJson(mixerGlobalGainRepository.load()))
            },
        )

        val cars = JSONObject()
        installedProfiles.forEach { profile ->
            cars.put(
                profile.id,
                JSONObject().apply {
                    put("displayName", profile.displayName)
                    put("packGroup", profile.packGroup)
                    put("effectSoundOverrideGains", effectSoundOverrideGainsToJson(effectSoundOverrideGainRepository.load(profile)))
                    put(
                        "mixerSpecificGains",
                        JSONObject().apply {
                            put(
                                "cabin",
                                mixerCarSpecificGainsToJson(
                                    mixerCarSpecificGainRepository.load(profile, EngineSoundPerspective.CABIN),
                                ),
                            )
                            put(
                                "exterior",
                                mixerCarSpecificGainsToJson(
                                    mixerCarSpecificGainRepository.load(profile, EngineSoundPerspective.EXTERIOR),
                                ),
                            )
                        },
                    )
                    put("engineSoundPerspective", soundPerspectiveRepository.load(profile).name)
                    put("exteriorPureAudio", exteriorAudioModeRepository.load(profile))
                },
            )
        }
        root.put("cars", cars)

        val exportDir = File(appContext.filesDir, EXPORT_DIR)
        exportDir.mkdirs()

        val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val outputFile = File(exportDir, "settings-export-$timestamp.json")
        outputFile.writeText(root.toString(2))

        return outputFile.absolutePath
    }

    private fun effectSoundOverrideGainsToJson(gains: EffectSoundOverrideGains): JSONObject {
        return JSONObject().apply {
            put("shift", gains.shiftGain.toDouble())
            put("backfire", gains.backfireGain.toDouble())
        }
    }

    private fun mixerGlobalGainsToJson(gains: MixerGlobalGains): JSONObject {
        return JSONObject().apply {
            put("overall", gains.overall.toDouble())
            put("engineInterior", gains.engineInterior.toDouble())
            put("engineExterior", gains.engineExterior.toDouble())
            put("effectsHost", gains.effectsHost.toDouble())
            put("transmission", gains.transmission.toDouble())
            put("gearShift", gains.gearShift.toDouble())
            put("turbo", gains.turbo.toDouble())
            put("backfire", gains.backfire.toDouble())
            put("limiter", gains.limiter.toDouble())
            put("supercharger", gains.supercharger.toDouble())
            put("backfireOverrideGain", gains.backfireOverrideGain.toDouble())
            put("shiftOverrideGain", gains.shiftOverrideGain.toDouble())
        }
    }

    private fun mixerCarSpecificGainsToJson(gains: MixerCarSpecificGains): JSONObject {
        return JSONObject().apply {
            put("overall", gains.overall.toDouble())
            put("engineIdle", gains.engineIdle.toDouble())
            put("engineInterior", gains.engineInterior.toDouble())
            put("engineExterior", gains.engineExterior.toDouble())
            put("effectsHost", gains.effectsHost.toDouble())
            put("transmission", gains.transmission.toDouble())
            put("gearShift", gains.gearShift.toDouble())
            put("turbo", gains.turbo.toDouble())
            put("backfire", gains.backfire.toDouble())
            put("limiter", gains.limiter.toDouble())
            put("supercharger", gains.supercharger.toDouble())
        }
    }

    private fun effectSoundOverridesToJson(overrides: EffectSoundOverrideSettings): JSONObject {
        return JSONObject().apply {
            put("popsAndBangsOverride", overrides.popsAndBangsOverride)
            put("shiftSoundsOverride", overrides.shiftSoundsOverride)
        }
    }

    private fun minimumAudioThrottleToJson(settings: MinimumAudioThrottleSettings): JSONObject {
        return JSONObject().apply {
            put("minimum", settings.minimum.toDouble())
            put("rampUpMilliseconds", settings.rampUpMilliseconds)
            put("rampDownMilliseconds", settings.rampDownMilliseconds)
        }
    }

    private fun speedAudioToJson(settings: SpeedAudioSettings): JSONObject {
        val normalized = settings.normalized()
        return JSONObject().apply {
            put(
                "curvePoints",
                JSONArray().apply {
                    normalized.curvePoints.forEach { point ->
                        put(
                            JSONObject().apply {
                                put("rpm", point.rpm)
                                put("gainOffset", point.gainOffset.toDouble())
                            },
                        )
                    }
                },
            )
            put("speedGainCoefficient", normalized.speedGainCoefficient.toDouble())
        }
    }

    private fun virtualGearSpeedBoundariesToJson(settings: VirtualGearSpeedBoundariesSettings): JSONObject {
        val normalized = settings.normalized()
        return JSONObject().apply {
            VirtualGearSpeedBoundaries.PRESETS.forEach { preset ->
                put(
                    preset.toString(),
                    JSONArray(normalized.boundariesFor(preset)),
                )
            }
        }
    }

    private fun automaticTransmissionToJson(settings: AutomaticTransmissionSettings): JSONObject {
        return JSONObject().apply {
            put("cruisingLogicEnabled", settings.cruisingLogicEnabled)
            put("allowManualOnLaunchEnabled", settings.allowManualOnLaunchEnabled)
            put("manualTransmissionKickdownEnabled", settings.manualTransmissionKickdownEnabled)
            put(
                "cruisingShiftOffsetsByTachMaxRpm",
                JSONObject().apply {
                    CruisingShiftOffsetByTachMaxRpm.TIERS.forEach { tier ->
                        put(
                            tier.toString(),
                            settings.cruisingShiftOffsetsByTachMaxRpm[tier]
                                ?: CruisingShiftOffsetByTachMaxRpm.defaultOffsets().getValue(tier),
                        )
                    }
                },
            )
            put("racingReturnThrottlePercent", settings.racingReturnThrottlePercent)
            put("kickdownStompDeltaPercent", settings.kickdownStompDeltaPercent)
            put("kickdownStompMinThrottlePercent", settings.kickdownStompMinThrottlePercent)
            put("racingEnterDelayMilliseconds", settings.racingEnterDelayMilliseconds)
            put("automaticUpshiftMilliseconds", settings.automaticUpshiftMilliseconds)
            put("automaticDownshiftMilliseconds", settings.automaticDownshiftMilliseconds)
            put("racingReturnHoldSeconds", settings.racingReturnHoldSeconds)
            put("manualRedlineHoldSeconds", settings.manualRedlineHoldSeconds)
            put("manualAutodownshiftRpm", settings.manualAutodownshiftRpm)
            put("tachometerCruisingShiftRangeOverlayEnabled", settings.tachometerCruisingShiftRangeOverlayEnabled)
        }
    }

    private fun backfireToJson(settings: BackfireSettings): JSONObject {
        return JSONObject().apply {
            put("soundOnlyOverrideEnabled", settings.soundOnlyOverrideEnabled)
            put("allowParkNeutralOverride", settings.allowParkNeutralOverride)
            put("armThrottle", settings.armThrottle)
            put("releaseThrottle", settings.releaseThrottle)
            put("releaseDelaySeconds", settings.releaseDelaySeconds)
            put("minimumRpm", settings.minimumRpm)
            put("maximumRpm", settings.maximumRpm)
            put("allowedSamples", JSONArray(settings.allowedSamples.sorted()))
        }
    }
}
