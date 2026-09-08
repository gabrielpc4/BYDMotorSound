package com.gabrielpc.enginesoundsimulator.drive

import com.gabrielpc.enginesoundsimulator.simulation.VirtualGearProfile

/** Forward-gear profile: bank-authored ratios or a virtual 6–15 gear layout. */
sealed class GearProfileSelection {
    data object Original : GearProfileSelection()

    data class Virtual(
        val count: Int,
    ) : GearProfileSelection()

    fun virtualCountOrNull(): Int? {
        return when (this) {
            is Original -> null
            is Virtual -> count.coerceIn(
                VirtualGearProfile.MIN_VIRTUAL_GEARS,
                VirtualGearProfile.MAX_VIRTUAL_GEARS,
            )
        }
    }

    fun matchesMixerPreset(presetCount: Int): Boolean {
        return this is Virtual && count == presetCount
    }

    fun isOriginal(): Boolean {
        return this is Original
    }

    companion object {
        fun virtual(count: Int): Virtual {
            return Virtual(
                count = count.coerceIn(
                    VirtualGearProfile.MIN_VIRTUAL_GEARS,
                    VirtualGearProfile.MAX_VIRTUAL_GEARS,
                ),
            )
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
