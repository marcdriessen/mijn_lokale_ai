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
import com.example.auralocalai.data.work.Quiz
import androidx.compose.foundation.layout.heightIn
import com.example.auralocalai.data.work.DocxIO
import com.example.auralocalai.data.work.DocumentReader
import com.example.auralocalai.ui.LlmViewModel
import com.example.auralocalai.ui.WorkViewModel
import com.example.auralocalai.ui.components.ScriptSelector
import com.example.auralocalai.ui.components.DictationButton
import com.example.auralocalai.ui.components.DictationStatus
import com.example.auralocalai.ui.components.rememberDictation
import com.example.auralocalai.ui.components.rememberPhotoCapture
import com.example.auralocalai.ui.components.rememberSpeaker
import androidx.compose.material.icons.automirrored.filled.VolumeUp

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
private fun ResultCard(text: String, languageCode: String) {
    val context = LocalContext.current
    val speaker = rememberSpeaker()
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
                AssistChip(
                    onClick = { speaker.toggle(text, languageCode) },
                    label = { Text(stringResource(if (speaker.isSpeaking) R.string.dictation_stop else R.string.briefing_read_aloud)) },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.VolumeUp, null, Modifier.size(16.dp)) }
                )
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
            ResultCard(state.psychoOutput, state.psychoLanguage)
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(stringResource(R.string.work_check_note), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private data class ScaleItem(val label: Int, val values: List<Int>, val key: String = "")
private data class ScaleDef(val name: String, val items: List<ScaleItem>, val bands: List<ScoreBand>, val hint: Int)

/** Offline score helper for CIWA-Ar, COWS and AUDIT (adds up; no advice on doses). */
@Composable
fun ScalesTab() {
    val r7 = (0..7).toList()
    val scales = remember {
        listOf(
            ScaleDef(
                "CIWA-Ar",
                (1..10).map { i ->
                    ScaleItem(
                        listOf(R.string.ciwa_1, R.string.ciwa_2, R.string.ciwa_3, R.string.ciwa_4, R.string.ciwa_5, R.string.ciwa_6, R.string.ciwa_7, R.string.ciwa_8, R.string.ciwa_9, R.string.ciwa_10)[i - 1],
                        if (i == 10) (0..4).toList() else r7,
                        "ciwa_$i"
                    )
                },
                ScaleScoring.CIWA_BANDS,
                R.string.scale_ciwa_hint
            ),
            ScaleDef(
                "COWS",
                listOf(
                    ScaleItem(R.string.cows_1, listOf(0, 1, 2, 4), "cows_1"), ScaleItem(R.string.cows_2, listOf(0, 1, 2, 3, 4), "cows_2"),
                    ScaleItem(R.string.cows_3, listOf(0, 1, 3, 5), "cows_3"), ScaleItem(R.string.cows_4, listOf(0, 1, 2, 5), "cows_4"),
                    ScaleItem(R.string.cows_5, listOf(0, 1, 2, 4), "cows_5"), ScaleItem(R.string.cows_6, listOf(0, 1, 2, 4), "cows_6"),
                    ScaleItem(R.string.cows_7, listOf(0, 1, 2, 3, 5), "cows_7"), ScaleItem(R.string.cows_8, listOf(0, 1, 2, 4), "cows_8"),
                    ScaleItem(R.string.cows_9, listOf(0, 1, 2, 4), "cows_9"), ScaleItem(R.string.cows_10, listOf(0, 1, 2, 4), "cows_10"),
                    ScaleItem(R.string.cows_11, listOf(0, 3, 5), "cows_11")
                ),
                ScaleScoring.COWS_BANDS,
                R.string.scale_cows_hint
            ),
            ScaleDef(
                "AUDIT",
                (0 until 10).map { i ->
                    ScaleItem(
                        listOf(R.string.audit_1, R.string.audit_2, R.string.audit_3, R.string.audit_4, R.string.audit_5, R.string.audit_6, R.string.audit_7, R.string.audit_8, R.string.audit_9, R.string.audit_10)[i],
                        if (i >= 8) listOf(0, 2, 4) else (0..4).toList(),
                        when (i) { 0 -> "audit_q1"; 1 -> "audit_q2"; 8, 9 -> "audit_yn"; else -> "audit_freq" }
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
            val anchors = ScaleAnchors.ANCHORS[item.key].orEmpty()
            val how = ScaleAnchors.HOW[item.key]
            var expanded by remember(selected, index) { mutableStateOf(false) }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                Text(stringResource(item.label), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (anchors.isNotEmpty()) {
                    TextButton(onClick = { expanded = !expanded }) {
                        Text(stringResource(if (expanded) R.string.scale_hide_help else R.string.scale_show_help), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                item.values.forEach { v ->
                    FilterChip(selected = values[index] == v, onClick = { values[index] = v }, label = { Text(v.toString()) })
                }
            }
            if (anchors.isNotEmpty()) {
                // Short description of the chosen score, always visible.
                val chosen = anchors.firstOrNull { it.first == values[index] }
                Text(
                    if (chosen != null) "${chosen.first} – ${stringResource(chosen.second)}" else "${values[index]} – ${stringResource(R.string.scale_between)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (expanded) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        how?.let { Text(stringResource(it), style = MaterialTheme.typography.bodySmall, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic) }
                        anchors.forEach { (v, res) ->
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable { values[index] = v }.padding(vertical = 2.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    v.toString(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (values[index] == v) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.width(24.dp)
                                )
                                Text(stringResource(res), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            }
                        }
                    }
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
    // Dictated text is collected while listening and anonymized once when the user stops.
    var dictatedBuffer by remember { mutableStateOf("") }
    val dictation = rememberDictation { heard -> dictatedBuffer = (dictatedBuffer + " " + heard).trim() }
    LaunchedEffect(dictation.isListening) {
        if (!dictation.isListening && dictatedBuffer.isNotBlank()) {
            workViewModel.addReportText(dictatedBuffer, llm)
            dictatedBuffer = ""
        }
    }
    val scan = rememberPhotoCapture { bitmap -> workViewModel.scanReport(context, bitmap, llm) }
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
            DictationButton(dictation, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = { scan() }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(20.dp)) {
                Icon(Icons.Default.CameraAlt, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.report_scan))
            }
            OutlinedButton(onClick = { importLauncher.launch(DocumentReader.PICKER_TYPES) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(20.dp)) {
                Icon(Icons.Default.Description, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.report_import))
            }
        }
        DictationStatus(dictation)
        if (dictatedBuffer.isNotBlank()) {
            Text(dictatedBuffer, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 6.dp))
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
            ResultCard(state.reportOutput, com.example.auralocalai.AppLocale.effectiveLanguage(context))
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

/** Quick topics for decks from the knowledge base (the knowledge base is in Dutch). */
private val KNOWLEDGE_TOPICS = listOf(
    "alcoholontwenning", "delier", "Korsakov", "methadon", "buprenorfine", "opioïden", "benzodiazepines", "GHB",
    "cannabis", "cocaïne", "amfetamine", "craving", "terugval", "motiverende gespreksvoering", "psychose",
    "depressie", "suïcidaliteit", "ADHD", "PTSS", "persoonlijkheidsstoornis", "lithium", "clozapine"
)

/**
 * Flashcards made from the knowledge base, library documents or pasted text, studied with a simple
 * Leitner schedule, plus a multiple-choice quiz per deck.
 */
@Composable
fun FlashcardsTab(workViewModel: WorkViewModel, llm: LlmViewModel) {
    val state by workViewModel.state.collectAsState()
    WorkMessages(workViewModel)
    var studyingDeckId by remember { mutableStateOf<String?>(null) }
    var quizDeckId by remember { mutableStateOf<String?>(null) }
    var pasted by remember { mutableStateOf("") }
    var deckName by remember { mutableStateOf("") }
    var topic by remember { mutableStateOf("") }
    var cardCount by remember { mutableStateOf(20) }
    LaunchedEffect(Unit) {
        workViewModel.reloadLibrary()
        workViewModel.refreshKnowledge()
    }

    state.decks.firstOrNull { it.id == quizDeckId }?.let { quizDeck ->
        QuizView(quizDeck, workViewModel) { quizDeckId = null }
        return
    }

    val deck = state.decks.firstOrNull { it.id == studyingDeckId }
    if (deck != null) {
        val due = Flashcards.dueCards(deck, System.currentTimeMillis())
        var revealed by remember(deck.id, due.firstOrNull()) { mutableStateOf(false) }
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(deck.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = { studyingDeckId = null }) { Text(stringResource(R.string.dismiss)) }
            }
            Text(stringResource(R.string.cards_due, due.size, deck.cards.size), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(16.dp))
            val index = due.firstOrNull()
            if (index == null) {
                Text(stringResource(R.string.cards_all_done), style = MaterialTheme.typography.bodyLarge)
                if (deck.cards.size >= Quiz.OPTIONS) {
                    Button(onClick = { studyingDeckId = null; quizDeckId = deck.id }, modifier = Modifier.padding(top = 12.dp)) {
                        Text(stringResource(R.string.quiz_start))
                    }
                }
            } else {
                val card = deck.cards[index]
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 260.dp).clickable { revealed = true }
                ) {
                    Column(modifier = Modifier.padding(22.dp), verticalArrangement = Arrangement.Center) {
                        Text(card.question, style = MaterialTheme.typography.titleLarge)
                        Spacer(modifier = Modifier.height(16.dp))
                        if (revealed) {
                            Text(card.answer, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                            if (card.source.isNotBlank()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(stringResource(R.string.cards_source, card.source), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            Text(stringResource(R.string.cards_tap_reveal), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
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
                Row(modifier = Modifier.padding(start = 14.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(d.name, style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.cards_due, due, d.cards.size), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (d.cards.size >= Quiz.OPTIONS) {
                        TextButton(onClick = { quizDeckId = d.id }) { Text(stringResource(R.string.quiz_start)) }
                    }
                    IconButton(onClick = { workViewModel.deleteDeck(d.id) }) { Icon(Icons.Default.Delete, stringResource(R.string.delete), tint = MaterialTheme.colorScheme.error) }
                }
            }
        }

        // From the offline knowledge base: no model needed.
        Spacer(modifier = Modifier.height(12.dp))
        Text(stringResource(R.string.cards_knowledge_title), style = MaterialTheme.typography.titleMedium)
        if (state.knowledgeCount == 0) {
            Text(stringResource(R.string.cards_knowledge_missing), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text(stringResource(R.string.cards_knowledge_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = topic,
                onValueChange = { topic = it },
                label = { Text(stringResource(R.string.cards_knowledge_topic)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
            )
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                KNOWLEDGE_TOPICS.forEach { t ->
                    AssistChip(onClick = { topic = t }, label = { Text(t) })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(10, 20, 50).forEach { n ->
                    FilterChip(selected = cardCount == n, onClick = { cardCount = n }, label = { Text(stringResource(R.string.cards_knowledge_count, n)) })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { workViewModel.createKnowledgeDeck(topic, cardCount) },
                    enabled = topic.isNotBlank() && state.busy == null,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.cards_knowledge_make)) }
                OutlinedButton(
                    onClick = { workViewModel.createKnowledgeDeck("", cardCount) },
                    enabled = state.busy == null,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.cards_knowledge_random)) }
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

/** Multiple-choice quiz over one deck; each answer also counts as a Leitner review of that card. */
@Composable
private fun QuizView(deck: com.example.auralocalai.data.work.FlashcardDeck, workViewModel: WorkViewModel, onClose: () -> Unit) {
    var round by remember { mutableStateOf(0) }
    val questions = remember(deck.id, round) { Quiz.build(deck.cards, count = 10) }
    var position by remember(deck.id, round) { mutableStateOf(0) }
    var chosen by remember(deck.id, round) { mutableStateOf<Int?>(null) }
    var score by remember(deck.id, round) { mutableStateOf(0) }
    var showFull by remember(deck.id, round, position) { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.quiz_title, deck.name), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = onClose) { Text(stringResource(R.string.dismiss)) }
        }
        if (questions.isEmpty()) {
            Text(stringResource(R.string.quiz_too_few), style = MaterialTheme.typography.bodyLarge)
            return@Column
        }
        if (position >= questions.size) {
            Spacer(modifier = Modifier.height(24.dp))
            Text(stringResource(R.string.quiz_done, score, questions.size), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 16.dp)) {
                Button(onClick = { round++ }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.quiz_again)) }
                OutlinedButton(onClick = onClose, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.dismiss)) }
            }
            return@Column
        }
        val q = questions[position]
        Text(stringResource(R.string.quiz_intro), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            stringResource(R.string.quiz_progress, position + 1, questions.size, score),
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(q.question, style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(12.dp))
        q.options.forEachIndexed { i, option ->
            val answered = chosen != null
            val color = when {
                answered && i == q.correct -> MaterialTheme.colorScheme.primaryContainer
                answered && i == chosen -> MaterialTheme.colorScheme.errorContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = color,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable(enabled = !answered) {
                    chosen = i
                    val right = i == q.correct
                    if (right) score++
                    workViewModel.review(deck.id, q.cardIndex, known = right)
                }
            ) {
                Row(modifier = Modifier.padding(14.dp)) {
                    Text("${'A' + i}", fontWeight = FontWeight.Bold, modifier = Modifier.width(24.dp))
                    Text(option, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        chosen?.let { c ->
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                stringResource(if (c == q.correct) R.string.quiz_correct else R.string.quiz_wrong),
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                color = if (c == q.correct) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
            val card = deck.cards.getOrNull(q.cardIndex)
            if (card != null) {
                TextButton(onClick = { showFull = !showFull }) { Text(stringResource(R.string.quiz_full_answer)) }
                if (showFull) {
                    Text(card.answer, style = MaterialTheme.typography.bodyMedium)
                    if (card.source.isNotBlank()) {
                        Text(stringResource(R.string.cards_source, card.source), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
            Button(onClick = { position++; chosen = null }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                Text(stringResource(R.string.quiz_next))
            }
        }
    }
}
