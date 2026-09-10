package com.gabrielpc.enginesoundsimulator.audio

import android.content.Context
import com.gabrielpc.enginesoundsimulator.AppPreferenceStores
import kotlin.math.roundToInt

data class ManualLoudnessTableEntry(
    val profileId: String,
    val carName: String,
    val previewAssetName: String,
    val interiorAdjustmentDb: Double,
    val exteriorAdjustmentDb: Double,
    val previewActive: Boolean,
    val previewExterior: Boolean,
) {
    val activePerspective: EngineSoundPerspective
        get() {
            if (previewExterior) {
                return EngineSoundPerspective.EXTERIOR
            }

            return EngineSoundPerspective.CABIN
        }

    val activeAdjustmentDb: Double
        get() {
            if (previewExterior) {
                return exteriorAdjustmentDb
            }

            return interiorAdjustmentDb
        }

    companion object {
        const val MIN_DB = -20.0
        const val MAX_DB = 20.0
        const val SLIDER_STEPS = 80
    }
}

internal class ManualLoudnessRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        AppPreferenceStores.MANUAL_LOUDNESS,
        Context.MODE_PRIVATE,
    )

    fun hasDb(key: LoudnessCalibrationKey): Boolean =
        preferences.contains(recordKey(key))

    fun loadDb(key: LoudnessCalibrationKey): Double {
        return preferences.getFloat(recordKey(key), 0f).toDouble().coerceIn(
            ManualLoudnessTableEntry.MIN_DB,
            ManualLoudnessTableEntry.MAX_DB,
        )
    }

    fun saveDb(key: LoudnessCalibrationKey, adjustmentDb: Double) {
        val clamped = adjustmentDb.coerceIn(
            ManualLoudnessTableEntry.MIN_DB,
            ManualLoudnessTableEntry.MAX_DB,
        )
        preferences.edit()
            .putFloat(recordKey(key), clamped.toFloat())
            .commit()
    }

    fun hasPreviewExterior(profileId: String, packGroup: String): Boolean =
        preferences.contains(previewExteriorKey(profileId, packGroup))

    fun loadPreviewExterior(profileId: String, packGroup: String): Boolean {
        return preferences.getBoolean(previewExteriorKey(profileId, packGroup), false)
    }

    fun savePreviewExterior(profileId: String, packGroup: String, exterior: Boolean) {
        preferences.edit()
            .putBoolean(previewExteriorKey(profileId, packGroup), exterior)
            .commit()
    }

    fun reset() {
        preferences.edit().clear().commit()
    }

    private fun recordKey(key: LoudnessCalibrationKey): String =
        "${key.profileId}|${key.packGroup}|${key.perspective.name}"

    private fun previewExteriorKey(profileId: String, packGroup: String): String =
        "$profileId|$packGroup|preview_exterior"

    companion object {
        fun sliderDbFromPercent(percent: Float): Double {
            val normalized = (percent / ManualLoudnessTableEntry.SLIDER_STEPS.toDouble())
                .coerceIn(0.0, 1.0)

            return ManualLoudnessTableEntry.MIN_DB +
                normalized * (ManualLoudnessTableEntry.MAX_DB - ManualLoudnessTableEntry.MIN_DB)
        }

        fun sliderPercentFromDb(db: Double): Float {
            val clamped = db.coerceIn(ManualLoudnessTableEntry.MIN_DB, ManualLoudnessTableEntry.MAX_DB)
            val normalized = (clamped - ManualLoudnessTableEntry.MIN_DB) /
                (ManualLoudnessTableEntry.MAX_DB - ManualLoudnessTableEntry.MIN_DB)

            return (normalized * ManualLoudnessTableEntry.SLIDER_STEPS).roundToInt().toFloat()
        }
    }
}
