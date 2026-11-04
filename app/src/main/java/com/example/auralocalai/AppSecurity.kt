package com.example.auralocalai

import android.content.Context

/** App lock and privacy mode switches (stored in "app_settings"). */
object AppSecurity {
    private const val PREFS = "app_settings"
    const val KEY_LOCK = "app_lock_enabled"
    const val KEY_PRIVACY = "privacy_mode"
    const val KEY_ANONYMIZE = "auto_anonymize"

    /** Automatically remove personal data from scanned, dictated and imported texts (default on). */
    fun isAnonymizeEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ANONYMIZE, true)

    fun setAnonymizeEnabled(context: Context, enabled: Boolean) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ANONYMIZE, enabled).apply()

    /** Regex-only anonymization when the setting is on (used where no model call is wanted). */
    fun maybeAnonymize(context: Context, text: String?): String? =
        if (text == null || !isAnonymizeEnabled(context)) text
        else com.example.auralocalai.data.privacy.Anonymizer.anonymize(text).text

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
