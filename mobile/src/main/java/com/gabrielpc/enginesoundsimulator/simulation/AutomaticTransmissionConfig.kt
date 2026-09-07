package com.gabrielpc.enginesoundsimulator.simulation

import com.gabrielpc.enginesoundsimulator.drive.AutomaticTransmissionSettings
import com.gabrielpc.enginesoundsimulator.drive.ManualAutodownshiftRpm
import com.gabrielpc.enginesoundsimulator.drive.ManualRedlineHoldSeconds
import com.gabrielpc.enginesoundsimulator.drive.RacingReturnHoldSeconds
import com.gabrielpc.enginesoundsimulator.drive.RacingReturnThrottlePercent

internal data class AutomaticTransmissionConfig(
    val cruisingLogicEnabled: Boolean = true,
    val sixGearOnLaunchEnabled: Boolean = false,
    val cruisingShiftOffsetsByTachMaxRpm: Map<Int, Int> = emptyMap(),
    val racingReturnMaxThrottle: Double = RacingReturnThrottlePercent.asFraction(
        RacingReturnThrottlePercent.DEFAULT,
    ),
    val racingReturnHoldSeconds: Double = RacingReturnHoldSeconds.DEFAULT.toDouble(),
    val manualRedlineHoldSeconds: Double? = ManualRedlineHoldSeconds.asHoldSeconds(ManualRedlineHoldSeconds.DEFAULT),
    val manualAutodownshiftRpm: Double = ManualAutodownshiftRpm.DEFAULT.toDouble(),
) {
    companion object {
        fun fromSettings(settings: AutomaticTransmissionSettings): AutomaticTransmissionConfig {
            return AutomaticTransmissionConfig(
                cruisingLogicEnabled = settings.cruisingLogicEnabled,
                sixGearOnLaunchEnabled = settings.sixGearOnLaunchEnabled,
                cruisingShiftOffsetsByTachMaxRpm = settings.cruisingShiftOffsetsByTachMaxRpm,
                racingReturnMaxThrottle = RacingReturnThrottlePercent.asFraction(
                    settings.racingReturnThrottlePercent,
                ),
                racingReturnHoldSeconds = settings.racingReturnHoldSeconds.toDouble(),
                manualRedlineHoldSeconds = ManualRedlineHoldSeconds.asHoldSeconds(settings.manualRedlineHoldSeconds),
                manualAutodownshiftRpm = settings.manualAutodownshiftRpm.toDouble(),
            )
        }
    }
}
