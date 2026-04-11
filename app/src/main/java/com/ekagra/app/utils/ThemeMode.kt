package com.ekagra.app.utils

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import com.ekagra.app.R

enum class ThemeMode(val value: String) {
    LIGHT("light"),
    DARK("dark"),
    SYSTEM("system");

    fun toNightMode(): Int = when (this) {
        LIGHT  -> AppCompatDelegate.MODE_NIGHT_NO
        DARK   -> AppCompatDelegate.MODE_NIGHT_YES
        SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    }

    fun displayName(context: Context): String = when (this) {
        LIGHT  -> context.getString(R.string.theme_light)
        DARK   -> context.getString(R.string.theme_dark)
        SYSTEM -> context.getString(R.string.theme_system)
    }

    companion object {
        fun fromValue(value: String): ThemeMode =
            entries.firstOrNull { it.value == value } ?: SYSTEM
    }
}

