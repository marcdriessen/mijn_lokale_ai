package com.example.auralocalai.ui

import android.app.Application
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.auralocalai.AppLocale
import com.example.auralocalai.R
import com.example.auralocalai.data.briefing.Briefing
import com.example.auralocalai.data.briefing.BriefingArticle
import com.example.auralocalai.data.briefing.BriefingConfig
import com.example.auralocalai.data.briefing.BriefingProfile
import com.example.auralocalai.data.briefing.BriefingPrompts
import com.example.auralocalai.data.briefing.BriefingRepository
import com.example.auralocalai.data.briefing.BriefingScheduler
import com.example.auralocalai.data.briefing.BriefingTopic
import com.example.auralocalai.data.briefing.DefaultBriefing
import com.example.auralocalai.data.briefing.FetchCache
import com.example.auralocalai.data.briefing.NewsFetcher
import com.example.auralocalai.data.briefing.NewsItem
import com.example.auralocalai.data.briefing.NewsSource
import com.example.auralocalai.data.briefing.SourceMode
import com.example.auralocalai.data.briefing.TopicMatcher
import com.example.auralocalai.data.medical.MedicalPrompts
import com.example.auralocalai.data.medical.MedicalRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class BriefingUiState(
    val config: BriefingConfig = BriefingConfig(),
    val selectedProfileId: String = "",
    val current: Briefing? = null,
    val history: List<Briefing> = emptyList(),
    val isRunning: Boolean = false,
    val progress: String? = null,
    val streamingArticleIndex: Int = -1,
    val message: String? = null,
    val sourceErrors: Map<String, String> = emptyMap(),
    val sourceTestResults: Map<String, String> = emptyMap(),
    val translatingTopicId: String? = null
) {
    val selectedProfile: BriefingProfile?
        get() = config.profiles.firstOrNull { it.id == selectedProfileId } ?: config.profiles.firstOrNull()
}

/**
 * Editorial news briefings. Each profile has its own instructions, topics, sources and schedule.
 * News comes from RSS feeds and/or a site-restricted web search; the loaded local model rewrites
 * every selected article into a title, a short summary and "what this means for your work".
 */
class BriefingViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = BriefingRepository(application.filesDir)
    private val braveUsage = MedicalRepository(application.filesDir)
    private val fetcher = NewsFetcher()
    private val _state = MutableStateFlow(BriefingUiState())
    val state: StateFlow<BriefingUiState> = _state.asStateFlow()
    private var runJob: Job? = null

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val config = repository.loadConfig()
            repository.saveConfig(config) // persists the migration to profiles
            val history = repository.loadHistory()
            val selected = config.profiles.firstOrNull()?.id ?: ""
            _state.update {
                it.copy(
                    config = config,
                    history = history,
                    selectedProfileId = selected,
                    current = latestFor(history, selected)
                )
            }
        }
    }

    private fun str(@StringRes id: Int, vararg args: Any): String =
        AppLocale.localized(getApplication<Application>()).getString(id, *args)

    private fun latestFor(history: List<Briefing>, profileId: String): Briefing? =
        history.firstOrNull { it.profileId == profileId }
            ?: history.firstOrNull { it.profileId.isEmpty() && profileId == DefaultBriefing.worldProfile.id }

    private fun updateConfig(reschedule: Boolean = false, transform: (BriefingConfig) -> BriefingConfig) {
        val updated = transform(_state.value.config)
        _state.update { it.copy(config = updated) }
        viewModelScope.launch(Dispatchers.IO) {
            repository.saveConfig(updated)
            if (reschedule) {
                try {
                    BriefingScheduler.apply(getApplication(), updated)
                } catch (e: Exception) {
                    _state.update { it.copy(message = e.message) }
                }
            }
        }
    }

    private fun updateProfileInternal(profileId: String, reschedule: Boolean = false, transform: (BriefingProfile) -> BriefingProfile) =
        updateConfig(reschedule) { config ->
            config.copy(profiles = config.profiles.map { if (it.id == profileId) transform(it) else it })
        }

    fun clearMessage() = _state.update { it.copy(message = null) }

    // ---------------- Profiles ----------------

    fun selectProfile(id: String) {
        _state.update { it.copy(selectedProfileId = id, current = latestFor(it.history, id)) }
    }

    /** Creates a new profile and returns its id. */
    fun addProfile(): String {
        val id = "profile_" + System.currentTimeMillis()
        val profile = BriefingProfile(id = id, name = str(R.string.briefing_new_profile_name))
        updateConfig { it.copy(profiles = it.profiles + profile) }
        _state.update { it.copy(selectedProfileId = id, current = null) }
        return id
    }

    /** Saves the edited profile (name, instructions, schedule, sources) and reschedules. */
    fun saveProfile(profile: BriefingProfile) {
        updateConfig(reschedule = true) { config ->
            config.copy(profiles = config.profiles.map { if (it.id == profile.id) profile else it })
        }
    }

    fun deleteProfile(id: String) {
        updateConfig(reschedule = true) { config -> config.copy(profiles = config.profiles.filterNot { it.id == id }) }
        _state.update { s ->
            val next = s.config.profiles.firstOrNull { it.id != id }?.id ?: ""
            s.copy(selectedProfileId = next, current = latestFor(s.history, next))
        }
    }

    // ---------------- Topics (per profile) ----------------

    fun toggleTopic(profileId: String, topicId: String) = updateProfileInternal(profileId) { p ->
        p.copy(topics = p.topics.map { if (it.id == topicId) it.copy(enabled = !it.enabled) else it })
    }

    fun deleteTopic(profileId: String, topicId: String) = updateProfileInternal(profileId) { p ->
        p.copy(topics = p.topics.filterNot { it.id == topicId })
    }

    fun addTopic(profileId: String, name: String, llm: LlmViewModel) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        val topicId = "topic_" + System.currentTimeMillis()
        updateProfileInternal(profileId) { p -> p.copy(topics = p.topics + BriefingTopic(topicId, clean)) }
        if (llm.isModelReady) translateKeywords(profileId, topicId, llm)
    }

    fun updateTopic(profileId: String, topicId: String, name: String, keywordsText: String) = updateProfileInternal(profileId) { p ->
        p.copy(topics = p.topics.map {
            if (it.id == topicId) it.copy(
                name = name.trim().ifEmpty { it.name },
                keywords = TopicMatcher.parseKeywordList(keywordsText, max = 25)
            ) else it
        })
    }

    /** Lets the local model write keywords for the topic in Dutch, English and Bulgarian. */
    fun translateKeywords(profileId: String, topicId: String, llm: LlmViewModel) {
        val topic = _state.value.config.profiles.firstOrNull { it.id == profileId }?.topics?.firstOrNull { it.id == topicId } ?: return
        if (!llm.isModelReady) {
            _state.update { it.copy(message = str(R.string.briefing_need_model_keywords)) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(translatingTopicId = topicId) }
            val answer = llm.runOneShotPrompt(BriefingPrompts.KEYWORD_SYSTEM_PROMPT, BriefingPrompts.keywordPrompt(topic.name))
            val newKeywords = answer?.let { TopicMatcher.parseKeywordList(it.lines().firstOrNull { line -> line.isNotBlank() } ?: it) }
            if (newKeywords.isNullOrEmpty()) {
                _state.update { it.copy(translatingTopicId = null, message = str(R.string.briefing_keywords_failed)) }
            } else {
                updateProfileInternal(profileId) { p ->
                    p.copy(topics = p.topics.map {
                        if (it.id == topicId) it.copy(keywords = (it.keywords + newKeywords).distinctBy { k -> k.lowercase() }.take(25)) else it
                    })
                }
                _state.update { it.copy(translatingTopicId = null) }
            }
        }
    }

    // ---------------- Sources (shared by all profiles) ----------------

    fun toggleSource(id: String) = updateConfig { config ->
        config.copy(sources = config.sources.map { if (it.id == id) it.copy(enabled = !it.enabled) else it })
    }

    fun deleteSource(id: String) = updateConfig { config -> config.copy(sources = config.sources.filterNot { it.id == id }) }

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
        config.copy(sources = config.sources + DefaultBriefing.config.sources.filterNot { it.id in existing })
    }

    fun testSource(id: String) {
        val source = _state.value.config.sources.firstOrNull { it.id == id } ?: return
        _state.update { it.copy(sourceTestResults = it.sourceTestResults + (id to str(R.string.briefing_testing))) }
        viewModelScope.launch {
            val keywords = _state.value.selectedProfile?.topics?.filter { it.enabled }?.flatMap { it.allKeywords }?.take(2).orEmpty()
            val result = fetcher.fetchSource(source, keywords.ifEmpty { listOf("news") })
            val error = result.error
            val text = if (error != null) str(R.string.briefing_test_failed, error) else str(R.string.briefing_test_ok, result.items.size)
            _state.update { it.copy(sourceTestResults = it.sourceTestResults + (id to text)) }
        }
    }

    // ---------------- Briefings ----------------

    fun showBriefing(id: String) {
        _state.update { s ->
            val briefing = s.history.firstOrNull { it.id == id } ?: return@update s
            s.copy(current = briefing, selectedProfileId = briefing.profileId.ifEmpty { s.selectedProfileId })
        }
    }

    fun deleteBriefing(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val history = repository.deleteBriefing(id)
            _state.update { s -> s.copy(history = history, current = if (s.current?.id == id) latestFor(history, s.selectedProfileId) else s.current) }
        }
    }

    fun stop() {
        runJob?.cancel()
        runJob = null
        _state.update { it.copy(isRunning = false, progress = null, streamingArticleIndex = -1) }
    }

    /** Builds a new editorial briefing for the selected profile. */
    fun generate(llm: LlmViewModel, forceRefresh: Boolean = false) {
        if (_state.value.isRunning) return
        val profile = _state.value.selectedProfile ?: return
        runJob = viewModelScope.launch {
            val topics = profile.topics.filter { it.enabled }
            if (topics.isEmpty()) {
                _state.update { it.copy(message = str(R.string.briefing_no_topics)) }
                return@launch
            }
            _state.update { it.copy(isRunning = true, progress = str(R.string.briefing_fetching), message = null, sourceErrors = emptyMap()) }
            try {
                val languageCode = profile.language.takeIf { it in BRIEFING_LANGUAGES }
                    ?: if (profile.id == DefaultBriefing.CARE_PROFILE_ID && profile.language.isEmpty()) "nl"
                    else AppLocale.effectiveLanguage(getApplication())
                val items = mutableListOf<NewsItem>()
                if (profile.mode != SourceMode.WEB) items += loadRssItems(_state.value.config, topics, forceRefresh)
                if (profile.mode != SourceMode.RSS) items += searchWeb(profile, topics, llm, languageCode)

                val selected = TopicMatcher.selectArticles(items, topics, profile.maxArticles.coerceIn(1, 15))
                val now = System.currentTimeMillis()
                var briefing = Briefing(
                    id = now.toString(),
                    createdAt = now,
                    language = languageCode,
                    profileId = profile.id,
                    profileName = profile.name,
                    articles = selected.map { (item, topic) -> BriefingArticle(item, topic.name) }
                )
                _state.update { it.copy(current = briefing) }

                if (selected.isEmpty()) {
                    _state.update { it.copy(message = str(R.string.briefing_no_news)) }
                } else {
                    // Preview images for articles whose feed had none.
                    _state.update { it.copy(progress = str(R.string.briefing_images)) }
                    val images = coroutineScope {
                        briefing.articles.map { article ->
                            async { if (article.item.imageUrl.isNullOrBlank()) fetcher.fetchPreviewImage(article.item.link) else article.item.imageUrl }
                        }.awaitAll()
                    }
                    briefing = briefing.copy(articles = briefing.articles.mapIndexed { i, a -> a.copy(item = a.item.copy(imageUrl = images[i])) })
                    _state.update { it.copy(current = briefing) }

                    if (llm.isModelReady) {
                        val languageName = AppLocale.languageNameForModel(languageCode) ?: "English"
                        val systemPrompt = BriefingPrompts.articleSystemPrompt(languageName, profile.instructions)
                        briefing.articles.forEachIndexed { index, article ->
                            _state.update {
                                it.copy(
                                    progress = str(R.string.briefing_writing, index + 1, briefing.articles.size),
                                    streamingArticleIndex = index
                                )
                            }
                            val prompt = BriefingPrompts.articlePrompt(article.item, article.topicName, languageName)
                            val raw = llm.runOneShotPrompt(systemPrompt, prompt) { partial ->
                                briefing = briefing.withArticleText(index, partial)
                                _state.update { it.copy(current = briefing) }
                            }
                            if (raw != null) {
                                briefing = briefing.withArticleText(index, raw)
                                _state.update { it.copy(current = briefing) }
                            }
                        }
                    }
                }

                val history = withContext(Dispatchers.IO) { repository.saveBriefing(briefing) }
                _state.update { it.copy(history = history, current = briefing) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(message = str(R.string.briefing_failed, e.message ?: "")) }
            } finally {
                _state.update { it.copy(isRunning = false, progress = null, streamingArticleIndex = -1) }
            }
        }
    }

    private fun Briefing.withArticleText(index: Int, raw: String): Briefing {
        val parsed = BriefingPrompts.parseArticle(raw)
        return copy(articles = articles.mapIndexed { i, a ->
            if (i == index) a.copy(
                title = parsed.title,
                summary = parsed.summary,
                relevance = parsed.relevance,
                processed = parsed.summary.isNotBlank() || parsed.title.isNotBlank()
            ) else a
        })
    }

    private suspend fun loadRssItems(config: BriefingConfig, topics: List<BriefingTopic>, forceRefresh: Boolean): List<NewsItem> {
        val cache = withContext(Dispatchers.IO) { repository.loadCache() }
        val cacheIsFresh = System.currentTimeMillis() - cache.fetchedAt < CACHE_MAX_AGE_MS && cache.items.isNotEmpty()
        if (!forceRefresh && cacheIsFresh) return cache.items

        val keywords = topics.flatMap { it.allKeywords }.distinctBy { it.lowercase() }
        val results = fetcher.fetchAll(config.sources, keywords)
        val errors = results.filter { it.error != null }.associate { it.source.id to (it.error ?: "") }
        _state.update { it.copy(sourceErrors = it.sourceErrors + errors) }
        val items = results.flatMap { it.items }
        if (items.isNotEmpty()) {
            withContext(Dispatchers.IO) { repository.saveCache(FetchCache(System.currentTimeMillis(), items)) }
        }
        return items
    }

    /** Site-restricted Brave search per topic (counts towards the monthly Brave limit). */
    private suspend fun searchWeb(profile: BriefingProfile, topics: List<BriefingTopic>, llm: LlmViewModel, languageCode: String): List<NewsItem> {
        if (profile.webDomains.isEmpty()) return emptyList()
        if (!llm.isBraveConfigured) {
            _state.update { it.copy(message = str(R.string.briefing_web_needs_brave)) }
            return emptyList()
        }
        val limit = withContext(Dispatchers.IO) { braveUsage.loadConfig().monthlyBraveLimit }
        val results = mutableListOf<NewsItem>()
        for (topic in topics.take(MAX_WEB_TOPICS)) {
            val used = withContext(Dispatchers.IO) { braveUsage.braveUsageThisMonth() }
            if (used >= limit) {
                _state.update { it.copy(message = str(R.string.medical_note_limit, limit)) }
                break
            }
            withContext(Dispatchers.IO) { braveUsage.recordBraveUse() }
            val query = MedicalPrompts.siteQuery(topic.allKeywords.take(3).joinToString(" OR "), profile.webDomains)
            val outcome = llm.searchWithBrave(query, maxResults = 6, language = languageCode) ?: continue
            if (outcome.isError) {
                _state.update { it.copy(sourceErrors = it.sourceErrors + ("web" to (outcome.errorMessage ?: ""))) }
                continue
            }
            outcome.sources.forEach { source ->
                results += NewsItem(
                    title = source.title,
                    link = source.url,
                    sourceName = source.domain.removePrefix("www."),
                    publishedMillis = null,
                    description = source.snippet
                )
            }
        }
        return results
    }

    /** Articles to show: the new editorial format, or the topic sections of older briefings. */
    fun displayArticles(briefing: Briefing): List<BriefingArticle> =
        briefing.articles.ifEmpty {
            briefing.sections.flatMap { section ->
                section.items.mapIndexed { i, item ->
                    BriefingArticle(item, section.topicName, summary = if (i == 0) section.summary else "")
                }
            }
        }

    /** Plain-text version for sharing. */
    fun briefingAsText(briefing: Briefing, header: String, relevanceLabel: String): String = buildString {
        append(header).append("\n\n")
        displayArticles(briefing).forEach { a ->
            append("■ ").append(a.title.ifBlank { a.item.title }).append(" (").append(a.item.sourceName).append(")\n")
            if (a.summary.isNotBlank()) append(a.summary.trim()).append("\n")
            if (a.relevance.isNotBlank()) append(relevanceLabel).append(": ").append(a.relevance.trim()).append("\n")
            if (a.item.link.isNotBlank()) append(a.item.link).append("\n")
            append("\n")
        }
    }

    /** Spoken version, without links. */
    fun briefingAsSpeech(briefing: Briefing): String = buildString {
        displayArticles(briefing).forEach { a ->
            append(a.title.ifBlank { a.item.title }).append(". ")
            if (a.summary.isNotBlank()) append(a.summary).append(" ")
            if (a.relevance.isNotBlank()) append(a.relevance).append(" ")
        }
    }

    companion object {
        const val CACHE_MAX_AGE_MS = 3L * 60L * 60L * 1000L
        const val MAX_WEB_TOPICS = 4
        val BRIEFING_LANGUAGES = listOf("nl", "en", "bg")
    }
}
