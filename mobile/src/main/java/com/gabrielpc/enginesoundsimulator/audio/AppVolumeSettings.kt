package com.gabrielpc.enginesoundsimulator.audio

import android.content.Context
import com.gabrielpc.enginesoundsimulator.AppPreferenceStores
import kotlin.math.roundToInt

data class AppVolumeSettings(
    val percent: Int = DEFAULT_PERCENT,
) {
    val linear: Float get() = normalized().percent / 100f

    fun normalized(): AppVolumeSettings = copy(percent = percent.coerceIn(MIN_PERCENT, MAX_PERCENT))

    companion object {
        const val MIN_PERCENT = 0
        const val DEFAULT_PERCENT = 100
        const val MAX_PERCENT = 200

        fun sliderSteps(): Int = MAX_PERCENT - MIN_PERCENT - 1

        fun fromLegacyOverall(overall: Float?): AppVolumeSettings {
            val migrated = overall
                ?.takeIf(Float::isFinite)
                ?.let { (it * 100f).roundToInt() }
                ?: DEFAULT_PERCENT

            return AppVolumeSettings(migrated).normalized()
        }
    }
}

internal class AppVolumeRepository(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(
        AppPreferenceStores.APP_VOLUME,
        Context.MODE_PRIVATE,
    )
    private val legacyPreferences = appContext.getSharedPreferences(
        AppPreferenceStores.MIXER_GLOBAL_GAINS,
        Context.MODE_PRIVATE,
    )

    fun load(): AppVolumeSettings {
        migrateIfNeeded()

        return AppVolumeSettings(
            percent = preferences.getInt(KEY_PERCENT, AppVolumeSettings.DEFAULT_PERCENT),
        ).normalized()
    }

    fun save(settings: AppVolumeSettings) {
        val normalized = settings.normalized()
        preferences.edit()
            .putInt(KEY_PERCENT, normalized.percent)
            .putBoolean(KEY_MIGRATION_COMPLETE, true)
            .commit()
    }

    fun reset() {
        save(AppVolumeSettings())
    }

    private fun migrateIfNeeded() {
        if (preferences.getBoolean(KEY_MIGRATION_COMPLETE, false)) {
            return
        }

        val legacyOverall = if (legacyPreferences.contains(KEY_LEGACY_OVERALL)) {
            legacyPreferences.getFloat(KEY_LEGACY_OVERALL, 1f)
        } else {
            null
        }
        val migrated = AppVolumeSettings.fromLegacyOverall(legacyOverall)
        preferences.edit()
            .putInt(KEY_PERCENT, migrated.percent)
            .putBoolean(KEY_MIGRATION_COMPLETE, true)
            .commit()
    }

    private companion object {
        const val KEY_PERCENT = "percent"
        const val KEY_MIGRATION_COMPLETE = "overall_migration_complete"
        const val KEY_LEGACY_OVERALL = "overall"
    }
}

