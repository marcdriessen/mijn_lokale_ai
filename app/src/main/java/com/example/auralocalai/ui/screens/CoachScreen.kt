package com.example.auralocalai.ui.screens

import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.auralocalai.R
import com.example.auralocalai.ui.CoachViewModel
import com.example.auralocalai.ui.LlmViewModel
import com.example.auralocalai.ui.components.AppHeader
import com.example.auralocalai.ui.components.CircleIconButton
import java.util.Locale

@Composable
fun CoachScreen(
    llmViewModel: LlmViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    coachViewModel: CoachViewModel = viewModel()
) {
    val state by coachViewModel.state.collectAsState()
    val context = LocalContext.current
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    val tts = remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(Unit) {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context) { status -> if (status == TextToSpeech.SUCCESS) tts.value = engine }
        onDispose {
            engine?.stop()
            engine?.shutdown()
            tts.value = null
        }
    }
    LaunchedEffect(state.message) {
        state.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            coachViewModel.clearMessage()
        }
    }
    LaunchedEffect(state.turns.size) {
        if (state.turns.isNotEmpty()) listState.animateScrollToItem(state.turns.size - 1)
    }
    val scenarios = listOf(
        stringResource(R.string.coach_sc_meet),
        stringResource(R.string.coach_sc_shop),
        stringResource(R.string.coach_sc_doctor),
        stringResource(R.string.coach_sc_family),
        stringResource(R.string.coach_sc_travel)
    )

    Scaffold(
        topBar = {
            AppHeader(title = stringResource(R.string.home_coach), subtitle = stringResource(R.string.home_coach_desc), onBack = onBack) {
                CircleIconButton(Icons.Default.Refresh, stringResource(R.string.coach_restart)) { coachViewModel.reset() }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().imePadding()) {
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("bg" to "Български", "en" to "English", "de" to "Deutsch", "nl" to "Nederlands").forEach { (c, l) ->
                    FilterChip(selected = state.targetLanguage == c, onClick = { coachViewModel.setTarget(c) }, label = { Text(l) })
                }
                listOf("A1", "A2", "B1", "B2").forEach { lvl ->
                    FilterChip(selected = state.level == lvl, onClick = { coachViewModel.setLevel(lvl) }, label = { Text(lvl) })
                }
            }
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                scenarios.forEach { sc ->
                    AssistChip(onClick = { coachViewModel.setScenario(sc) }, label = { Text(if (state.scenario == sc) "✓ $sc" else sc) })
                }
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (state.turns.isEmpty()) {
                    item { Text(stringResource(R.string.coach_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                itemsIndexed(state.turns) { _, turn ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = if (turn.fromLearner) Arrangement.End else Arrangement.Start) {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = if (turn.fromLearner) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.widthIn(max = 320.dp)
                        ) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                                Text(turn.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f, fill = false))
                                if (!turn.fromLearner) {
                                    IconButton(onClick = {
                                        val reply = turn.text.substringAfter("Antwoord:", turn.text).trim()
                                        tts.value?.setLanguage(Locale.forLanguageTag(state.targetLanguage))
                                        tts.value?.speak(reply, TextToSpeech.QUEUE_FLUSH, null, "coach")
                                    }, modifier = Modifier.size(32.dp)) {
                                        Icon(Icons.Default.VolumeUp, contentDescription = stringResource(R.string.briefing_read_aloud), modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }
                if (state.busy) item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
            }
            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = { Text(stringResource(R.string.coach_hint)) },
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.size(8.dp))
                CircleIconButton(Icons.AutoMirrored.Filled.Send, stringResource(R.string.send_message), accent = true) {
                    coachViewModel.send(input, llmViewModel)
                    input = ""
                }
            }
        }
    }
}
