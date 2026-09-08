package com.gabrielpc.enginesoundsimulator.drive

import com.gabrielpc.enginesoundsimulator.simulation.VirtualGearProfile

/** Forward-gear profile: bank-authored ratios or a virtual 6 / 10 / 15 gear layout. */
sealed class GearProfileSelection {
    data object Original : GearProfileSelection()

    data class Virtual(
        val count: Int,
    ) : GearProfileSelection()

    fun virtualCountOrNull(): Int? {
        return when (this) {
            is Original -> null
            is Virtual -> coercePreset(count)
        }
    }

    fun matchesMixerPreset(presetCount: Int): Boolean {
        return this is Virtual && count == presetCount
    }

    fun isOriginal(): Boolean {
        return this is Original
    }

    companion object {
        val VIRTUAL_PRESETS: List<Int> = VirtualGearSpeedBoundaries.PRESETS

        fun coercePreset(count: Int): Int {
            return VirtualGearSpeedBoundaries.coerceVirtualPreset(count)
        }

        fun virtual(count: Int): Virtual {
            return Virtual(count = coercePreset(count))
        }

        fun fromPersisted(value: String?): GearProfileSelection {
            if (value == null) {
                return virtual(VirtualGearProfile.DEFAULT_VIRTUAL_GEARS)
            }

            if (value == PERSISTED_ORIGINAL) {
                return Original
            }

            if (value.startsWith(PERSISTED_VIRTUAL_PREFIX)) {
                val count = value.removePrefix(PERSISTED_VIRTUAL_PREFIX).toIntOrNull()
                    ?: VirtualGearProfile.DEFAULT_VIRTUAL_GEARS
                return virtual(count)
            }

            return virtual(VirtualGearProfile.DEFAULT_VIRTUAL_GEARS)
        }

        fun toPersisted(selection: GearProfileSelection): String {
            return when (selection) {
                is Original -> PERSISTED_ORIGINAL
                is Virtual -> PERSISTED_VIRTUAL_PREFIX + virtual(selection.count).count
            }
        }

        fun migrateLegacyVirtualCount(count: Int): GearProfileSelection {
            return virtual(count)
        }

        private const val PERSISTED_ORIGINAL = "ORIGINAL"
        private const val PERSISTED_VIRTUAL_PREFIX = "VIRTUAL:"
    }
}
