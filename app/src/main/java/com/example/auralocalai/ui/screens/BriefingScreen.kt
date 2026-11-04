package com.example.auralocalai.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.speech.tts.TextToSpeech
import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Tune
import com.example.auralocalai.ui.components.AppHeader
import com.example.auralocalai.ui.components.CircleIconButton
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.auralocalai.R
import com.example.auralocalai.data.briefing.BriefingArticle
import com.example.auralocalai.data.briefing.BriefingFrequency
import com.example.auralocalai.data.briefing.BriefingProfile
import com.example.auralocalai.data.briefing.BriefingScheduler
import com.example.auralocalai.data.briefing.BriefingTopic
import com.example.auralocalai.data.briefing.SourceMode
import com.example.auralocalai.data.briefing.TopicMatcher
import com.example.auralocalai.ui.BriefingViewModel
import com.example.auralocalai.ui.LlmViewModel
import com.example.auralocalai.ui.ModelState
import java.net.URLEncoder
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BriefingScreen(
    llmViewModel: LlmViewModel,
    onBack: () -> Unit,
    onOpenChat: () -> Unit,
    onOpenModels: () -> Unit,
    modifier: Modifier = Modifier,
    initialProfileId: String? = null,
    briefingViewModel: BriefingViewModel = viewModel()
) {
    val state by briefingViewModel.state.collectAsState()
    val llmState by llmViewModel.uiState.collectAsState()
    val context = LocalContext.current
    val locale = context.resources.configuration.locales[0]
    var editingProfileId by remember { mutableStateOf<String?>(null) }
    var showSources by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var isSpeaking by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf<String?>(null) }
    var detailArticle by remember { mutableStateOf<BriefingArticle?>(null) }
    val modelReady = llmState.modelState is ModelState.Loaded
    val profile = state.selectedProfile

    LaunchedEffect(initialProfileId, state.config.profiles.size) {
        if (!initialProfileId.isNullOrBlank() && state.config.profiles.any { it.id == initialProfileId }) {
            briefingViewModel.selectProfile(initialProfileId)
        }
    }

    val tts = remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(Unit) {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                engine?.setLanguage(locale)
                tts.value = engine
            }
        }
        onDispose {
            engine?.stop()
            engine?.shutdown()
            tts.value = null
        }
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            briefingViewModel.clearMessage()
        }
    }

    val briefing = state.current
    val allArticles = briefing?.let { briefingViewModel.displayArticles(it) } ?: emptyList()
    val topics = allArticles.map { it.topicName }.distinct()
    val articles = if (selectedTab == null) allArticles else allArticles.filter { it.topicName == selectedTab }
    val shareTitle = stringResource(R.string.briefing_share)
    val relevanceLabel = stringResource(R.string.briefing_relevance_label)

    Scaffold(
        topBar = {
            AppHeader(
                title = stringResource(R.string.nav_news),
                subtitle = SimpleDateFormat("EEEE d MMMM", locale).format(Date()).replaceFirstChar { it.titlecase(locale) },
                onBack = onBack
            ) {
                if (profile != null) {
                    CircleIconButton(Icons.Default.Tune, stringResource(R.string.briefing_edit_profile)) { editingProfileId = profile.id }
                }
                CircleIconButton(Icons.Default.Add, stringResource(R.string.briefing_new_profile), accent = true) {
                    editingProfileId = briefingViewModel.addProfile()
                }
                Box {
                    CircleIconButton(Icons.Default.MoreVert, stringResource(R.string.briefing_more)) { showMenu = true }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.briefing_refresh)) },
                            leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                briefingViewModel.generate(llmViewModel, forceRefresh = true)
                            }
                        )
                        if (briefing != null) {
                            DropdownMenuItem(
                                text = { Text(if (isSpeaking) stringResource(R.string.briefing_stop_reading) else stringResource(R.string.briefing_read_aloud)) },
                                leadingIcon = { Icon(if (isSpeaking) Icons.Default.Stop else Icons.Default.RecordVoiceOver, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    val engine = tts.value
                                    if (engine == null) {
                                        Toast.makeText(context, context.getString(R.string.briefing_tts_unavailable), Toast.LENGTH_SHORT).show()
                                    } else if (isSpeaking) {
                                        engine.stop()
                                        isSpeaking = false
                                    } else {
                                        engine.speak(briefingViewModel.briefingAsSpeech(briefing), TextToSpeech.QUEUE_FLUSH, null, "briefing")
                                        isSpeaking = true
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(shareTitle) },
                                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    val header = context.getString(R.string.briefing_share_header, formatDateTime(context, briefing.createdAt))
                                    val send = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, briefingViewModel.briefingAsText(briefing, header, relevanceLabel))
                                    }
                                    context.startActivity(Intent.createChooser(send, shareTitle))
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.digest_title)) },
                            leadingIcon = { Icon(Icons.Default.AutoAwesome, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                briefingViewModel.weeklyDigest(llmViewModel)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.briefing_history)) },
                            leadingIcon = { Icon(Icons.Default.History, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                showHistory = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.briefing_sources)) },
                            leadingIcon = { Icon(Icons.Default.RssFeed, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                showSources = true
                            }
                        )
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            // Briefings as pills (selected = dark filled)
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    state.config.profiles.forEach { p ->
                        PillTab(
                            text = p.name,
                            selected = p.id == profile?.id,
                            scheduled = p.scheduleEnabled
                        ) {
                            selectedTab = null
                            briefingViewModel.selectProfile(p.id)
                        }
                    }
                }
            }

            if (profile == null) {
                item { Text(stringResource(R.string.briefing_empty), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(20.dp)) }
                return@LazyColumn
            }

            // "Your selection" + edit link, status and the main action
            item {
                Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.briefing_your_selection),
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            stringResource(R.string.briefing_edit_topics),
                            style = MaterialTheme.typography.labelLarge,
                            textDecoration = TextDecoration.Underline,
                            modifier = Modifier.clickable { editingProfileId = profile.id }
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    val status = buildList {
                        if (briefing != null) add(
                            context.getString(
                                R.string.briefing_updated,
                                DateUtils.getRelativeTimeSpanString(briefing.createdAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
                            )
                        )
                        if (profile.scheduleEnabled) add(
                            context.getString(
                                R.string.briefing_next_run,
                                DateUtils.getRelativeTimeSpanString(BriefingScheduler.nextRunAt(profile, Calendar.getInstance()), System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
                            )
                        )
                    }.joinToString(" · ")
                    if (status.isNotBlank()) {
                        Text(status, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    if (!modelReady) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    stringResource(R.string.briefing_load_model_translate),
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = onOpenModels) { Text(stringResource(R.string.models), fontWeight = FontWeight.Bold) }
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    if (state.isRunning) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(state.progress ?: "", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                            TextButton(onClick = { briefingViewModel.stop() }) { Text(stringResource(R.string.briefing_stop)) }
                        }
                    } else {
                        Button(
                            onClick = { briefingViewModel.generate(llmViewModel) },
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                if (briefing == null) stringResource(R.string.briefing_generate) else stringResource(R.string.briefing_update_now),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // Topic links (like "IN FOCUS")
            if (topics.size > 1) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            stringResource(R.string.briefing_in_focus).uppercase(locale),
                            style = MaterialTheme.typography.labelMedium,
                            letterSpacing = 1.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TopicLink(stringResource(R.string.briefing_tab_all), selectedTab == null) { selectedTab = null }
                        topics.forEach { t -> TopicLink(t, selectedTab == t) { selectedTab = t } }
                    }
                }
            }

            if (articles.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.briefing_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(20.dp)
                    )
                }
            } else {
                val hero = articles.first()
                val cards = articles.drop(1).take(3)
                val timeline = articles.drop(4)

                item(key = "hero_${briefing?.id}_${selectedTab}") {
                    HeroOverlay(hero, isWriting = allArticles.indexOf(hero) == state.streamingArticleIndex) { detailArticle = hero }
                }
                itemsIndexed(cards, key = { i, _ -> "card_${briefing?.id}_${selectedTab}_$i" }) { _, article ->
                    StoryCard(
                        article = article,
                        isWriting = allArticles.indexOf(article) == state.streamingArticleIndex,
                        onClick = { detailArticle = article },
                        onShare = {
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "${article.title.ifBlank { article.item.title }}\n${article.item.link}")
                            }
                            context.startActivity(Intent.createChooser(send, shareTitle))
                        }
                    )
                }
                if (timeline.isNotEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.briefing_just_in),
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 8.dp)
                        )
                    }
                    itemsIndexed(timeline, key = { i, _ -> "tl_${briefing?.id}_${selectedTab}_$i" }) { i, article ->
                        TimelineRow(
                            article = article,
                            isLast = i == timeline.lastIndex,
                            onClick = { detailArticle = article }
                        )
                    }
                }
                if (state.sourceErrors.isNotEmpty()) {
                    item {
                        val names = state.sourceErrors.keys.joinToString { id -> state.config.sources.firstOrNull { it.id == id }?.name ?: id }
                        Text(
                            stringResource(R.string.briefing_sources_failed, names),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                        )
                    }
                }
                item {
                    Text(
                        stringResource(R.string.briefing_disclaimer),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(20.dp)
                    )
                }
            }
        }
    }

    detailArticle?.let { article ->
        ArticleDetailDialog(
            article = article,
            canAskMore = modelReady && !state.isRunning,
            onAskMore = {
                llmViewModel.startNewChatWith(
                    context.getString(
                        R.string.briefing_follow_up_article,
                        article.title.ifBlank { article.item.title },
                        article.summary.trim(),
                        article.item.link
                    )
                )
                detailArticle = null
                onOpenChat()
            },
            onDismiss = { detailArticle = null }
        )
    }

    editingProfileId?.let { id ->
        val editing = state.config.profiles.firstOrNull { it.id == id }
        if (editing != null) {
            ProfileEditorDialog(
                profile = editing,
                canDelete = state.config.profiles.size > 1,
                translatingTopicId = state.translatingTopicId,
                briefingViewModel = briefingViewModel,
                llmViewModel = llmViewModel,
                onDismiss = { editingProfileId = null }
            )
        } else {
            editingProfileId = null
        }
    }

    if (state.digestBusy || state.weeklyDigest != null) {
        AlertDialog(
            onDismissRequest = { briefingViewModel.closeDigest() },
            title = { Text(stringResource(R.string.digest_title)) },
            text = {
                if (state.digestBusy) {
                    Column {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(stringResource(R.string.translate_ai_busy))
                    }
                } else {
                    Column(modifier = Modifier.height(420.dp).verticalScroll(rememberScrollState())) {
                        Text(state.weeklyDigest ?: "", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            },
            confirmButton = {
                Row {
                    if (!state.digestBusy && state.weeklyDigest != null) {
                        TextButton(onClick = {
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, state.weeklyDigest)
                            }
                            context.startActivity(Intent.createChooser(send, shareTitle))
                        }) { Text(shareTitle) }
                    }
                    TextButton(onClick = { briefingViewModel.closeDigest() }) { Text(stringResource(R.string.dismiss)) }
                }
            }
        )
    }

    if (showSources) {
        SourcesDialog(briefingViewModel = briefingViewModel, onDismiss = { showSources = false })
    }

    if (showHistory) {
        AlertDialog(
            onDismissRequest = { showHistory = false },
            title = { Text(stringResource(R.string.briefing_history)) },
            text = {
                if (state.history.isEmpty()) {
                    Text(stringResource(R.string.briefing_history_empty))
                } else {
                    LazyColumn(modifier = Modifier.height(380.dp)) {
                        items(state.history, key = { it.id }) { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        briefingViewModel.showBriefing(item.id)
                                        showHistory = false
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(formatDateTime(context, item.createdAt), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    Text(
                                        item.profileName.ifBlank { item.sections.filter { it.items.isNotEmpty() }.joinToString { it.topicName } },
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { briefingViewModel.deleteBriefing(item.id) }) {
                                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete), tint = MaterialTheme.colorScheme.error)
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showHistory = false }) { Text(stringResource(R.string.dismiss)) } }
        )
    }
}

@Composable
private fun TagChip(text: String, active: Boolean = true, onClick: (() -> Unit)? = null) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (active) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.background,
        border = if (active) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun RoundIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(23.dp),
        contentPadding = PaddingValues(0.dp),
        modifier = Modifier.size(46.dp)
    ) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(18.dp))
    }
}

/** Briefing selector pill: dark filled when selected, light otherwise. */
@Composable
private fun PillTab(text: String, selected: Boolean, scheduled: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (scheduled) {
                Icon(
                    Icons.Default.Schedule,
                    contentDescription = null,
                    tint = if (selected) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun TopicLink(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        textDecoration = if (selected) TextDecoration.Underline else null,
        maxLines = 1,
        modifier = Modifier.clickable(onClick = onClick)
    )
}

/** Image with a placeholder (source name) when there is no picture. */
@Composable
private fun ArticleImage(article: BriefingArticle, modifier: Modifier, placeholderStyle: androidx.compose.ui.text.TextStyle) {
    Box(modifier = modifier.background(MaterialTheme.colorScheme.primaryContainer)) {
        if (!article.item.imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = article.item.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                text = article.item.sourceName,
                style = placeholderStyle,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(8.dp)
            )
        }
    }
}

@Composable
private fun ArticleMeta(article: BriefingArticle) {
    val context = LocalContext.current
    val time = article.item.publishedMillis?.let {
        DateUtils.getRelativeTimeSpanString(it, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = listOfNotNull(article.item.sourceName, time).joinToString(" · "),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (!article.processed && article.title.isBlank()) {
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = context.getString(R.string.briefing_not_translated),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun TopicLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp,
        color = MaterialTheme.colorScheme.primary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

/** Top story: large picture with the headline on a dark gradient, like a magazine cover. */
@Composable
private fun HeroOverlay(article: BriefingArticle, isWriting: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
            .height(280.dp)
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
    ) {
        ArticleImage(
            article = article,
            modifier = Modifier.fillMaxSize(),
            placeholderStyle = MaterialTheme.typography.headlineMedium
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.35f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.85f)
                    )
                )
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(6.dp), color = Color.White.copy(alpha = 0.92f)) {
                    Text(
                        article.topicName.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        color = Color.Black,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                if (isWriting) {
                    Spacer(modifier = Modifier.width(8.dp))
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = Color.White)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = article.title.ifBlank { article.item.title },
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = listOfNotNull(article.item.sourceName, relativeTime(article.item.publishedMillis)).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.8f)
            )
        }
    }
}

/** Card: topic and source on top, bold headline with a square picture, meta and a menu. */
@Composable
private fun StoryCard(article: BriefingArticle, isWriting: Boolean, onClick: () -> Unit, onShare: () -> Unit) {
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(start = 18.dp, end = 10.dp, top = 16.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 8.dp)) {
                Text(
                    article.topicName,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (isWriting) CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    article.item.sourceName,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(end = 8.dp)) {
                Text(
                    text = article.title.ifBlank { article.item.title },
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.width(12.dp))
                ArticleImage(
                    article = article,
                    modifier = Modifier
                        .size(84.dp)
                        .clip(RoundedCornerShape(14.dp)),
                    placeholderStyle = MaterialTheme.typography.labelSmall
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.weight(1f)) { ArticleMeta(article) }
                Box {
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.briefing_more))
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.briefing_read_at, article.item.sourceName)) }, onClick = {
                            menu = false
                            openUrl(context, article.item.link)
                        })
                        DropdownMenuItem(text = { Text(stringResource(R.string.briefing_share)) }, onClick = {
                            menu = false
                            onShare()
                        })
                    }
                }
            }
        }
    }
}

/** "Just in" list: a dotted time line, "26 min ago in Topic", headline and picture on the right. */
@Composable
private fun TimelineRow(article: BriefingArticle, isLast: Boolean, onClick: () -> Unit) {
    val lineColor = MaterialTheme.colorScheme.outline
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp)
    ) {
        Box(
            modifier = Modifier
                .width(18.dp)
                .fillMaxHeight()
                .drawBehind {
                    val x = size.width / 2
                    drawLine(
                        color = lineColor,
                        start = Offset(x, if (isLast) 0f else 0f),
                        end = Offset(x, if (isLast) 30.dp.toPx() else size.height),
                        strokeWidth = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 6f))
                    )
                    drawCircle(color = lineColor, radius = 4.dp.toPx(), center = Offset(x, 30.dp.toPx()))
                }
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 14.dp)
        ) {
            val time = relativeTime(article.item.publishedMillis)
            Text(
                text = if (time != null) stringResource(R.string.briefing_time_in_topic, time, article.topicName) else article.topicName,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = article.title.ifBlank { article.item.title },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        ArticleImage(
            article = article,
            modifier = Modifier
                .padding(vertical = 14.dp)
                .size(width = 100.dp, height = 74.dp)
                .clip(RoundedCornerShape(12.dp)),
            placeholderStyle = MaterialTheme.typography.labelSmall
        )
    }
}

private fun relativeTime(millis: Long?): String? =
    millis?.let { DateUtils.getRelativeTimeSpanString(it, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString() }

/** Full article view: picture, headline, summary, "what this means for your work" and links. */
@Composable
private fun ArticleDetailDialog(
    article: BriefingArticle,
    canAskMore: Boolean,
    onAskMore: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val item = article.item
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                Box {
                    ArticleImage(
                        article = article,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(250.dp),
                        placeholderStyle = MaterialTheme.typography.headlineMedium
                    )
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(12.dp)
                    ) {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.go_back))
                        }
                    }
                }
                Column(modifier = Modifier.padding(20.dp)) {
                    TopicLabel(article.topicName)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = article.title.ifBlank { item.title },
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    ArticleMeta(article)
                    if (article.title.isNotBlank() && article.title != item.title) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            stringResource(R.string.briefing_original_title, item.title),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    val summary = article.summary.ifBlank { item.description }
                    if (summary.isNotBlank()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(summary.trim(), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
                    }
                    if (article.relevance.isNotBlank()) {
                        Spacer(modifier = Modifier.height(18.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .fillMaxHeight()
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    stringResource(R.string.briefing_relevance_label).uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.8.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(article.relevance.trim(), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { openUrl(context, item.link) },
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                    ) {
                        Text(stringResource(R.string.briefing_read_at, item.sourceName), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(Icons.Default.NorthEast, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onAskMore, enabled = canAskMore, shape = RoundedCornerShape(24.dp), modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.briefing_ask_more))
                        }
                        OutlinedButton(
                            onClick = {
                                val query = URLEncoder.encode("site:ground.news ${article.title.ifBlank { item.title }}", "UTF-8")
                                openUrl(context, "https://www.google.com/search?q=$query")
                            },
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.briefing_ground_news))
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

/** Settings of one briefing: name, instructions, schedule, notifications, sources and topics. */
@Composable
private fun ProfileEditorDialog(
    profile: BriefingProfile,
    canDelete: Boolean,
    translatingTopicId: String?,
    briefingViewModel: BriefingViewModel,
    llmViewModel: LlmViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var name by remember(profile.id) { mutableStateOf(profile.name) }
    var instructions by remember(profile.id) { mutableStateOf(profile.instructions) }
    var scheduleEnabled by remember(profile.id) { mutableStateOf(profile.scheduleEnabled) }
    var frequency by remember(profile.id) { mutableStateOf(profile.schedule) }
    var weekday by remember(profile.id) { mutableStateOf(profile.weekday) }
    var hour by remember(profile.id) { mutableStateOf(profile.hour) }
    var minute by remember(profile.id) { mutableStateOf(profile.minute) }
    var startDate by remember(profile.id) { mutableStateOf(profile.startDateMillis) }
    var notify by remember(profile.id) { mutableStateOf(profile.notify) }
    var mode by remember(profile.id) { mutableStateOf(profile.mode) }
    var domains by remember(profile.id) { mutableStateOf(profile.webDomains.joinToString(", ")) }
    var maxArticles by remember(profile.id) { mutableStateOf(profile.maxArticles) }
    var language by remember(profile.id) {
        mutableStateOf(
            profile.language.ifBlank { if (profile.id == com.example.auralocalai.data.briefing.DefaultBriefing.CARE_PROFILE_ID) "nl" else "app" }
        )
    }
    var newTopic by remember { mutableStateOf("") }
    var editingTopic by remember { mutableStateOf<BriefingTopic?>(null) }
    var frequencyMenu by remember { mutableStateOf(false) }
    var weekdayMenu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    fun current(): BriefingProfile = profile.copy(
        name = name.trim().ifEmpty { profile.name },
        instructions = instructions.trim(),
        scheduleEnabled = scheduleEnabled,
        frequency = frequency.name,
        weekday = weekday,
        hour = hour,
        minute = minute,
        startDateMillis = startDate,
        notify = notify,
        sourceMode = mode.name,
        webDomains = TopicMatcher.parseKeywordList(domains, max = 30).map {
            it.lowercase().removePrefix("https://").removePrefix("http://").removePrefix("www.").substringBefore('/')
        },
        maxArticles = maxArticles,
        language = language
    )

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                // Header: status + next run
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (scheduleEnabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            if (scheduleEnabled) stringResource(R.string.briefing_status_active) else stringResource(R.string.briefing_status_manual),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (scheduleEnabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    if (scheduleEnabled) {
                        val next = BriefingScheduler.nextRunAt(current(), Calendar.getInstance())
                        Text(
                            stringResource(R.string.briefing_next_run, DateUtils.getRelativeTimeSpanString(next, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel)) }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    textStyle = MaterialTheme.typography.titleLarge,
                    label = { Text(stringResource(R.string.briefing_profile_name)) },
                    modifier = Modifier.fillMaxWidth()
                )

                EditorSectionLabel(stringResource(R.string.briefing_instructions))
                OutlinedTextField(
                    value = instructions,
                    onValueChange = { instructions = it },
                    placeholder = { Text(stringResource(R.string.briefing_instructions_hint)) },
                    minLines = 5,
                    maxLines = 12,
                    modifier = Modifier.fillMaxWidth()
                )

                // Triggers
                EditorSectionLabel(stringResource(R.string.briefing_triggers))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.briefing_run_automatically), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = scheduleEnabled, onCheckedChange = { scheduleEnabled = it })
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    Box {
                        OutlinedButton(onClick = { frequencyMenu = true }, shape = RoundedCornerShape(10.dp)) {
                            Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(frequencyLabel(context, frequency))
                        }
                        DropdownMenu(expanded = frequencyMenu, onDismissRequest = { frequencyMenu = false }) {
                            BriefingFrequency.values().forEach { f ->
                                DropdownMenuItem(text = { Text(frequencyLabel(context, f)) }, onClick = {
                                    frequency = f
                                    frequencyMenu = false
                                })
                            }
                        }
                    }
                    if (frequency == BriefingFrequency.WEEKLY) {
                        Box {
                            OutlinedButton(onClick = { weekdayMenu = true }, shape = RoundedCornerShape(10.dp)) {
                                Text(weekdayName(weekday))
                            }
                            DropdownMenu(expanded = weekdayMenu, onDismissRequest = { weekdayMenu = false }) {
                                listOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY).forEach { d ->
                                    DropdownMenuItem(text = { Text(weekdayName(d)) }, onClick = {
                                        weekday = d
                                        weekdayMenu = false
                                    })
                                }
                            }
                        }
                    }
                    Text(stringResource(R.string.briefing_at), style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(
                        onClick = {
                            TimePickerDialog(context, { _, h, m ->
                                hour = h
                                minute = m
                            }, hour, minute, android.text.format.DateFormat.is24HourFormat(context)).show()
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(String.format(Locale.US, "%02d:%02d", hour, minute))
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.briefing_starts_on), style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = {
                            val cal = Calendar.getInstance().apply { if (startDate > 0) timeInMillis = startDate }
                            DatePickerDialog(context, { _, y, mo, d ->
                                startDate = Calendar.getInstance().apply {
                                    set(y, mo, d, 0, 0, 0)
                                    set(Calendar.MILLISECOND, 0)
                                }.timeInMillis
                            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(if (startDate > 0) formatDate(context, startDate) else stringResource(R.string.briefing_today))
                    }
                    if (startDate > 0) {
                        IconButton(onClick = { startDate = 0L }) { Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    }
                }

                // Advanced
                EditorSectionLabel(stringResource(R.string.briefing_advanced))
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.briefing_notifications), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            Switch(checked = notify, onCheckedChange = { notify = it })
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 8.dp))
                        Text(stringResource(R.string.briefing_news_from), style = MaterialTheme.typography.bodyMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                            SourceMode.values().forEach { m ->
                                FilterChip(
                                    selected = mode == m,
                                    onClick = { mode = m },
                                    label = { Text(sourceModeLabel(context, m), fontSize = 12.sp) }
                                )
                            }
                        }
                        if (mode != SourceMode.RSS) {
                            OutlinedTextField(
                                value = domains,
                                onValueChange = { domains = it },
                                label = { Text(stringResource(R.string.briefing_web_domains)) },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(stringResource(R.string.briefing_web_domains_hint), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 8.dp))
                        Text(stringResource(R.string.briefing_max_articles, maxArticles), style = MaterialTheme.typography.bodyMedium)
                        Slider(value = maxArticles.toFloat(), onValueChange = { maxArticles = it.toInt() }, valueRange = 3f..12f, steps = 8)
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 8.dp))
                        Text(stringResource(R.string.briefing_language), style = MaterialTheme.typography.bodyMedium)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .padding(top = 6.dp)
                                .horizontalScroll(rememberScrollState())
                        ) {
                            listOf("nl" to "Nederlands", "en" to "English", "bg" to "Български", "app" to stringResource(R.string.briefing_language_app)).forEach { (code, label) ->
                                FilterChip(selected = language == code, onClick = { language = code }, label = { Text(label, fontSize = 12.sp) })
                            }
                        }
                    }
                }

                // Topics
                EditorSectionLabel(stringResource(R.string.briefing_topics))
                profile.topics.forEach { topic ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = topic.enabled, onCheckedChange = { briefingViewModel.toggleTopic(profile.id, topic.id) })
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(topic.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text(
                                topic.keywords.joinToString(", ").ifEmpty { stringResource(R.string.briefing_no_keywords) },
                                fontSize = 11.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (translatingTopicId == topic.id) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            IconButton(onClick = { briefingViewModel.translateKeywords(profile.id, topic.id, llmViewModel) }) {
                                Icon(Icons.Default.Translate, contentDescription = stringResource(R.string.briefing_translate_keywords), modifier = Modifier.size(18.dp))
                            }
                        }
                        IconButton(onClick = { editingTopic = topic }) {
                            Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.briefing_edit_topic), modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = { briefingViewModel.deleteTopic(profile.id, topic.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete), tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newTopic,
                        onValueChange = { newTopic = it },
                        label = { Text(stringResource(R.string.briefing_new_topic)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = {
                        briefingViewModel.addTopic(profile.id, newTopic, llmViewModel)
                        newTopic = ""
                    }) { Icon(Icons.Default.Add, contentDescription = stringResource(R.string.briefing_add_topic)) }
                }
                Text(stringResource(R.string.briefing_topic_hint), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                Spacer(modifier = Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (canDelete) {
                        TextButton(onClick = { confirmDelete = true }) {
                            Text(stringResource(R.string.briefing_delete_profile), color = MaterialTheme.colorScheme.error)
                        }
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                    Button(onClick = {
                        briefingViewModel.saveProfile(current())
                        onDismiss()
                    }) { Text(stringResource(R.string.save)) }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.briefing_delete_profile)) },
            text = { Text(stringResource(R.string.briefing_delete_profile_confirm, profile.name)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    briefingViewModel.deleteProfile(profile.id)
                    onDismiss()
                }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    editingTopic?.let { topic ->
        var topicName by remember(topic.id) { mutableStateOf(topic.name) }
        var keywords by remember(topic.id) { mutableStateOf(topic.keywords.joinToString(", ")) }
        AlertDialog(
            onDismissRequest = { editingTopic = null },
            title = { Text(stringResource(R.string.briefing_edit_topic)) },
            text = {
                Column {
                    OutlinedTextField(value = topicName, onValueChange = { topicName = it }, label = { Text(stringResource(R.string.briefing_new_topic)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = keywords, onValueChange = { keywords = it }, label = { Text(stringResource(R.string.briefing_keywords)) }, modifier = Modifier.fillMaxWidth().height(140.dp))
                    Text(stringResource(R.string.briefing_keywords_hint), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    briefingViewModel.updateTopic(profile.id, topic.id, topicName, keywords)
                    editingTopic = null
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { editingTopic = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

@Composable
private fun EditorSectionLabel(text: String) {
    Spacer(modifier = Modifier.height(18.dp))
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(modifier = Modifier.height(8.dp))
}

/** RSS sources shared by all briefings. */
@Composable
private fun SourcesDialog(briefingViewModel: BriefingViewModel, onDismiss: () -> Unit) {
    val state by briefingViewModel.state.collectAsState()
    var newSourceName by remember { mutableStateOf("") }
    var newSourceUrl by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.briefing_sources)) },
        text = {
            LazyColumn(modifier = Modifier.height(520.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(state.config.sources, key = { it.id }) { source ->
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(checked = source.enabled, onCheckedChange = { briefingViewModel.toggleSource(source.id) })
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(source.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text(source.url, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton(onClick = { briefingViewModel.testSource(source.id) }) { Text(stringResource(R.string.briefing_test), fontSize = 12.sp) }
                            IconButton(onClick = { briefingViewModel.deleteSource(source.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete), tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                            }
                        }
                        state.sourceTestResults[source.id]?.let {
                            Text(it, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 56.dp))
                        }
                    }
                }
                item {
                    Column {
                        OutlinedTextField(value = newSourceName, onValueChange = { newSourceName = it }, label = { Text(stringResource(R.string.briefing_source_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = newSourceUrl, onValueChange = { newSourceUrl = it }, label = { Text(stringResource(R.string.briefing_source_url)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Text(stringResource(R.string.briefing_source_hint), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row {
                            TextButton(onClick = {
                                briefingViewModel.addSource(newSourceName, newSourceUrl)
                                newSourceName = ""
                                newSourceUrl = ""
                            }) { Text(stringResource(R.string.briefing_add_source)) }
                            TextButton(onClick = { briefingViewModel.restoreDefaultSources() }) { Text(stringResource(R.string.briefing_restore_sources)) }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.dismiss)) } }
    )
}

private fun frequencyLabel(context: android.content.Context, frequency: BriefingFrequency): String = when (frequency) {
    BriefingFrequency.DAILY -> context.getString(R.string.briefing_freq_daily)
    BriefingFrequency.WEEKDAYS -> context.getString(R.string.briefing_freq_weekdays)
    BriefingFrequency.WEEKLY -> context.getString(R.string.briefing_freq_weekly)
}

private fun sourceModeLabel(context: android.content.Context, mode: SourceMode): String = when (mode) {
    SourceMode.RSS -> context.getString(R.string.briefing_mode_rss)
    SourceMode.WEB -> context.getString(R.string.briefing_mode_web)
    SourceMode.BOTH -> context.getString(R.string.briefing_mode_both)
}

private fun weekdayName(day: Int): String {
    val cal = Calendar.getInstance().apply { set(Calendar.DAY_OF_WEEK, day) }
    return SimpleDateFormat("EEEE", Locale.getDefault()).format(cal.time)
}

private fun editionLabel(context: android.content.Context, millis: Long): String {
    val hour = Calendar.getInstance().apply { timeInMillis = millis }.get(Calendar.HOUR_OF_DAY)
    return when {
        hour < 12 -> context.getString(R.string.briefing_edition_morning)
        hour < 18 -> context.getString(R.string.briefing_edition_afternoon)
        else -> context.getString(R.string.briefing_edition_evening)
    }
}

private fun openUrl(context: android.content.Context, url: String) {
    if (url.isBlank()) return
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: Exception) {
        Toast.makeText(context, url, Toast.LENGTH_SHORT).show()
    }
}

private fun formatDate(context: android.content.Context, millis: Long): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM, context.resources.configuration.locales[0]).format(Date(millis))

private fun formatDateTime(context: android.content.Context, millis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, context.resources.configuration.locales[0]).format(Date(millis))
