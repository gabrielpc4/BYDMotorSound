package com.gabrielpc.enginesoundsimulator.drive

import android.content.Context
import com.gabrielpc.enginesoundsimulator.AppBuildInfo
import com.gabrielpc.enginesoundsimulator.AppPreferenceStores
import com.gabrielpc.enginesoundsimulator.audio.AudioMixGainRepository
import com.gabrielpc.enginesoundsimulator.audio.AudioMixGains
import com.gabrielpc.enginesoundsimulator.audio.CarEffectModes
import com.gabrielpc.enginesoundsimulator.audio.CarEffectModesRepository
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
        val virtualGearCountRepository = VirtualGearCountRepository(appContext)
        val minimumAudioThrottleRepository = MinimumAudioThrottleRepository(appContext)
        val automaticTransmissionSettingsRepository = AutomaticTransmissionSettingsRepository(appContext)
        val backfireSettingsRepository = BackfireSettingsRepository(appContext)
        val shiftSoundSettingsRepository = ShiftSoundSettingsRepository(appContext)
        val mixerGlobalGainRepository = MixerGlobalGainRepository(appContext)
        val audioMixGainRepository = AudioMixGainRepository(appContext)
        val mixerCarSpecificGainRepository = MixerCarSpecificGainRepository(appContext)
        val carEffectModesRepository = CarEffectModesRepository(appContext)
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
                put("virtualForwardGearCount", virtualGearCountRepository.load())
                put("carPickerGroup", carPickerGroup)
                put("favoriteCarIds", JSONArray(carFavoritesRepository.load().sorted()))
                put("minimumAudioThrottle", minimumAudioThrottleToJson(minimumAudioThrottleRepository.load()))
                put("automaticTransmission", automaticTransmissionToJson(automaticTransmissionSettingsRepository.load()))
                put("backfire", backfireToJson(backfireSettingsRepository.load()))
                put("shiftSoundGlobalOverride", shiftSoundSettingsRepository.load().overrideEnabled)
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
                    put("dashboardMixGains", audioMixGainsToJson(audioMixGainRepository.load(profile)))
                    put("mixerSpecificGains", mixerCarSpecificGainsToJson(mixerCarSpecificGainRepository.load(profile)))
                    put("effectModes", carEffectModesToJson(carEffectModesRepository.load(profile)))
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

    private fun audioMixGainsToJson(gains: AudioMixGains): JSONObject {
        return JSONObject().apply {
            put("engineHost", gains.engineHost.toDouble())
            put("effectsHost", gains.effectsHost.toDouble())
            put("transmission", gains.transmission.toDouble())
            put("gearShift", gains.gearShift.toDouble())
            put("turbo", gains.turbo.toDouble())
            put("backfire", gains.backfire.toDouble())
            put("limiter", gains.limiter.toDouble())
        }
    }

    private fun mixerGlobalGainsToJson(gains: MixerGlobalGains): JSONObject {
        return JSONObject().apply {
            put("engineInterior", gains.engineInterior.toDouble())
            put("engineExterior", gains.engineExterior.toDouble())
            put("effectsHost", gains.effectsHost.toDouble())
            put("transmission", gains.transmission.toDouble())
            put("gearShift", gains.gearShift.toDouble())
            put("turbo", gains.turbo.toDouble())
            put("backfire", gains.backfire.toDouble())
            put("limiter", gains.limiter.toDouble())
        }
    }

    private fun mixerCarSpecificGainsToJson(gains: MixerCarSpecificGains): JSONObject {
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
        }
    }

    private fun carEffectModesToJson(modes: CarEffectModes): JSONObject {
        return JSONObject().apply {
            put("popsAndBangsEnabled", modes.popsAndBangsEnabled)
            put("popsAndBangsOverride", modes.popsAndBangsOverride)
            put("shiftSoundsEnabled", modes.shiftSoundsEnabled)
            put("shiftSoundsOverride", modes.shiftSoundsOverride)
            put("transmissionEnabled", modes.transmissionEnabled)
            put("turboEnabled", modes.turboEnabled)
        }
    }

    private fun minimumAudioThrottleToJson(settings: MinimumAudioThrottleSettings): JSONObject {
        return JSONObject().apply {
            put("minimum", settings.minimum.toDouble())
            put("rampUpMilliseconds", settings.rampUpMilliseconds)
            put("rampDownMilliseconds", settings.rampDownMilliseconds)
        }
    }

    private fun automaticTransmissionToJson(settings: AutomaticTransmissionSettings): JSONObject {
        return JSONObject().apply {
            put("cruisingLogicEnabled", settings.cruisingLogicEnabled)
            put("sixGearOnLaunchEnabled", settings.sixGearOnLaunchEnabled)
            put("cruisingShiftOffsetRpm", settings.cruisingShiftOffsetRpm)
            put("racingReturnThrottlePercent", settings.racingReturnThrottlePercent)
            put("racingReturnHoldSeconds", settings.racingReturnHoldSeconds)
            put("manualRedlineHoldSeconds", settings.manualRedlineHoldSeconds)
            put("manualAutodownshiftRpm", settings.manualAutodownshiftRpm)
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
