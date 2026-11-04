package com.example.auralocalai.ui

import android.app.Application
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.auralocalai.AppLocale
import com.example.auralocalai.R
import com.example.auralocalai.data.briefing.Briefing
import com.example.auralocalai.data.briefing.BriefingConfig
import com.example.auralocalai.data.briefing.BriefingPrompts
import com.example.auralocalai.data.briefing.BriefingRepository
import com.example.auralocalai.data.briefing.BriefingScheduler
import com.example.auralocalai.data.briefing.BriefingSection
import com.example.auralocalai.data.briefing.BriefingTopic
import com.example.auralocalai.data.briefing.DefaultBriefing
import com.example.auralocalai.data.briefing.FetchCache
import com.example.auralocalai.data.briefing.NewsFetcher
import com.example.auralocalai.data.briefing.NewsItem
import com.example.auralocalai.data.briefing.NewsSource
import com.example.auralocalai.data.briefing.TopicMatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class BriefingUiState(
    val config: BriefingConfig = BriefingConfig(),
    val current: Briefing? = null,
    val history: List<Briefing> = emptyList(),
    val isRunning: Boolean = false,
    val progress: String? = null,
    val streamingTopicId: String? = null,
    val message: String? = null,
    val sourceErrors: Map<String, String> = emptyMap(),
    val sourceTestResults: Map<String, String> = emptyMap(),
    val translatingTopicId: String? = null
)

/**
 * Daily briefing: fetches news from the user's sources, keeps the items that match the
 * selected topics and lets the loaded local model summarize them per topic.
 */
class BriefingViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = BriefingRepository(application.filesDir)
    private val fetcher = NewsFetcher()
    private val _state = MutableStateFlow(BriefingUiState())
    val state: StateFlow<BriefingUiState> = _state.asStateFlow()
    private var runJob: Job? = null

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val config = repository.loadConfig()
            val history = repository.loadHistory()
            _state.update { it.copy(config = config, history = history, current = history.firstOrNull()) }
        }
    }

    private fun str(@StringRes id: Int, vararg args: Any): String =
        AppLocale.localized(getApplication<Application>()).getString(id, *args)

    private fun updateConfig(transform: (BriefingConfig) -> BriefingConfig) {
        val updated = transform(_state.value.config)
        _state.update { it.copy(config = updated) }
        viewModelScope.launch(Dispatchers.IO) { repository.saveConfig(updated) }
    }

    // ---------------- Topics ----------------

    fun toggleTopic(id: String) = updateConfig { config ->
        config.copy(topics = config.topics.map { if (it.id == id) it.copy(enabled = !it.enabled) else it })
    }

    fun deleteTopic(id: String) = updateConfig { config ->
        config.copy(topics = config.topics.filterNot { it.id == id })
    }

    /** Adds a topic; when a model is loaded, its keywords are translated right away. */
    fun addTopic(name: String, llm: LlmViewModel) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        val id = "topic_" + System.currentTimeMillis()
        updateConfig { it.copy(topics = it.topics + BriefingTopic(id, clean)) }
        if (llm.isModelReady) translateKeywords(id, llm)
    }

    fun updateTopic(id: String, name: String, keywordsText: String) = updateConfig { config ->
        config.copy(topics = config.topics.map {
            if (it.id == id) it.copy(
                name = name.trim().ifEmpty { it.name },
                keywords = TopicMatcher.parseKeywordList(keywordsText, max = 25)
            ) else it
        })
    }

    /** Lets the local model write keywords for the topic in Dutch, English and Bulgarian. */
    fun translateKeywords(id: String, llm: LlmViewModel) {
        val topic = _state.value.config.topics.firstOrNull { it.id == id } ?: return
        if (!llm.isModelReady) {
            _state.update { it.copy(message = str(R.string.briefing_need_model_keywords)) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(translatingTopicId = id) }
            val answer = llm.runOneShotPrompt(
                systemPrompt = BriefingPrompts.KEYWORD_SYSTEM_PROMPT,
                prompt = BriefingPrompts.keywordPrompt(topic.name)
            )
            val newKeywords = answer?.let { TopicMatcher.parseKeywordList(it.lines().firstOrNull { line -> line.isNotBlank() } ?: it) }
            if (newKeywords.isNullOrEmpty()) {
                _state.update { it.copy(translatingTopicId = null, message = str(R.string.briefing_keywords_failed)) }
            } else {
                updateConfig { config ->
                    config.copy(topics = config.topics.map {
                        if (it.id == id) it.copy(keywords = (it.keywords + newKeywords).distinctBy { k -> k.lowercase() }.take(25)) else it
                    })
                }
                _state.update { it.copy(translatingTopicId = null) }
            }
        }
    }

    // ---------------- Sources ----------------

    fun toggleSource(id: String) = updateConfig { config ->
        config.copy(sources = config.sources.map { if (it.id == id) it.copy(enabled = !it.enabled) else it })
    }

    fun deleteSource(id: String) = updateConfig { config ->
        config.copy(sources = config.sources.filterNot { it.id == id })
    }

    fun addSource(name: String, url: String) {
        val cleanUrl = url.trim()
        if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
            _state.update { it.copy(message = str(R.string.briefing_invalid_url)) }
            return
        }
        val cleanName = name.trim().ifEmpty { android.net.Uri.parse(cleanUrl).host ?: cleanUrl }
        updateConfig { it.copy(sources = it.sources + NewsSource("src_" + System.currentTimeMillis(), cleanName, cleanUrl)) }
    }

    fun restoreDefaultSources() = updateConfig { config ->
        val existing = config.sources.map { it.id }.toSet()
        config.copy(sources = config.sources + DefaultBriefing.sources.filterNot { it.id in existing })
    }

    fun testSource(id: String) {
        val source = _state.value.config.sources.firstOrNull { it.id == id } ?: return
        _state.update { it.copy(sourceTestResults = it.sourceTestResults + (id to str(R.string.briefing_testing))) }
        viewModelScope.launch {
            val keywords = _state.value.config.topics.filter { it.enabled }.flatMap { it.allKeywords }.take(2)
            val result = fetcher.fetchSource(source, keywords.ifEmpty { listOf("news") })
            val text = if (result.error != null) {
                str(R.string.briefing_test_failed, result.error)
            } else {
                str(R.string.briefing_test_ok, result.items.size)
            }
            _state.update { it.copy(sourceTestResults = it.sourceTestResults + (id to text)) }
        }
    }

    // ---------------- Schedule ----------------

    fun setAutoUpdate(enabled: Boolean, hour: Int) {
        updateConfig { it.copy(autoUpdateEnabled = enabled, autoUpdateHour = hour.coerceIn(0, 23)) }
        val config = _state.value.config
        try {
            BriefingScheduler.apply(getApplication(), config)
        } catch (e: Exception) {
            _state.update { it.copy(message = e.message) }
        }
    }

    // ---------------- Briefing ----------------

    fun clearMessage() {
        _state.update { it.copy(message = null) }
    }

    fun showBriefing(id: String) {
        _state.update { s -> s.copy(current = s.history.firstOrNull { it.id == id } ?: s.current) }
    }

    fun deleteBriefing(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val history = repository.deleteBriefing(id)
            _state.update { s -> s.copy(history = history, current = if (s.current?.id == id) history.firstOrNull() else s.current) }
        }
    }

    fun stop() {
        runJob?.cancel()
        runJob = null
        _state.update { it.copy(isRunning = false, progress = null, streamingTopicId = null) }
    }

    /**
     * Builds a new briefing. News fetched less than [CACHE_MAX_AGE_MS] ago (for example by the
     * morning background job) is reused unless [forceRefresh] is true.
     */
    fun generate(llm: LlmViewModel, forceRefresh: Boolean = false) {
        if (_state.value.isRunning) return
        runJob = viewModelScope.launch {
            val config = _state.value.config
            val topics = config.topics.filter { it.enabled }
            if (topics.isEmpty()) {
                _state.update { it.copy(message = str(R.string.briefing_no_topics)) }
                return@launch
            }
            _state.update { it.copy(isRunning = true, progress = str(R.string.briefing_fetching), message = null) }
            try {
                val items = loadItems(config, topics, forceRefresh)
                val now = System.currentTimeMillis()
                val sections = topics.map { topic ->
                    BriefingSection(topic.id, topic.name, "", TopicMatcher.selectForTopic(items, topic, config.maxItemsPerTopic))
                }
                val languageCode = AppLocale.effectiveLanguage(getApplication())
                var briefing = Briefing(id = now.toString(), createdAt = now, sections = sections, language = languageCode)
                _state.update { it.copy(current = briefing) }

                if (items.isEmpty()) {
                    _state.update { it.copy(message = str(R.string.briefing_no_news)) }
                } else if (llm.isModelReady) {
                    val languageName = AppLocale.languageNameForModel(languageCode) ?: "English"
                    val systemPrompt = BriefingPrompts.systemPrompt(languageName)
                    val charBudget = BriefingPrompts.charBudgetForContext(llm.activeContextTokens())
                    val withNews = sections.filter { it.items.isNotEmpty() }
                    withNews.forEachIndexed { index, section ->
                        _state.update {
                            it.copy(
                                progress = str(R.string.briefing_summarizing, section.topicName, index + 1, withNews.size),
                                streamingTopicId = section.topicId
                            )
                        }
                        val prompt = BriefingPrompts.topicPrompt(section.topicName, section.items, languageName, charBudget)
                        val summary = llm.runOneShotPrompt(systemPrompt, prompt) { partial ->
                            briefing = briefing.withSummary(section.topicId, partial)
                            _state.update { it.copy(current = briefing) }
                        } ?: ""
                        briefing = briefing.withSummary(section.topicId, summary)
                        _state.update { it.copy(current = briefing) }
                    }
                }

                val history = withContext(Dispatchers.IO) { repository.saveBriefing(briefing) }
                _state.update { it.copy(history = history, current = briefing) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(message = str(R.string.briefing_failed, e.message ?: "")) }
            } finally {
                _state.update { it.copy(isRunning = false, progress = null, streamingTopicId = null) }
            }
        }
    }

    private suspend fun loadItems(config: BriefingConfig, topics: List<BriefingTopic>, forceRefresh: Boolean): List<NewsItem> {
        val cache = withContext(Dispatchers.IO) { repository.loadCache() }
        val cacheIsFresh = System.currentTimeMillis() - cache.fetchedAt < CACHE_MAX_AGE_MS && cache.items.isNotEmpty()
        if (!forceRefresh && cacheIsFresh) return cache.items

        val keywords = topics.flatMap { it.allKeywords }.distinctBy { it.lowercase() }
        val results = fetcher.fetchAll(config.sources, keywords)
        val errors = results.filter { it.error != null }.associate { it.source.id to (it.error ?: "") }
        _state.update { it.copy(sourceErrors = errors) }
        val items = results.flatMap { it.items }
        if (items.isNotEmpty()) {
            withContext(Dispatchers.IO) { repository.saveCache(FetchCache(System.currentTimeMillis(), items)) }
        }
        return items
    }

    private fun Briefing.withSummary(topicId: String, summary: String): Briefing =
        copy(sections = sections.map { if (it.topicId == topicId) it.copy(summary = summary) else it })

    /** Plain-text version for sharing and reading aloud. */
    fun briefingAsText(briefing: Briefing, header: String): String = buildString {
        append(header).append("\n\n")
        briefing.sections.filter { it.items.isNotEmpty() || it.summary.isNotBlank() }.forEach { section ->
            append("■ ").append(section.topicName).append("\n")
            if (section.summary.isNotBlank()) append(section.summary.trim()).append("\n")
            section.items.forEach { item -> append("- ").append(item.title).append(" (").append(item.sourceName).append(") ").append(item.link).append("\n") }
            append("\n")
        }
    }

    /** Spoken version: summaries (or headlines when there is no summary), without links. */
    fun briefingAsSpeech(briefing: Briefing): String = buildString {
        briefing.sections.filter { it.items.isNotEmpty() }.forEach { section ->
            append(section.topicName).append(". ")
            if (section.summary.isNotBlank()) {
                append(section.summary.replace("•", "").replace(Regex("\\([^)]*\\)"), "")).append(" ")
            } else {
                section.items.take(3).forEach { append(it.title).append(". ") }
            }
        }
    }

    companion object {
        const val CACHE_MAX_AGE_MS = 3L * 60L * 60L * 1000L
    }
}
