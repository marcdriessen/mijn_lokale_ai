package com.example.auralocalai.data.translate

import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Translates short texts (headlines, teasers) into a target language. */
interface NewsTranslator {
    /** Returns the translation, or null when the text is already in [targetLanguage] or cannot be translated. */
    suspend fun translate(text: String, targetLanguage: String): String?
    fun close()
}

/**
 * Offline translation with Google ML Kit: detects the language of the text and translates it
 * on the device. Language packs (~30 MB each) are downloaded once and then work without internet.
 */
class MlKitNewsTranslator : NewsTranslator {

    private val identifier = LanguageIdentification.getClient()
    private val translators = mutableMapOf<String, Translator>()
    private val failedPairs = mutableSetOf<String>()

    override suspend fun translate(text: String, targetLanguage: String): String? {
        val clean = text.trim()
        if (clean.isEmpty()) return null
        val source = try {
            identifier.identifyLanguage(clean.take(500)).awaitResult()
        } catch (_: Exception) {
            return null
        }
        if (source == "und" || source.equals(targetLanguage, ignoreCase = true)) return null
        val sourceLang = TranslateLanguage.fromLanguageTag(source) ?: return null
        val targetLang = TranslateLanguage.fromLanguageTag(targetLanguage) ?: return null
        val key = "$sourceLang>$targetLang"
        if (key in failedPairs) return null
        return try {
            val translator = translators.getOrPut(key) {
                Translation.getClient(
                    TranslatorOptions.Builder()
                        .setSourceLanguage(sourceLang)
                        .setTargetLanguage(targetLang)
                        .build()
                )
            }
            translator.downloadModelIfNeeded(DownloadConditions.Builder().build()).awaitResult()
            translator.translate(clean).awaitResult().takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            failedPairs += key
            null
        }
    }

    override fun close() {
        translators.values.forEach { runCatching { it.close() } }
        translators.clear()
        runCatching { identifier.close() }
    }
}

suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel() }
}
