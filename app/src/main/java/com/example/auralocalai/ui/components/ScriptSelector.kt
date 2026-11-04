package com.example.auralocalai.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.auralocalai.R
import com.example.auralocalai.data.ocr.OcrEngine

/** Choice of script for text recognition in photos and scanned PDFs: Latin or Cyrillic (Bulgarian). */
@Composable
fun ScriptSelector(
    selected: OcrEngine.Script,
    onSelect: (OcrEngine.Script) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()).padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(stringResource(R.string.ocr_script_label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FilterChip(selected = selected == OcrEngine.Script.LATIN, onClick = { onSelect(OcrEngine.Script.LATIN) }, label = { Text(stringResource(R.string.ocr_script_latin)) })
        FilterChip(selected = selected == OcrEngine.Script.CYRILLIC, onClick = { onSelect(OcrEngine.Script.CYRILLIC) }, label = { Text(stringResource(R.string.ocr_script_cyrillic)) })
    }
}

/** Same selector, stored directly in the app-wide preference. */
@Composable
fun ScriptSelector(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var script by remember { mutableStateOf(OcrEngine.script(context)) }
    ScriptSelector(script, { script = it; OcrEngine.setScript(context, it) }, modifier)
}
