package com.example.auralocalai

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.os.LocaleList
import java.util.Locale

/**
 * In-app language choice ("system", "en" or "nl").
 *
 * The choice is stored in the "app_settings" preferences. Activities and services
 * wrap their base context with [wrap] so all resources (stringResource, getString)
 * use the chosen language. "system" keeps the device language.
 */
object AppLocale {
    const val PREF_KEY = "app_language"
    const val SYSTEM = "system"
    val SUPPORTED = listOf(SYSTEM, "en", "nl", "bg")

    private const val PREFS_NAME = "app_settings"

    @Volatile
    private var cachedTag: String? = null
    @Volatile
    private var cachedContext: Context? = null

    fun getLanguage(context: Context): String {
        val stored = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(PREF_KEY, SYSTEM) ?: SYSTEM
        return if (stored in SUPPORTED) stored else SYSTEM
    }

    /** The language the app actually shows: the chosen one, or the device language for "system". */
    fun effectiveLanguage(context: Context): String {
        val tag = getLanguage(context)
        return if (tag == SYSTEM) Locale.getDefault().language.ifBlank { "en" } else tag
    }

    /** English name of a language code, for instructions to the model. Null when unknown. */
    fun languageNameForModel(code: String): String? = when (code) {
        "nl" -> "Dutch"
        "bg" -> "Bulgarian"
        "en" -> "English"
        "de" -> "German"
        "fr" -> "French"
        else -> com.example.auralocalai.data.translate.TranslateLanguages.englishName(code)
    }

    fun setLanguage(context: Context, tag: String) {
        val value = if (tag in SUPPORTED) tag else SYSTEM
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(PREF_KEY, value).apply()
        cachedTag = null
        cachedContext = null
    }

    /** Returns [base] with its configuration set to the chosen language (or [base] itself for "system"). */
    fun wrap(base: Context): Context {
        val tag = getLanguage(base)
        if (tag == SYSTEM) return base
        val locale = Locale.forLanguageTag(tag)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLocales(LocaleList(locale))
        return base.createConfigurationContext(config)
    }

    /**
     * Localized context for non-UI code (ViewModel, services) that only has the
     * application context. Cached per language to avoid re-creating it for every string.
     */
    fun localized(appContext: Context): Context {
        val tag = getLanguage(appContext)
        val cached = cachedContext
        if (cached != null && cachedTag == tag) return cached
        val wrapped = wrap(appContext)
        cachedTag = tag
        cachedContext = wrapped
        return wrapped
    }

    fun findActivity(context: Context): Activity? {
        var current: Context? = context
        while (current is ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        return null
    }
}
