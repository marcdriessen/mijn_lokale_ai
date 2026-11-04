package com.example.auralocalai.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.auralocalai.AppLocale
import com.example.auralocalai.R
import com.example.auralocalai.data.medical.LibraryDocument
import com.example.auralocalai.data.medical.MedicalRepository
import com.example.auralocalai.data.work.Flashcard
import com.example.auralocalai.data.work.FlashcardDeck
import com.example.auralocalai.data.work.Flashcards
import com.example.auralocalai.data.work.WorkPrompts
import com.example.auralocalai.data.translate.awaitResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

data class WorkUiState(
    val psychoTopic: String = "",
    val psychoLanguage: String = "nl",
    val psychoLevel: String = "B1",
    val psychoOutput: String = "",
    val reportNotes: String = "",
    val reportFormat: String = "SBAR",
    val reportOutput: String = "",
    val decks: List<FlashcardDeck> = emptyList(),
    val library: List<LibraryDocument> = emptyList(),
    val busy: String? = null,
    val message: String? = null
)

/** Work tools: psycho-education hand-outs, SBAR/SOAP structuring and flashcards (all with the shared model). */
class WorkViewModel(application: Application) : AndroidViewModel(application) {

    private val decksFile = File(File(application.filesDir, "work").apply { mkdirs() }, "flashcards.json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val _state = MutableStateFlow(WorkUiState())
    val state: StateFlow<WorkUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val decks = runCatching { json.decodeFromString(ListSerializer(FlashcardDeck.serializer()), decksFile.readText()) }.getOrDefault(emptyList())
            val library = MedicalRepository(application.filesDir).loadLibrary()
            _state.update { it.copy(decks = decks, library = library) }
        }
    }

    private fun str(id: Int, vararg args: Any): String = AppLocale.localized(getApplication<Application>()).getString(id, *args)

    fun clearMessage() = _state.update { it.copy(message = null) }
    fun setPsychoTopic(v: String) = _state.update { it.copy(psychoTopic = v) }
    fun setPsychoLanguage(v: String) = _state.update { it.copy(psychoLanguage = v) }
    fun setPsychoLevel(v: String) = _state.update { it.copy(psychoLevel = v) }
    fun setReportNotes(v: String) = _state.update { it.copy(reportNotes = v) }
    fun setReportFormat(v: String) = _state.update { it.copy(reportFormat = v) }

    private fun requireModel(llm: LlmViewModel): Boolean {
        if (!llm.isModelReady) {
            _state.update { it.copy(message = str(R.string.translate_need_model)) }
            return false
        }
        return true
    }

    fun generatePsychoEducation(llm: LlmViewModel) {
        val topic = _state.value.psychoTopic.trim()
        if (topic.isEmpty() || !requireModel(llm)) return
        val language = AppLocale.languageNameForModel(_state.value.psychoLanguage) ?: "Dutch"
        viewModelScope.launch {
            _state.update { it.copy(busy = str(R.string.translate_ai_busy), psychoOutput = "") }
            val text = llm.runOneShotPrompt(
                WorkPrompts.psychoEducationSystem(language, _state.value.psychoLevel),
                WorkPrompts.psychoEducationPrompt(topic)
            )
            _state.update { it.copy(busy = null, psychoOutput = text ?: "", message = if (text == null) str(R.string.translate_ai_failed) else null) }
        }
    }

    /**
     * Adds dictated, scanned or imported text to the notes, after removing personal data
     * (names, BSN, dates of birth, addresses, …).
     */
    fun addReportText(raw: String, llm: LlmViewModel) {
        if (raw.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(busy = str(R.string.anon_busy)) }
            val result = llm.anonymize(raw.trim())
            _state.update {
                it.copy(
                    busy = null,
                    reportNotes = listOf(it.reportNotes.trim(), result.text).filter { t -> t.isNotBlank() }.joinToString("\n\n"),
                    message = str(R.string.anon_done, result.replacements)
                )
            }
        }
    }

    /** Text recognition on a photo of (handwritten or printed) notes. */
    fun scanReport(context: android.content.Context, bitmap: android.graphics.Bitmap, llm: LlmViewModel) {
        viewModelScope.launch {
            _state.update { it.copy(busy = str(R.string.translate_reading_photo)) }
            val recognizer = com.google.mlkit.vision.text.TextRecognition.getClient(com.google.mlkit.vision.text.latin.TextRecognizerOptions.DEFAULT_OPTIONS)
            try {
                val text = recognizer.process(com.google.mlkit.vision.common.InputImage.fromBitmap(bitmap, 0))
                    .awaitResult().text
                _state.update { it.copy(busy = null) }
                if (text.isBlank()) _state.update { it.copy(message = str(R.string.translate_no_text_photo)) } else addReportText(text, llm)
            } catch (e: Exception) {
                _state.update { it.copy(busy = null, message = str(R.string.ocr_failed, e.message ?: "")) }
            } finally {
                runCatching { recognizer.close() }
            }
        }
    }

    /** Imports a text file into the notes (anonymized). */
    fun importReportFile(context: android.content.Context, uri: android.net.Uri, llm: LlmViewModel) {
        viewModelScope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText().take(30_000) } }.getOrNull()
            }
            if (text.isNullOrBlank()) _state.update { it.copy(message = str(R.string.translate_nothing)) } else addReportText(text, llm)
        }
    }

    fun generateReport(llm: LlmViewModel) {
        val typed = _state.value.reportNotes.trim()
        if (typed.isEmpty() || !requireModel(llm)) return
        val language = AppLocale.languageNameForModel(AppLocale.effectiveLanguage(getApplication())) ?: "Dutch"
        viewModelScope.launch {
            _state.update { it.copy(busy = str(R.string.anon_busy), reportOutput = "") }
            // Typed notes are checked too, so no personal data reaches the model output.
            val anon = llm.anonymize(typed)
            val notes = anon.text
            _state.update { it.copy(reportNotes = notes, busy = str(R.string.translate_ai_busy)) }
            val text = llm.runOneShotPrompt(WorkPrompts.reportSystem(language), WorkPrompts.reportPrompt(notes, _state.value.reportFormat))
            _state.update { it.copy(busy = null, reportOutput = text ?: "", message = if (text == null) str(R.string.translate_ai_failed) else null) }
        }
    }

    /** Makes a flashcard deck from a library document or pasted text. */
    fun createDeck(name: String, text: String, llm: LlmViewModel, count: Int = 12) {
        if (text.isBlank() || !requireModel(llm)) return
        val language = AppLocale.languageNameForModel(AppLocale.effectiveLanguage(getApplication())) ?: "Dutch"
        viewModelScope.launch {
            _state.update { it.copy(busy = str(R.string.work_cards_making)) }
            val raw = llm.runOneShotPrompt(WorkPrompts.flashcardSystem(language), WorkPrompts.flashcardPrompt(text, count))
            val cards = raw?.let { Flashcards.parse(it) }.orEmpty()
            if (cards.isEmpty()) {
                _state.update { it.copy(busy = null, message = str(R.string.work_cards_failed)) }
            } else {
                val deck = FlashcardDeck("deck_" + System.currentTimeMillis(), name.ifBlank { str(R.string.work_cards_default_name) }, System.currentTimeMillis(), cards)
                saveDecks(listOf(deck) + _state.value.decks)
                _state.update { it.copy(busy = null, message = str(R.string.work_cards_done, cards.size)) }
            }
        }
    }

    fun deleteDeck(id: String) = saveDecks(_state.value.decks.filterNot { it.id == id })

    fun review(deckId: String, cardIndex: Int, known: Boolean) {
        val now = System.currentTimeMillis()
        saveDecks(_state.value.decks.map { deck ->
            if (deck.id != deckId) deck
            else deck.copy(cards = deck.cards.mapIndexed { i, c: Flashcard -> if (i == cardIndex) Flashcards.review(c, known, now) else c })
        })
    }

    private fun saveDecks(decks: List<FlashcardDeck>) {
        _state.update { it.copy(decks = decks) }
        viewModelScope.launch(Dispatchers.IO) {
            val tmp = File(decksFile.parentFile, decksFile.name + ".tmp")
            tmp.writeText(json.encodeToString(ListSerializer(FlashcardDeck.serializer()), decks))
            if (!tmp.renameTo(decksFile)) {
                decksFile.writeText(tmp.readText())
                tmp.delete()
            }
        }
    }

    fun reloadLibrary() {
        viewModelScope.launch(Dispatchers.IO) {
            val library = MedicalRepository(getApplication<Application>().filesDir).loadLibrary()
            withContext(Dispatchers.Main) { _state.update { it.copy(library = library) } }
        }
    }
}
