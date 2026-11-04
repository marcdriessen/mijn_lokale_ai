package com.example.auralocalai

import android.content.Context

/** App lock and privacy mode switches (stored in "app_settings"). */
object AppSecurity {
    private const val PREFS = "app_settings"
    const val KEY_LOCK = "app_lock_enabled"
    const val KEY_PRIVACY = "privacy_mode"

    fun isLockEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_LOCK, false)

    fun setLockEnabled(context: Context, enabled: Boolean) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_LOCK, enabled).apply()

    /** Privacy mode: no internet functions (web search, news, PubMed, Brave); everything stays on the phone. */
    fun isPrivacyMode(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_PRIVACY, false)

    fun setPrivacyMode(context: Context, enabled: Boolean) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_PRIVACY, enabled).apply()
}
