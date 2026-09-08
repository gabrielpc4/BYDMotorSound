package com.gabrielpc.enginesoundsimulator.ui.theme

import androidx.compose.ui.graphics.Color
import com.gabrielpc.enginesoundsimulator.simulation.AutomaticTransmissionMode

/**
 * Wording and tint for the automatic gearbox mode caption.
 *
 * Both tachometers show this caption, so the mapping lives in one place. Only the calm color
 * differs per skin, which is why it comes from the skin instead of being hardcoded here.
 */
internal object AutomaticTransmissionModeCaption {
    fun label(mode: AutomaticTransmissionMode, preparingCruising: Boolean): String {
        if (preparingCruising) {
            return "P-CRUISING"
        }

        return when (mode) {
            AutomaticTransmissionMode.RACING -> "RACING"
            else -> "CRUISING"
        }
    }

    fun color(
        mode: AutomaticTransmissionMode,
        preparingCruising: Boolean,
        skin: DashboardSkin,
    ): Color {
        if (preparingCruising) {
            return skin.modeCaptionCalm
        }

        return when (mode) {
            AutomaticTransmissionMode.RACING -> skin.warning
            else -> skin.modeCaptionCalm
        }
    }
}
