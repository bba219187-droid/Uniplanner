package com.uniplanner.app.settings

import android.content.Context
import android.content.res.Configuration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** The student's look and language choices. "System" follows the phone. */
object AppSettings {
    private const val PREFS = "settings"
    private const val THEME = "theme"
    private const val LANGUAGE = "language"

    /** Language tags the app is translated into; an empty tag means the phone's language. */
    val languages = listOf("", "pt", "en")

    private val theme = MutableStateFlow<ThemeMode?>(null)

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun themeFlow(ctx: Context): StateFlow<ThemeMode?> {
        if (theme.value == null) {
            theme.value = runCatching { ThemeMode.valueOf(prefs(ctx).getString(THEME, null)!!) }.getOrDefault(ThemeMode.SYSTEM)
        }
        return theme
    }

    fun setTheme(ctx: Context, mode: ThemeMode) {
        prefs(ctx).edit().putString(THEME, mode.name).apply()
        theme.value = mode
    }

    fun language(ctx: Context): String = prefs(ctx).getString(LANGUAGE, "").orEmpty()

    /** Saves the language; the caller recreates the screen so the new texts show. */
    fun setLanguage(ctx: Context, tag: String) {
        prefs(ctx).edit().putString(LANGUAGE, tag).commit()
    }

    /** A context that shows texts in the chosen language, or [base] itself when following the phone. */
    fun wrap(base: Context): Context {
        val tag = language(base)
        if (tag.isEmpty()) return base
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }
}
