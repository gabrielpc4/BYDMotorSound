package com.gabrielpc.enginesoundsimulator.simulation

import com.gabrielpc.enginesoundsimulator.drive.AutomaticDownshiftMilliseconds
import com.gabrielpc.enginesoundsimulator.drive.AutomaticTransmissionSettings
import com.gabrielpc.enginesoundsimulator.drive.AutomaticUpshiftMilliseconds
import com.gabrielpc.enginesoundsimulator.drive.ManualAutodownshiftRpm
import com.gabrielpc.enginesoundsimulator.drive.ManualRedlineHoldSeconds
import com.gabrielpc.enginesoundsimulator.drive.RacingEnterDelayMilliseconds
import com.gabrielpc.enginesoundsimulator.drive.RacingReturnHoldSeconds
import com.gabrielpc.enginesoundsimulator.drive.RacingReturnThrottlePercent

internal data class AutomaticTransmissionConfig(
    val cruisingLogicEnabled: Boolean = true,
    val allowManualOnLaunchEnabled: Boolean = false,
    val manualTransmissionKickdownEnabled: Boolean = true,
    val cruisingShiftOffsetsByTachMaxRpm: Map<Int, Int> = emptyMap(),
    val racingReturnMaxThrottle: Double = RacingReturnThrottlePercent.asFraction(
        RacingReturnThrottlePercent.DEFAULT,
    ),
    val racingEnterDelayMilliseconds: Int = RacingEnterDelayMilliseconds.DEFAULT,
    val automaticUpshiftMilliseconds: Int = AutomaticUpshiftMilliseconds.DEFAULT,
    val automaticDownshiftMilliseconds: Int = AutomaticDownshiftMilliseconds.DEFAULT,
    val racingReturnHoldSeconds: Double = RacingReturnHoldSeconds.DEFAULT.toDouble(),
    val manualRedlineHoldSeconds: Double? = ManualRedlineHoldSeconds.asHoldSeconds(ManualRedlineHoldSeconds.DEFAULT),
    val manualAutodownshiftRpm: Double = ManualAutodownshiftRpm.DEFAULT.toDouble(),
) {
    companion object {
        fun fromSettings(settings: AutomaticTransmissionSettings): AutomaticTransmissionConfig {
            return AutomaticTransmissionConfig(
                cruisingLogicEnabled = settings.cruisingLogicEnabled,
                allowManualOnLaunchEnabled = settings.allowManualOnLaunchEnabled,
                manualTransmissionKickdownEnabled = settings.manualTransmissionKickdownEnabled,
                cruisingShiftOffsetsByTachMaxRpm = settings.cruisingShiftOffsetsByTachMaxRpm,
                racingReturnMaxThrottle = RacingReturnThrottlePercent.asFraction(
                    settings.racingReturnThrottlePercent,
                ),
                racingEnterDelayMilliseconds = RacingEnterDelayMilliseconds.normalize(
                    settings.racingEnterDelayMilliseconds,
                ),
                automaticUpshiftMilliseconds = AutomaticUpshiftMilliseconds.normalize(
                    settings.automaticUpshiftMilliseconds,
                ),
                automaticDownshiftMilliseconds = AutomaticDownshiftMilliseconds.normalize(
                    settings.automaticDownshiftMilliseconds,
                ),
                racingReturnHoldSeconds = settings.racingReturnHoldSeconds.toDouble(),
                manualRedlineHoldSeconds = ManualRedlineHoldSeconds.asHoldSeconds(settings.manualRedlineHoldSeconds),
                manualAutodownshiftRpm = settings.manualAutodownshiftRpm.toDouble(),
            )
        }
    }
}
