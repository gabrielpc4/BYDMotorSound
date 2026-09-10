package com.gabrielpc.enginesoundsimulator.audio

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal object ManualLoudnessPreset {
    const val PRESET_VERSION = 1
    const val EXPORT_FILE_NAME = "manual_loudness_preset.json"
    const val FACTORY_ASSET_NAME = "manual_loudness_factory_defaults.json"

    fun exportCurrent(
        context: Context,
        repository: ManualLoudnessRepository,
        profiles: List<FmodBankProfile>,
        manualLoudnessEnabled: Boolean,
    ): File {
        val preset = buildPreset(repository, profiles, manualLoudnessEnabled)
        val exportFile = presetFile(context, EXPORT_FILE_NAME)
        exportFile.writeText(preset.toString(2))
        return exportFile
    }

    fun restoreFactoryDefaults(context: Context, repository: ManualLoudnessRepository) {
        val factoryPreset = loadFactoryDefaults(context) ?: return
        applyPreset(repository, factoryPreset)
    }

    fun applyMissingDefaults(context: Context, repository: ManualLoudnessRepository) {
        val factoryPreset = loadFactoryDefaults(context) ?: return
        applyMissingEntries(repository, factoryPreset)
    }

    fun applyPreset(repository: ManualLoudnessRepository, preset: JSONObject) {
        preset.optJSONArray("cars")?.let { cars ->
            for (index in 0 until cars.length()) {
                val car = cars.optJSONObject(index) ?: continue
                val profileId = car.optString("profileId")
                val packGroup = car.optString("packGroup")
                if (profileId.isBlank() || packGroup.isBlank()) {
                    continue
                }

                repository.saveDb(
                    LoudnessCalibrationKey(profileId, packGroup, EngineSoundPerspective.CABIN),
                    car.optDouble("interiorDb", 0.0),
                )
                repository.saveDb(
                    LoudnessCalibrationKey(profileId, packGroup, EngineSoundPerspective.EXTERIOR),
                    car.optDouble("exteriorDb", 0.0),
                )
                repository.savePreviewExterior(
                    profileId = profileId,
                    packGroup = packGroup,
                    exterior = car.optBoolean("previewExterior", false),
                )
            }
        }
    }

    private fun applyMissingEntries(repository: ManualLoudnessRepository, preset: JSONObject) {
        preset.optJSONArray("cars")?.let { cars ->
            for (index in 0 until cars.length()) {
                val car = cars.optJSONObject(index) ?: continue
                val profileId = car.optString("profileId")
                val packGroup = car.optString("packGroup")
                if (profileId.isBlank() || packGroup.isBlank()) {
                    continue
                }

                val cabinKey = LoudnessCalibrationKey(profileId, packGroup, EngineSoundPerspective.CABIN)
                if (!repository.hasDb(cabinKey)) {
                    repository.saveDb(cabinKey, car.optDouble("interiorDb", 0.0))
                }

                val exteriorKey = LoudnessCalibrationKey(profileId, packGroup, EngineSoundPerspective.EXTERIOR)
                if (!repository.hasDb(exteriorKey)) {
                    repository.saveDb(exteriorKey, car.optDouble("exteriorDb", 0.0))
                }

                if (!repository.hasPreviewExterior(profileId, packGroup)) {
                    repository.savePreviewExterior(
                        profileId = profileId,
                        packGroup = packGroup,
                        exterior = car.optBoolean("previewExterior", false),
                    )
                }
            }
        }
    }

    private fun buildPreset(
        repository: ManualLoudnessRepository,
        profiles: List<FmodBankProfile>,
        manualLoudnessEnabled: Boolean,
    ): JSONObject {
        val cars = JSONArray()
        profiles.forEach { profile ->
            cars.put(
                JSONObject()
                    .put("profileId", profile.id)
                    .put("packGroup", profile.packGroup)
                    .put(
                        "interiorDb",
                        repository.loadDb(
                            LoudnessCalibrationKey(profile.id, profile.packGroup, EngineSoundPerspective.CABIN),
                        ),
                    )
                    .put(
                        "exteriorDb",
                        repository.loadDb(
                            LoudnessCalibrationKey(profile.id, profile.packGroup, EngineSoundPerspective.EXTERIOR),
                        ),
                    )
                    .put(
                        "previewExterior",
                        repository.loadPreviewExterior(profile.id, profile.packGroup),
                    ),
            )
        }

        return JSONObject()
            .put("version", PRESET_VERSION)
            .put(
                "exportedAt",
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date()),
            )
            .put("manualLoudnessEnabled", manualLoudnessEnabled)
            .put("cars", cars)
    }

    fun defaultDb(
        context: Context,
        profileId: String,
        packGroup: String,
        perspective: EngineSoundPerspective,
    ): Double {
        val preset = loadFactoryDefaults(context) ?: return 0.0
        val cars = preset.optJSONArray("cars") ?: return 0.0
        for (index in 0 until cars.length()) {
            val car = cars.optJSONObject(index) ?: continue
            if (car.optString("profileId") != profileId || car.optString("packGroup") != packGroup) {
                continue
            }

            return if (perspective == EngineSoundPerspective.EXTERIOR) {
                car.optDouble("exteriorDb", 0.0)
            } else {
                car.optDouble("interiorDb", 0.0)
            }
        }

        return 0.0
    }

    fun loadFactoryDefaults(context: Context): JSONObject? {
        return runCatching {
            context.applicationContext.assets.open(FACTORY_ASSET_NAME).bufferedReader().use { reader ->
                JSONObject(reader.readText())
            }
        }.getOrNull()
    }

    fun presetFile(context: Context, fileName: String): File {
        val directory = storageDirectory(context)
        directory.mkdirs()
        val destination = File(directory, fileName)
        migrateInternalPresetIfNeeded(context, fileName, destination)
        return destination
    }

    private fun storageDirectory(context: Context): File {
        val appContext = context.applicationContext
        val externalDirectory = appContext.getExternalFilesDir(null)
        if (externalDirectory != null) {
            return externalDirectory
        }

        return appContext.filesDir
    }

    private fun migrateInternalPresetIfNeeded(context: Context, fileName: String, destination: File) {
        if (destination.isFile) {
            return
        }

        val legacyFile = File(context.applicationContext.filesDir, fileName)
        if (!legacyFile.isFile) {
            return
        }

        runCatching {
            legacyFile.copyTo(destination, overwrite = false)
        }
    }
}
