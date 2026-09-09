package com.gabrielpc.enginesoundsimulator.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LoudnessNormalizationTest {
    @Test
    fun astonMartinDbsExteriorIsExcludedFromLoudnessCalibration() {
        assertTrue(
            LoudnessCalibrationPolicy.shouldMeasure(
                LoudnessCalibrationPolicy.ASTON_MARTIN_DBS_PROFILE_ID,
                EngineSoundPerspective.CABIN,
            ),
        )
        assertFalse(
            LoudnessCalibrationPolicy.shouldMeasure(
                LoudnessCalibrationPolicy.ASTON_MARTIN_DBS_PROFILE_ID,
                EngineSoundPerspective.EXTERIOR,
            ),
        )
    }

    @Test
    fun medianTargetUsesCeilingAndCorrectionCapsOnlyAmplification() {
        assertEquals(-22.0, LoudnessNormalizationMath.targetLufs(listOf(-30.0, -22.0, -10.0))!!, 0.0001)
        assertEquals(-18.0, LoudnessNormalizationMath.targetLufs(listOf(-17.0, -15.0))!!, 0.0001)
        assertEquals(12.0, LoudnessNormalizationMath.normalizationDb(-40.0, -18.0), 0.0001)
        assertEquals(-12.0, LoudnessNormalizationMath.normalizationDb(-6.0, -18.0), 0.0001)
        assertEquals(2.0, LoudnessNormalizationMath.dbToLinear(6.020599913), 0.0001)
    }

    @Test
    fun fingerprintChangesWhenAnyCalibrationInputChanges() {
        val original = fingerprint(car = "car-a", physics = "physics-a", common = "common-a", strings = "strings-a")

        assertNotEquals(original, fingerprint(car = "car-b", physics = "physics-a", common = "common-a", strings = "strings-a"))
        assertNotEquals(original, fingerprint(car = "car-a", physics = "physics-b", common = "common-a", strings = "strings-a"))
        assertNotEquals(original, fingerprint(car = "car-a", physics = "physics-a", common = "common-b", strings = "strings-a"))
        assertNotEquals(original, fingerprint(car = "car-a", physics = "physics-a", common = "common-a", strings = "strings-b"))
    }

    @Test
    fun recordValidityRequiresCurrentAlgorithmAndFingerprint() {
        val record = record(fingerprint = "current")

        assertTrue(LoudnessCalibrationRecordValidity.isValid(record, "current"))
        assertFalse(LoudnessCalibrationRecordValidity.isValid(record, "changed"))
        assertFalse(
            LoudnessCalibrationRecordValidity.isValid(
                record.copy(algorithmVersion = LOUDNESS_CALIBRATION_ALGORITHM_VERSION + 1),
                "current",
            ),
        )
    }

    @Test
    fun missingAndStaleNormalizationUseUnity() {
        assertEquals(1f, LoudnessNormalizationState().linear, 0.0001f)
        assertEquals(
            1f,
            LoudnessNormalizationState(
                validity = LoudnessNormalizationValidity.STALE,
                normalizationDb = 6.0,
            ).linear,
            0.0001f,
        )
        assertEquals(
            2f,
            LoudnessNormalizationState(
                validity = LoudnessNormalizationValidity.VALID,
                normalizationDb = 6.020599913,
            ).linear,
            0.0001f,
        )
    }

    @Test
    fun resumeSkipsOnlyCheckpointPairsThatStillHaveValidRecords() {
        val cabin = LoudnessCalibrationKey("car", "original", EngineSoundPerspective.CABIN)
        val exterior = LoudnessCalibrationKey("car", "original", EngineSoundPerspective.EXTERIOR)
        val checkpoint = LoudnessCalibrationCheckpoint(
            startedAtEpochMs = 1L,
            completedFingerprints = mapOf(cabin to "old", exterior to "current"),
        )
        val resumable = LoudnessCalibrationRecordValidity.resumableCompleted(
            checkpoint = checkpoint,
            records = listOf(
                record(EngineSoundPerspective.CABIN, "old"),
                record(EngineSoundPerspective.EXTERIOR, "current"),
            ),
            currentFingerprints = mapOf(cabin to "changed", exterior to "current"),
        )

        assertEquals(mapOf(exterior to "current"), resumable)
    }

    @Test
    fun duplicateProfileIdsInDifferentPackGroupsRemainIndependent() {
        val original = LoudnessCalibrationKey("shared-id", "original", EngineSoundPerspective.CABIN)
        val modded = LoudnessCalibrationKey("shared-id", "modded", EngineSoundPerspective.CABIN)

        assertNotEquals(original, modded)
        assertEquals(original, LoudnessCalibrationKey.fromPersisted(original.persisted))
        assertEquals(modded, LoudnessCalibrationKey.fromPersisted(modded.persisted))
    }

    private fun fingerprint(car: String, physics: String, common: String, strings: String): String {
        return LoudnessCalibrationFingerprint.create(
            profileId = "profile",
            algorithmVersion = LOUDNESS_CALIBRATION_ALGORITHM_VERSION,
            carBankSha256 = car,
            physicsSha256 = physics,
            commonBankSha256 = common,
            commonStringsBankSha256 = strings,
        )
    }

    private fun record(
        perspective: EngineSoundPerspective = EngineSoundPerspective.CABIN,
        fingerprint: String,
    ): LoudnessCalibrationRecord {
        return LoudnessCalibrationRecord(
            profileId = "car",
            packGroup = "original",
            perspective = perspective,
            algorithmVersion = LOUDNESS_CALIBRATION_ALGORITHM_VERSION,
            bankFingerprint = fingerprint,
            measuredIntegratedLufs = -20.0,
            measuredMaxTruePeak = -2.0,
            normalizationDb = 0.0,
            measuredAtEpochMs = 1L,
        )
    }
}
