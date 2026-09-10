package com.gabrielpc.enginesoundsimulator.audio

import android.content.Context
import com.gabrielpc.enginesoundsimulator.AppPreferenceStores

data class CatalogGainSettings(
    val applyLufsNormalization: Boolean = false,
    val applyIphoneAcousticAdjustment: Boolean = false,
    val manualLoudnessEnabled: Boolean = true,
    val savedApplyLufsNormalization: Boolean = true,
    val savedApplyIphoneAcousticAdjustment: Boolean = true,
) {
    // Manual per-car trim is the only loudness stage the dashboard exposes, so it always owns the
    // master gain and the catalog-wide LUFS/iPhone stages stay bypassed.
    fun normalized(): CatalogGainSettings = copy(
        manualLoudnessEnabled = true,
        applyLufsNormalization = false,
        applyIphoneAcousticAdjustment = false,
    )
}

enum class CatalogGainEntryStatus {
    ACTIVE,
    MISSING,
    STALE,
    FAILED,
    SKIPPED,
}

data class CatalogGainTableEntry(
    val carName: String,
    val profileId: String,
    val perspective: EngineSoundPerspective,
    val lufsNormalizationDb: Double?,
    val lufsStatus: CatalogGainEntryStatus,
    val iphoneAcousticAdjustmentDb: Double?,
    val iphoneStatus: CatalogGainEntryStatus,
)

internal class CatalogGainSettingsRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        AppPreferenceStores.CATALOG_GAIN_SETTINGS,
        Context.MODE_PRIVATE,
    )

    fun load(): CatalogGainSettings {
        return CatalogGainSettings(
            applyLufsNormalization = preferences.getBoolean(KEY_APPLY_LUFS, false),
            applyIphoneAcousticAdjustment = preferences.getBoolean(KEY_APPLY_IPHONE, false),
            manualLoudnessEnabled = preferences.getBoolean(KEY_MANUAL_ENABLED, true),
            savedApplyLufsNormalization = preferences.getBoolean(KEY_SAVED_APPLY_LUFS, true),
            savedApplyIphoneAcousticAdjustment = preferences.getBoolean(KEY_SAVED_APPLY_IPHONE, true),
        ).normalized()
    }

    fun save(settings: CatalogGainSettings) {
        val normalized = settings.normalized()
        preferences.edit()
            .putBoolean(KEY_APPLY_LUFS, normalized.applyLufsNormalization)
            .putBoolean(KEY_APPLY_IPHONE, normalized.applyIphoneAcousticAdjustment)
            .putBoolean(KEY_MANUAL_ENABLED, normalized.manualLoudnessEnabled)
            .putBoolean(KEY_SAVED_APPLY_LUFS, normalized.savedApplyLufsNormalization)
            .putBoolean(KEY_SAVED_APPLY_IPHONE, normalized.savedApplyIphoneAcousticAdjustment)
            .commit()
    }

    fun reset() {
        preferences.edit().clear().commit()
    }

    private companion object {
        const val KEY_APPLY_LUFS = "apply_lufs_normalization"
        const val KEY_APPLY_IPHONE = "apply_iphone_acoustic_adjustment"
        const val KEY_MANUAL_ENABLED = "manual_loudness_enabled"
        const val KEY_SAVED_APPLY_LUFS = "saved_apply_lufs_normalization"
        const val KEY_SAVED_APPLY_IPHONE = "saved_apply_iphone_acoustic_adjustment"
    }
}

internal object CatalogGainCatalog {
    fun build(
        profiles: List<FmodBankProfile>,
        fingerprints: Map<LoudnessCalibrationKey, String>,
        loudnessRecords: List<LoudnessCalibrationRecord>,
        acousticRecords: List<AcousticDiagnosticRecord>,
    ): List<CatalogGainTableEntry> {
        val lufsByKey = loudnessRecords.associateBy(LoudnessCalibrationRecord::key)
        val acousticByKey = acousticRecords.associateBy(AcousticDiagnosticRecord::key)

        return profiles
            .sortedBy { it.displayName.lowercase() }
            .flatMap { profile ->
                EngineSoundPerspective.entries.map { perspective ->
                    buildEntry(
                        profile = profile,
                        perspective = perspective,
                        fingerprint = fingerprints[LoudnessCalibrationKey(profile.id, profile.packGroup, perspective)],
                        lufsRecord = lufsByKey[LoudnessCalibrationKey(profile.id, profile.packGroup, perspective)],
                        acousticRecord = acousticByKey[LoudnessCalibrationKey(profile.id, profile.packGroup, perspective)],
                    )
                }
            }
    }

    private fun buildEntry(
        profile: FmodBankProfile,
        perspective: EngineSoundPerspective,
        fingerprint: String?,
        lufsRecord: LoudnessCalibrationRecord?,
        acousticRecord: AcousticDiagnosticRecord?,
    ): CatalogGainTableEntry {
        if (!LoudnessCalibrationPolicy.shouldMeasure(profile.id, perspective)) {
            return CatalogGainTableEntry(
                carName = profile.displayName,
                profileId = profile.id,
                perspective = perspective,
                lufsNormalizationDb = null,
                lufsStatus = CatalogGainEntryStatus.SKIPPED,
                iphoneAcousticAdjustmentDb = null,
                iphoneStatus = CatalogGainEntryStatus.SKIPPED,
            )
        }

        val lufsStatus = when {
            lufsRecord == null -> CatalogGainEntryStatus.MISSING
            fingerprint == null -> CatalogGainEntryStatus.MISSING
            !LoudnessCalibrationRecordValidity.isValid(lufsRecord, fingerprint) -> CatalogGainEntryStatus.STALE
            else -> CatalogGainEntryStatus.ACTIVE
        }
        val iphoneStatus = when {
            lufsStatus != CatalogGainEntryStatus.ACTIVE -> CatalogGainEntryStatus.MISSING
            acousticRecord == null -> CatalogGainEntryStatus.MISSING
            !acousticRecord.valid -> CatalogGainEntryStatus.FAILED
            fingerprint == null || lufsRecord == null ||
                acousticRecord.bankFingerprint != fingerprint ||
                acousticRecord.normalizationDb.toBits() != lufsRecord.normalizationDb.toBits() ->
                CatalogGainEntryStatus.STALE
            else -> CatalogGainEntryStatus.ACTIVE
        }

        return CatalogGainTableEntry(
            carName = profile.displayName,
            profileId = profile.id,
            perspective = perspective,
            lufsNormalizationDb = lufsRecord?.normalizationDb?.takeIf { lufsStatus != CatalogGainEntryStatus.MISSING },
            lufsStatus = lufsStatus,
            iphoneAcousticAdjustmentDb = acousticRecord?.acousticAdjustmentDb?.takeIf {
                iphoneStatus == CatalogGainEntryStatus.ACTIVE
            },
            iphoneStatus = iphoneStatus,
        )
    }
}
