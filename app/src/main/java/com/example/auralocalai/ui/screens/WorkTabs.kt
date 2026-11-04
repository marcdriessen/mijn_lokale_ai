package com.example.auralocalai.ui.screens

import android.content.ClipData
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.auralocalai.R
import com.example.auralocalai.data.work.ScaleScoring
import com.example.auralocalai.data.work.ScoreBand
import com.example.auralocalai.data.work.Flashcards
import com.example.auralocalai.data.work.DocxIO
import com.example.auralocalai.data.work.DocumentReader
import com.example.auralocalai.ui.LlmViewModel
import com.example.auralocalai.ui.WorkViewModel
import com.example.auralocalai.ui.components.ScriptSelector

@Composable
private fun BusyBar(busy: String?) {
    if (busy != null) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text(busy, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ResultCard(text: String) {
    val context = LocalContext.current
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                AssistChip(onClick = {
                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("text", text))
                    Toast.makeText(context, context.getString(R.string.copied_response), Toast.LENGTH_SHORT).show()
                }, label = { Text(stringResource(R.string.copy_message)) }, leadingIcon = { Icon(Icons.Default.ContentCopy, null, Modifier.size(16.dp)) })
                AssistChip(onClick = {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, text)
                    }
                    context.startActivity(Intent.createChooser(send, context.getString(R.string.briefing_share)))
                }, label = { Text(stringResource(R.string.briefing_share)) }, leadingIcon = { Icon(Icons.Default.Share, null, Modifier.size(16.dp)) })
            }
        }
    }
}

@Composable
private fun WorkMessages(workViewModel: WorkViewModel) {
    val state by workViewModel.state.collectAsState()
    val context = LocalContext.current
    LaunchedEffect(state.message) {
        state.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            workViewModel.clearMessage()
        }
    }
}

/** Psycho-education hand-outs at B1/A2 level, in Dutch, Bulgarian or English. */
@Composable
fun PsychoEducationTab(workViewModel: WorkViewModel, llm: LlmViewModel) {
    val state by workViewModel.state.collectAsState()
    WorkMessages(workViewModel)
    val topics = listOf(
        stringResource(R.string.psy_topic_craving),
        stringResource(R.string.psy_topic_relapse),
        stringResource(R.string.psy_topic_sleep),
        stringResource(R.string.psy_topic_delirium),
        stringResource(R.string.psy_topic_medication),
        stringResource(R.string.psy_topic_stress)
    )
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text(stringResource(R.string.psy_intro), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(10.dp))
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            topics.forEach { t -> AssistChip(onClick = { workViewModel.setPsychoTopic(t) }, label = { Text(t) }) }
        }
        OutlinedTextField(
            value = state.psychoTopic,
            onValueChange = { workViewModel.setPsychoTopic(it) },
            label = { Text(stringResource(R.string.psy_topic_label)) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        )
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("nl" to "Nederlands", "bg" to "Български", "en" to "English").forEach { (c, l) ->
                FilterChip(selected = state.psychoLanguage == c, onClick = { workViewModel.setPsychoLanguage(c) }, label = { Text(l) })
            }
            listOf("B1", "A2").forEach { lvl ->
                FilterChip(selected = state.psychoLevel == lvl, onClick = { workViewModel.setPsychoLevel(lvl) }, label = { Text(lvl) })
            }
        }
        Button(
            onClick = { workViewModel.generatePsychoEducation(llm) },
            enabled = state.psychoTopic.isNotBlank() && state.busy == null,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth().height(46.dp)
        ) { Text(stringResource(R.string.psy_generate)) }
        BusyBar(state.busy)
        if (state.psychoOutput.isNotBlank()) {
            Spacer(modifier = Modifier.height(12.dp))
            ResultCard(state.psychoOutput)
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(stringResource(R.string.work_check_note), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private data class ScaleItem(val label: Int, val values: List<Int>)
private data class ScaleDef(val name: String, val items: List<ScaleItem>, val bands: List<ScoreBand>, val hint: Int)

/** Offline score helper for CIWA-Ar, COWS and AUDIT (adds up; no advice on doses). */
@Composable
fun ScalesTab() {
    val r7 = (0..7).toList()
    val scales = remember {
        listOf(
            ScaleDef(
                "CIWA-Ar",
                listOf(
                    ScaleItem(R.string.ciwa_1, r7), ScaleItem(R.string.ciwa_2, r7), ScaleItem(R.string.ciwa_3, r7), ScaleItem(R.string.ciwa_4, r7),
                    ScaleItem(R.string.ciwa_5, r7), ScaleItem(R.string.ciwa_6, r7), ScaleItem(R.string.ciwa_7, r7), ScaleItem(R.string.ciwa_8, r7),
                    ScaleItem(R.string.ciwa_9, r7), ScaleItem(R.string.ciwa_10, (0..4).toList())
                ),
                ScaleScoring.CIWA_BANDS,
                R.string.scale_ciwa_hint
            ),
            ScaleDef(
                "COWS",
                listOf(
                    ScaleItem(R.string.cows_1, listOf(0, 1, 2, 4)), ScaleItem(R.string.cows_2, listOf(0, 1, 2, 3, 4)), ScaleItem(R.string.cows_3, listOf(0, 1, 3, 5)),
                    ScaleItem(R.string.cows_4, listOf(0, 1, 2, 5)), ScaleItem(R.string.cows_5, listOf(0, 1, 2, 4)), ScaleItem(R.string.cows_6, listOf(0, 1, 2, 4)),
                    ScaleItem(R.string.cows_7, listOf(0, 1, 2, 3, 5)), ScaleItem(R.string.cows_8, listOf(0, 1, 2, 4)), ScaleItem(R.string.cows_9, listOf(0, 1, 2, 4)),
                    ScaleItem(R.string.cows_10, listOf(0, 1, 2, 4)), ScaleItem(R.string.cows_11, listOf(0, 3, 5))
                ),
                ScaleScoring.COWS_BANDS,
                R.string.scale_cows_hint
            ),
            ScaleDef(
                "AUDIT",
                (0 until 10).map { i ->
                    ScaleItem(
                        listOf(R.string.audit_1, R.string.audit_2, R.string.audit_3, R.string.audit_4, R.string.audit_5, R.string.audit_6, R.string.audit_7, R.string.audit_8, R.string.audit_9, R.string.audit_10)[i],
                        if (i >= 8) listOf(0, 2, 4) else (0..4).toList()
                    )
                },
                ScaleScoring.AUDIT_BANDS,
                R.string.scale_audit_hint
            )
        )
    }
    var selected by remember { mutableStateOf(0) }
    val scale = scales[selected]
    val values = remember(selected) { mutableStateListOf<Int>().apply { repeat(scale.items.size) { add(0) } } }
    val total = ScaleScoring.total(values)
    val band = ScaleScoring.band(total, scale.bands)

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            scales.forEachIndexed { i, s -> FilterChip(selected = selected == i, onClick = { selected = i }, label = { Text(s.name) }) }
        }
        Text(stringResource(scale.hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 6.dp))
        scale.items.forEachIndexed { index, item ->
            Text(stringResource(item.label), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp))
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                item.values.forEach { v ->
                    FilterChip(selected = values[index] == v, onClick = { values[index] = v }, label = { Text(v.toString()) })
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(stringResource(R.string.scale_total, total), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.background)
                band?.let {
                    Text(bandLabel(it.key), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.background.copy(alpha = 0.85f))
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(stringResource(R.string.scale_disclaimer), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = { for (i in values.indices) values[i] = 0 }) { Text(stringResource(R.string.reset_defaults)) }
    }
}

@Composable
private fun bandLabel(key: String): String = when (key) {
    "none" -> stringResource(R.string.band_none)
    "mild" -> stringResource(R.string.band_mild)
    "moderate" -> stringResource(R.string.band_moderate)
    "moderately_severe" -> stringResource(R.string.band_moderately_severe)
    "severe" -> stringResource(R.string.band_severe)
    "low" -> stringResource(R.string.band_low)
    "hazardous" -> stringResource(R.string.band_hazardous)
    "harmful" -> stringResource(R.string.band_harmful)
    "dependence" -> stringResource(R.string.band_dependence)
    else -> key
}

/** SBAR/SOAP structuring of dictated, scanned or typed notes; personal data is removed first. */
@Composable
fun ReportTab(workViewModel: WorkViewModel, llm: LlmViewModel) {
    val state by workViewModel.state.collectAsState()
    val context = LocalContext.current
    WorkMessages(workViewModel)
    val dictate = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val text = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!text.isNullOrBlank()) workViewModel.addReportText(text, llm)
    }
    val scan = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) workViewModel.scanReport(context, bitmap, llm)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) workViewModel.importReportFile(context, uri, llm)
    }
    val saveDocx = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(DocxIO.MIME)) { uri ->
        if (uri != null) workViewModel.saveReportAsDocx(context, uri)
    }
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.report_privacy), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(12.dp))
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, context.resources.configuration.locales[0].toLanguageTag())
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                }
                try {
                    dictate.launch(intent)
                } catch (_: Exception) {
                    Toast.makeText(context, context.getString(R.string.speech_not_available), Toast.LENGTH_SHORT).show()
                }
            }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(20.dp)) {
                Icon(Icons.Default.Mic, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.report_dictate))
            }
            OutlinedButton(onClick = { scan.launch(null) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(20.dp)) {
                Icon(Icons.Default.CameraAlt, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.report_scan))
            }
            OutlinedButton(onClick = { importLauncher.launch(DocumentReader.PICKER_TYPES) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(20.dp)) {
                Icon(Icons.Default.Description, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.report_import))
            }
        }
        ScriptSelector()
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedTextField(
            value = state.reportNotes,
            onValueChange = { workViewModel.setReportNotes(it) },
            placeholder = { Text(stringResource(R.string.report_hint)) },
            minLines = 5,
            maxLines = 14,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        )
        Row(modifier = Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("SOEP", "SBAR", "SOAP").forEach { f ->
                FilterChip(selected = state.reportFormat == f, onClick = { workViewModel.setReportFormat(f) }, label = { Text(f) })
            }
        }
        Button(
            onClick = { workViewModel.generateReport(llm) },
            enabled = state.reportNotes.isNotBlank() && state.busy == null,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth().height(46.dp)
        ) { Text(stringResource(R.string.report_generate, state.reportFormat)) }
        BusyBar(state.busy)
        if (state.reportOutput.isNotBlank()) {
            Spacer(modifier = Modifier.height(12.dp))
            ResultCard(state.reportOutput)
            Button(
                onClick = { saveDocx.launch(workViewModel.suggestedReportFileName()) },
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) {
                Icon(Icons.Default.Description, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.report_save_docx))
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(stringResource(R.string.work_check_note), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Flashcards made from library documents or pasted text, studied with a simple Leitner schedule. */
@Composable
fun FlashcardsTab(workViewModel: WorkViewModel, llm: LlmViewModel) {
    val state by workViewModel.state.collectAsState()
    WorkMessages(workViewModel)
    var studyingDeckId by remember { mutableStateOf<String?>(null) }
    var pasted by remember { mutableStateOf("") }
    var deckName by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { workViewModel.reloadLibrary() }

    val deck = state.decks.firstOrNull { it.id == studyingDeckId }
    if (deck != null) {
        val due = Flashcards.dueCards(deck, System.currentTimeMillis())
        var revealed by remember(deck.id, due.firstOrNull()) { mutableStateOf(false) }
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(deck.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = { studyingDeckId = null }) { Text(stringResource(R.string.dismiss)) }
            }
            Text(stringResource(R.string.cards_due, due.size, deck.cards.size), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(16.dp))
            val index = due.firstOrNull()
            if (index == null) {
                Text(stringResource(R.string.cards_all_done), style = MaterialTheme.typography.bodyLarge)
            } else {
                val card = deck.cards[index]
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth().height(260.dp).clickable { revealed = true }
                ) {
                    Column(modifier = Modifier.padding(22.dp), verticalArrangement = Arrangement.Center) {
                        Text(card.question, style = MaterialTheme.typography.titleLarge)
                        Spacer(modifier = Modifier.height(16.dp))
                        if (revealed) Text(card.answer, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                        else Text(stringResource(R.string.cards_tap_reveal), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (revealed) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 14.dp)) {
                        OutlinedButton(onClick = { workViewModel.review(deck.id, index, known = false) }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.cards_again)) }
                        Button(onClick = { workViewModel.review(deck.id, index, known = true) }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.cards_known)) }
                    }
                }
            }
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text(stringResource(R.string.cards_intro), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(10.dp))
        state.decks.forEach { d ->
            val due = Flashcards.dueCards(d, System.currentTimeMillis()).size
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { studyingDeckId = d.id }
            ) {
                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(d.name, style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.cards_due, due, d.cards.size), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { workViewModel.deleteDeck(d.id) }) { Icon(Icons.Default.Delete, stringResource(R.string.delete), tint = MaterialTheme.colorScheme.error) }
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(stringResource(R.string.cards_new), style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(value = deckName, onValueChange = { deckName = it }, label = { Text(stringResource(R.string.cards_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        if (state.library.isNotEmpty()) {
            Text(stringResource(R.string.cards_from_library), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
            state.library.forEach { doc ->
                TextButton(onClick = {
                    workViewModel.createDeck(deckName.ifBlank { doc.name }, doc.passages.joinToString("\n\n"), llm)
                }, enabled = state.busy == null) { Text(doc.name) }
            }
        }
        OutlinedTextField(
            value = pasted,
            onValueChange = { pasted = it },
            placeholder = { Text(stringResource(R.string.cards_paste)) },
            minLines = 4,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )
        Button(
            onClick = { workViewModel.createDeck(deckName, pasted, llm) },
            enabled = pasted.isNotBlank() && state.busy == null,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        ) { Text(stringResource(R.string.cards_make)) }
        BusyBar(state.busy)
    }
}
