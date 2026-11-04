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
import com.example.auralocalai.data.translate.TranslateLanguages
import com.example.auralocalai.data.work.DocumentReader
import com.example.auralocalai.data.work.DocxIO
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
    /** File name of an imported document (used for the Word file name). */
    val documentName: String? = null,
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
        AppLocale.effectiveLanguage(app).takeIf { code -> TranslateLanguages.ALL.any { it.code == code } } ?: "nl"

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

    /**
     * Offline translation (ML Kit). The language is detected once for the whole text, then it is
     * translated line by line so headings, lists and empty lines of documents stay in place.
     */
    fun translate() {
        val text = _state.value.input.trim()
        if (text.isEmpty()) return
        val target = _state.value.targetLanguage
        viewModelScope.launch {
            _state.update { it.copy(busy = str(R.string.translate_busy), output = "") }
            val source = translator.identify(text.take(2_000))
            val lines = text.lines().take(MAX_LINES)
            val total = lines.count { it.isNotBlank() }
            val out = StringBuilder()
            var translatedAny = false
            var done = 0
            for (line in lines) {
                if (line.isBlank()) {
                    out.append('\n')
                    continue
                }
                val t = if (source != null) translator.translateFrom(line, source, target) else translator.translate(line, target)
                if (t != null) translatedAny = true
                val indent = line.takeWhile { it == ' ' || it == '\t' }
                out.append(indent).append(t ?: line.trim()).append('\n')
                done++
                if (done % 5 == 0 || done == total) {
                    _state.update { it.copy(output = out.toString().trim(), busy = if (total > 10) str(R.string.translate_progress, done, total) else it.busy) }
                }
            }
            _state.update {
                it.copy(
                    busy = null,
                    output = out.toString().trim(),
                    message = when {
                        source != null && source == target -> str(R.string.translate_same_language)
                        !translatedAny -> str(R.string.translate_nothing)
                        lines.size < text.lines().size -> str(R.string.translate_truncated, MAX_LINES)
                        else -> null
                    }
                )
            }
        }
    }

    /**
     * Translation with the loaded AI model: slower, but often more natural than offline translation
     * (for example Dutch → Bulgarian). The text is sent in parts so long documents fit the model.
     */
    fun translateWithAi(llm: LlmViewModel) {
        val text = _state.value.input.trim()
        if (text.isEmpty()) return
        if (!llm.isModelReady) {
            _state.update { it.copy(message = str(R.string.translate_need_model)) }
            return
        }
        val languageName = AppLocale.languageNameForModel(_state.value.targetLanguage) ?: "English"
        val parts = TranslateLanguages.chunks(text.take(MAX_AI_CHARS))
        viewModelScope.launch {
            val out = StringBuilder()
            for ((index, part) in parts.withIndex()) {
                _state.update { it.copy(busy = str(R.string.translate_ai_progress, index + 1, parts.size), output = out.toString().trim()) }
                val answer = llm.runOneShotPrompt(
                    "You are a professional translator. Translate the user's text into $languageName. " +
                        "Keep the meaning, tone, line breaks, headings and lists exactly. Keep labels such as [NAAM] unchanged. " +
                        "Reply with the translation only, without notes or explanations.",
                    part
                )
                if (answer == null) {
                    _state.update { it.copy(busy = null, output = out.toString().trim(), message = str(R.string.translate_ai_failed)) }
                    return@launch
                }
                out.append(answer.trim()).append("\n\n")
            }
            _state.update {
                it.copy(
                    busy = null,
                    output = out.toString().trim(),
                    message = if (text.length > MAX_AI_CHARS) str(R.string.translate_ai_truncated) else null
                )
            }
        }
    }

    /** Reads a document (PDF, Word, ODT, RTF, HTML, text) and translates it. */
    fun importDocument(context: Context, uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(busy = str(R.string.report_reading_file), output = "") }
            val result = runCatching {
                DocumentReader.read(context, uri) { page, pages ->
                    _state.update { it.copy(busy = str(R.string.medical_import_page, page, pages)) }
                }
            }.getOrElse { e ->
                _state.update { it.copy(busy = null, message = str(R.string.ocr_failed, e.message ?: "")) }
                return@launch
            }
            when (result) {
                DocumentReader.Result.OldWordFormat -> _state.update { it.copy(busy = null, message = str(R.string.report_doc_unsupported)) }
                DocumentReader.Result.Empty -> _state.update { it.copy(busy = null, message = str(R.string.translate_nothing)) }
                is DocumentReader.Result.Text -> {
                    // Same privacy rule as for photos: anonymize when the setting is on.
                    val text = com.example.auralocalai.AppSecurity.maybeAnonymize(getApplication(), result.text) ?: result.text
                    val name = result.name?.substringBeforeLast('.')
                    _state.update { it.copy(busy = null, input = text, sourceTitle = name.orEmpty(), documentName = name) }
                    translate()
                }
            }
        }
    }

    /** Suggested name for the Word file, for example "Brief_bg.docx". */
    fun suggestedFileName(): String {
        val base = (_state.value.documentName ?: _state.value.sourceTitle.ifBlank { null } ?: str(R.string.translate_file_base))
            .let { if (com.example.auralocalai.AppSecurity.isAnonymizeEnabled(getApplication())) com.example.auralocalai.data.privacy.Anonymizer.anonymize(it).text else it }
            .replace(Regex("[\\[\\]\\\\/:*?\"<>|]"), "")
            .trim()
            .take(60)
            .ifBlank { "Vertaling" }
        return "${base}_${_state.value.targetLanguage}.docx".replace(' ', '_')
    }

    /** Saves the translation as a Word document at the place the user chose. */
    fun saveAsDocx(context: Context, uri: Uri) {
        val output = _state.value.output
        if (output.isBlank()) return
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val language = TranslateLanguages.nativeName(_state.value.targetLanguage)
                    val title = _state.value.sourceTitle.ifBlank { str(R.string.translate_file_base) }
                    val bytes = DocxIO.write(title, str(R.string.translate_doc_subtitle, language), output, detectHeadings = false)
                    context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) } != null
                }.getOrDefault(false)
            }
            _state.update { it.copy(message = str(if (ok) R.string.report_saved_docx else R.string.report_save_failed)) }
        }
    }

    /** Text recognition on a photo, then translation. */
    fun translateImage(context: Context, uri: Uri? = null, bitmap: Bitmap? = null) {
        viewModelScope.launch {
            _state.update { it.copy(busy = str(R.string.translate_reading_photo), output = "", sourceTitle = "", documentName = null) }
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
                    _state.update { it.copy(input = com.example.auralocalai.AppSecurity.maybeAnonymize(getApplication(), text) ?: text, busy = null) }
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
        const val MAX_LINES = 1_500
        const val MAX_AI_CHARS = 24_000
    }
}
