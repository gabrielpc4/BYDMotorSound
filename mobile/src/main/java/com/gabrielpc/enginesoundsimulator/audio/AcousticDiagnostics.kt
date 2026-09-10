package com.gabrielpc.enginesoundsimulator.audio

import android.content.Context
import com.gabrielpc.enginesoundsimulator.AppPreferenceStores
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow

internal const val ACOUSTIC_DIAGNOSTIC_ALGORITHM_VERSION = 3

enum class AcousticDiagnosticStatus {
    IDLE,
    CONNECTING,
    PAIRING,
    PREPARING,
    RUNNING,
    INTERRUPTED,
    COMPLETED,
    CANCELLED,
    FAILED,
}

data class AcousticDiagnosticProgress(
    val status: AcousticDiagnosticStatus = AcousticDiagnosticStatus.IDLE,
    val meterName: String? = null,
    val activeCarName: String? = null,
    val perspective: EngineSoundPerspective? = null,
    val completedCount: Int = 0,
    val totalCount: Int = 0,
    val skippedCount: Int = 0,
    val failedCount: Int = 0,
    val lastError: String? = null,
) {
    val isRunning: Boolean
        get() = status in setOf(
            AcousticDiagnosticStatus.CONNECTING,
            AcousticDiagnosticStatus.PAIRING,
            AcousticDiagnosticStatus.PREPARING,
            AcousticDiagnosticStatus.RUNNING,
        )

    val canResume: Boolean
        get() = status == AcousticDiagnosticStatus.INTERRUPTED || status == AcousticDiagnosticStatus.CANCELLED
    val fraction: Float get() = if (totalCount == 0) 0f else completedCount.toFloat() / totalCount
}

internal data class AcousticMeterLink(
    val meterInstanceId: String,
    val meterName: String,
    val deviceAddress: String,
    val tokenHex: String,
)

internal data class AcousticMeasurementMetrics(
    val weightedEngineEnergy: Double,
    val lowBandEngineEnergy: Double,
    val presenceBandEngineEnergy: Double,
    val weightedAmbientBeforeEnergy: Double,
    val weightedAmbientAfterEnergy: Double,
    val lowBandAmbientBeforeEnergy: Double,
    val lowBandAmbientAfterEnergy: Double,
    val presenceBandAmbientBeforeEnergy: Double,
    val presenceBandAmbientAfterEnergy: Double,
    val peakLinear: Double,
    val sampleCount: Long,
    val sampleRateHz: Double,
    val channelCount: Int,
    val channelBalanceDb: Double?,
    val routeValid: Boolean,
    val windowValid: Boolean,
)

internal data class AcousticDiagnosticRecord(
    val sessionId: String,
    val profileId: String,
    val packGroup: String,
    val perspective: EngineSoundPerspective,
    val algorithmVersion: Int,
    val bankFingerprint: String,
    val normalizationDb: Double,
    val meterInstanceId: String,
    val meterModel: String,
    val correctedWeightedDb: Double,
    val deltaFromMedianDb: Double,
    val acousticAdjustmentDb: Double,
    val snrDb: Double,
    val peakDbFs: Double,
    val presenceBalanceDb: Double,
    val ambientBeforeDb: Double,
    val ambientAfterDb: Double,
    val clockUncertaintyMs: Double,
    val acousticLatencyMs: Double,
    val androidMediaVolumeIndex: Int,
    val androidMediaVolumeMax: Int,
    val sampleCount: Long,
    val sampleRateHz: Double,
    val channelCount: Int,
    val channelBalanceDb: Double?,
    val valid: Boolean,
    val failureReason: String?,
    val measuredAtEpochMs: Long,
) {
    val key: LoudnessCalibrationKey
        get() = LoudnessCalibrationKey(profileId, packGroup, perspective)
}

enum class AcousticAdjustmentValidity {
    VALID,
    STALE,
    FAILED,
    MISSING,
    EXCLUDED,
}

data class AcousticAdjustmentState(
    val validity: AcousticAdjustmentValidity = AcousticAdjustmentValidity.MISSING,
    val adjustmentDb: Double? = null,
) {
    val linear: Float
        get() = if (validity == AcousticAdjustmentValidity.VALID && adjustmentDb?.isFinite() == true) {
            10.0.pow(adjustmentDb / 20.0).toFloat()
        } else {
            1f
        }
}

data class AcousticDiagnosticSummary(
    val validCount: Int = 0,
    val failedCount: Int = 0,
    val lenientRecoverableCount: Int = 0,
    val staleCount: Int = 0,
    val missingCount: Int = 0,
    val totalCount: Int = 0,
    val catalogReferenceWeightedDb: Double? = null,
    val spreadDb: Double? = null,
    val medianPresenceBalanceDb: Double? = null,
    val withinToleranceCount: Int = 0,
    val loudestCar: String? = null,
    val loudestDeltaDb: Double? = null,
    val quietestCar: String? = null,
    val quietestDeltaDb: Double? = null,
    val mostMuffledCar: String? = null,
    val mostMuffledPresenceBalanceDb: Double? = null,
)

internal data class AcousticDiagnosticCheckpoint(
    val sessionId: String,
    val startedAtEpochMs: Long,
    val completedFingerprints: Map<LoudnessCalibrationKey, String>,
)

internal data class AcousticDiagnosticEvaluation(
    val correctedWeightedDb: Double,
    val snrDb: Double,
    val peakDbFs: Double,
    val presenceBalanceDb: Double,
    val ambientBeforeDb: Double,
    val ambientAfterDb: Double,
    val valid: Boolean,
    val failureReason: String?,
)

internal object AcousticDiagnosticMath {
    const val MINIMUM_SNR_DB = 10.0
    const val MAXIMUM_PEAK_DB_FS = -1.0
    const val MAXIMUM_AMBIENT_DRIFT_DB = 6.0
    const val CATALOG_TOLERANCE_DB = 1.5
    const val AMBIENT_DRIFT_FAILURE =
        "O ruído ambiente variou mais de ${MAXIMUM_AMBIENT_DRIFT_DB.toInt()} dB."
    private const val MINIMUM_ENERGY = 1e-15
    private const val LENIENT_MINIMUM_CORRECTED_DB = -100.0

    fun canAcceptLenient(record: AcousticDiagnosticRecord): Boolean {
        if (record.valid) {
            return false
        }
        if (record.failureReason != AMBIENT_DRIFT_FAILURE &&
            record.failureReason != "O ruído ambiente variou mais de 3 dB."
        ) {
            return false
        }
        if (record.correctedWeightedDb <= LENIENT_MINIMUM_CORRECTED_DB) {
            return false
        }
        if (record.snrDb < MINIMUM_SNR_DB) {
            return false
        }
        if (record.peakDbFs > MAXIMUM_PEAK_DB_FS) {
            return false
        }

        return true
    }

    fun evaluate(metrics: AcousticMeasurementMetrics): AcousticDiagnosticEvaluation {
        val ambientWeighted = averageEnergy(
            metrics.weightedAmbientBeforeEnergy,
            metrics.weightedAmbientAfterEnergy,
        )
        val correctedWeighted = metrics.weightedEngineEnergy - ambientWeighted
        val correctedLow = metrics.lowBandEngineEnergy - averageEnergy(
            metrics.lowBandAmbientBeforeEnergy,
            metrics.lowBandAmbientAfterEnergy,
        )
        val correctedPresence = metrics.presenceBandEngineEnergy - averageEnergy(
            metrics.presenceBandAmbientBeforeEnergy,
            metrics.presenceBandAmbientAfterEnergy,
        )
        val correctedWeightedDb = energyToDb(correctedWeighted)
        val snrDb = energyToDb(max(correctedWeighted, MINIMUM_ENERGY)) - energyToDb(ambientWeighted)
        val peakDbFs = amplitudeToDb(metrics.peakLinear)
        val ambientBeforeDb = energyToDb(metrics.weightedAmbientBeforeEnergy)
        val ambientAfterDb = energyToDb(metrics.weightedAmbientAfterEnergy)
        val presenceBalanceDb = energyToDb(correctedPresence) - energyToDb(correctedLow)
        val failure = when {
            !metrics.routeValid -> "O iPhone não estava usando o microfone interno."
            !metrics.windowValid || metrics.sampleCount <= 0L -> "A janela recebida do iPhone ficou incompleta."
            correctedWeighted <= MINIMUM_ENERGY -> "O motor não ficou acima do ruído ambiente."
            snrDb < MINIMUM_SNR_DB -> "SNR abaixo de 10 dB."
            peakDbFs > MAXIMUM_PEAK_DB_FS -> "Pico acima de -1 dBFS."
            abs(ambientBeforeDb - ambientAfterDb) > MAXIMUM_AMBIENT_DRIFT_DB ->
                AMBIENT_DRIFT_FAILURE
            else -> null
        }

        return AcousticDiagnosticEvaluation(
            correctedWeightedDb = correctedWeightedDb,
            snrDb = snrDb,
            peakDbFs = peakDbFs,
            presenceBalanceDb = presenceBalanceDb,
            ambientBeforeDb = ambientBeforeDb,
            ambientAfterDb = ambientAfterDb,
            valid = failure == null,
            failureReason = failure,
        )
    }

    fun median(values: List<Double>): Double? {
        val sorted = values.filter(Double::isFinite).sorted()
        if (sorted.isEmpty()) return null
        val middle = sorted.size / 2

        return if (sorted.size % 2 == 0) {
            (sorted[middle - 1] + sorted[middle]) / 2.0
        } else {
            sorted[middle]
        }
    }

    fun adjustmentDb(deltaFromMedianDb: Double): Double =
        if (deltaFromMedianDb.isFinite()) -deltaFromMedianDb else 0.0

    private fun averageEnergy(first: Double, second: Double): Double =
        (first.coerceAtLeast(0.0) + second.coerceAtLeast(0.0)) / 2.0

    private fun energyToDb(energy: Double): Double = 10.0 * log10(max(energy, MINIMUM_ENERGY))

    private fun amplitudeToDb(amplitude: Double): Double =
        20.0 * log10(max(amplitude, MINIMUM_ENERGY))
}

internal class IphoneAcousticMeterRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        AppPreferenceStores.IPHONE_ACOUSTIC_METER,
        Context.MODE_PRIVATE,
    )

    @Synchronized
    fun loadLink(): AcousticMeterLink? {
        val meterInstanceId = preferences.getString(KEY_INSTANCE_ID, null) ?: return null
        val tokenHex = preferences.getString(KEY_TOKEN, null) ?: return null

        return AcousticMeterLink(
            meterInstanceId = meterInstanceId,
            meterName = preferences.getString(KEY_NAME, "iPhone") ?: "iPhone",
            deviceAddress = preferences.getString(KEY_ADDRESS, "") ?: "",
            tokenHex = tokenHex,
        )
    }

    @Synchronized
    fun saveLink(link: AcousticMeterLink) {
        preferences.edit()
            .putString(KEY_INSTANCE_ID, link.meterInstanceId)
            .putString(KEY_NAME, link.meterName)
            .putString(KEY_ADDRESS, link.deviceAddress)
            .putString(KEY_TOKEN, link.tokenHex)
            .commit()
    }

    @Synchronized
    fun clear() {
        preferences.edit().clear().commit()
    }

    private companion object {
        const val KEY_INSTANCE_ID = "meter_instance_id"
        const val KEY_NAME = "meter_name"
        const val KEY_ADDRESS = "device_address"
        const val KEY_TOKEN = "token_hex"
    }
}

internal class AcousticDiagnosticRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        AppPreferenceStores.ACOUSTIC_DIAGNOSTICS,
        Context.MODE_PRIVATE,
    )

    @Synchronized
    fun beginCheckpoint(): AcousticDiagnosticCheckpoint {
        checkpoint()?.let { removeSessionRecords(it.sessionId) }
        val checkpoint = AcousticDiagnosticCheckpoint(
            sessionId = UUID.randomUUID().toString(),
            startedAtEpochMs = System.currentTimeMillis(),
            completedFingerprints = emptyMap(),
        )
        saveCheckpoint(checkpoint)

        return checkpoint
    }

    @Synchronized
    fun checkpoint(): AcousticDiagnosticCheckpoint? {
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
            AcousticDiagnosticCheckpoint(
                sessionId = json.getString("sessionId"),
                startedAtEpochMs = json.getLong("startedAtEpochMs"),
                completedFingerprints = completed,
            )
        }.getOrNull()
    }

    @Synchronized
    fun records(sessionId: String? = latestCompletedSessionId()): List<AcousticDiagnosticRecord> {
        if (sessionId == null) return emptyList()

        return preferences.all
            .filterKeys { it.startsWith("$RECORD_PREFIX$sessionId|") }
            .values
            .mapNotNull { (it as? String)?.let(::recordFromJson) }
    }

    @Synchronized
    fun saveRecord(record: AcousticDiagnosticRecord) {
        preferences.edit()
            .putString(recordKey(record.sessionId, record.key), recordToJson(record).toString())
            .commit()
        val checkpoint = checkpoint() ?: return
        if (checkpoint.sessionId != record.sessionId) return
        saveCheckpoint(
            checkpoint.copy(
                completedFingerprints = checkpoint.completedFingerprints +
                    (record.key to diagnosticFingerprint(record.bankFingerprint, record.normalizationDb)),
            ),
        )
    }

    @Synchronized
    fun completeSession(sessionId: String) {
        recomputeSessionAdjustments(sessionId)
        preferences.edit()
            .putString(KEY_LATEST_SESSION, sessionId)
            .remove(KEY_CHECKPOINT)
            .commit()
    }

    @Synchronized
    fun recomputeSessionAdjustments(sessionId: String) {
        val validRecords = records(sessionId).filter(AcousticDiagnosticRecord::valid)
        val median = AcousticDiagnosticMath.median(validRecords.map(AcousticDiagnosticRecord::correctedWeightedDb))
        val editor = preferences.edit()
        if (median != null) {
            validRecords.forEach { record ->
                val delta = record.correctedWeightedDb - median
                val updated = record.copy(
                    deltaFromMedianDb = delta,
                    acousticAdjustmentDb = AcousticDiagnosticMath.adjustmentDb(delta),
                )
                editor.putString(recordKey(sessionId, record.key), recordToJson(updated).toString())
            }
        }
        editor.commit()
    }

    @Synchronized
    fun acceptLenientRecords(): Int {
        val sessionId = latestCompletedSessionId() ?: return 0
        val recoverable = records(sessionId).filter(AcousticDiagnosticMath::canAcceptLenient)
        if (recoverable.isEmpty()) {
            return 0
        }
        val editor = preferences.edit()
        recoverable.forEach { record ->
            editor.putString(
                recordKey(sessionId, record.key),
                recordToJson(
                    record.copy(
                        valid = true,
                        failureReason = null,
                    ),
                ).toString(),
            )
        }
        editor.commit()
        recomputeSessionAdjustments(sessionId)

        return recoverable.size
    }

    @Synchronized
    fun clearCheckpoint() {
        preferences.edit().remove(KEY_CHECKPOINT).commit()
    }

    @Synchronized
    fun latestCompletedSessionId(): String? = preferences.getString(KEY_LATEST_SESSION, null)

    @Synchronized
    fun adjustmentState(
        key: LoudnessCalibrationKey,
        bankFingerprint: String?,
        normalizationDb: Double?,
    ): AcousticAdjustmentState {
        val record = records().firstOrNull { it.key == key }
            ?: return AcousticAdjustmentState()
        if (!record.valid) return AcousticAdjustmentState(AcousticAdjustmentValidity.FAILED)
        if (
            bankFingerprint == null || normalizationDb == null ||
            record.algorithmVersion != ACOUSTIC_DIAGNOSTIC_ALGORITHM_VERSION ||
            record.bankFingerprint != bankFingerprint ||
            record.normalizationDb.toBits() != normalizationDb.toBits()
        ) {
            return AcousticAdjustmentState(AcousticAdjustmentValidity.STALE)
        }

        return AcousticAdjustmentState(
            validity = AcousticAdjustmentValidity.VALID,
            adjustmentDb = record.acousticAdjustmentDb,
        )
    }

    @Synchronized
    fun removePackGroup(packGroup: String) {
        val editor = preferences.edit()
        preferences.all.forEach { (key, value) ->
            if (key.startsWith(RECORD_PREFIX) &&
                (value as? String)?.let(::recordFromJson)?.packGroup == packGroup
            ) {
                editor.remove(key)
            }
        }
        checkpoint()?.let { active ->
            val filtered = active.completedFingerprints.filterKeys { it.packGroup != packGroup }
            if (filtered != active.completedFingerprints) {
                editor.putString(KEY_CHECKPOINT, checkpointToJson(active.copy(completedFingerprints = filtered)).toString())
            }
        }
        editor.commit()
    }

    @Synchronized
    fun summary(
        profileNames: Map<LoudnessCalibrationKey, String>,
        currentTargets: Map<LoudnessCalibrationKey, Pair<String, Double>> = emptyMap(),
        scopePackGroup: String? = null,
    ): AcousticDiagnosticSummary {
        val records = records().filter { scopePackGroup == null || it.packGroup == scopePackGroup }
        val recordsByKey = records.associateBy(AcousticDiagnosticRecord::key)
        val currentRecords = if (currentTargets.isEmpty()) {
            records
        } else {
            records.filter { record ->
                val current = currentTargets[record.key]
                current != null && record.algorithmVersion == ACOUSTIC_DIAGNOSTIC_ALGORITHM_VERSION &&
                    record.bankFingerprint == current.first && record.normalizationDb.toBits() == current.second.toBits()
            }
        }
        val valid = currentRecords.filter(AcousticDiagnosticRecord::valid)
        val median = AcousticDiagnosticMath.median(valid.map(AcousticDiagnosticRecord::correctedWeightedDb))
        val ordered = valid.sortedBy { record -> record.correctedWeightedDb - (median ?: record.correctedWeightedDb) }
        val adjustedLevels = valid.map { it.correctedWeightedDb + it.acousticAdjustmentDb }.sorted()
        val spread = if (adjustedLevels.size > 1) {
            adjustedLevels.last() - adjustedLevels.first()
        } else {
            null
        }

        return AcousticDiagnosticSummary(
            validCount = valid.size,
            failedCount = currentRecords.count { !it.valid },
            lenientRecoverableCount = currentRecords.count(AcousticDiagnosticMath::canAcceptLenient),
            staleCount = if (currentTargets.isEmpty()) 0 else records.count { it !in currentRecords },
            missingCount = if (currentTargets.isEmpty()) 0 else currentTargets.keys.count { it !in recordsByKey },
            totalCount = if (currentTargets.isEmpty()) records.size else currentTargets.size,
            catalogReferenceWeightedDb = median,
            spreadDb = spread,
            medianPresenceBalanceDb = AcousticDiagnosticMath.median(
                valid.map(AcousticDiagnosticRecord::presenceBalanceDb),
            ),
            withinToleranceCount = valid.count {
                abs(it.correctedWeightedDb + it.acousticAdjustmentDb - requireNotNull(median)) <=
                    AcousticDiagnosticMath.CATALOG_TOLERANCE_DB
            },
            loudestCar = ordered.lastOrNull()?.let { profileNames[it.key] },
            loudestDeltaDb = ordered.lastOrNull()?.let { it.correctedWeightedDb - requireNotNull(median) },
            quietestCar = ordered.firstOrNull()?.let { profileNames[it.key] },
            quietestDeltaDb = ordered.firstOrNull()?.let { it.correctedWeightedDb - requireNotNull(median) },
            mostMuffledCar = valid.minByOrNull(AcousticDiagnosticRecord::presenceBalanceDb)?.let { profileNames[it.key] },
            mostMuffledPresenceBalanceDb = valid.minOfOrNull(AcousticDiagnosticRecord::presenceBalanceDb),
        )
    }

    @Synchronized
    fun clearAll() {
        preferences.edit().clear().commit()
    }

    private fun saveCheckpoint(checkpoint: AcousticDiagnosticCheckpoint) {
        preferences.edit().putString(KEY_CHECKPOINT, checkpointToJson(checkpoint).toString()).commit()
    }

    private fun checkpointToJson(checkpoint: AcousticDiagnosticCheckpoint): JSONObject {
        val completed = JSONArray()
        checkpoint.completedFingerprints.entries.sortedBy { it.key.persisted }.forEach { (key, fingerprint) ->
            completed.put(JSONObject().put("key", key.persisted).put("fingerprint", fingerprint))
        }
        val json = JSONObject()
            .put("sessionId", checkpoint.sessionId)
            .put("startedAtEpochMs", checkpoint.startedAtEpochMs)
            .put("completed", completed)

        return json
    }

    private fun recordKey(sessionId: String, key: LoudnessCalibrationKey): String =
        "$RECORD_PREFIX$sessionId|${key.persisted}"

    private fun removeSessionRecords(sessionId: String) {
        val editor = preferences.edit()
        preferences.all.keys
            .filter { it.startsWith("$RECORD_PREFIX$sessionId|") }
            .forEach(editor::remove)
        editor.commit()
    }

    private fun recordToJson(record: AcousticDiagnosticRecord): JSONObject = JSONObject()
        .put("sessionId", record.sessionId)
        .put("profileId", record.profileId)
        .put("packGroup", record.packGroup)
        .put("perspective", record.perspective.name)
        .put("algorithmVersion", record.algorithmVersion)
        .put("bankFingerprint", record.bankFingerprint)
        .put("normalizationDb", record.normalizationDb)
        .put("meterInstanceId", record.meterInstanceId)
        .put("meterModel", record.meterModel)
        .put("correctedWeightedDb", record.correctedWeightedDb)
        .put("deltaFromMedianDb", record.deltaFromMedianDb)
        .put("acousticAdjustmentDb", record.acousticAdjustmentDb)
        .put("snrDb", record.snrDb)
        .put("peakDbFs", record.peakDbFs)
        .put("presenceBalanceDb", record.presenceBalanceDb)
        .put("ambientBeforeDb", record.ambientBeforeDb)
        .put("ambientAfterDb", record.ambientAfterDb)
        .put("clockUncertaintyMs", record.clockUncertaintyMs)
        .put("acousticLatencyMs", record.acousticLatencyMs)
        .put("androidMediaVolumeIndex", record.androidMediaVolumeIndex)
        .put("androidMediaVolumeMax", record.androidMediaVolumeMax)
        .put("sampleCount", record.sampleCount)
        .put("sampleRateHz", record.sampleRateHz)
        .put("channelCount", record.channelCount)
        .put("channelBalanceDb", record.channelBalanceDb ?: JSONObject.NULL)
        .put("valid", record.valid)
        .put("failureReason", record.failureReason ?: JSONObject.NULL)
        .put("measuredAtEpochMs", record.measuredAtEpochMs)

    private fun recordFromJson(encoded: String): AcousticDiagnosticRecord? = runCatching {
        val json = JSONObject(encoded)
        AcousticDiagnosticRecord(
            sessionId = json.getString("sessionId"),
            profileId = json.getString("profileId"),
            packGroup = json.getString("packGroup"),
            perspective = EngineSoundPerspective.valueOf(json.getString("perspective")),
            algorithmVersion = json.getInt("algorithmVersion"),
            bankFingerprint = json.getString("bankFingerprint"),
            normalizationDb = json.getDouble("normalizationDb"),
            meterInstanceId = json.getString("meterInstanceId"),
            meterModel = json.getString("meterModel"),
            correctedWeightedDb = json.getDouble("correctedWeightedDb"),
            deltaFromMedianDb = json.getDouble("deltaFromMedianDb"),
            acousticAdjustmentDb = json.optDouble("acousticAdjustmentDb", 0.0),
            snrDb = json.getDouble("snrDb"),
            peakDbFs = json.getDouble("peakDbFs"),
            presenceBalanceDb = json.getDouble("presenceBalanceDb"),
            ambientBeforeDb = json.getDouble("ambientBeforeDb"),
            ambientAfterDb = json.getDouble("ambientAfterDb"),
            clockUncertaintyMs = json.getDouble("clockUncertaintyMs"),
            acousticLatencyMs = json.getDouble("acousticLatencyMs"),
            androidMediaVolumeIndex = json.getInt("androidMediaVolumeIndex"),
            androidMediaVolumeMax = json.getInt("androidMediaVolumeMax"),
            sampleCount = json.getLong("sampleCount"),
            sampleRateHz = json.getDouble("sampleRateHz"),
            channelCount = json.getInt("channelCount"),
            channelBalanceDb = json.optDouble("channelBalanceDb").takeIf(Double::isFinite),
            valid = json.getBoolean("valid"),
            failureReason = json.optString("failureReason").takeIf { it.isNotBlank() && it != "null" },
            measuredAtEpochMs = json.getLong("measuredAtEpochMs"),
        )
    }.getOrNull()

    companion object {
        fun diagnosticFingerprint(bankFingerprint: String, normalizationDb: Double): String =
            "$ACOUSTIC_DIAGNOSTIC_ALGORITHM_VERSION|$bankFingerprint|${normalizationDb.toBits()}"

        private const val RECORD_PREFIX = "record."
        private const val KEY_CHECKPOINT = "active_checkpoint"
        private const val KEY_LATEST_SESSION = "latest_completed_session"
    }
}
