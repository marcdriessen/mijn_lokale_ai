package com.example.auralocalai.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.auralocalai.AppLocale
import com.example.auralocalai.R
import com.example.auralocalai.data.medical.LibraryDocument
import com.example.auralocalai.data.medical.MedicalRepository
import com.example.auralocalai.data.medical.PassageIndex
import com.example.auralocalai.data.monitor.MonitorRepository
import com.example.auralocalai.data.monitor.MonitorWorker
import com.example.auralocalai.data.monitor.WatchedPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class MonitorUiState(
    val pages: List<WatchedPage> = emptyList(),
    val checking: Boolean = false,
    val message: String? = null
)

/** Watched guideline pages: add, check now, see what changed, and copy a page to the library. */
class MonitorViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MonitorRepository(application.filesDir)
    private val _state = MutableStateFlow(MonitorUiState())
    val state: StateFlow<MonitorUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) { _state.update { it.copy(pages = repository.load()) } }
    }

    private fun str(id: Int, vararg args: Any) = AppLocale.localized(getApplication<Application>()).getString(id, *args)

    fun clearMessage() = _state.update { it.copy(message = null) }

    private fun save(pages: List<WatchedPage>) {
        _state.update { it.copy(pages = pages) }
        viewModelScope.launch(Dispatchers.IO) { repository.save(pages) }
        MonitorWorker.schedule(getApplication(), pages.any { it.enabled })
    }

    fun add(name: String, url: String) {
        val clean = url.trim()
        if (!clean.startsWith("http")) {
            _state.update { it.copy(message = str(R.string.briefing_invalid_url)) }
            return
        }
        if (_state.value.pages.any { it.url == clean }) return
        save(_state.value.pages + WatchedPage("page_" + System.currentTimeMillis(), name.trim().ifBlank { android.net.Uri.parse(clean).host ?: clean }, clean))
        checkNow()
    }

    fun delete(id: String) = save(_state.value.pages.filterNot { it.id == id })

    fun toggle(id: String) = save(_state.value.pages.map { if (it.id == id) it.copy(enabled = !it.enabled) else it })

    fun checkNow() {
        if (_state.value.checking) return
        if (com.example.auralocalai.AppSecurity.isPrivacyMode(getApplication())) {
            _state.update { it.copy(message = str(R.string.privacy_blocked)) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(checking = true) }
            val changed = repository.checkAll()
            val pages = withContext(Dispatchers.IO) { repository.load() }
            _state.update {
                it.copy(
                    pages = pages,
                    checking = false,
                    message = if (changed.isEmpty()) str(R.string.monitor_no_changes) else str(R.string.monitor_changes, changed.joinToString(", "))
                )
            }
        }
    }

    /** Puts the current text of a watched page (e.g. a standard) in the offline library for the Q&A. */
    fun addToLibrary(page: WatchedPage) {
        if (page.lastText.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val repo = MedicalRepository(getApplication<Application>().filesDir)
            val passages = PassageIndex.chunk(page.lastText)
            val doc = LibraryDocument("doc_" + System.currentTimeMillis(), page.name, System.currentTimeMillis(), passages)
            repo.saveLibrary(repo.loadLibrary().filterNot { it.name == page.name } + doc)
            _state.update { it.copy(message = str(R.string.monitor_added_library, page.name)) }
        }
    }
}
