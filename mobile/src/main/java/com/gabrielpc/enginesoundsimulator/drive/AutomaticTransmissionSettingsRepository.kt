package com.gabrielpc.enginesoundsimulator.drive

import android.content.Context
import com.gabrielpc.enginesoundsimulator.AppPreferenceStores
import kotlin.math.roundToInt

/** Light-acceleration threshold used after braking in racing mode to return to cruising. */
internal object RacingReturnThrottlePercent {
    const val MIN = 20
    const val MAX = 80
    const val DEFAULT = 30
    const val STEP = 10

    fun normalize(value: Int): Int {
        val stepped = ((value.toFloat() / STEP).roundToInt() * STEP)
        return stepped.coerceIn(MIN, MAX)
    }

    fun asFraction(percent: Int): Double {
        return normalize(percent) / 100.0
    }
}

/** Time below [RacingReturnThrottlePercent] required to leave racing mode. */
internal object RacingReturnHoldSeconds {
    const val MIN = 0
    const val MAX = 20
    const val DEFAULT = 10
    const val STEP = 5

    fun normalize(value: Int): Int {
        val stepped = ((value.toFloat() / STEP).roundToInt() * STEP)
        return stepped.coerceIn(MIN, MAX)
    }
}

/** Manual mode: sustained redline time before returning to automatic racing mode. */
internal object ManualRedlineHoldSeconds {
    const val MIN = 1
    const val MAX = 10
    const val DEFAULT = 1
    const val STEP = 1

    fun normalize(value: Int): Int {
        val stepped = ((value.toFloat() / STEP).roundToInt() * STEP)
        return stepped.coerceIn(MIN, MAX)
    }
}

/** Manual mode: below this RPM the drivetrain downshifts once without leaving manual. */
internal object ManualAutodownshiftRpm {
    const val MIN = 500
    const val MAX = 4_000
    const val DEFAULT = 2_000
    const val STEP = 100

    fun normalize(value: Int): Int {
        val stepped = ((value.toFloat() / STEP).roundToInt() * STEP)
        return stepped.coerceIn(MIN, MAX)
    }
}

internal data class AutomaticTransmissionSettings(
    val cruisingLogicEnabled: Boolean = true,
    val sixGearOnLaunchEnabled: Boolean = true,
    val cruisingShiftOffsetsByTachMaxRpm: Map<Int, Int> = CruisingShiftOffsetByTachMaxRpm.defaultOffsets(),
    val racingReturnThrottlePercent: Int = RacingReturnThrottlePercent.DEFAULT,
    val racingReturnHoldSeconds: Int = RacingReturnHoldSeconds.DEFAULT,
    val manualRedlineHoldSeconds: Int = ManualRedlineHoldSeconds.DEFAULT,
    val manualAutodownshiftRpm: Int = ManualAutodownshiftRpm.DEFAULT,
    val tachometerCruisingShiftRangeOverlayEnabled: Boolean = true,
)

internal class AutomaticTransmissionSettingsRepository(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(
        AppPreferenceStores.AUTOMATIC_TRANSMISSION_SETTINGS,
        Context.MODE_PRIVATE,
    )

    fun load(): AutomaticTransmissionSettings {
        migrateLegacyOffsetIfNeeded()
        return AutomaticTransmissionSettings(
            cruisingLogicEnabled = preferences.getBoolean(KEY_CRUISING_LOGIC_ENABLED, true),
            sixGearOnLaunchEnabled = preferences.getBoolean(KEY_SIX_GEAR_ON_LAUNCH_ENABLED, true),
            cruisingShiftOffsetsByTachMaxRpm = loadCruisingShiftOffsetsByTachMaxRpm(),
            racingReturnThrottlePercent = RacingReturnThrottlePercent.normalize(
                preferences.getInt(
                    KEY_RACING_RETURN_THROTTLE_PERCENT,
                    RacingReturnThrottlePercent.DEFAULT,
                ),
            ),
            racingReturnHoldSeconds = RacingReturnHoldSeconds.normalize(
                preferences.getInt(KEY_RACING_RETURN_HOLD_SECONDS, RacingReturnHoldSeconds.DEFAULT),
            ),
            manualRedlineHoldSeconds = ManualRedlineHoldSeconds.normalize(
                preferences.getInt(KEY_MANUAL_REDLINER_HOLD_SECONDS, ManualRedlineHoldSeconds.DEFAULT),
            ),
            manualAutodownshiftRpm = ManualAutodownshiftRpm.normalize(
                preferences.getInt(KEY_MANUAL_AUTODOWNSHIFT_RPM, ManualAutodownshiftRpm.DEFAULT),
            ),
            tachometerCruisingShiftRangeOverlayEnabled = preferences.getBoolean(
                KEY_TACHOMETER_CRUISING_SHIFT_RANGE_OVERLAY_ENABLED,
                true,
            ),
        )
    }

    fun save(settings: AutomaticTransmissionSettings) {
        val normalizedOffsets = CruisingShiftOffsetByTachMaxRpm.normalizeMap(settings.cruisingShiftOffsetsByTachMaxRpm)
        val editor = preferences.edit()
            .putBoolean(KEY_CRUISING_LOGIC_ENABLED, settings.cruisingLogicEnabled)
            .putBoolean(KEY_SIX_GEAR_ON_LAUNCH_ENABLED, settings.sixGearOnLaunchEnabled)
        CruisingShiftOffsetByTachMaxRpm.TIERS.forEach { tier ->
            editor.putInt(
                CruisingShiftOffsetByTachMaxRpm.preferenceKey(tier),
                normalizedOffsets.getValue(tier),
            )
        }
        editor
            .putInt(
                KEY_RACING_RETURN_THROTTLE_PERCENT,
                RacingReturnThrottlePercent.normalize(settings.racingReturnThrottlePercent),
            )
            .putInt(
                KEY_RACING_RETURN_HOLD_SECONDS,
                RacingReturnHoldSeconds.normalize(settings.racingReturnHoldSeconds),
            )
            .putInt(
                KEY_MANUAL_REDLINER_HOLD_SECONDS,
                ManualRedlineHoldSeconds.normalize(settings.manualRedlineHoldSeconds),
            )
            .putInt(
                KEY_MANUAL_AUTODOWNSHIFT_RPM,
                ManualAutodownshiftRpm.normalize(settings.manualAutodownshiftRpm),
            )
            .putBoolean(
                KEY_TACHOMETER_CRUISING_SHIFT_RANGE_OVERLAY_ENABLED,
                settings.tachometerCruisingShiftRangeOverlayEnabled,
            )
            .commit()
    }

    fun reset() {
        preferences.edit().clear().commit()
    }

    private fun loadCruisingShiftOffsetsByTachMaxRpm(): Map<Int, Int> {
        val loaded = CruisingShiftOffsetByTachMaxRpm.TIERS.associateWith { tier ->
            CruisingShiftOffsetByTachMaxRpm.normalize(
                preferences.getInt(
                    CruisingShiftOffsetByTachMaxRpm.preferenceKey(tier),
                    CruisingShiftOffsetByTachMaxRpm.defaultOffsets().getValue(tier),
                ),
            )
        }
        return CruisingShiftOffsetByTachMaxRpm.normalizeMap(loaded)
    }

    private fun migrateLegacyOffsetIfNeeded() {
        if (CruisingShiftOffsetByTachMaxRpm.TIERS.all { preferences.contains(CruisingShiftOffsetByTachMaxRpm.preferenceKey(it)) }) {
            return
        }

        val legacySingleOffset = when {
            preferences.contains(KEY_CRUISING_SHIFT_OFFSET_RPM) -> {
                CruisingShiftOffsetByTachMaxRpm.normalize(
                    preferences.getInt(KEY_CRUISING_SHIFT_OFFSET_RPM, 2_000),
                )
            }
            else -> {
                val legacyPreferences = appContext.getSharedPreferences(
                    AppPreferenceStores.CRUISING_SHIFT_OFFSET_RPM,
                    Context.MODE_PRIVATE,
                )
                if (legacyPreferences.contains(LEGACY_OFFSET_RPM_KEY)) {
                    CruisingShiftOffsetByTachMaxRpm.normalize(
                        legacyPreferences.getInt(LEGACY_OFFSET_RPM_KEY, 2_000),
                    )
                } else {
                    null
                }
            }
        }

        if (legacySingleOffset == null) {
            return
        }

        val editor = preferences.edit()
        CruisingShiftOffsetByTachMaxRpm.TIERS.forEach { tier ->
            editor.putInt(CruisingShiftOffsetByTachMaxRpm.preferenceKey(tier), legacySingleOffset)
        }
        editor.commit()
    }

    private companion object {
        const val KEY_CRUISING_LOGIC_ENABLED = "cruising_logic_enabled"
        const val KEY_SIX_GEAR_ON_LAUNCH_ENABLED = "six_gear_on_launch_enabled"
        const val KEY_CRUISING_SHIFT_OFFSET_RPM = "cruising_shift_offset_rpm"
        const val KEY_RACING_RETURN_THROTTLE_PERCENT = "racing_return_throttle_percent"
        const val KEY_RACING_RETURN_HOLD_SECONDS = "racing_return_hold_seconds"
        const val KEY_MANUAL_REDLINER_HOLD_SECONDS = "manual_redline_hold_seconds"
        const val KEY_MANUAL_AUTODOWNSHIFT_RPM = "manual_autodownshift_rpm"
        const val KEY_TACHOMETER_CRUISING_SHIFT_RANGE_OVERLAY_ENABLED =
            "tachometer_cruising_shift_range_overlay_enabled"
        const val LEGACY_OFFSET_RPM_KEY = "offset_rpm"
    }
}
