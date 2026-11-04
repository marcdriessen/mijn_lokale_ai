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
import com.example.auralocalai.data.briefing.BriefingGenerator
import com.example.auralocalai.data.translate.MlKitNewsTranslator
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
    val sourceStatus: Map<String, com.example.auralocalai.data.briefing.SourceStatus> = emptyMap(),
    val translatingTopicId: String? = null,
    val weeklyDigest: String? = null,
    val digestBusy: Boolean = false
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

    fun setSkipPaywalled(skip: Boolean) = updateConfig { it.copy(skipPaywalled = skip) }

    /** Next category for a source (news → professional → science → tech → forum). */
    fun cycleSourceCategory(id: String) = updateConfig { config ->
        val all = com.example.auralocalai.data.briefing.SourceCategory.ALL
        config.copy(sources = config.sources.map {
            if (it.id == id) it.copy(category = all[(all.indexOf(it.category).coerceAtLeast(0) + 1) % all.size]) else it
        })
    }

    fun deleteSource(id: String) = updateConfig { config -> config.copy(sources = config.sources.filterNot { it.id == id }) }

    fun addSource(name: String, url: String) {
        val cleanUrl = url.trim()
        if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
            _state.update { it.copy(message = str(R.string.briefing_invalid_url)) }
            return
        }
        val cleanName = name.trim().ifEmpty { android.net.Uri.parse(cleanUrl).host ?: cleanUrl }
        if (cleanUrl.contains(NewsSource.SEARCH_PLACEHOLDER)) {
            updateConfig { it.copy(sources = it.sources + NewsSource("src_" + System.currentTimeMillis(), cleanName, cleanUrl)) }
            return
        }
        // A normal web address: look for the feed the site announces (feed autodetection).
        _state.update { it.copy(message = str(R.string.briefing_feed_searching)) }
        viewModelScope.launch {
            val feed = fetcher.discoverFeed(cleanUrl)
            if (feed == null) {
                _state.update { it.copy(message = str(R.string.briefing_feed_not_found)) }
            } else {
                updateConfig { it.copy(sources = it.sources + NewsSource("src_" + System.currentTimeMillis(), cleanName, feed)) }
                _state.update { it.copy(message = if (feed == cleanUrl) str(R.string.briefing_feed_added) else str(R.string.briefing_feed_found, feed)) }
            }
        }
    }

    fun restoreDefaultSources() = updateConfig { config ->
        val existing = config.sources.map { it.id }.toSet()
        config.copy(sources = config.sources + DefaultBriefing.config.sources.filterNot { it.id in existing })
    }

    /**
     * Saves an article permanently in the library of the Q&A (on request only): the full text when it
     * can be read, otherwise the summary. Public news, so no anonymisation is needed.
     */
    fun saveArticleToLibrary(article: com.example.auralocalai.data.briefing.BriefingArticle) {
        viewModelScope.launch {
            _state.update { it.copy(message = str(R.string.briefing_saving_article)) }
            val text = withContext(Dispatchers.IO) {
                runCatching { fetcher.fetchArticle(article.item.link, maxChars = 30_000)?.text }.getOrNull()
                    ?.takeIf { com.example.auralocalai.data.briefing.ArticleContent.isUsable(it) }
            }
            val title = article.title.ifBlank { article.item.title }
            val body = text ?: listOf(title, article.summary, article.relevance, article.item.link).filter { it.isNotBlank() }.joinToString("\n\n")
            withContext(Dispatchers.IO) {
                val repo = com.example.auralocalai.data.medical.MedicalRepository(getApplication<android.app.Application>().filesDir)
                val name = "$title (${article.item.sourceName})"
                val doc = com.example.auralocalai.data.medical.LibraryDocument(
                    "doc_" + System.currentTimeMillis(), name, System.currentTimeMillis(),
                    com.example.auralocalai.data.medical.PassageIndex.chunk(body)
                )
                repo.saveLibrary(repo.loadLibrary().filterNot { it.name == name } + doc)
            }
            _state.update { it.copy(message = str(if (text != null) R.string.briefing_saved_full else R.string.briefing_saved_summary)) }
        }
    }

    /** Reloads the stored fetch status of all sources (for the Sources screen). */
    fun refreshSourceStatus() {
        viewModelScope.launch(Dispatchers.IO) {
            val status = repository.loadSourceStatus()
            _state.update { it.copy(sourceStatus = status) }
        }
    }

    fun testSource(id: String) {
        val source = _state.value.config.sources.firstOrNull { it.id == id } ?: return
        _state.update { it.copy(sourceTestResults = it.sourceTestResults + (id to str(R.string.briefing_testing))) }
        viewModelScope.launch {
            val keywords = _state.value.selectedProfile?.topics?.filter { it.enabled }?.flatMap { it.allKeywords }?.take(2).orEmpty()
            val result = fetcher.fetchSource(source, keywords.ifEmpty { listOf("news") })
            withContext(Dispatchers.IO) { repository.saveSourceResults(listOf(result)) }
            refreshSourceStatus()
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

    /**
     * Builds a new briefing for the selected profile in the background. The previous briefing stays
     * on screen; the new one replaces it only when it is completely finished.
     */
    fun generate(llm: LlmViewModel, forceRefresh: Boolean = false) {
        if (_state.value.isRunning) return
        val profile = _state.value.selectedProfile ?: return
        if (com.example.auralocalai.AppSecurity.isPrivacyMode(getApplication())) {
            _state.update { it.copy(message = str(R.string.privacy_blocked)) }
            return
        }
        runJob = viewModelScope.launch {
            if (profile.topics.none { it.enabled }) {
                _state.update { it.copy(message = str(R.string.briefing_no_topics)) }
                return@launch
            }
            _state.update { it.copy(isRunning = true, progress = str(R.string.briefing_fetching), message = null, sourceErrors = emptyMap()) }
            val translator = MlKitNewsTranslator()
            try {
                val languageCode = BriefingGenerator.languageFor(profile, AppLocale.effectiveLanguage(getApplication()))
                val languageName = AppLocale.languageNameForModel(languageCode) ?: "English"
                val webItems = if (profile.mode != SourceMode.RSS) searchWeb(profile, profile.topics.filter { it.enabled }, llm, languageCode) else emptyList()
                val researchItems = if (profile.includeResearch) {
                    _state.update { it.copy(progress = str(R.string.briefing_research_fetching)) }
                    com.example.auralocalai.data.briefing.ResearchFeed.fetch(profile.topics)
                } else emptyList()
                val generator = BriefingGenerator(repository, fetcher, translator) { doi -> com.example.auralocalai.data.medical.StudyLookup.lookup(doi) }
                val model = if (llm.isModelReady) BriefingGenerator.ModelCall { system, prompt -> llm.runOneShotPrompt(system, prompt) } else null
                val briefing = generator.build(
                    profile = profile,
                    config = _state.value.config,
                    languageCode = languageCode,
                    languageName = languageName,
                    forceRefresh = forceRefresh,
                    extraItems = webItems + researchItems,
                    model = model,
                    onProgress = { p -> _state.update { it.copy(progress = progressText(p)) } },
                    onSourceErrors = { errors -> _state.update { it.copy(sourceErrors = it.sourceErrors + errors) } }
                )
                if (briefing.articles.isEmpty()) {
                    _state.update { it.copy(message = str(R.string.briefing_no_news)) }
                } else {
                    val history = withContext(Dispatchers.IO) { repository.saveBriefing(briefing) }
                    _state.update { it.copy(history = history, current = briefing) }
                    runCatching { com.example.auralocalai.data.briefing.BriefingWidgetProvider.refresh(getApplication()) }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(message = str(R.string.briefing_failed, e.message ?: "")) }
            } finally {
                translator.close()
                _state.update { it.copy(isRunning = false, progress = null, streamingArticleIndex = -1) }
            }
        }
    }

    private fun progressText(p: BriefingGenerator.Progress): String = when (p.stage) {
        BriefingGenerator.Stage.FETCHING -> str(R.string.briefing_fetching)
        BriefingGenerator.Stage.RANKING -> str(R.string.briefing_ranking)
        BriefingGenerator.Stage.IMAGES -> str(R.string.briefing_images)
        BriefingGenerator.Stage.TRANSLATING -> str(R.string.briefing_translating, p.done, p.total)
        BriefingGenerator.Stage.WRITING -> str(R.string.briefing_writing, p.done, p.total)
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

    /** Writes an overview of the last 7 days of briefings with the shared model. */
    fun weeklyDigest(llm: LlmViewModel) {
        if (!llm.isModelReady) {
            _state.update { it.copy(message = str(R.string.translate_need_model)) }
            return
        }
        val since = System.currentTimeMillis() - 7L * 24L * 60L * 60L * 1000L
        val articles = _state.value.history.filter { it.createdAt >= since }
            .flatMap { displayArticles(it) }
            .distinctBy { it.item.link.ifBlank { it.item.title } }
            .take(40)
        if (articles.isEmpty()) {
            _state.update { it.copy(message = str(R.string.digest_empty)) }
            return
        }
        val languageCode = _state.value.selectedProfile?.let { BriefingGenerator.languageFor(it, AppLocale.effectiveLanguage(getApplication())) } ?: "nl"
        val languageName = AppLocale.languageNameForModel(languageCode) ?: "Dutch"
        val list = articles.joinToString("\n") { a -> "- [${a.topicName}] ${a.title.ifBlank { a.item.title }}: ${a.summary.take(200)}" }
        viewModelScope.launch {
            _state.update { it.copy(digestBusy = true, weeklyDigest = null) }
            val text = llm.runOneShotPrompt(
                "You are a news editor. Write in $languageName. Use only the items given.",
                "Write a weekly overview of these news items: a short intro, then per topic a heading with 2–4 bullet points of the most important developments.\n\n$list"
            )
            _state.update { it.copy(digestBusy = false, weeklyDigest = text ?: str(R.string.translate_ai_failed)) }
        }
    }

    fun closeDigest() = _state.update { it.copy(weeklyDigest = null, digestBusy = false) }

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
