package com.example.auralocalai.ui.components

import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.widget.Toast
import com.example.auralocalai.R
import java.util.Locale

/**
 * Picks the right voice for a text: the language is taken from the text itself (Cyrillic → Bulgarian),
 * then the best installed offline voice for that language is chosen. When no voice for the language is
 * installed, nothing is read (a Dutch voice reading Bulgarian is not understandable) and the user is
 * told how to install it.
 */
object TtsVoices {

    /** Language of the text: Cyrillic text is Bulgarian (the app's Cyrillic language); otherwise [fallback]. */
    fun languageOf(text: String, fallback: String): String {
        val letters = text.count { it.isLetter() }
        if (letters == 0) return fallback
        val cyrillic = text.count { Character.UnicodeBlock.of(it) == Character.UnicodeBlock.CYRILLIC }
        return if (cyrillic * 10 > letters * 3) "bg" else if (fallback == "bg") "en" else fallback
    }

    /**
     * Sets the best voice for [languageCode] on [tts]. Returns false (and explains) when the phone has no
     * voice for this language. Offline voices are preferred, so the text does not leave the phone.
     */
    fun apply(context: Context, tts: TextToSpeech, languageCode: String): Boolean {
        val locale = Locale.forLanguageTag(dictationLanguageTag(languageCode))
        val available = runCatching { tts.isLanguageAvailable(locale) }.getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
        if (available < TextToSpeech.LANG_AVAILABLE) {
            Toast.makeText(context, context.getString(R.string.tts_language_missing_named, locale.getDisplayLanguage(Locale.getDefault())), Toast.LENGTH_LONG).show()
            runCatching {
                context.startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            return false
        }
        tts.language = locale
        val best = runCatching { tts.voices }.getOrNull().orEmpty()
            .filter { it.locale.language == locale.language }
            .filterNot { it.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) == true }
            .sortedWith(
                compareBy<Voice> { it.isNetworkConnectionRequired }
                    .thenBy { if (it.locale.country.equals(locale.country, ignoreCase = true)) 0 else 1 }
                    .thenByDescending { it.quality }
                    .thenBy { it.latency }
            )
            .firstOrNull()
        if (best != null) runCatching { tts.voice = best }
        return true
    }

    /** Reads [text] in the language it is written in; long texts are split into sentences. */
    fun speak(context: Context, tts: TextToSpeech, text: String, preferredLanguage: String, utteranceId: String = "speak"): Boolean {
        val clean = text.replace(Regex("[*#`_>|]"), " ")
            .replace(Regex("\\[(NAAM|BSN|EGN|DATUM|ADRES|TELEFOON|E-MAIL|POSTCODE|IBAN|NUMMER)]"), " ")
        if (!apply(context, tts, languageOf(clean, preferredLanguage))) return false
        val chunks = clean.split(Regex("(?<=[.!?])\\s+|\\n+")).filter { it.isNotBlank() }
        chunks.forEachIndexed { i, chunk ->
            tts.speak(chunk, if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, "${utteranceId}_$i")
        }
        return chunks.isNotEmpty()
    }
}
