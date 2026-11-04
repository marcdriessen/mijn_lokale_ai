package com.example.auralocalai.ui.screens

import android.content.ClipData
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.auralocalai.R
import com.example.auralocalai.data.translate.TranslateLanguages
import com.example.auralocalai.data.work.DocumentReader
import com.example.auralocalai.data.work.DocxIO
import com.example.auralocalai.ui.AiAction
import com.example.auralocalai.ui.LlmViewModel
import com.example.auralocalai.ui.TranslateViewModel
import com.example.auralocalai.ui.components.AppHeader
import com.example.auralocalai.ui.components.ScriptSelector
import com.example.auralocalai.ui.components.rememberPhotoCapture

@Composable
fun TranslateScreen(
    llmViewModel: LlmViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    sharedText: String? = null,
    sharedImage: android.net.Uri? = null,
    onSharedConsumed: () -> Unit = {},
    translateViewModel: TranslateViewModel = viewModel()
) {
    val state by translateViewModel.state.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(sharedText, sharedImage) {
        when {
            !sharedText.isNullOrBlank() -> {
                translateViewModel.receiveShared(sharedText)
                onSharedConsumed()
            }
            sharedImage != null -> {
                translateViewModel.translateImage(context, uri = sharedImage)
                onSharedConsumed()
            }
        }
    }
    LaunchedEffect(state.message) {
        state.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            translateViewModel.clearMessage()
        }
    }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) translateViewModel.translateImage(context, uri = uri)
    }
    val camera = rememberPhotoCapture { bitmap -> translateViewModel.translateImage(context, bitmap = bitmap) }
    val documentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) translateViewModel.importDocument(context, uri)
    }
    val saveDocx = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(DocxIO.MIME)) { uri ->
        if (uri != null) translateViewModel.saveAsDocx(context, uri)
    }

    Scaffold(
        topBar = {
            AppHeader(
                title = stringResource(R.string.home_translate),
                subtitle = stringResource(R.string.translate_subtitle),
                onBack = onBack
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Target language
            Text(stringResource(R.string.translate_to), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TranslateLanguages.ALL.forEach { language ->
                    FilterChip(selected = state.targetLanguage == language.code, onClick = { translateViewModel.setTarget(language.code) }, label = { Text(language.nativeName) })
                }
            }

            // Photo input
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                OutlinedButton(onClick = { camera() }, shape = RoundedCornerShape(20.dp), modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.translate_take_photo))
                }
                OutlinedButton(
                    onClick = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.translate_pick_photo))
                }
            }
            OutlinedButton(
                onClick = { documentPicker.launch(DocumentReader.PICKER_TYPES) },
                enabled = state.busy == null,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.translate_document))
            }
            ScriptSelector(selected = state.script, onSelect = { translateViewModel.setScript(it) })
            Text(stringResource(R.string.translate_document_hint), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(12.dp))

            // Text input
            if (state.sourceTitle.isNotBlank()) {
                Text(state.sourceTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
            }
            OutlinedTextField(
                value = state.input,
                onValueChange = { translateViewModel.setInput(it) },
                placeholder = { Text(stringResource(R.string.translate_input_hint)) },
                minLines = 4,
                maxLines = 10,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = { translateViewModel.translate() },
                enabled = state.input.isNotBlank() && state.busy == null,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
            ) {
                Icon(Icons.Default.Translate, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.translate_action), fontWeight = FontWeight.SemiBold)
            }
            OutlinedButton(
                onClick = { translateViewModel.translateWithAi(llmViewModel) },
                enabled = state.input.isNotBlank() && state.busy == null,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.translate_with_ai))
            }

            state.busy?.let {
                Spacer(modifier = Modifier.height(10.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (state.output.isNotBlank()) {
                Spacer(modifier = Modifier.height(16.dp))
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(state.output, style = MaterialTheme.typography.bodyLarge)
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AssistChip(onClick = {
                                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("translation", state.output))
                                Toast.makeText(context, context.getString(R.string.copied_response), Toast.LENGTH_SHORT).show()
                            }, label = { Text(stringResource(R.string.copy_message)) }, leadingIcon = { Icon(Icons.Default.ContentCopy, null, Modifier.size(16.dp)) })
                            AssistChip(onClick = {
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, state.output)
                                }
                                context.startActivity(Intent.createChooser(send, context.getString(R.string.briefing_share)))
                            }, label = { Text(stringResource(R.string.briefing_share)) }, leadingIcon = { Icon(Icons.Default.Share, null, Modifier.size(16.dp)) })
                            AssistChip(
                                onClick = { saveDocx.launch(translateViewModel.suggestedFileName()) },
                                label = { Text(stringResource(R.string.translate_save_docx)) },
                                leadingIcon = { Icon(Icons.Default.Description, null, Modifier.size(16.dp)) }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(stringResource(R.string.translate_ai_title), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AssistChip(onClick = { translateViewModel.runAi(AiAction.SUMMARIZE, llmViewModel) }, label = { Text(stringResource(R.string.shortcut_summarize)) })
                    AssistChip(onClick = { translateViewModel.runAi(AiAction.SIMPLIFY, llmViewModel) }, label = { Text(stringResource(R.string.shortcut_simplify)) })
                    AssistChip(onClick = { translateViewModel.runAi(AiAction.EMAIL, llmViewModel) }, label = { Text(stringResource(R.string.shortcut_email)) })
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
