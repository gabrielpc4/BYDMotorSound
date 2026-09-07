package com.gabrielpc.enginesoundsimulator.simulation

import com.gabrielpc.enginesoundsimulator.drive.AutomaticTransmissionSettings
import com.gabrielpc.enginesoundsimulator.drive.ManualAutodownshiftRpm
import com.gabrielpc.enginesoundsimulator.drive.ManualRedlineHoldSeconds
import com.gabrielpc.enginesoundsimulator.drive.RacingReturnHoldSeconds
import com.gabrielpc.enginesoundsimulator.drive.RacingReturnThrottlePercent

internal data class AutomaticTransmissionConfig(
    val cruisingLogicEnabled: Boolean = true,
    val cruisingShiftOffsetRpm: Int = 0,
    val racingReturnMaxThrottle: Double = RacingReturnThrottlePercent.asFraction(
        RacingReturnThrottlePercent.DEFAULT,
    ),
    val racingReturnHoldSeconds: Double = RacingReturnHoldSeconds.DEFAULT.toDouble(),
    val manualRedlineHoldSeconds: Double = ManualRedlineHoldSeconds.DEFAULT.toDouble(),
    val manualAutodownshiftRpm: Double = ManualAutodownshiftRpm.DEFAULT.toDouble(),
) {
    companion object {
        fun fromSettings(settings: AutomaticTransmissionSettings): AutomaticTransmissionConfig {
            return AutomaticTransmissionConfig(
                cruisingLogicEnabled = settings.cruisingLogicEnabled,
                cruisingShiftOffsetRpm = settings.cruisingShiftOffsetRpm,
                racingReturnMaxThrottle = RacingReturnThrottlePercent.asFraction(
                    settings.racingReturnThrottlePercent,
                ),
                racingReturnHoldSeconds = settings.racingReturnHoldSeconds.toDouble(),
                manualRedlineHoldSeconds = settings.manualRedlineHoldSeconds.toDouble(),
                manualAutodownshiftRpm = settings.manualAutodownshiftRpm.toDouble(),
            )
        }
    }
}
