package com.gabrielpc.enginesoundsimulator.audio

import android.content.Context
import com.gabrielpc.enginesoundsimulator.AppPreferenceStores
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import kotlin.math.min
import kotlin.math.pow

internal const val LOUDNESS_CALIBRATION_ALGORITHM_VERSION = 1

internal data class LoudnessCalibrationKey(
    val profileId: String,
    val packGroup: String,
    val perspective: EngineSoundPerspective,
) {
    val persisted: String get() = "$packGroup|$profileId|${perspective.name}"

    companion object {
        fun fromPersisted(value: String): LoudnessCalibrationKey? {
            val parts = value.split('|')
            if (parts.size != 3 || parts.any(String::isEmpty)) return null
            val perspective = runCatching {
                EngineSoundPerspective.valueOf(parts[2])
            }.getOrNull() ?: return null

            return LoudnessCalibrationKey(
                profileId = parts[1],
                packGroup = parts[0],
                perspective = perspective,
            )
        }
    }
}

internal data class LoudnessCalibrationRecord(
    val profileId: String,
    val packGroup: String,
    val perspective: EngineSoundPerspective,
    val algorithmVersion: Int,
    val bankFingerprint: String,
    val measuredIntegratedLufs: Double,
    val measuredMaxTruePeak: Double,
    val normalizationDb: Double,
    val measuredAtEpochMs: Long,
) {
    val key: LoudnessCalibrationKey
        get() = LoudnessCalibrationKey(profileId, packGroup, perspective)
}

data class LoudnessNormalizationSummary(
    val validCount: Int = 0,
    val staleCount: Int = 0,
    val missingCount: Int = 0,
    val totalCount: Int = 0,
    val targetLufs: Double? = null,
)

enum class LoudnessNormalizationValidity {
    VALID,
    STALE,
    MISSING,
}

data class LoudnessNormalizationState(
    val validity: LoudnessNormalizationValidity = LoudnessNormalizationValidity.MISSING,
    val normalizationDb: Double? = null,
) {
    val linear: Float
        get() = if (validity == LoudnessNormalizationValidity.VALID && normalizationDb?.isFinite() == true) {
            LoudnessNormalizationMath.dbToLinear(normalizationDb).toFloat()
        } else {
            1f
        }
}

enum class LoudnessCalibrationStatus {
    IDLE,
    INTERRUPTED,
    RUNNING,
    COMPLETED,
    CANCELLED,
    FAILED,
}

data class LoudnessCalibrationProgress(
    val status: LoudnessCalibrationStatus = LoudnessCalibrationStatus.IDLE,
    val activeProfileId: String? = null,
    val activeCarName: String? = null,
    val perspective: EngineSoundPerspective? = null,
    val completedCount: Int = 0,
    val totalCount: Int = 0,
    val failedCount: Int = 0,
    val lastError: String? = null,
) {
    val isRunning: Boolean get() = status == LoudnessCalibrationStatus.RUNNING
    val canResume: Boolean get() = status == LoudnessCalibrationStatus.INTERRUPTED
    val fraction: Float get() = if (totalCount == 0) 0f else completedCount.toFloat() / totalCount
}

internal data class LoudnessCalibrationCheckpoint(
    val startedAtEpochMs: Long,
    val completedFingerprints: Map<LoudnessCalibrationKey, String>,
)

internal object LoudnessCalibrationRecordValidity {
    fun isValid(record: LoudnessCalibrationRecord?, fingerprint: String?): Boolean {
        return record != null && fingerprint != null &&
            record.algorithmVersion == LOUDNESS_CALIBRATION_ALGORITHM_VERSION &&
            record.bankFingerprint == fingerprint
    }

    fun resumableCompleted(
        checkpoint: LoudnessCalibrationCheckpoint?,
        records: Collection<LoudnessCalibrationRecord>,
        currentFingerprints: Map<LoudnessCalibrationKey, String>,
    ): Map<LoudnessCalibrationKey, String> {
        val recordsByKey = records.associateBy(LoudnessCalibrationRecord::key)

        return checkpoint?.completedFingerprints.orEmpty().filter { (key, checkpointFingerprint) ->
            currentFingerprints[key] == checkpointFingerprint &&
                isValid(recordsByKey[key], checkpointFingerprint)
        }
    }
}

internal object LoudnessNormalizationMath {
    const val TARGET_CEILING_LUFS = -18.0
    const val MAX_AMPLIFICATION_DB = 12.0

    fun targetLufs(measurements: List<Double>): Double? {
        val sorted = measurements.filter(Double::isFinite).sorted()
        if (sorted.isEmpty()) return null
        val middle = sorted.size / 2
        val median = if (sorted.size % 2 == 0) {
            (sorted[middle - 1] + sorted[middle]) / 2.0
        } else {
            sorted[middle]
        }

        return min(median, TARGET_CEILING_LUFS)
    }

    fun normalizationDb(measuredLufs: Double, targetLufs: Double): Double {
        return min(targetLufs - measuredLufs, MAX_AMPLIFICATION_DB)
    }

    fun dbToLinear(decibels: Double): Double = 10.0.pow(decibels / 20.0)
}

internal object LoudnessCalibrationFingerprint {
    fun create(
        profileId: String,
        algorithmVersion: Int,
        carBankSha256: String,
        physicsSha256: String,
        commonBankSha256: String,
        commonStringsBankSha256: String,
    ): String {
        val canonical = listOf(
            algorithmVersion.toString(),
            profileId,
            carBankSha256,
            physicsSha256,
            commonBankSha256,
            commonStringsBankSha256,
        ).joinToString("\n")
        val digest = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))

        return digest.joinToString("") { "%02x".format(it) }
    }
}

internal fun composeMasterOutputGain(
    appVolumeLinear: Float,
    normalizationLinear: Float,
    carSpecificOverallLinear: Float,
): Float {
    val components = listOf(appVolumeLinear, normalizationLinear, carSpecificOverallLinear)
    if (components.any { !it.isFinite() }) return 0f

    return components.fold(1f) { result, value -> result * value.coerceAtLeast(0f) }
}

internal class LoudnessNormalizationRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        AppPreferenceStores.LOUDNESS_NORMALIZATION,
        Context.MODE_PRIVATE,
    )

    @Synchronized
    fun records(): List<LoudnessCalibrationRecord> = preferences.all
        .filterKeys { it.startsWith(RECORD_PREFIX) }
        .values
        .mapNotNull { value -> (value as? String)?.let(::recordFromJson) }

    @Synchronized
    fun saveMeasurement(
        key: LoudnessCalibrationKey,
        fingerprint: String,
        integratedLufs: Double,
        maxTruePeak: Double,
        measuredAtEpochMs: Long,
        currentFingerprints: Map<LoudnessCalibrationKey, String>,
    ) {
        require(integratedLufs.isFinite()) { "Integrated loudness is not finite" }
        require(maxTruePeak.isFinite()) { "Maximum true peak is not finite" }
        require(currentFingerprints[key] == fingerprint) { "Calibration fingerprint is no longer current" }
        val record = LoudnessCalibrationRecord(
            profileId = key.profileId,
            packGroup = key.packGroup,
            perspective = key.perspective,
            algorithmVersion = LOUDNESS_CALIBRATION_ALGORITHM_VERSION,
            bankFingerprint = fingerprint,
            measuredIntegratedLufs = integratedLufs,
            measuredMaxTruePeak = maxTruePeak,
            normalizationDb = 0.0,
            measuredAtEpochMs = measuredAtEpochMs,
        )
        val valid = records()
            .filter { existing ->
                existing.key != key &&
                    LoudnessCalibrationRecordValidity.isValid(existing, currentFingerprints[existing.key])
            } + record
        val target = requireNotNull(
            LoudnessNormalizationMath.targetLufs(valid.map(LoudnessCalibrationRecord::measuredIntegratedLufs)),
        )
        persistNormalizations(valid, target)
    }

    @Synchronized
    fun recomputeNormalizations(currentFingerprints: Map<LoudnessCalibrationKey, String>): Double? {
        val valid = records().filter { record ->
            LoudnessCalibrationRecordValidity.isValid(record, currentFingerprints[record.key])
        }
        val target = LoudnessNormalizationMath.targetLufs(valid.map(LoudnessCalibrationRecord::measuredIntegratedLufs))
            ?: return null
        persistNormalizations(valid, target)

        return target
    }

    @Synchronized
    fun normalizationLinear(key: LoudnessCalibrationKey, fingerprint: String?): Float {
        return normalizationState(key, fingerprint).linear
    }

    @Synchronized
    fun normalizationState(
        key: LoudnessCalibrationKey,
        fingerprint: String?,
    ): LoudnessNormalizationState {
        val record = record(key) ?: return LoudnessNormalizationState()
        if (!LoudnessCalibrationRecordValidity.isValid(record, fingerprint)) {
            return LoudnessNormalizationState(LoudnessNormalizationValidity.STALE)
        }

        return LoudnessNormalizationState(
            validity = LoudnessNormalizationValidity.VALID,
            normalizationDb = record.normalizationDb,
        )
    }

    @Synchronized
    fun summary(currentFingerprints: Map<LoudnessCalibrationKey, String>): LoudnessNormalizationSummary {
        val recordsByKey = records().associateBy(LoudnessCalibrationRecord::key)
        var valid = 0
        var stale = 0
        var missing = 0
        val validRecords = mutableListOf<LoudnessCalibrationRecord>()
        currentFingerprints.forEach { (key, fingerprint) ->
            val record = recordsByKey[key]
            when {
                record == null -> missing++
                !LoudnessCalibrationRecordValidity.isValid(record, fingerprint) -> stale++
                else -> {
                    valid++
                    validRecords += record
                }
            }
        }

        return LoudnessNormalizationSummary(
            validCount = valid,
            staleCount = stale,
            missingCount = missing,
            totalCount = currentFingerprints.size,
            targetLufs = LoudnessNormalizationMath.targetLufs(
                validRecords.map(LoudnessCalibrationRecord::measuredIntegratedLufs),
            ),
        )
    }

    @Synchronized
    fun beginCheckpoint(startedAtEpochMs: Long = System.currentTimeMillis()) {
        saveCheckpoint(LoudnessCalibrationCheckpoint(startedAtEpochMs, emptyMap()))
    }

    @Synchronized
    fun markCheckpointCompleted(key: LoudnessCalibrationKey, fingerprint: String) {
        val checkpoint = checkpoint() ?: LoudnessCalibrationCheckpoint(System.currentTimeMillis(), emptyMap())
        saveCheckpoint(
            checkpoint.copy(completedFingerprints = checkpoint.completedFingerprints + (key to fingerprint)),
        )
    }

    @Synchronized
    fun checkpoint(): LoudnessCalibrationCheckpoint? {
        val encoded = preferences.getString(KEY_CHECKPOINT, null) ?: return null

        return runCatching {
            val json = JSONObject(encoded)
            val completed = linkedMapOf<LoudnessCalibrationKey, String>()
            val entries = json.optJSONArray("completed") ?: JSONArray()
            for (index in 0 until entries.length()) {
                val item = entries.getJSONObject(index)
                val key = LoudnessCalibrationKey.fromPersisted(item.getString("key")) ?: continue
                completed[key] = item.getString("fingerprint")
            }
            LoudnessCalibrationCheckpoint(
                startedAtEpochMs = json.getLong("startedAtEpochMs"),
                completedFingerprints = completed,
            )
        }.getOrNull()
    }

    @Synchronized
    fun clearCheckpoint() {
        preferences.edit().remove(KEY_CHECKPOINT).commit()
    }

    @Synchronized
    fun clearAll() {
        preferences.edit().clear().commit()
    }

    private fun record(key: LoudnessCalibrationKey): LoudnessCalibrationRecord? {
        return preferences.getString(recordKey(key), null)?.let(::recordFromJson)
    }

    private fun persistNormalizations(records: List<LoudnessCalibrationRecord>, targetLufs: Double) {
        val editor = preferences.edit()
        records.forEach { record ->
            val normalized = record.copy(
                normalizationDb = LoudnessNormalizationMath.normalizationDb(
                    measuredLufs = record.measuredIntegratedLufs,
                    targetLufs = targetLufs,
                ),
            )
            editor.putString(recordKey(record.key), recordToJson(normalized).toString())
        }
        editor.commit()
    }

    private fun saveCheckpoint(checkpoint: LoudnessCalibrationCheckpoint) {
        val json = JSONObject()
        json.put("startedAtEpochMs", checkpoint.startedAtEpochMs)
        json.put(
            "completed",
            JSONArray().apply {
                checkpoint.completedFingerprints.entries.sortedBy { it.key.persisted }.forEach { (key, fingerprint) ->
                    put(JSONObject().put("key", key.persisted).put("fingerprint", fingerprint))
                }
            },
        )
        preferences.edit().putString(KEY_CHECKPOINT, json.toString()).commit()
    }

    private fun recordKey(key: LoudnessCalibrationKey): String = RECORD_PREFIX + key.persisted

    private fun recordToJson(record: LoudnessCalibrationRecord): JSONObject = JSONObject()
        .put("profileId", record.profileId)
        .put("packGroup", record.packGroup)
        .put("perspective", record.perspective.name)
        .put("algorithmVersion", record.algorithmVersion)
        .put("bankFingerprint", record.bankFingerprint)
        .put("measuredIntegratedLufs", record.measuredIntegratedLufs)
        .put("measuredMaxTruePeak", record.measuredMaxTruePeak)
        .put("normalizationDb", record.normalizationDb)
        .put("measuredAtEpochMs", record.measuredAtEpochMs)

    private fun recordFromJson(encoded: String): LoudnessCalibrationRecord? = runCatching {
        val json = JSONObject(encoded)
        LoudnessCalibrationRecord(
            profileId = json.getString("profileId"),
            packGroup = json.getString("packGroup"),
            perspective = EngineSoundPerspective.valueOf(json.getString("perspective")),
            algorithmVersion = json.getInt("algorithmVersion"),
            bankFingerprint = json.getString("bankFingerprint"),
            measuredIntegratedLufs = json.getDouble("measuredIntegratedLufs"),
            measuredMaxTruePeak = json.getDouble("measuredMaxTruePeak"),
            normalizationDb = json.getDouble("normalizationDb"),
            measuredAtEpochMs = json.getLong("measuredAtEpochMs"),
        )
    }.getOrNull()

    private companion object {
        const val RECORD_PREFIX = "record."
        const val KEY_CHECKPOINT = "active_checkpoint"
    }
}
