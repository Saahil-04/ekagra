package com.ekagra.app.utils

import android.content.Context
import android.content.SharedPreferences

/**
 * Centralised SharedPreferences manager.
 *
 * Keys managed:
 *  - focus_mode_enabled  : whether focus blocking is active
 *  - theme_mode          : ThemeMode value (light/dark/system) — replaces dark_theme_enabled
 *  - dark_theme_enabled  : DEPRECATED; kept for migration reads only
 *  - onboarding_complete : true once user finishes onboarding
 */
object PreferencesManager {

    private const val PREF_FILE           = "ekagra_prefs"
    private const val KEY_FOCUS_ENABLED   = "focus_mode_enabled"
    private const val KEY_THEME_MODE      = "theme_mode"
    private const val KEY_DARK_THEME      = "dark_theme_enabled"   // legacy
    private const val KEY_ONBOARDING_DONE = "onboarding_complete"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)

    // ─── Focus mode ──────────────────────────────────────────────────────────

    fun isFocusModeEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_FOCUS_ENABLED, false)

    fun setFocusModeEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_FOCUS_ENABLED, enabled).apply()
    }

    // ─── Theme ───────────────────────────────────────────────────────────────

    fun getThemeMode(context: Context): ThemeMode {
        val stored = prefs(context).getString(KEY_THEME_MODE, null)
        if (stored != null) return ThemeMode.fromValue(stored)
        // Migrate from legacy boolean key on first read
        val legacy = prefs(context).getBoolean(KEY_DARK_THEME, false)
        return if (legacy) ThemeMode.DARK else ThemeMode.SYSTEM
    }

    fun setThemeMode(context: Context, mode: ThemeMode) {
        prefs(context).edit().putString(KEY_THEME_MODE, mode.value).apply()
    }

    /** @deprecated Use getThemeMode() instead. */
    @Deprecated("Use getThemeMode()", ReplaceWith("getThemeMode(context) == ThemeMode.DARK"))
    fun isDarkTheme(context: Context): Boolean = getThemeMode(context) == ThemeMode.DARK

    /** @deprecated Use setThemeMode() instead. */
    @Deprecated("Use setThemeMode()", ReplaceWith("setThemeMode(context, if (dark) ThemeMode.DARK else ThemeMode.LIGHT)"))
    fun setDarkTheme(context: Context, dark: Boolean) =
        setThemeMode(context, if (dark) ThemeMode.DARK else ThemeMode.LIGHT)

    // ─── Onboarding ──────────────────────────────────────────────────────────

    fun isOnboardingComplete(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ONBOARDING_DONE, false)

    fun markOnboardingComplete(context: Context) {
        prefs(context).edit().putBoolean(KEY_ONBOARDING_DONE, true).apply()
    }
}