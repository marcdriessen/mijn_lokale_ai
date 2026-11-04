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
    var isSpeaking by remember { mutableStateOf(false) }
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

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(stringResource(R.string.briefing_brand), style = MaterialTheme.typography.titleLarge) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.go_back))
                        }
                    },
                    actions = {
                        IconButton(onClick = { showHistory = true }) {
                            Icon(Icons.Default.History, contentDescription = stringResource(R.string.briefing_history))
                        }
                        IconButton(onClick = { showSources = true }) {
                            Icon(Icons.Default.RssFeed, contentDescription = stringResource(R.string.briefing_sources))
                        }
                        if (profile != null) {
                            IconButton(onClick = { editingProfileId = profile.id }) {
                                Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.briefing_edit_profile))
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Profile switcher
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    state.config.profiles.forEach { p ->
                        FilterChip(
                            selected = p.id == profile?.id,
                            onClick = { briefingViewModel.selectProfile(p.id) },
                            label = { Text(p.name, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingIcon = if (p.scheduleEnabled) {
                                { Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(14.dp)) }
                            } else null,
                            shape = RoundedCornerShape(20.dp)
                        )
                    }
                    FilterChip(
                        selected = false,
                        onClick = { editingProfileId = briefingViewModel.addProfile() },
                        label = { Text(stringResource(R.string.briefing_new_profile), fontSize = 12.sp) },
                        leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp)) },
                        shape = RoundedCornerShape(20.dp)
                    )
                }
            }

            if (profile == null) {
                item { Text(stringResource(R.string.briefing_empty), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                return@LazyColumn
            }

            val briefing = state.current
            val shownAt = briefing?.createdAt ?: System.currentTimeMillis()

            // Editorial masthead
            item {
                Column {
                    Text(
                        text = (DateFormat.getDateInstance(DateFormat.FULL, locale).format(Date(shownAt)) + " · " + editionLabel(context, shownAt))
                            .uppercase(locale),
                        style = MaterialTheme.typography.labelMedium,
                        letterSpacing = 1.2.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = profile.name,
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    val intro = profile.instructions.substringBefore(". ").trim().trimEnd('.')
                    if (intro.isNotBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "$intro.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        profile.topics.forEach { topic ->
                            TagChip(
                                text = topic.name,
                                active = topic.enabled,
                                onClick = { briefingViewModel.toggleTopic(profile.id, topic.id) }
                            )
                        }
                    }
                    if (profile.scheduleEnabled) {
                        Spacer(modifier = Modifier.height(8.dp))
                        val next = BriefingScheduler.nextRunAt(profile, Calendar.getInstance())
                        Text(
                            text = stringResource(
                                R.string.briefing_next_run,
                                DateUtils.getRelativeTimeSpanString(next, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            if (!modelReady) {
                item {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.briefing_load_model_first),
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = onOpenModels) { Text(stringResource(R.string.models), fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }

            // Actions / progress
            item {
                if (state.isRunning) {
                    Column {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(state.progress ?: "", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                            TextButton(onClick = { briefingViewModel.stop() }) {
                                Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(R.string.briefing_stop))
                            }
                        }
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = { briefingViewModel.generate(llmViewModel) },
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.briefing_generate), fontWeight = FontWeight.SemiBold)
                        }
                        RoundIconButton(Icons.Default.Refresh, stringResource(R.string.briefing_refresh)) {
                            briefingViewModel.generate(llmViewModel, forceRefresh = true)
                        }
                        if (briefing != null) {
                            RoundIconButton(
                                if (isSpeaking) Icons.Default.Stop else Icons.Default.RecordVoiceOver,
                                if (isSpeaking) stringResource(R.string.briefing_stop_reading) else stringResource(R.string.briefing_read_aloud)
                            ) {
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
                            val header = stringResource(R.string.briefing_share_header, formatDateTime(context, briefing.createdAt))
                            val relevanceLabel = stringResource(R.string.briefing_relevance_label)
                            val shareTitle = stringResource(R.string.briefing_share)
                            RoundIconButton(Icons.Default.Share, shareTitle) {
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, briefingViewModel.briefingAsText(briefing, header, relevanceLabel))
                                }
                                context.startActivity(Intent.createChooser(send, shareTitle))
                            }
                        }
                    }
                }
            }

            if (briefing == null) {
                item {
                    Text(
                        stringResource(R.string.briefing_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                }
            } else {
                val articles = briefingViewModel.displayArticles(briefing)
                item {
                    Column {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                stringResource(R.string.briefing_selected_for_you),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                stringResource(R.string.briefing_article_count, articles.size),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
                itemsIndexed(articles, key = { index, a -> "${briefing.id}_$index" }) { index, article ->
                    EditorialArticleCard(
                        article = article,
                        isWriting = state.streamingArticleIndex == index,
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
                            onOpenChat()
                        }
                    )
                }
                if (state.sourceErrors.isNotEmpty()) {
                    item {
                        val names = state.sourceErrors.keys.joinToString { id -> state.config.sources.firstOrNull { it.id == id }?.name ?: id }
                        Text(stringResource(R.string.briefing_sources_failed, names), fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                    }
                }
                item {
                    Column {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            stringResource(R.string.briefing_disclaimer),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 24.dp)
                        )
                    }
                }
            }
        }
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

@Composable
private fun EditorialArticleCard(
    article: BriefingArticle,
    isWriting: Boolean,
    canAskMore: Boolean,
    onAskMore: () -> Unit
) {
    val context = LocalContext.current
    val item = article.item
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        // Image with topic label
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .background(MaterialTheme.colorScheme.primaryContainer)
        ) {
            if (!item.imageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = item.imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    text = item.sourceName,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.55f),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(16.dp),
                    maxLines = 2
                )
            }
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
            ) {
                Text(
                    text = article.topicName.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        Column(modifier = Modifier.padding(18.dp)) {
            val meta = listOfNotNull(item.sourceName, item.publishedMillis?.let { formatDate(context, it) }).joinToString(" · ")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = meta.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 0.6.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                if (isWriting) CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = article.title.ifBlank { item.title },
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            val summary = article.summary.ifBlank { item.description.take(280) }
            if (summary.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(summary.trim(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f))
            }
            if (article.relevance.isNotBlank()) {
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                ) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.primary)
                    )
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            stringResource(R.string.briefing_relevance_label).uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(article.relevance.trim(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { openUrl(context, item.link) }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.briefing_read_at, item.sourceName),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(Icons.Default.NorthEast, contentDescription = null, modifier = Modifier.size(16.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onAskMore, enabled = canAskMore, contentPadding = PaddingValues(horizontal = 0.dp)) {
                    Text(stringResource(R.string.briefing_ask_more), style = MaterialTheme.typography.labelMedium)
                }
                Spacer(modifier = Modifier.width(12.dp))
                TextButton(
                    onClick = {
                        val query = URLEncoder.encode("site:ground.news ${article.title.ifBlank { item.title }}", "UTF-8")
                        openUrl(context, "https://www.google.com/search?q=$query")
                    },
                    contentPadding = PaddingValues(horizontal = 0.dp)
                ) {
                    Text(stringResource(R.string.briefing_ground_news), style = MaterialTheme.typography.labelMedium)
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
        maxArticles = maxArticles
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
