package com.gabrielpc.enginesoundsimulator.audio

/** Per-car loudness-calibration exclusions layered on top of the modded catalog. */
internal object LoudnessCalibrationPolicy {
    const val ASTON_MARTIN_DBS_PROFILE_ID = "modded-aston-martin-dbrs9-gt3"

    fun shouldMeasure(profileId: String, perspective: EngineSoundPerspective): Boolean {
        if (profileId == ASTON_MARTIN_DBS_PROFILE_ID && perspective == EngineSoundPerspective.EXTERIOR) {
            return false
        }

        return true
    }
}
