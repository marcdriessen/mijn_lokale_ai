package com.example.auralocalai.ui

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.provider.OpenableColumns
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.auralocalai.AppLocale
import com.example.auralocalai.R
import com.example.auralocalai.data.medical.LibraryDocument
import com.example.auralocalai.data.medical.MedicalAnswer
import com.example.auralocalai.data.medical.MedicalConfig
import com.example.auralocalai.data.medical.MedicalPrompts
import com.example.auralocalai.data.medical.MedicalRepository
import com.example.auralocalai.data.medical.MedicalSource
import com.example.auralocalai.data.medical.PassageIndex
import com.example.auralocalai.data.medical.PubMedClient
import com.example.auralocalai.data.medical.SourceKind
import com.example.auralocalai.data.medical.TrustedSite
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class MedicalUiState(
    val config: MedicalConfig = MedicalConfig(),
    val library: List<LibraryDocument> = emptyList(),
    val history: List<MedicalAnswer> = emptyList(),
    val current: MedicalAnswer? = null,
    val isRunning: Boolean = false,
    val progress: String? = null,
    val notes: List<String> = emptyList(),
    val braveUsed: Int = 0,
    val importing: String? = null,
    val message: String? = null
)

/**
 * Clinical Q&A for mental health and addiction care: searches the user's offline library,
 * trusted websites (via Brave, site-restricted) and PubMed, then lets the local model answer
 * only from those sources, with numbered citations and without doses.
 */
class MedicalViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MedicalRepository(application.filesDir)
    private val pubMed = PubMedClient()
    private val europePmc = com.example.auralocalai.data.medical.EuropePmcClient()
    private val _state = MutableStateFlow(MedicalUiState())
    val state: StateFlow<MedicalUiState> = _state.asStateFlow()
    private var runJob: Job? = null

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val history = repository.loadHistory()
            _state.update {
                it.copy(
                    config = repository.loadConfig(),
                    library = repository.loadLibrary(),
                    history = history,
                    current = history.firstOrNull(),
                    braveUsed = repository.braveUsageThisMonth()
                )
            }
        }
    }

    private fun str(@StringRes id: Int, vararg args: Any): String =
        AppLocale.localized(getApplication<Application>()).getString(id, *args)

    private fun updateConfig(transform: (MedicalConfig) -> MedicalConfig) {
        val updated = transform(_state.value.config)
        _state.update { it.copy(config = updated) }
        viewModelScope.launch(Dispatchers.IO) { repository.saveConfig(updated) }
    }

    fun clearMessage() = _state.update { it.copy(message = null) }

    fun setUsePubMed(value: Boolean) = updateConfig { it.copy(usePubMed = value) }
    fun setUseSites(value: Boolean) = updateConfig { it.copy(useSites = value) }
    fun setUseLibrary(value: Boolean) = updateConfig { it.copy(useLibrary = value) }
    fun setMonthlyLimit(value: Int) = updateConfig { it.copy(monthlyBraveLimit = value.coerceIn(0, 100_000)) }

    fun toggleSite(domain: String) = updateConfig { config ->
        config.copy(sites = config.sites.map { if (it.domain == domain) it.copy(enabled = !it.enabled) else it })
    }

    fun deleteSite(domain: String) = updateConfig { config -> config.copy(sites = config.sites.filterNot { it.domain == domain }) }

    fun addSite(input: String) {
        val domain = input.trim().lowercase()
            .removePrefix("https://").removePrefix("http://").removePrefix("www.")
            .substringBefore('/')
        if (domain.isEmpty() || !domain.contains('.')) {
            _state.update { it.copy(message = str(R.string.medical_invalid_domain)) }
            return
        }
        updateConfig { config ->
            if (config.sites.any { it.domain == domain }) config
            else config.copy(sites = config.sites + TrustedSite(domain, domain))
        }
    }

    fun showAnswer(id: String) = _state.update { s -> s.copy(current = s.history.firstOrNull { it.id == id } ?: s.current) }

    fun deleteAnswer(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val history = repository.deleteAnswer(id)
            _state.update { s -> s.copy(history = history, current = if (s.current?.id == id) history.firstOrNull() else s.current) }
        }
    }

    fun stop() {
        runJob?.cancel()
        runJob = null
        _state.update { it.copy(isRunning = false, progress = null) }
    }

    fun ask(question: String, llm: LlmViewModel) {
        val clean = question.trim()
        if (clean.isEmpty() || _state.value.isRunning) return
        runJob = viewModelScope.launch {
            val privacy = com.example.auralocalai.AppSecurity.isPrivacyMode(getApplication())
            val config = _state.value.config.let { if (privacy) it.copy(useSites = false, usePubMed = false) else it }
            val notes = mutableListOf<String>()
            if (privacy) notes += str(R.string.privacy_only_library)
            val sources = mutableListOf<MedicalSource>()
            val languageCode = AppLocale.effectiveLanguage(getApplication())
            val languageName = AppLocale.languageNameForModel(languageCode) ?: "English"
            val now = System.currentTimeMillis()
            var answer = MedicalAnswer(now.toString(), clean, "", emptyList(), now)
            _state.update { it.copy(isRunning = true, progress = str(R.string.medical_searching), notes = emptyList(), current = answer) }

            try {
                // 1. Offline library
                if (config.useLibrary && _state.value.library.isNotEmpty()) {
                    val hits = withContext(Dispatchers.Default) { PassageIndex.search(_state.value.library, clean, limit = 3) }
                    hits.forEach { hit ->
                        sources += MedicalSource(SourceKind.LIBRARY.name, hit.documentName, "", hit.passage, str(R.string.medical_origin_library))
                    }
                }

                // 2. Trusted websites via Brave (site-restricted), within the monthly limit
                if (config.useSites) {
                    val domains = config.sites.filter { it.enabled }.map { it.domain }
                    val used = withContext(Dispatchers.IO) { repository.braveUsageThisMonth() }
                    when {
                        domains.isEmpty() -> Unit
                        !llm.isBraveConfigured -> notes += str(R.string.medical_note_no_brave)
                        used >= config.monthlyBraveLimit -> notes += str(R.string.medical_note_limit, config.monthlyBraveLimit)
                        else -> {
                            val count = withContext(Dispatchers.IO) { repository.recordBraveUse() }
                            _state.update { it.copy(braveUsed = count) }
                            val outcome = llm.searchWithBrave(MedicalPrompts.siteQuery(clean, domains), maxResults = 6, language = "nl")
                            if (outcome == null || outcome.isError) {
                                notes += str(R.string.medical_note_brave_error, outcome?.errorMessage ?: "")
                            } else {
                                outcome.sources.forEach { result ->
                                    val site = config.sites.firstOrNull { s -> result.domain.endsWith(s.domain) || result.url.contains(s.domain) }
                                    if (site != null) {
                                        sources += MedicalSource(SourceKind.WEB.name, result.title, result.url, result.snippet, site.name)
                                    }
                                }
                            }
                        }
                    }
                }

                // 3. PubMed (free); the model turns the question into English search terms
                if (config.usePubMed) {
                    _state.update { it.copy(progress = str(R.string.medical_searching_pubmed)) }
                    val pubQuery = if (llm.isModelReady) {
                        llm.runOneShotPrompt(MedicalPrompts.PUBMED_QUERY_SYSTEM_PROMPT, MedicalPrompts.pubMedQueryPrompt(clean))
                            ?.let { MedicalPrompts.cleanQuery(it) }
                            ?.ifBlank { null } ?: clean
                    } else clean
                    answer = answer.copy(searchQuery = pubQuery)
                    val seenPmids = mutableSetOf<String>()
                    val seenTitles = mutableSetOf<String>()
                    try {
                        pubMed.search(pubQuery, maxResults = 4).forEach { article ->
                            val origin = listOf("PubMed", article.journal, article.year).filter { it.isNotBlank() }.joinToString(" · ")
                            sources += MedicalSource(SourceKind.PUBMED.name, article.title, article.url, article.abstract.ifBlank { article.title }, origin)
                            seenPmids += article.pmid
                            seenTitles += com.example.auralocalai.data.medical.EuropePmcParser.titleKey(article.title)
                        }
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        notes += str(R.string.medical_note_pubmed_error, e.message ?: "")
                    }
                    // Europe PMC: European and open-access literature. The same study found in PubMed
                    // (same PMID or title) is not counted twice.
                    _state.update { it.copy(progress = str(R.string.medical_searching_europepmc)) }
                    try {
                        europePmc.search(pubQuery, maxResults = 4)
                            .filter { it.pmid !in seenPmids && com.example.auralocalai.data.medical.EuropePmcParser.titleKey(it.title) !in seenTitles }
                            .take(3)
                            .forEach { article ->
                                val origin = listOfNotNull(
                                    "Europe PMC", article.journal.ifBlank { null }, article.year.ifBlank { null },
                                    if (article.isOpenAccess) "open access" else null
                                ).joinToString(" · ")
                                sources += MedicalSource(SourceKind.PUBMED.name, article.title, article.url, article.abstract.ifBlank { article.title }, origin)
                            }
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        notes += str(R.string.medical_note_pubmed_error, "Europe PMC: " + (e.message ?: ""))
                    }
                }

                answer = answer.copy(sources = sources.toList())
                _state.update { it.copy(current = answer, notes = notes.toList()) }

                val text = when {
                    sources.isEmpty() -> str(R.string.medical_no_sources)
                    !llm.isModelReady -> str(R.string.medical_no_model_sources_only)
                    else -> {
                        _state.update { it.copy(progress = str(R.string.medical_answering)) }
                        val budget = (llm.activeContextTokens() * 5 / 2).coerceIn(2_000, 12_000)
                        llm.runOneShotPrompt(
                            MedicalPrompts.systemPrompt(languageName),
                            MedicalPrompts.answerPrompt(clean, sources, languageName, budget),
                            temperature = 0.3
                        ) { partial ->
                            answer = answer.copy(answer = partial)
                            _state.update { it.copy(current = answer) }
                        } ?: str(R.string.medical_answer_failed)
                    }
                }
                answer = answer.copy(answer = text)
                val history = withContext(Dispatchers.IO) { repository.saveAnswer(answer) }
                _state.update { it.copy(current = answer, history = history) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(message = str(R.string.medical_failed, e.message ?: "")) }
            } finally {
                _state.update { it.copy(isRunning = false, progress = null, notes = notes.toList()) }
            }
        }
    }

    // ---------------- Offline library ----------------

    fun deleteDocument(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = _state.value.library.filterNot { it.id == id }
            repository.saveLibrary(updated)
            _state.update { it.copy(library = updated) }
        }
    }

    /** Imports a PDF (text via on-device OCR) or a text/Markdown file into the offline library. */
    fun importDocument(uri: Uri, context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val name = displayName(context, uri)
            _state.update { it.copy(importing = name) }
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
                val kind = com.example.auralocalai.data.work.DocumentText.detect(bytes, name)
                val text = when (kind) {
                    com.example.auralocalai.data.work.DocumentText.Kind.PDF -> ocrPdf(context, uri)
                    com.example.auralocalai.data.work.DocumentText.Kind.IMAGE -> {
                        val bitmap = com.example.auralocalai.data.ocr.OcrEngine.loadBitmap(context, uri)
                        if (bitmap == null) "" else try {
                            com.example.auralocalai.data.ocr.OcrEngine.recognize(context, bitmap)
                        } finally {
                            bitmap.recycle()
                        }
                    }
                    else -> com.example.auralocalai.data.work.DocumentText.extract(bytes, kind, MAX_TEXT_CHARS).orEmpty()
                }
                // Personal data never enters the library.
                val passages = PassageIndex.chunk(com.example.auralocalai.data.privacy.Anonymizer.anonymize(text).text)
                if (passages.isEmpty()) {
                    _state.update { it.copy(message = str(R.string.medical_import_empty, name)) }
                } else {
                    val doc = LibraryDocument("doc_" + System.currentTimeMillis(), name, System.currentTimeMillis(), passages)
                    val updated = _state.value.library + doc
                    repository.saveLibrary(updated)
                    _state.update { it.copy(library = updated, message = str(R.string.medical_import_done, name, passages.size)) }
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        message = if (e is com.example.auralocalai.data.ocr.OcrEngine.CyrillicDataMissing) str(R.string.ocr_cyrillic_missing)
                        else str(R.string.medical_import_failed, e.message ?: "")
                    )
                }
            } finally {
                _state.update { it.copy(importing = null) }
            }
        }
    }

    private fun displayName(context: Context, uri: Uri): String {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index != -1) cursor.getString(index)?.let { return it }
            }
        }
        return uri.lastPathSegment ?: "document"
    }

    private suspend fun ocrPdf(context: Context, uri: Uri): String {
        val descriptor = context.contentResolver.openFileDescriptor(uri, "r") ?: return ""
        val renderer = PdfRenderer(descriptor)
        val builder = StringBuilder()
        try {
            val pages = minOf(renderer.pageCount, MAX_PDF_PAGES)
            for (i in 0 until pages) {
                _state.update { it.copy(importing = str(R.string.medical_import_page, i + 1, pages)) }
                val page = renderer.openPage(i)
                try {
                    val bitmap = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                    try {
                        Canvas(bitmap).drawColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        val result = com.example.auralocalai.data.ocr.OcrEngine.recognize(context, bitmap)
                        builder.append(result).append("\n\n")
                    } finally {
                        bitmap.recycle()
                    }
                } finally {
                    page.close()
                }
            }
        } finally {
            try { renderer.close() } catch (_: Exception) {}
            try { descriptor.close() } catch (_: Exception) {}
        }
        return builder.toString()
    }

    companion object {
        const val MAX_PDF_PAGES = 40
        const val MAX_TEXT_CHARS = 600_000
    }
}
