package com.example.auralocalai.ui.screens

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import com.example.auralocalai.data.medical.MedicalAnswer
import com.example.auralocalai.data.medical.MedicalSource
import com.example.auralocalai.data.medical.PassageIndex
import com.example.auralocalai.data.medical.SourceKind
import com.example.auralocalai.ui.LlmViewModel
import com.example.auralocalai.ui.MedicalViewModel
import com.example.auralocalai.ui.ModelState
import java.net.URLEncoder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicalScreen(
    llmViewModel: LlmViewModel,
    onBack: () -> Unit,
    onOpenChat: () -> Unit,
    onOpenModels: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    medicalViewModel: MedicalViewModel = viewModel()
) {
    val state by medicalViewModel.state.collectAsState()
    val llmState by llmViewModel.uiState.collectAsState()
    val context = LocalContext.current
    var question by remember { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var showLibrary by remember { mutableStateOf(false) }
    val modelReady = llmState.modelState is ModelState.Loaded

    LaunchedEffect(state.message) {
        state.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            medicalViewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            com.example.auralocalai.ui.components.AppHeader(
                title = stringResource(R.string.medical_title_short),
                subtitle = stringResource(R.string.medical_subtitle),
                onBack = onBack
            ) {
                com.example.auralocalai.ui.components.CircleIconButton(Icons.Default.LibraryBooks, stringResource(R.string.medical_library)) { showLibrary = true }
                com.example.auralocalai.ui.components.CircleIconButton(Icons.Default.History, stringResource(R.string.medical_history)) { showHistory = true }
                com.example.auralocalai.ui.components.CircleIconButton(Icons.Default.Tune, stringResource(R.string.medical_sources_settings)) { showSettings = true }
            }
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
            // Safety notice
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.medical_warning),
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            if (!modelReady) {
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.medical_load_model_first),
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = onOpenModels) { Text(stringResource(R.string.models), fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }

            // Question input
            item {
                Column {
                    OutlinedTextField(
                        value = question,
                        onValueChange = { question = it },
                        placeholder = { Text(stringResource(R.string.medical_question_hint)) },
                        shape = RoundedCornerShape(24.dp),
                        minLines = 2,
                        maxLines = 6,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = state.config.useLibrary,
                            onClick = { medicalViewModel.setUseLibrary(!state.config.useLibrary) },
                            label = { Text(stringResource(R.string.medical_use_library, state.library.size), fontSize = 12.sp) }
                        )
                        FilterChip(
                            selected = state.config.useSites,
                            onClick = { medicalViewModel.setUseSites(!state.config.useSites) },
                            label = { Text(stringResource(R.string.medical_use_sites), fontSize = 12.sp) }
                        )
                        FilterChip(
                            selected = state.config.usePubMed,
                            onClick = { medicalViewModel.setUsePubMed(!state.config.usePubMed) },
                            label = { Text("PubMed", fontSize = 12.sp) }
                        )
                    }
                    Text(
                        text = stringResource(R.string.medical_brave_usage, state.braveUsed, state.config.monthlyBraveLimit),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (state.isRunning) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(state.progress ?: "", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                            TextButton(onClick = { medicalViewModel.stop() }) {
                                Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(R.string.briefing_stop))
                            }
                        }
                    } else {
                        Button(
                            onClick = { medicalViewModel.ask(question, llmViewModel) },
                            enabled = question.isNotBlank(),
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.medical_ask), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            state.notes.forEach { note ->
                item {
                    Text(note, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                }
            }

            val current = state.current
            if (current != null) {
                item {
                    AnswerCard(
                        answer = current,
                        isStreaming = state.isRunning,
                        canAskMore = modelReady && !state.isRunning,
                        onAskMore = {
                            llmViewModel.startNewChatWith(
                                context.getString(R.string.medical_follow_up_message, current.question, current.answer.trim())
                            )
                            onOpenChat()
                        }
                    )
                }
                if (current.sources.isNotEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.medical_sources_title),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    itemsIndexed(current.sources) { index, source ->
                        SourceRow(index = index, source = source)
                    }
                }
            }

            item {
                Text(
                    text = stringResource(R.string.medical_disclaimer),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    modifier = Modifier.padding(bottom = 24.dp)
                )
            }
        }
    }

    if (showSettings) {
        MedicalSettingsDialog(medicalViewModel, llmViewModel.isBraveConfigured, onOpenSettings) { showSettings = false }
    }
    if (showLibrary) {
        MedicalLibraryDialog(medicalViewModel) { showLibrary = false }
    }
    if (showHistory) {
        AlertDialog(
            onDismissRequest = { showHistory = false },
            title = { Text(stringResource(R.string.medical_history)) },
            text = {
                if (state.history.isEmpty()) {
                    Text(stringResource(R.string.medical_history_empty))
                } else {
                    LazyColumn(modifier = Modifier.height(380.dp)) {
                        items(state.history, key = { it.id }) { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        medicalViewModel.showAnswer(item.id)
                                        question = item.question
                                        showHistory = false
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(item.question, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                IconButton(onClick = { medicalViewModel.deleteAnswer(item.id) }) {
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
private fun AnswerCard(
    answer: MedicalAnswer,
    isStreaming: Boolean,
    canAskMore: Boolean,
    onAskMore: () -> Unit
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
                Text(answer.question, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (isStreaming) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            }
            Spacer(modifier = Modifier.height(10.dp))
            if (answer.answer.isNotBlank()) {
                Text(answer.answer.trim(), fontSize = 14.sp, lineHeight = 21.sp, color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(12.dp))
            }
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AssistChip(
                    onClick = {
                        val terms = PassageIndex.terms(answer.question).take(5).joinToString(" ")
                        val query = URLEncoder.encode("site:farmacotherapeutischkompas.nl $terms", "UTF-8")
                        openLink(context, "https://www.google.com/search?q=$query")
                    },
                    label = { Text(stringResource(R.string.medical_kompas), fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Default.Medication, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
                AssistChip(
                    onClick = onAskMore,
                    enabled = canAskMore && answer.answer.isNotBlank(),
                    label = { Text(stringResource(R.string.briefing_ask_more), fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Default.Forum, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
                AssistChip(
                    onClick = {
                        val text = buildString {
                            append(answer.question).append("\n\n").append(answer.answer.trim()).append("\n\n")
                            answer.sources.forEachIndexed { i, s -> append("[${i + 1}] ${s.title} (${s.origin}) ${s.url}\n") }
                        }
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("answer", text))
                        Toast.makeText(context, context.getString(R.string.copied_response), Toast.LENGTH_SHORT).show()
                    },
                    label = { Text(stringResource(R.string.copy_message), fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
            }
        }
    }
}

@Composable
private fun SourceRow(index: Int, source: MedicalSource) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    val kindLabel = when (source.kind) {
        SourceKind.PUBMED.name -> "PubMed"
        SourceKind.LIBRARY.name -> stringResource(R.string.medical_origin_library)
        else -> stringResource(R.string.medical_origin_web)
    }
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text("[${index + 1}]", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(30.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(source.title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = if (expanded) 6 else 2, overflow = TextOverflow.Ellipsis)
                    Text("$kindLabel · ${source.origin}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (expanded) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(source.snippet.take(1_500), fontSize = 12.sp, lineHeight = 17.sp)
                if (source.url.isNotBlank()) {
                    TextButton(onClick = { openLink(context, source.url) }) { Text(stringResource(R.string.medical_open_source)) }
                }
            }
        }
    }
}

@Composable
private fun MedicalSettingsDialog(
    medicalViewModel: MedicalViewModel,
    braveConfigured: Boolean,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    val state by medicalViewModel.state.collectAsState()
    var newSite by remember { mutableStateOf("") }
    var limitText by remember { mutableStateOf(state.config.monthlyBraveLimit.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.medical_sources_settings)) },
        text = {
            LazyColumn(modifier = Modifier.height(480.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                item {
                    Column {
                        Text(
                            if (braveConfigured) stringResource(R.string.medical_brave_ok) else stringResource(R.string.medical_brave_missing),
                            fontSize = 12.sp,
                            color = if (braveConfigured) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                        if (!braveConfigured) {
                            TextButton(onClick = {
                                onDismiss()
                                onOpenSettings()
                            }) { Text(stringResource(R.string.settings)) }
                        }
                    }
                }
                item { Text(stringResource(R.string.medical_trusted_sites), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp) }
                items(state.config.sites, key = { it.domain }) { site ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = site.enabled, onCheckedChange = { medicalViewModel.toggleSite(site.domain) })
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(site.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text(site.domain, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { medicalViewModel.deleteSite(site.domain) }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete), tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                        }
                    }
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = newSite,
                            onValueChange = { newSite = it },
                            label = { Text(stringResource(R.string.medical_add_site_hint)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = {
                            medicalViewModel.addSite(newSite)
                            newSite = ""
                        }) { Icon(Icons.Default.Add, contentDescription = null) }
                    }
                }
                item {
                    Column {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = limitText,
                            onValueChange = { text ->
                                limitText = text.filter { it.isDigit() }.take(6)
                                limitText.toIntOrNull()?.let { medicalViewModel.setMonthlyLimit(it) }
                            },
                            label = { Text(stringResource(R.string.medical_limit_label)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(stringResource(R.string.medical_limit_hint), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.dismiss)) } }
    )
}

@Composable
private fun MedicalLibraryDialog(medicalViewModel: MedicalViewModel, onDismiss: () -> Unit) {
    val state by medicalViewModel.state.collectAsState()
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) medicalViewModel.importDocument(uri, context)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.medical_library)) },
        text = {
            Column {
                Text(stringResource(R.string.medical_library_desc), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(8.dp))
                state.importing?.let {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.medical_importing, it), fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                if (state.library.isEmpty()) {
                    Text(stringResource(R.string.medical_library_empty), fontSize = 13.sp)
                } else {
                    LazyColumn(modifier = Modifier.height(280.dp)) {
                        items(state.library, key = { it.id }) { doc ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(doc.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text(stringResource(R.string.medical_passages, doc.passages.size), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton(onClick = { medicalViewModel.deleteDocument(doc.id) }) {
                                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete), tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { picker.launch("*/*") }, enabled = state.importing == null) {
                Text(stringResource(R.string.medical_add_document))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.dismiss)) } }
    )
}

private fun openLink(context: android.content.Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: Exception) {
        Toast.makeText(context, url, Toast.LENGTH_SHORT).show()
    }
}
