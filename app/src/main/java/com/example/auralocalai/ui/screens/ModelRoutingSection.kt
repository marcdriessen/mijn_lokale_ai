package com.example.auralocalai.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.auralocalai.R
import com.example.auralocalai.data.ModelPreset
import com.example.auralocalai.data.ModelRouting
import com.example.auralocalai.ui.LlmViewModel
import com.example.auralocalai.ui.UiState

/** Settings: switch automatically to the best downloaded model per task, with a choice per task. */
@Composable
fun ModelRoutingSection(uiState: UiState, viewModel: LlmViewModel) {
    val downloaded = ModelRouting.downloadedIds(uiState.localModels)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.routing_title), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    stringResource(R.string.routing_desc),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    lineHeight = 15.sp
                )
            }
            Switch(checked = uiState.autoModelRouting, onCheckedChange = { viewModel.setAutoModelRouting(it) })
        }
        if (!uiState.autoModelRouting) return@Column
        ModelRouting.Task.entries.forEach { task ->
            TaskRow(task, uiState.modelForTask[task.key] ?: ModelRouting.AUTO, downloaded) { viewModel.setModelForTask(task, it) }
        }
    }
}

@Composable
private fun TaskRow(task: ModelRouting.Task, choice: String, downloaded: Set<String>, onChoose: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val recommended = ModelRouting.recommended(task, downloaded)
    fun nameOf(id: String?) = id?.let { ModelPreset.presetsById[it]?.name ?: it }
    val label = when (choice) {
        ModelRouting.AUTO -> nameOf(recommended)?.let { stringResource(R.string.routing_auto_with, it) } ?: stringResource(R.string.routing_auto_none)
        ModelRouting.CURRENT -> stringResource(R.string.routing_current)
        else -> nameOf(choice) ?: choice
    }
    val best = ModelRouting.PREFERENCES[task].orEmpty().firstOrNull()
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(taskName(task)), fontSize = 12.sp, modifier = Modifier.weight(0.42f))
            Box(modifier = Modifier.weight(0.58f)) {
                OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(label, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Icon(Icons.Default.ArrowDropDown, null, Modifier.size(18.dp))
                }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    DropdownMenuItem(
                        text = { Text(nameOf(recommended)?.let { stringResource(R.string.routing_auto_with, it) } ?: stringResource(R.string.routing_auto_none)) },
                        onClick = { onChoose(ModelRouting.AUTO); open = false }
                    )
                    DropdownMenuItem(text = { Text(stringResource(R.string.routing_current)) }, onClick = { onChoose(ModelRouting.CURRENT); open = false })
                    ModelPreset.presets.filter { it.id in downloaded }.forEach { preset ->
                        DropdownMenuItem(text = { Text(preset.name) }, onClick = { onChoose(preset.id); open = false })
                    }
                }
            }
        }
        if (choice == ModelRouting.AUTO && best != null && best !in downloaded) {
            Text(
                stringResource(R.string.routing_tip, nameOf(best) ?: best),
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

private fun taskName(task: ModelRouting.Task): Int = when (task) {
    ModelRouting.Task.CHAT -> R.string.routing_task_chat
    ModelRouting.Task.MEDICAL -> R.string.routing_task_medical
    ModelRouting.Task.TRANSLATE -> R.string.routing_task_translate
    ModelRouting.Task.REPORT -> R.string.routing_task_report
    ModelRouting.Task.COACH -> R.string.routing_task_coach
    ModelRouting.Task.NEWS -> R.string.routing_task_news
}
