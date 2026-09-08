package com.gabrielpc.enginesoundsimulator.drive

import android.content.Context
import com.gabrielpc.enginesoundsimulator.AppPreferenceStores
import kotlin.math.roundToInt

/** Light-acceleration threshold used after arming a racing return to enter cruising on the next pedal input. */
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

/** Per-downshift RPM blend duration during cruising→racing kickdown. */
internal object RacingEnterDelayMilliseconds {
    const val MIN = 0
    const val MAX = 2_000
    const val DEFAULT = 100
    const val STEP = 100

    fun normalize(value: Int): Int {
        val stepped = ((value.toFloat() / STEP).roundToInt() * STEP)
        return stepped.coerceIn(MIN, MAX)
    }

    fun format(value: Int): String {
        val ms = normalize(value)
        if (ms >= 1_000) {
            return if (ms % 1_000 == 0) {
                "${ms / 1_000}s"
            } else {
                String.format(java.util.Locale.US, "%.1fs", ms / 1_000.0)
            }
        }

        return "$ms ms"
    }

    fun asKickdownDownshiftSeconds(milliseconds: Int): Double {
        return normalize(milliseconds) / 1_000.0
    }
}

/** Automatic upshift RPM blend duration (normal shifts, not kickdown). */
internal object AutomaticUpshiftMilliseconds {
    const val MIN = 50
    const val MAX = 500
    const val DEFAULT = 100
    const val STEP = 10

    fun normalize(value: Int): Int {
        val stepped = ((value.toFloat() / STEP).roundToInt() * STEP)
        return stepped.coerceIn(MIN, MAX)
    }

    fun format(value: Int): String {
        return "${normalize(value)} ms"
    }

    fun asSeconds(milliseconds: Int): Double {
        return normalize(milliseconds) / 1_000.0
    }
}

/** Automatic downshift RPM blend duration (normal shifts, not kickdown). */
internal object AutomaticDownshiftMilliseconds {
    const val MIN = 50
    const val MAX = 500
    const val DEFAULT = 150
    const val STEP = 10

    fun normalize(value: Int): Int {
        val stepped = ((value.toFloat() / STEP).roundToInt() * STEP)
        return stepped.coerceIn(MIN, MAX)
    }

    fun format(value: Int): String {
        return "${normalize(value)} ms"
    }

    fun asSeconds(milliseconds: Int): Double {
        return normalize(milliseconds) / 1_000.0
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
    const val NEVER = -1
    const val DEFAULT = 1_000

    val STOPS: IntArray = intArrayOf(0, 500, 1_000, NEVER)

    fun normalize(value: Int): Int {
        return STOPS.minByOrNull { kotlin.math.abs(it - value) } ?: DEFAULT
    }

    fun stopIndex(value: Int): Int {
        val normalized = normalize(value)
        return STOPS.indexOf(normalized).coerceAtLeast(0)
    }

    fun stopValue(index: Int): Int {
        return STOPS[index.coerceIn(0, STOPS.lastIndex)]
    }

    fun format(value: Int): String {
        return when (normalize(value)) {
            0 -> "0s"
            500 -> "0.5s"
            1_000 -> "1s"
            NEVER -> "NEVER"
            else -> "1s"
        }
    }

    fun asHoldSeconds(value: Int): Double? {
        return when (normalize(value)) {
            NEVER -> null
            else -> normalize(value) / 1_000.0
        }
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
    val allowManualOnLaunchEnabled: Boolean = false,
    val manualTransmissionKickdownEnabled: Boolean = true,
    val cruisingShiftOffsetsByTachMaxRpm: Map<Int, Int> = CruisingShiftOffsetByTachMaxRpm.defaultOffsets(),
    val racingReturnThrottlePercent: Int = RacingReturnThrottlePercent.DEFAULT,
    val racingEnterDelayMilliseconds: Int = RacingEnterDelayMilliseconds.DEFAULT,
    val automaticUpshiftMilliseconds: Int = AutomaticUpshiftMilliseconds.DEFAULT,
    val automaticDownshiftMilliseconds: Int = AutomaticDownshiftMilliseconds.DEFAULT,
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
            allowManualOnLaunchEnabled = preferences.getBoolean(KEY_ALLOW_MANUAL_ON_LAUNCH_ENABLED, false),
            manualTransmissionKickdownEnabled = preferences.getBoolean(
                KEY_MANUAL_TRANSMISSION_KICKDOWN_ENABLED,
                true,
            ),
            cruisingShiftOffsetsByTachMaxRpm = loadCruisingShiftOffsetsByTachMaxRpm(),
            racingReturnThrottlePercent = RacingReturnThrottlePercent.normalize(
                preferences.getInt(
                    KEY_RACING_RETURN_THROTTLE_PERCENT,
                    RacingReturnThrottlePercent.DEFAULT,
                ),
            ),
            racingEnterDelayMilliseconds = RacingEnterDelayMilliseconds.normalize(
                preferences.getInt(
                    KEY_RACING_ENTER_DELAY_MILLISECONDS,
                    RacingEnterDelayMilliseconds.DEFAULT,
                ),
            ),
            automaticUpshiftMilliseconds = AutomaticUpshiftMilliseconds.normalize(
                preferences.getInt(
                    KEY_AUTOMATIC_UPSHIFT_MILLISECONDS,
                    AutomaticUpshiftMilliseconds.DEFAULT,
                ),
            ),
            automaticDownshiftMilliseconds = AutomaticDownshiftMilliseconds.normalize(
                preferences.getInt(
                    KEY_AUTOMATIC_DOWNSHIFT_MILLISECONDS,
                    AutomaticDownshiftMilliseconds.DEFAULT,
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
            .putBoolean(KEY_ALLOW_MANUAL_ON_LAUNCH_ENABLED, settings.allowManualOnLaunchEnabled)
            .putBoolean(
                KEY_MANUAL_TRANSMISSION_KICKDOWN_ENABLED,
                settings.manualTransmissionKickdownEnabled,
            )
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
                KEY_RACING_ENTER_DELAY_MILLISECONDS,
                RacingEnterDelayMilliseconds.normalize(settings.racingEnterDelayMilliseconds),
            )
            .putInt(
                KEY_AUTOMATIC_UPSHIFT_MILLISECONDS,
                AutomaticUpshiftMilliseconds.normalize(settings.automaticUpshiftMilliseconds),
            )
            .putInt(
                KEY_AUTOMATIC_DOWNSHIFT_MILLISECONDS,
                AutomaticDownshiftMilliseconds.normalize(settings.automaticDownshiftMilliseconds),
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
        const val KEY_ALLOW_MANUAL_ON_LAUNCH_ENABLED = "allow_manual_on_launch_enabled"
        const val KEY_MANUAL_TRANSMISSION_KICKDOWN_ENABLED = "manual_transmission_kickdown_enabled"
        const val KEY_CRUISING_SHIFT_OFFSET_RPM = "cruising_shift_offset_rpm"
        const val KEY_RACING_RETURN_THROTTLE_PERCENT = "racing_return_throttle_percent"
        const val KEY_RACING_ENTER_DELAY_MILLISECONDS = "racing_enter_delay_milliseconds"
        const val KEY_AUTOMATIC_UPSHIFT_MILLISECONDS = "automatic_upshift_milliseconds"
        const val KEY_AUTOMATIC_DOWNSHIFT_MILLISECONDS = "automatic_downshift_milliseconds"
        const val KEY_RACING_RETURN_HOLD_SECONDS = "racing_return_hold_seconds"
        const val KEY_MANUAL_REDLINER_HOLD_SECONDS = "manual_redline_hold_seconds"
        const val KEY_MANUAL_AUTODOWNSHIFT_RPM = "manual_autodownshift_rpm"
        const val KEY_TACHOMETER_CRUISING_SHIFT_RANGE_OVERLAY_ENABLED =
            "tachometer_cruising_shift_range_overlay_enabled"
        const val LEGACY_OFFSET_RPM_KEY = "offset_rpm"
    }
}
