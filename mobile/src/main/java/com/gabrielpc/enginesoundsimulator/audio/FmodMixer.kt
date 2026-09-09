package com.gabrielpc.enginesoundsimulator.audio

/**
 * One diagnostic source aggregate owned by one authored Studio event.
 *
 * The ID is the event path plus raw FMOD sound name. It is deliberately not a single exclusive
 * channel: several Core voices from the same source can coexist, and a source can remain active
 * with zero audibility while Studio automation or virtualization makes it silent.
 */
data class FmodSourceState(
    val id: String,
    val eventPath: String,
    val eventName: String,
    val soundName: String,
    val audibility: Double,
    val routeGain: Double,
    val voiceCount: Int,
    val isVirtual: Boolean,
    val isActive: Boolean,
) {
    val audibilityPercent: Int
        get() = (audibility.coerceIn(0.0, 1.0) * 100.0).toInt()

    val section: FmodEventSection
        get() = FmodEventSection.forEvent(eventName)

    /** True when the source should appear in the live mixer cards. */
    val isAudibleInMixer: Boolean
        get() {
            if (!isActive) {
                return false
            }

            if (isVirtual) {
                return false
            }

            if (audibility <= SILENT_AUDIBILITY_THRESHOLD) {
                return false
            }

            return true
        }

    companion object {
        const val SILENT_AUDIBILITY_THRESHOLD = 0.002
    }
}

enum class FmodEventSection(val displayName: String, val order: Int) {
    ENGINE("ENGINE", 0),
    DRIVETRAIN("DRIVETRAIN", 1),
    FORCED_INDUCTION("TURBO", 2),
    DRIVER_EVENTS("DRIVER EVENTS", 3),
    ENGINE_EVENTS("ENGINE EVENTS", 4),
    OTHER("OTHER AUTHORED EVENTS", 5),
    ;

    companion object {
        fun forEvent(eventName: String): FmodEventSection = when (eventName) {
            "engine_int", "engine_ext" -> ENGINE
            "transmission", "transmission_ext" -> DRIVETRAIN
            "turbo" -> FORCED_INDUCTION
            "gear_int", "gear_ext", "gear_grind", "tractioncontrol_int", "tractioncontrol_ext" ->
                DRIVER_EVENTS
            "limiter", "backfire_int", "backfire_ext", "start" -> ENGINE_EVENTS
            else -> OTHER
        }
    }
}

/** Authored FMOD events controlled by one mixer category row. */
enum class MixerEventCategory(val eventNames: List<String>) {
    ENGINE_INTERIOR(listOf("engine_int")),
    ENGINE_EXTERIOR(listOf("engine_ext")),
    TRANSMISSION(listOf("transmission", "transmission_ext")),
    SHIFT(listOf("gear_int", "gear_ext", "gear_grind")),
    TURBO(listOf("turbo")),
    BACKFIRE(listOf("backfire_int", "backfire_ext")),
    LIMITER(listOf("limiter")),
    /** Embedded engine subsounds; routed through native pseudo-event mute/solo keys. */
    SUPERCHARGER(listOf("supercharger")),
    ;

    fun isMuted(mutedEvents: Map<String, Boolean>): Boolean {
        return eventNames.all { mutedEvents[it] == true }
    }

    fun isSoloed(soloedEvents: Map<String, Boolean>): Boolean {
        return eventNames.any { soloedEvents[it] == true }
    }
}

internal fun mixerSourcesStructureChanged(previous: List<FmodSourceState>, next: List<FmodSourceState>): Boolean {
    if (previous.size != next.size) {
        return true
    }

    for (index in previous.indices) {
        val left = previous[index]
        val right = next[index]

        if (left.id != right.id) {
            return true
        }

        if (left.eventName != right.eventName) {
            return true
        }

        if (left.soundName != right.soundName) {
            return true
        }

        if (left.voiceCount != right.voiceCount) {
            return true
        }
    }

    return false
}

internal fun mixerSourcesDisplayChanged(previous: List<FmodSourceState>, next: List<FmodSourceState>): Boolean {
    if (previous.size != next.size) {
        return true
    }

    for (index in previous.indices) {
        val left = previous[index]
        val right = next[index]

        if (left.id != right.id) {
            return true
        }

        if (left.audibilityPercent != right.audibilityPercent) {
            return true
        }

        if (left.voiceCount != right.voiceCount) {
            return true
        }
    }

    return false
}

internal fun parseNativeVoiceSnapshots(rows: Array<String>): List<FmodSourceState> = rows.mapNotNull { row ->
    val fields = row.split(NATIVE_FIELD_SEPARATOR)
    if (fields.size != NATIVE_SNAPSHOT_FIELD_COUNT) return@mapNotNull null
    FmodSourceState(
        id = fields[0],
        eventPath = fields[1],
        eventName = fields[2],
        soundName = fields[3],
        audibility = fields[4].toDoubleOrNull()?.coerceIn(0.0, 1.0) ?: 0.0,
        routeGain = fields[5].toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0,
        voiceCount = fields[6].toIntOrNull()?.coerceAtLeast(0) ?: 0,
        isVirtual = fields[7] == "1",
        isActive = fields[8] == "1",
    )
}

private const val NATIVE_FIELD_SEPARATOR = '\u001f'
private const val NATIVE_SNAPSHOT_FIELD_COUNT = 9
