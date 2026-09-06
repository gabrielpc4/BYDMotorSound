package com.gabrielpc.enginesoundsimulator.audio

/** Per-car audio behavior layered on top of authored FMOD banks. */
internal object CarAudioPolicy {
    const val SKYLINE_PROFILE_ID = "assetto-nissan-skyline-r34"

    fun shouldBlockPopSubSounds(profileId: String): Boolean {
        return profileId == SKYLINE_PROFILE_ID
    }

    fun isBlockedSkylineSubSoundName(soundName: String): Boolean {
        if (soundName.contains("_pop_", ignoreCase = true)) {
            return true
        }

        return soundName.contains("rb26_ex_5_offmid", ignoreCase = true)
    }
}
