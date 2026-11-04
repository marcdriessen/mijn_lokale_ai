package com.example.auralocalai.ui

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.auralocalai.AppLocale
import com.example.auralocalai.R
import com.example.auralocalai.data.translate.MlKitNewsTranslator
import com.example.auralocalai.data.translate.PageTextExtractor
import com.example.auralocalai.data.translate.awaitResult
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class TranslateUiState(
    val input: String = "",
    val sourceTitle: String = "",
    val output: String = "",
    val targetLanguage: String = "nl",
    val busy: String? = null,
    val message: String? = null
)

enum class AiAction { SUMMARIZE, SIMPLIFY, EMAIL }

/**
 * "Translate" mini-app: offline translation (ML Kit) of typed text, photos (OCR) and shared
 * texts or web pages, plus optional AI actions (summarize, simplify, e-mail) with the shared model.
 */
class TranslateViewModel(application: Application) : AndroidViewModel(application) {

    private val translator = MlKitNewsTranslator()
    private val client = OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS).build()
    private val _state = MutableStateFlow(TranslateUiState(targetLanguage = defaultTarget(application)))
    val state: StateFlow<TranslateUiState> = _state.asStateFlow()

    private fun defaultTarget(app: Application): String =
        AppLocale.effectiveLanguage(app).takeIf { it in listOf("nl", "en", "bg", "de") } ?: "nl"

    private fun str(id: Int, vararg args: Any): String =
        AppLocale.localized(getApplication<Application>()).getString(id, *args)

    fun setInput(text: String) = _state.update { it.copy(input = text) }
    fun setTarget(code: String) = _state.update { it.copy(targetLanguage = code) }
    fun clearMessage() = _state.update { it.copy(message = null) }

    /** Text shared from another app: a web address is opened and its text extracted first. */
    fun receiveShared(text: String) {
        val url = if (com.example.auralocalai.AppSecurity.isPrivacyMode(getApplication())) null else PageTextExtractor.firstUrl(text)
        if (url == null || text.trim().length > url.length + 40) {
            _state.update { it.copy(input = text.trim(), sourceTitle = "", output = "") }
            translate()
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = str(R.string.translate_fetching_page), output = "") }
            try {
                val html = withContext(Dispatchers.IO) {
                    client.newCall(
                        Request.Builder().url(url).header("User-Agent", "Mozilla/5.0 (Android) AuraLocalAI/1.0").build()
                    ).execute().use { it.body?.string().orEmpty() }
                }
                val page = PageTextExtractor.extract(html)
                _state.update { it.copy(input = page.text.ifBlank { url }, sourceTitle = page.title.ifBlank { url }, busy = null) }
                translate()
            } catch (e: Exception) {
                _state.update { it.copy(busy = null, input = text, message = str(R.string.translate_page_failed, e.message ?: "")) }
            }
        }
    }

    /** Offline translation, paragraph by paragraph so the structure is kept. */
    fun translate() {
        val text = _state.value.input.trim()
        if (text.isEmpty()) return
        val target = _state.value.targetLanguage
        viewModelScope.launch {
            _state.update { it.copy(busy = str(R.string.translate_busy), output = "") }
            val paragraphs = text.split(Regex("\n\\s*\n")).filter { it.isNotBlank() }.take(MAX_PARAGRAPHS)
            val out = StringBuilder()
            var translatedAny = false
            for (p in paragraphs) {
                val t = translator.translate(p, target)
                if (t != null) translatedAny = true
                out.append(t ?: p).append("\n\n")
                _state.update { it.copy(output = out.toString().trim()) }
            }
            _state.update {
                it.copy(
                    busy = null,
                    message = if (!translatedAny) str(R.string.translate_nothing) else null
                )
            }
        }
    }

    /** Text recognition on a photo, then translation. */
    fun translateImage(context: Context, uri: Uri? = null, bitmap: Bitmap? = null) {
        viewModelScope.launch {
            _state.update { it.copy(busy = str(R.string.translate_reading_photo), output = "", sourceTitle = "") }
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            try {
                val image = when {
                    bitmap != null -> InputImage.fromBitmap(bitmap, 0)
                    uri != null -> InputImage.fromFilePath(context, uri)
                    else -> return@launch
                }
                val text = recognizer.process(image).awaitResult().text
                if (text.isBlank()) {
                    _state.update { it.copy(busy = null, message = str(R.string.translate_no_text_photo)) }
                } else {
                    _state.update { it.copy(input = text, busy = null) }
                    translate()
                }
            } catch (e: Exception) {
                _state.update { it.copy(busy = null, message = str(R.string.ocr_failed, e.message ?: "")) }
            } finally {
                runCatching { recognizer.close() }
            }
        }
    }

    /** AI actions with the shared model on the translation (or the input when not translated yet). */
    fun runAi(action: AiAction, llm: LlmViewModel) {
        val text = _state.value.output.ifBlank { _state.value.input }.trim()
        if (text.isEmpty()) return
        if (!llm.isModelReady) {
            _state.update { it.copy(message = str(R.string.translate_need_model)) }
            return
        }
        val languageName = AppLocale.languageNameForModel(_state.value.targetLanguage) ?: "Dutch"
        val instruction = when (action) {
            AiAction.SUMMARIZE -> "Summarize this text in $languageName in at most 6 short bullet points."
            AiAction.SIMPLIFY -> "Rewrite this text in $languageName at CEFR level B1: short sentences, everyday words, no jargon."
            AiAction.EMAIL -> "Write a short, polite e-mail in $languageName based on this text."
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = str(R.string.translate_ai_busy)) }
            val answer = llm.runOneShotPrompt(
                "You are a helpful writing assistant. Always answer in $languageName.",
                "$instruction\n\nText:\n${text.take(6_000)}"
            )
            _state.update { it.copy(busy = null, output = answer ?: it.output, message = if (answer == null) str(R.string.translate_ai_failed) else null) }
        }
    }

    override fun onCleared() {
        translator.close()
        super.onCleared()
    }

    companion object {
        const val MAX_PARAGRAPHS = 60
    }
}
