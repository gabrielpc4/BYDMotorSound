package com.gabrielpc.enginesoundsimulator.drive

import com.gabrielpc.enginesoundsimulator.simulation.AutomaticTransmissionMode
import com.gabrielpc.enginesoundsimulator.simulation.VirtualGearProfile

/** Forward-gear profile: bank-authored ratios, virtual 6 / 10 / 15, or adaptive 6↔10. */
sealed class GearProfileSelection {
    data object Original : GearProfileSelection()

    /** Uses 6 forward gears in cruising and 10 in racing or manual shift. */
    data object AdaptiveCruising6Racing10 : GearProfileSelection()

    data class Virtual(
        val count: Int,
    ) : GearProfileSelection()

    fun virtualCountOrNull(): Int? {
        return when (this) {
            is Original -> null
            is AdaptiveCruising6Racing10 -> null
            is Virtual -> coercePreset(count)
        }
    }

    fun matchesMixerPreset(presetCount: Int): Boolean {
        return this is Virtual && count == presetCount
    }

    fun isOriginal(): Boolean {
        return this is Original
    }

    fun isAdaptive(): Boolean {
        return this is AdaptiveCruising6Racing10
    }

    companion object {
        const val ADAPTIVE_CRUISING_GEARS = 6
        const val ADAPTIVE_RACING_GEARS = 10

        val VIRTUAL_PRESETS: List<Int> = VirtualGearSpeedBoundaries.PRESETS

        fun coercePreset(count: Int): Int {
            return VirtualGearSpeedBoundaries.coerceVirtualPreset(count)
        }

        fun virtual(count: Int): Virtual {
            return Virtual(count = coercePreset(count))
        }

        fun resolveAdaptiveGearCount(
            manualShiftEnabled: Boolean,
            automaticTransmissionMode: AutomaticTransmissionMode,
        ): Int {
            if (manualShiftEnabled) {
                return ADAPTIVE_RACING_GEARS
            }

            if (automaticTransmissionMode == AutomaticTransmissionMode.RACING) {
                return ADAPTIVE_RACING_GEARS
            }

            return ADAPTIVE_CRUISING_GEARS
        }

        fun fromPersisted(value: String?): GearProfileSelection {
            if (value == null) {
                return virtual(VirtualGearProfile.DEFAULT_VIRTUAL_GEARS)
            }

            if (value == PERSISTED_ORIGINAL) {
                return Original
            }

            if (value == PERSISTED_ADAPTIVE) {
                return AdaptiveCruising6Racing10
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
                is AdaptiveCruising6Racing10 -> PERSISTED_ADAPTIVE
                is Virtual -> PERSISTED_VIRTUAL_PREFIX + virtual(selection.count).count
            }
        }

        fun migrateLegacyVirtualCount(count: Int): GearProfileSelection {
            return virtual(count)
        }

        private const val PERSISTED_ORIGINAL = "ORIGINAL"
        private const val PERSISTED_ADAPTIVE = "ADAPTIVE:6/10"
        private const val PERSISTED_VIRTUAL_PREFIX = "VIRTUAL:"
    }
}
