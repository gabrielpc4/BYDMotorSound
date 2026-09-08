package com.gabrielpc.enginesoundsimulator.ui.theme

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.gabrielpc.enginesoundsimulator.AppPreferenceStores

/** Persists which dashboard skin the driver last selected. */
internal class DashboardThemeRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        AppPreferenceStores.DASHBOARD_VISUAL_THEME,
        Context.MODE_PRIVATE,
    )

    fun load(): DashboardTheme {
        val stored = preferences.getString(KEY_THEME, null)
        return DashboardTheme.entries.firstOrNull { it.name == stored } ?: DashboardTheme.Classic
    }

    fun save(theme: DashboardTheme) {
        preferences.edit()
            .putString(KEY_THEME, theme.name)
            .commit()
    }

    fun reset() {
        preferences.edit().clear().commit()
    }

    private companion object {
        const val KEY_THEME = "dashboard_visual_theme"
    }
}

/**
 * Holds the active skin for the composition and writes every change straight to disk.
 *
 * The theme is a pure presentation preference, so it lives here rather than in `DriveController`,
 * which stays focused on drive and audio state.
 */
internal class DashboardThemeController(
    private val repository: DashboardThemeRepository,
    initialTheme: DashboardTheme,
) {
    var theme by mutableStateOf(initialTheme)
        private set

    val skin: DashboardSkin
        get() = skinFor(theme)

    fun select(theme: DashboardTheme) {
        if (theme == this.theme) {
            return
        }

        this.theme = theme
        repository.save(theme)
    }

    fun toggle() {
        val next = when (theme) {
            DashboardTheme.Classic -> DashboardTheme.AudioLab
            DashboardTheme.AudioLab -> DashboardTheme.Classic
        }
        select(next)
    }

    /** Called by the settings "reset all" flow so the skin returns to Classic with everything else. */
    fun resetToDefault() {
        repository.reset()
        theme = repository.load()
    }
}

@Composable
internal fun rememberDashboardThemeController(context: Context): DashboardThemeController {
    return remember(context) {
        val repository = DashboardThemeRepository(context)
        DashboardThemeController(repository, repository.load())
    }
}
