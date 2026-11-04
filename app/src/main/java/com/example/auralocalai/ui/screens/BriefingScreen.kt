package com.example.auralocalai.ui.screens

import android.content.Intent
import android.net.Uri
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.auralocalai.R
import com.example.auralocalai.data.briefing.Briefing
import com.example.auralocalai.data.briefing.BriefingSection
import com.example.auralocalai.data.briefing.BriefingTopic
import com.example.auralocalai.data.briefing.NewsItem
import com.example.auralocalai.ui.BriefingViewModel
import com.example.auralocalai.ui.LlmViewModel
import java.net.URLEncoder
import java.text.DateFormat
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
    briefingViewModel: BriefingViewModel = viewModel()
) {
    val state by briefingViewModel.state.collectAsState()
    val llmState by llmViewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showManage by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var isSpeaking by remember { mutableStateOf(false) }
    val modelReady = llmState.modelState is com.example.auralocalai.ui.ModelState.Loaded

    // Text-to-speech for "Read aloud" (offline engine of the device).
    val tts = remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(Unit) {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                engine?.setLanguage(context.resources.configuration.locales[0])
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
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.briefing_title),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.go_back))
                    }
                },
                actions = {
                    IconButton(onClick = { showHistory = true }) {
                        Icon(Icons.Default.History, contentDescription = stringResource(R.string.briefing_history))
                    }
                    IconButton(onClick = { showManage = true }) {
                        Icon(Icons.Default.Tune, contentDescription = stringResource(R.string.briefing_manage))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Date header
            item {
                val now = remember { Date() }
                Column {
                    Text(
                        text = DateFormat.getDateInstance(DateFormat.FULL, context.resources.configuration.locales[0]).format(now)
                            .replaceFirstChar { it.titlecase(Locale.getDefault()) },
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    state.current?.let { b ->
                        Text(
                            text = stringResource(R.string.briefing_created_at, formatDateTime(context, b.createdAt)),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Topic chips
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    state.config.topics.forEach { topic ->
                        FilterChip(
                            selected = topic.enabled,
                            onClick = { briefingViewModel.toggleTopic(topic.id) },
                            label = { Text(topic.name, fontSize = 13.sp) },
                            shape = RoundedCornerShape(20.dp)
                        )
                    }
                    AssistChip(
                        onClick = { showManage = true },
                        label = { Text(stringResource(R.string.briefing_add_topic), fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        shape = RoundedCornerShape(20.dp)
                    )
                }
            }

            // No model banner
            if (!modelReady) {
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.briefing_load_model_first),
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            TextButton(onClick = onOpenModels) {
                                Text(stringResource(R.string.models), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Generate / progress
            item {
                if (state.isRunning) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = state.progress ?: "",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { briefingViewModel.stop() }) {
                                Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(R.string.briefing_stop))
                            }
                        }
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = { briefingViewModel.generate(llmViewModel) },
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.briefing_generate), fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = { briefingViewModel.generate(llmViewModel, forceRefresh = true) },
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier.height(48.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.briefing_refresh), modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            val briefing = state.current
            if (briefing == null) {
                item {
                    Text(
                        text = stringResource(R.string.briefing_empty),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                }
            } else {
                // Share / read aloud
                item {
                    val header = stringResource(R.string.briefing_share_header, formatDateTime(context, briefing.createdAt))
                    val shareTitle = stringResource(R.string.briefing_share)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(
                            onClick = {
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
                            },
                            label = { Text(if (isSpeaking) stringResource(R.string.briefing_stop_reading) else stringResource(R.string.briefing_read_aloud)) },
                            leadingIcon = { Icon(Icons.Default.RecordVoiceOver, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        )
                        AssistChip(
                            onClick = {
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, briefingViewModel.briefingAsText(briefing, header))
                                }
                                context.startActivity(Intent.createChooser(send, shareTitle))
                            },
                            label = { Text(shareTitle) },
                            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        )
                    }
                }

                items(briefing.sections, key = { it.topicId }) { section ->
                    BriefingSectionCard(
                        section = section,
                        isStreaming = state.streamingTopicId == section.topicId,
                        onAskMore = {
                            llmViewModel.startNewChatWith(buildFollowUpMessage(context, section))
                            onOpenChat()
                        },
                        canAskMore = modelReady && !state.isRunning
                    )
                }

                if (state.sourceErrors.isNotEmpty()) {
                    item {
                        val names = state.config.sources.filter { it.id in state.sourceErrors.keys }.joinToString { it.name }
                        Text(
                            text = stringResource(R.string.briefing_sources_failed, names),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                item {
                    Text(
                        text = stringResource(R.string.briefing_disclaimer),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        modifier = Modifier.padding(bottom = 24.dp)
                    )
                }
            }
        }
    }

    if (showManage) {
        BriefingManageDialog(
            briefingViewModel = briefingViewModel,
            llmViewModel = llmViewModel,
            onDismiss = { showManage = false }
        )
    }

    if (showHistory) {
        AlertDialog(
            onDismissRequest = { showHistory = false },
            title = { Text(stringResource(R.string.briefing_history)) },
            text = {
                if (state.history.isEmpty()) {
                    Text(stringResource(R.string.briefing_history_empty))
                } else {
                    LazyColumn(modifier = Modifier.height(360.dp)) {
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
                                        item.sections.filter { it.items.isNotEmpty() }.joinToString { it.topicName },
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
            confirmButton = {
                TextButton(onClick = { showHistory = false }) { Text(stringResource(R.string.dismiss)) }
            }
        )
    }
}

@Composable
private fun BriefingSectionCard(
    section: BriefingSection,
    isStreaming: Boolean,
    onAskMore: () -> Unit,
    canAskMore: Boolean
) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = section.topicName,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                if (isStreaming) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            if (section.items.isEmpty()) {
                Text(
                    text = stringResource(R.string.briefing_no_items_topic),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {

            if (section.summary.isNotBlank()) {
                Text(
                    text = section.summary.trim(),
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Source chips (open the article)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                section.items.forEachIndexed { index, item ->
                    AssistChip(
                        onClick = { openUrl(context, item.link) },
                        label = { Text("${index + 1}. ${item.sourceName}", fontSize = 11.sp, maxLines = 1) },
                        shape = RoundedCornerShape(14.dp),
                        colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            // Headlines
            section.items.forEachIndexed { index, item ->
                HeadlineRow(index = index, item = item, onClick = { openUrl(context, item.link) })
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = onAskMore,
                    enabled = canAskMore,
                    label = { Text(stringResource(R.string.briefing_ask_more), fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Default.Forum, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
                AssistChip(
                    onClick = {
                        val query = URLEncoder.encode("site:ground.news ${section.topicName}", "UTF-8")
                        openUrl(context, "https://www.google.com/search?q=$query")
                    },
                    label = { Text(stringResource(R.string.briefing_ground_news), fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Default.Public, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
            }
            }
        }
    }
}

@Composable
private fun HeadlineRow(index: Int, item: NewsItem, onClick: () -> Unit) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "${index + 1}",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(18.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            val meta = listOfNotNull(item.sourceName, item.publishedMillis?.let { formatDateTime(context, it) }).joinToString(" · ")
            Text(text = meta, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun BriefingManageDialog(
    briefingViewModel: BriefingViewModel,
    llmViewModel: LlmViewModel,
    onDismiss: () -> Unit
) {
    val state by briefingViewModel.state.collectAsState()
    var newTopic by remember { mutableStateOf("") }
    var newSourceName by remember { mutableStateOf("") }
    var newSourceUrl by remember { mutableStateOf("") }
    var editingTopic by remember { mutableStateOf<BriefingTopic?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.briefing_manage)) },
        text = {
            LazyColumn(
                modifier = Modifier.height(520.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // ---- Topics ----
                item { SectionHeader(stringResource(R.string.briefing_topics)) }
                items(state.config.topics, key = { "t_" + it.id }) { topic ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = topic.enabled, onCheckedChange = { briefingViewModel.toggleTopic(topic.id) })
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
                        if (state.translatingTopicId == topic.id) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            IconButton(onClick = { briefingViewModel.translateKeywords(topic.id, llmViewModel) }) {
                                Icon(Icons.Default.Translate, contentDescription = stringResource(R.string.briefing_translate_keywords), modifier = Modifier.size(18.dp))
                            }
                        }
                        IconButton(onClick = { editingTopic = topic }) {
                            Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.rename), modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = { briefingViewModel.deleteTopic(topic.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete), tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                        }
                    }
                }
                item { Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = newTopic,
                            onValueChange = { newTopic = it },
                            label = { Text(stringResource(R.string.briefing_new_topic)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = {
                            briefingViewModel.addTopic(newTopic, llmViewModel)
                            newTopic = ""
                        }) {
                            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.briefing_add_topic))
                        }
                    }
                    Text(stringResource(R.string.briefing_topic_hint), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } }

                // ---- Sources ----
                item { Column {
                    Spacer(modifier = Modifier.height(8.dp))
                    SectionHeader(stringResource(R.string.briefing_sources))
                } }
                items(state.config.sources, key = { "s_" + it.id }) { source ->
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(checked = source.enabled, onCheckedChange = { briefingViewModel.toggleSource(source.id) })
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(source.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text(source.url, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton(onClick = { briefingViewModel.testSource(source.id) }) {
                                Text(stringResource(R.string.briefing_test), fontSize = 12.sp)
                            }
                            IconButton(onClick = { briefingViewModel.deleteSource(source.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete), tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                            }
                        }
                        state.sourceTestResults[source.id]?.let {
                            Text(it, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 56.dp))
                        }
                    }
                }
                item { Column {
                    OutlinedTextField(
                        value = newSourceName,
                        onValueChange = { newSourceName = it },
                        label = { Text(stringResource(R.string.briefing_source_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newSourceUrl,
                        onValueChange = { newSourceUrl = it },
                        label = { Text(stringResource(R.string.briefing_source_url)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(stringResource(R.string.briefing_source_hint), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row {
                        TextButton(onClick = {
                            briefingViewModel.addSource(newSourceName, newSourceUrl)
                            newSourceName = ""
                            newSourceUrl = ""
                        }) { Text(stringResource(R.string.briefing_add_source)) }
                        TextButton(onClick = { briefingViewModel.restoreDefaultSources() }) {
                            Text(stringResource(R.string.briefing_restore_sources))
                        }
                    }
                } }

                // ---- Daily update ----
                item { Column {
                    Spacer(modifier = Modifier.height(8.dp))
                    SectionHeader(stringResource(R.string.briefing_auto_title))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.briefing_auto_desc),
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = state.config.autoUpdateEnabled,
                            onCheckedChange = { briefingViewModel.setAutoUpdate(it, state.config.autoUpdateHour) }
                        )
                    }
                    if (state.config.autoUpdateEnabled) {
                        Text(
                            stringResource(R.string.briefing_auto_time, String.format(Locale.US, "%02d:00", state.config.autoUpdateHour)),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Slider(
                            value = state.config.autoUpdateHour.toFloat(),
                            onValueChange = { briefingViewModel.setAutoUpdate(true, it.toInt()) },
                            valueRange = 0f..23f,
                            steps = 22
                        )
                    }
                } }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dismiss)) }
        }
    )

    editingTopic?.let { topic ->
        var name by remember(topic.id) { mutableStateOf(topic.name) }
        var keywords by remember(topic.id) { mutableStateOf(topic.keywords.joinToString(", ")) }
        AlertDialog(
            onDismissRequest = { editingTopic = null },
            title = { Text(stringResource(R.string.briefing_edit_topic)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(R.string.briefing_new_topic)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = keywords,
                        onValueChange = { keywords = it },
                        label = { Text(stringResource(R.string.briefing_keywords)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                    )
                    Text(stringResource(R.string.briefing_keywords_hint), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    briefingViewModel.updateTopic(topic.id, name, keywords)
                    editingTopic = null
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { editingTopic = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 4.dp)
    )
}

private fun openUrl(context: android.content.Context, url: String) {
    if (url.isBlank()) return
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: Exception) {
        Toast.makeText(context, url, Toast.LENGTH_SHORT).show()
    }
}

private fun formatDateTime(context: android.content.Context, millis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, context.resources.configuration.locales[0])
        .format(Date(millis))

/** Message for "Ask more": the topic, the summary and the articles, so the chat has the context. */
private fun buildFollowUpMessage(context: android.content.Context, section: BriefingSection): String {
    val articles = section.items.joinToString("\n") { "- ${it.title} (${it.sourceName}) ${it.link}" }
    return context.getString(R.string.briefing_follow_up_message, section.topicName, section.summary.trim(), articles)
}
