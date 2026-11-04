package com.example.auralocalai.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.AnnotatedString

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.AssistChip
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import com.example.auralocalai.data.ChatSession
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.auralocalai.ui.ChatMessage
import com.example.auralocalai.ui.LlmViewModel
import com.example.auralocalai.ui.ModelState
import com.example.auralocalai.data.ModelPreset
import kotlinx.coroutines.launch
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import android.content.Intent
import android.net.Uri
import android.speech.SpeechRecognizer
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.os.Bundle
import android.widget.Toast
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.ui.graphics.graphicsLayer
import com.example.auralocalai.data.SearchContextBuilder
import com.example.auralocalai.data.WebSearchMode
import androidx.compose.ui.res.stringResource
import com.example.auralocalai.R

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ChatScreen(
    viewModel: LlmViewModel,
    onNavigateToModelManager: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToBriefing: () -> Unit = {},
    onNavigateToMedical: () -> Unit = {},
    onHome: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    var showRenameDialog by remember { mutableStateOf<ChatSession?>(null) }
    var renameTitleInput by remember { mutableStateOf("") }
    var sessionToDelete by remember { mutableStateOf<ChatSession?>(null) }
    var textInput by remember { mutableStateOf("") }
    val context = LocalContext.current
    var showAttachmentSheet by remember { mutableStateOf(false) }
    var showSearchDisclosureDialog by remember { mutableStateOf(false) }

    var isListening by remember { mutableStateOf(false) }

    // Voice mode: answers are read aloud when they are finished.
    var voiceMode by remember { mutableStateOf(false) }
    var lastSpokenId by remember { mutableStateOf<String?>(null) }
    val chatTts = remember { mutableStateOf<android.speech.tts.TextToSpeech?>(null) }
    DisposableEffect(Unit) {
        var engine: android.speech.tts.TextToSpeech? = null
        engine = android.speech.tts.TextToSpeech(context) { status ->
            if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                engine?.setLanguage(context.resources.configuration.locales[0])
                chatTts.value = engine
            }
        }
        onDispose {
            engine?.stop()
            engine?.shutdown()
            chatTts.value = null
        }
    }
    LaunchedEffect(uiState.isGenerating, voiceMode) {
        val last = uiState.messages.lastOrNull()
        if (voiceMode && !uiState.isGenerating && last != null && !last.isUser && last.id != lastSpokenId && last.content.isNotBlank()) {
            lastSpokenId = last.id
            val spoken = LlmViewModel.stripThinkingBlocks(last.content).replace(Regex("[*#`_>]"), " ")
            chatTts.value?.speak(spoken, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, last.id)
        }
    }
    var speechRecognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }

    val speechIntent = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, context.resources.configuration.locales[0].toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }
    }

    // Graceful fallback for devices without background SpeechRecognizer service
    val speechPromptLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = { result ->
            isListening = false
            if (result.resultCode == android.app.Activity.RESULT_OK) {
                val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                if (!matches.isNullOrEmpty()) {
                    val text = matches[0]
                    textInput = if (textInput.isBlank()) text else if (textInput.endsWith(" ")) "$textInput$text" else "$textInput $text"
                }
            }
        }
    )

    val speechListener = remember {
        object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                isListening = false
            }
            override fun onError(error: Int) {
                isListening = false
                val errorMsg = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> context.getString(R.string.speech_error_audio)
                    SpeechRecognizer.ERROR_CLIENT -> context.getString(R.string.speech_error_client)
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> context.getString(R.string.speech_error_permissions)
                    SpeechRecognizer.ERROR_NETWORK -> context.getString(R.string.speech_error_network)
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> context.getString(R.string.speech_error_network_timeout)
                    SpeechRecognizer.ERROR_NO_MATCH -> context.getString(R.string.speech_error_no_match)
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> context.getString(R.string.speech_error_busy)
                    SpeechRecognizer.ERROR_SERVER -> context.getString(R.string.speech_error_server)
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> context.getString(R.string.speech_error_timeout)
                    else -> context.getString(R.string.speech_error_generic)
                }
                if (error != SpeechRecognizer.ERROR_NO_MATCH && error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                    Toast.makeText(context, errorMsg, Toast.LENGTH_SHORT).show()
                }
            }
            override fun onResults(results: Bundle?) {
                isListening = false
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val text = matches[0]
                    textInput = if (textInput.isBlank()) text else if (textInput.endsWith(" ")) "$textInput$text" else "$textInput $text"
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }

    fun startListeningInternal() {
        if (speechRecognizer == null && SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(speechListener)
            }
        }

        if (speechRecognizer != null) {
            try {
                isListening = true
                speechRecognizer?.startListening(speechIntent)
            } catch (e: Exception) {
                isListening = false
                try {
                    speechPromptLauncher.launch(speechIntent)
                } catch (dialogEx: Exception) {
                    Toast.makeText(context, context.getString(R.string.voice_input_unavailable, e.localizedMessage ?: ""), Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            try {
                speechPromptLauncher.launch(speechIntent)
            } catch (e: Exception) {
                Toast.makeText(context, context.getString(R.string.speech_not_available), Toast.LENGTH_SHORT).show()
            }
        }
    }

    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (isGranted) {
                startListeningInternal()
            } else {
                Toast.makeText(context, context.getString(R.string.mic_permission_required), Toast.LENGTH_SHORT).show()
            }
        }
    )

    DisposableEffect(context) {
        val recognizer = if (SpeechRecognizer.isRecognitionAvailable(context)) {
            SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(speechListener)
            }
        } else null
        speechRecognizer = recognizer

        onDispose {
            recognizer?.destroy()
        }
    }

    // In-app dictation (offline when possible); one sentence per tap.
    val chatDictation = com.example.auralocalai.ui.components.rememberDictation(continuous = false) { heard ->
        textInput = if (textInput.isBlank()) heard else if (textInput.endsWith(" ")) "$textInput$heard" else "$textInput $heard"
    }

    fun toggleListening() {
        if (isListening) {
            try {
                speechRecognizer?.stopListening()
            } catch (e: Exception) {
                // ignore
            }
            isListening = false
        } else {
            val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.RECORD_AUDIO
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

            if (hasPermission) {
                startListeningInternal()
            } else {
                recordAudioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    val imageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            viewModel.selectImage(uri, context)
        }
    )

    val videoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            viewModel.selectVideo(uri, context)
        }
    )

    val fileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            viewModel.selectFile(uri, context)
        }
    )



    if (showRenameDialog != null) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = null },
            title = { Text(stringResource(R.string.rename_chat)) },
            text = {
                OutlinedTextField(
                    value = renameTitleInput,
                    onValueChange = { renameTitleInput = it },
                    label = { Text(stringResource(R.string.chat_title)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val s = showRenameDialog
                        if (s != null && renameTitleInput.isNotBlank()) {
                            viewModel.renameSession(s.id, renameTitleInput.trim())
                        }
                        showRenameDialog = null
                    }
                ) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (sessionToDelete != null) {
        AlertDialog(
            onDismissRequest = { sessionToDelete = null },
            title = { Text(stringResource(R.string.delete_chat)) },
            text = { Text(stringResource(R.string.delete_chat_confirm, sessionToDelete?.title ?: "")) },
            confirmButton = {
                TextButton(
                    onClick = {
                        sessionToDelete?.let { viewModel.deleteSession(it.id) }
                        sessionToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { sessionToDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(310.dp),
                drawerContainerColor = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.conversations),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        IconButton(onClick = { coroutineScope.launch { drawerState.close() } }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close_drawer))
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    FilledTonalButton(
                        onClick = {
                            viewModel.createNewSession()
                            coroutineScope.launch { drawerState.close() }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.new_chat), fontWeight = FontWeight.SemiBold)
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(8.dp))

                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(
                            items = uiState.sessions,
                            key = { it.id }
                        ) { session ->
                            val isSelected = session.id == uiState.currentSessionId
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.switchSession(session.id)
                                        coroutineScope.launch { drawerState.close() }
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = session.title,
                                            fontSize = 13.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = context.resources.getQuantityString(R.plurals.chat_message_count, session.messageCount, session.messageCount),
                                            fontSize = 11.sp,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Row {
                                        IconButton(
                                            onClick = {
                                                renameTitleInput = session.title
                                                showRenameDialog = session
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Edit,
                                                contentDescription = stringResource(R.string.rename),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = { sessionToDelete = session },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = stringResource(R.string.delete),
                                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(8.dp))

                }
            }
        }
    ) {
    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surface)
                    .statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = { coroutineScope.launch { drawerState.open() } },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = stringResource(R.string.open_chat_drawer),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Local LLM/AI",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.5.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        val (statusColor, subtitleText) = when (val state = uiState.modelState) {
                            is ModelState.Loaded -> {
                                val baseName = state.modelName
                                    .replace(Regex("\\s*\\(.*\\)"), "")
                                    .replace("(Alibaba)", "")
                                    .trim()
                                val backendTag = if (uiState.activeBackend.isNotBlank() && uiState.activeBackend != "None") {
                                    " • ${uiState.activeBackend}"
                                } else ""
                                Pair(Color(0xFF22C55E), "$baseName$backendTag")
                            }
                            ModelState.Loading -> Pair(Color(0xFFEAB308), stringResource(R.string.loading_model))
                            is ModelState.Error -> Pair(MaterialTheme.colorScheme.error, stringResource(R.string.engine_error))
                            ModelState.Unloaded -> Pair(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), stringResource(R.string.no_model_offline))
                        }
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = statusColor.copy(alpha = 0.12f),
                            modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { onNavigateToModelManager() }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(statusColor)
                                )
                                Text(
                                    text = subtitleText,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = statusColor,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = {
                                voiceMode = !voiceMode
                                if (!voiceMode) chatTts.value?.stop()
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.RecordVoiceOver,
                                contentDescription = stringResource(R.string.voice_mode),
                                tint = if (voiceMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        IconButton(
                            onClick = onHome,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Home,
                                contentDescription = stringResource(R.string.home_dashboard),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        if (uiState.messages.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    // Copy the whole conversation (the model itself cannot do this).
                                    val text = uiState.messages.joinToString("\n\n") { msg ->
                                        val role = if (msg.isUser) context.getString(R.string.share_role_user) else "AI"
                                        "[$role]: ${LlmViewModel.stripThinkingBlocks(msg.content)}"
                                    }
                                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("chat", text))
                                    Toast.makeText(context, context.getString(R.string.chat_copied), Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = stringResource(R.string.chat_copy),
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            IconButton(
                                onClick = {
                                    val formattedChat = uiState.messages.joinToString("\n\n") { msg ->
                                        val role = if (msg.isUser) context.getString(R.string.share_role_user) else "AI"
                                        "[$role]: ${msg.content}"
                                    }
                                    val sendIntent: Intent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        putExtra(Intent.EXTRA_TEXT, formattedChat)
                                        type = "text/plain"
                                    }
                                    val shareIntent = Intent.createChooser(sendIntent, context.getString(R.string.share_chat_history))
                                    context.startActivity(shareIntent)
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = stringResource(R.string.share_chat),
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            IconButton(
                                onClick = { viewModel.clearChat() },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.clear_chat),
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 1.dp)
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .imePadding()
        ) {
            val isKeyboardVisible = WindowInsets.isImeVisible
            val isAtBottom by remember {
                derivedStateOf {
                    val layoutInfo = listState.layoutInfo
                    val total = layoutInfo.totalItemsCount
                    if (total == 0) true
                    else {
                        val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                        lastVisible >= total - 2
                    }
                }
            }

            // Scroll to bottom when message count changes or soft keyboard toggles
            LaunchedEffect(uiState.messages.size, isKeyboardVisible) {
                if (uiState.messages.isNotEmpty()) {
                    listState.animateScrollToItem(uiState.messages.size - 1)
                }
            }

            // Follow streaming tokens in real time ONLY when user is already near bottom (no fighting manual scrolling)
            val lastContentLength = uiState.messages.lastOrNull()?.content?.length ?: 0
            LaunchedEffect(lastContentLength) {
                if (uiState.isGenerating && isAtBottom && uiState.messages.isNotEmpty()) {
                    listState.scrollToItem(uiState.messages.size - 1)
                }
            }

            // Warn when the loaded model is known to be weak in Dutch/Bulgarian.
            val weakModels = setOf("qwen3-0.6b", "qwen-1.5b", "qwen3-1.7b", "qwen2.5-coder-1.5b", "deepseek-1.5b")
            val appLang = com.example.auralocalai.AppLocale.effectiveLanguage(context)
            var weakDismissed by remember(uiState.activeModelId) { mutableStateOf(false) }
            if (!weakDismissed && uiState.modelState is ModelState.Loaded && uiState.activeModelId?.let { it in weakModels } == true && appLang in setOf("nl", "bg")) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.weak_model_warning),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onNavigateToModelManager) { Text(stringResource(R.string.models), fontWeight = FontWeight.Bold) }
                        IconButton(onClick = { weakDismissed = true }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.dismiss), modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            if (uiState.messages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    EmptyStateOnboarding(
                        modelState = uiState.modelState,
                        localModels = uiState.localModels,
                        onNavigateToModelManager = onNavigateToModelManager,
                        onQuickPrompt = { prompt ->
                            viewModel.sendMessage(prompt)
                        }
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    itemsIndexed(
                        items = uiState.messages,
                        key = { _, it -> it.id }
                    ) { index, message ->
                        val isLastMessage = index == uiState.messages.lastIndex
                        val isActivelyStreaming = isLastMessage && uiState.isGenerating && !message.isUser
                        val isReasoning = !message.isUser && run {
                            if (message.modelId != null) {
                                when {
                                    // 1. Catalog non-reasoning models (e.g. Qwen Coder, Gemma): never treat as reasoning
                                    ModelPreset.isKnownNonReasoningModel(message.modelId) -> false
                                    // 2. Catalog reasoning models (DeepSeek-R1, QwQ): always treat as reasoning
                                    ModelPreset.isReasoningModel(message.modelId) -> true
                                    // 3. Custom imported models: gate strictly on confirmed full tag prefix
                                    else -> message.content.trimStart().startsWith("<think>")
                                }
                            } else {
                                // 4. Legacy history: decouple from active model; gate strictly on confirmed full tag prefix
                                message.content.trimStart().startsWith("<think>")
                            }
                        }

                        ChatBubble(
                            message = message,
                            isActivelyGenerating = isActivelyStreaming,
                            isReasoningModel = isReasoning
                        )
                    }

                    val lastMessage = uiState.messages.lastOrNull()
                    val isWaitingForFirstToken = uiState.isGenerating && (lastMessage == null || lastMessage.isUser || lastMessage.content.isEmpty())
                    if (isWaitingForFirstToken) {
                        item {
                            TypingIndicatorBubble()
                        }
                    }
                }
            }

            // Bottom text inputs (or loading/error state card)
            if (uiState.modelState is ModelState.Loaded) {
                if (uiState.isSearching) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 4.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = stringResource(R.string.searching_web_for, uiState.activeSearchQuery ?: ""),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                // "Via English" status while the question/answer is translated.
                uiState.pivotStatus?.let { status ->
                    if (uiState.isGenerating) {
                        Text(
                            "🌐 $status",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 20.dp, end = 16.dp, top = 6.dp)
                        )
                    }
                }
                // Shortcuts: put an instruction in front of the typed text (or start a new one).
                if (!uiState.isGenerating) {
                    val shortcuts = listOf(
                        stringResource(R.string.shortcut_summarize) to stringResource(R.string.shortcut_summarize_prompt),
                        stringResource(R.string.shortcut_translate_nl) to stringResource(R.string.shortcut_translate_nl_prompt),
                        stringResource(R.string.shortcut_simplify) to stringResource(R.string.shortcut_simplify_prompt),
                        stringResource(R.string.shortcut_email) to stringResource(R.string.shortcut_email_prompt),
                        stringResource(R.string.shortcut_bulgarian) to stringResource(R.string.shortcut_bulgarian_prompt)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(start = 16.dp, end = 16.dp, top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Choice per chat: answer directly, or via English (better with small models).
                        if (com.example.auralocalai.AppLocale.effectiveLanguage(context) != "en") {
                            FilterChip(
                                selected = uiState.pivotEnglish,
                                onClick = {
                                    viewModel.setPivotEnglish(!uiState.pivotEnglish)
                                    android.widget.Toast.makeText(
                                        context,
                                        context.getString(if (!uiState.pivotEnglish) R.string.pivot_on_toast else R.string.pivot_off_toast),
                                        android.widget.Toast.LENGTH_LONG
                                    ).show()
                                },
                                label = { Text(stringResource(R.string.pivot_chip), fontSize = 12.sp) },
                                shape = RoundedCornerShape(16.dp)
                            )
                        }
                        shortcuts.forEach { (label, prompt) ->
                            AssistChip(
                                onClick = {
                                    if (textInput.isBlank()) {
                                        textInput = "$prompt\n\n"
                                    } else {
                                        viewModel.sendMessage("$prompt\n\n$textInput")
                                        textInput = ""
                                    }
                                },
                                label = { Text(label, fontSize = 12.sp) },
                                shape = RoundedCornerShape(16.dp)
                            )
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(28.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant,
                                shape = RoundedCornerShape(28.dp)
                            )
                            .padding(8.dp)
                    ) {
                        // Attachment preview row (if any attachment is selected)
                        val hasAttachment = uiState.selectedImageUri != null || uiState.selectedVideoUri != null || uiState.selectedFileUri != null
                        if (hasAttachment) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (uiState.selectedImageUri != null) {
                                        AsyncImage(
                                            model = uiState.selectedImageUri,
                                            contentDescription = stringResource(R.string.image_preview),
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                        )
                                    } else if (uiState.selectedVideoUri != null) {
                                        Icon(
                                            imageVector = Icons.Default.PlayCircle,
                                            contentDescription = stringResource(R.string.video_preview),
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(32.dp)
                                        )
                                    } else if (uiState.selectedFileUri != null) {
                                        Icon(
                                            imageVector = Icons.Default.Description,
                                            contentDescription = stringResource(R.string.file_preview),
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }
                                }
                                
                                Spacer(modifier = Modifier.width(12.dp))
                                
                                Column(modifier = Modifier.weight(1f)) {
                                    val label = when {
                                        uiState.selectedImageUri != null -> stringResource(R.string.image_attachment)
                                        uiState.selectedVideoUri != null -> uiState.selectedFileName ?: stringResource(R.string.video)
                                        else -> uiState.selectedFileName ?: stringResource(R.string.document)
                                    }
                                    Text(
                                        text = label,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    if (uiState.isAttachmentProcessing) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(12.dp),
                                                strokeWidth = 2.dp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = stringResource(R.string.processing_attachment),
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    } else if (uiState.attachmentError != null) {
                                        Text(
                                            text = uiState.attachmentError ?: stringResource(R.string.error),
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.error,
                                            fontWeight = FontWeight.Medium
                                        )
                                    } else if (uiState.extractedOcrText != null) {
                                        Text(
                                            text = stringResource(R.string.extracted_chars, uiState.extractedOcrText?.length ?: 0),
                                            fontSize = 11.sp,
                                            color = Color(0xFF16A34A),
                                            fontWeight = FontWeight.Medium
                                        )
                                    } else {
                                        Text(
                                            text = stringResource(R.string.ready_to_send),
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                
                                IconButton(
                                    onClick = {
                                        viewModel.clearAttachment()
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = stringResource(R.string.remove_attachment),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                thickness = 1.dp,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }

                        // Input control row (attachment consolidated button, voice button, text field, send button)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Consolidated "+" attachment button
                                IconButton(
                                    onClick = { showAttachmentSheet = true },
                                    enabled = !hasAttachment && !uiState.isGenerating,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = stringResource(R.string.attach_content),
                                        tint = if (!hasAttachment) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    if (chatDictation.isListening) {
                                        PulsingListeningIndicator()
                                    }
                                    IconButton(
                                        onClick = { chatDictation.toggle() },
                                        enabled = !uiState.isGenerating,
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Mic,
                                            contentDescription = if (chatDictation.isListening) stringResource(R.string.stop_listening) else stringResource(R.string.voice_input),
                                            tint = if (chatDictation.isListening) Color(0xFFEF4444) else MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }

                                // Web search mode toggle button (Off -> Auto -> Always -> Off)
                                val effectiveSearchMode = uiState.chatSearchOverride ?: uiState.webSearchMode
                                val searchIconTint = when (effectiveSearchMode) {
                                    WebSearchMode.OFF -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                                    WebSearchMode.AUTO -> MaterialTheme.colorScheme.primary
                                    WebSearchMode.ALWAYS -> Color(0xFF16A34A)
                                }
                                val searchModeLabel = when (effectiveSearchMode) {
                                    WebSearchMode.OFF -> stringResource(R.string.web_search_off)
                                    WebSearchMode.AUTO -> stringResource(R.string.web_search_auto)
                                    WebSearchMode.ALWAYS -> stringResource(R.string.web_search_always)
                                }

                                IconButton(
                                    onClick = {
                                        val nextMode = when (effectiveSearchMode) {
                                            WebSearchMode.OFF -> WebSearchMode.AUTO
                                            WebSearchMode.AUTO -> WebSearchMode.ALWAYS
                                            WebSearchMode.ALWAYS -> WebSearchMode.OFF
                                        }
                                        if (nextMode != WebSearchMode.OFF && !uiState.searchDisclosureAcknowledged) {
                                            showSearchDisclosureDialog = true
                                        } else {
                                            viewModel.setChatSearchOverride(nextMode)
                                        }
                                    },
                                    enabled = !uiState.isGenerating,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.TravelExplore,
                                        contentDescription = searchModeLabel,
                                        tint = searchIconTint,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.width(4.dp))
                            
                            // Sleek and compact BasicTextField instead of the bulky TextField
                            BasicTextField(
                                value = textInput,
                                onValueChange = { textInput = it },
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                textStyle = LocalTextStyle.current.copy(
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 15.sp
                                ),
                                maxLines = 4,
                                decorationBox = { innerTextField ->
                                    Box(contentAlignment = Alignment.CenterStart) {
                                        if (textInput.isEmpty()) {
                                            Text(
                                                text = stringResource(R.string.input_hint),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                fontSize = 15.sp
                                            )
                                        }
                                        innerTextField()
                                    }
                                }
                            )
                            
                            Spacer(modifier = Modifier.width(4.dp))
                            
                            if (uiState.isGenerating) {
                                IconButton(
                                    onClick = {
                                        viewModel.stopGeneration()
                                    },
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.error)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = stringResource(R.string.stop_generation),
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            } else {
                                val canSend = (textInput.isNotBlank() || hasAttachment) && !uiState.isAttachmentProcessing
                                IconButton(
                                    onClick = {
                                        viewModel.sendMessage(textInput)
                                        textInput = ""
                                    },
                                    enabled = canSend,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (canSend) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                            }
                                        )
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Send,
                                        contentDescription = stringResource(R.string.send_message),
                                        tint = if (canSend) {
                                            MaterialTheme.colorScheme.onPrimary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                                        },
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Button(
                        onClick = onNavigateToModelManager,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .clip(RoundedCornerShape(26.dp)),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = 2.dp,
                            pressedElevation = 6.dp
                        )
                    ) {
                        Text(
                            text = stringResource(R.string.download_or_load_to_chat),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }

        if (showAttachmentSheet) {
            ModalBottomSheet(
                onDismissRequest = { showAttachmentSheet = false },
                containerColor = MaterialTheme.colorScheme.surface,
                dragHandle = { BottomSheetDefaults.DragHandle() },
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                        .navigationBarsPadding(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.add_to_conversation),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable {
                                showAttachmentSheet = false
                                imageLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Image, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.attach_image), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                                Text(stringResource(R.string.attach_image_desc), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable {
                                showAttachmentSheet = false
                                videoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.PlayCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.attach_video), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                                Text(stringResource(R.string.attach_video_desc), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable {
                                showAttachmentSheet = false
                                fileLauncher.launch("*/*")
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.attach_document), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                                Text(stringResource(R.string.attach_document_desc), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }

        if (showSearchDisclosureDialog) {
            AlertDialog(
                onDismissRequest = { showSearchDisclosureDialog = false },
                icon = {
                    Icon(
                        imageVector = Icons.Default.TravelExplore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                },
                title = {
                    Text(
                        text = stringResource(R.string.search_disclosure_title),
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                },
                text = {
                    Text(
                        text = stringResource(R.string.search_disclosure_body),
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.acknowledgeSearchDisclosure()
                            viewModel.setChatSearchOverride(WebSearchMode.AUTO)
                            showSearchDisclosureDialog = false
                        }
                    ) {
                        Text(stringResource(R.string.enable_search))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showSearchDisclosureDialog = false }
                    ) {
                        Text(stringResource(R.string.keep_offline))
                    }
                }
            )
        }
    }
}
}

@Immutable
data class ParsedMessageContent(
    val thinkContent: String?,
    val hasUnclosedThink: Boolean,
    val mainContent: String
)

// Single-pass fast path for the predominant single-think-block reasoning model case
fun parseThinkBlocks(rawContent: String): ParsedMessageContent {
    val thinkStartTag = "<think>"
    val thinkEndTag = "</think>"

    val firstStartIndex = rawContent.indexOf(thinkStartTag)
    if (firstStartIndex == -1) {
        return ParsedMessageContent(thinkContent = null, hasUnclosedThink = false, mainContent = rawContent)
    }

    // Fast-path: single think block (covers DeepSeek-R1, QwQ, etc. without list allocations)
    val secondStartIndex = rawContent.indexOf(thinkStartTag, firstStartIndex + thinkStartTag.length)
    if (secondStartIndex == -1) {
        val before = rawContent.substring(0, firstStartIndex).trim()
        val endIndex = rawContent.indexOf(thinkEndTag, firstStartIndex + thinkStartTag.length)
        return if (endIndex != -1) {
            val thinkText = rawContent.substring(firstStartIndex + thinkStartTag.length, endIndex).trim().ifEmpty { null }
            val after = rawContent.substring(endIndex + thinkEndTag.length).trim()
            val main = when {
                before.isEmpty() -> after
                after.isEmpty() -> before
                else -> "$before\n\n$after"
            }
            ParsedMessageContent(thinkContent = thinkText, hasUnclosedThink = false, mainContent = main)
        } else {
            val unclosedThink = rawContent.substring(firstStartIndex + thinkStartTag.length).trim().ifEmpty { null }
            ParsedMessageContent(thinkContent = unclosedThink, hasUnclosedThink = true, mainContent = before)
        }
    }

    // Fallback: multiple interleaved think blocks
    val thinkBlocks = mutableListOf<String>()
    val mainBlocks = mutableListOf<String>()
    var currIndex = 0
    var hasUnclosed = false

    while (currIndex < rawContent.length) {
        val startIndex = rawContent.indexOf(thinkStartTag, currIndex)
        if (startIndex == -1) {
            val remaining = rawContent.substring(currIndex).trim()
            if (remaining.isNotEmpty()) mainBlocks.add(remaining)
            break
        }

        val before = rawContent.substring(currIndex, startIndex).trim()
        if (before.isNotEmpty()) mainBlocks.add(before)

        val endIndex = rawContent.indexOf(thinkEndTag, startIndex + thinkStartTag.length)
        if (endIndex != -1) {
            val thinkText = rawContent.substring(startIndex + thinkStartTag.length, endIndex).trim()
            if (thinkText.isNotEmpty()) thinkBlocks.add(thinkText)
            currIndex = endIndex + thinkEndTag.length
        } else {
            val unclosedThink = rawContent.substring(startIndex + thinkStartTag.length).trim()
            if (unclosedThink.isNotEmpty()) thinkBlocks.add(unclosedThink)
            hasUnclosed = true
            currIndex = rawContent.length
            break
        }
    }

    val combinedThink = if (thinkBlocks.isNotEmpty()) thinkBlocks.joinToString("\n\n---\n\n") else null
    val combinedMain = mainBlocks.joinToString("\n\n")

    return ParsedMessageContent(
        thinkContent = combinedThink,
        hasUnclosedThink = hasUnclosed,
        mainContent = combinedMain
    )
}

@Composable
fun ChatBubble(
    message: ChatMessage,
    isActivelyGenerating: Boolean = false,
    isReasoningModel: Boolean = false
) {
    // Modern chat layout: your own messages in a soft bubble, answers as plain full-width text.
    val bubbleColor = if (message.isUser) {
        MaterialTheme.colorScheme.surfaceVariant
    } else {
        Color.Transparent
    }

    val alignment = if (message.isUser) Alignment.End else Alignment.Start
    val shape = if (message.isUser) {
        RoundedCornerShape(22.dp, 22.dp, 6.dp, 22.dp)
    } else {
        RoundedCornerShape(0.dp)
    }

    val borderStroke = BorderStroke(0.dp, Color.Transparent)

    val textColor = MaterialTheme.colorScheme.onSurface

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalAlignment = alignment
    ) {
        Card(
            shape = shape,
            colors = CardDefaults.cardColors(containerColor = bubbleColor),
            border = borderStroke,
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = if (message.isUser) Modifier.widthIn(max = 320.dp) else Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(
                    horizontal = if (message.isUser) 16.dp else 2.dp,
                    vertical = if (message.isUser) 11.dp else 4.dp
                )
            ) {
                if (message.imageUri != null) {
                    AsyncImage(
                        model = message.imageUri,
                        contentDescription = stringResource(R.string.image_attachment_desc),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                
                if (message.videoUri != null) {
                    val context = LocalContext.current
                    Card(
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(Uri.parse(message.videoUri), "video/*")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    // ignore
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayCircle,
                                contentDescription = stringResource(R.string.play_video),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = message.fileName ?: stringResource(R.string.attached_video),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1
                                )
                                Text(
                                    text = stringResource(R.string.tap_to_play),
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (message.fileUri != null) {
                    val context = LocalContext.current
                    Card(
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(Uri.parse(message.fileUri), "*/*")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    // ignore
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = stringResource(R.string.open_file),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = message.fileName ?: stringResource(R.string.document),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1
                                )
                                Text(
                                    text = stringResource(R.string.file_tap_to_open, message.fileType?.uppercase() ?: stringResource(R.string.document)),
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Parse think blocks only for AI assistant messages from reasoning models
                // TODO: consider incremental parsing from last known offset if this shows up in profiling
                val parsed = if (!message.isUser && isReasoningModel) {
                    remember(message.content) { parseThinkBlocks(message.content) }
                } else {
                    ParsedMessageContent(thinkContent = null, hasUnclosedThink = false, mainContent = message.content)
                }

                // Collapsible reasoning process accordion for DeepSeek-R1 CoT
                if (!parsed.thinkContent.isNullOrBlank()) {
                    val isThinkingLive = parsed.hasUnclosedThink && isActivelyGenerating
                    val isThinkingStopped = parsed.hasUnclosedThink && !isActivelyGenerating
                    var isUserExpanded by remember(message.id) { mutableStateOf<Boolean?>(null) }
                    val isThinkExpanded = isUserExpanded ?: isThinkingLive

                    val headerText = when {
                        isThinkingLive -> stringResource(R.string.thinking_live)
                        isThinkingStopped -> stringResource(R.string.thinking_stopped)
                        else -> stringResource(R.string.thinking_process)
                    }

                    val headerColor = when {
                        isThinkingStopped -> MaterialTheme.colorScheme.onSurfaceVariant
                        else -> MaterialTheme.colorScheme.primary
                    }

                    Card(
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        ),
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .then(if (!isThinkingLive) Modifier.animateContentSize() else Modifier)
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { isUserExpanded = !isThinkExpanded },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = headerText,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = headerColor
                                )
                                Icon(
                                    imageVector = if (isThinkExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = stringResource(R.string.toggle_thought_process),
                                    tint = headerColor,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            if (isThinkExpanded) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = parsed.thinkContent,
                                    fontSize = 12.sp,
                                    lineHeight = 17.sp,
                                    color = if (isSystemInDarkTheme()) Color(0xFF94A3B8) else Color(0xFF475569),
                                    fontStyle = FontStyle.Italic
                                )
                            }
                        }
                    }
                }

                if (parsed.mainContent.isNotEmpty()) {
                    Text(
                        text = parsed.mainContent,
                        color = textColor,
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }

                // Grounded search sources card
                if (!message.isUser && !message.searchSources.isNullOrEmpty()) {
                    val sources = message.searchSources
                    var isSourcesExpanded by remember(message.id) { mutableStateOf(false) }
                    val context = LocalContext.current

                    Spacer(modifier = Modifier.height(6.dp))
                    Card(
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        ),
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { isSourcesExpanded = !isSourcesExpanded },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.TravelExplore,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = stringResource(R.string.sources_count, sources.size),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Icon(
                                    imageVector = if (isSourcesExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = stringResource(R.string.toggle_sources),
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            if (isSourcesExpanded) {
                                if (!message.searchQuery.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = stringResource(R.string.external_query_sent, message.searchQuery ?: ""),
                                        fontSize = 11.sp,
                                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    sources.forEachIndexed { index, source ->
                                        val cleanTitle = SearchContextBuilder.sanitize(source.title).ifBlank { source.domain }
                                        val cleanSnippet = SearchContextBuilder.sanitize(source.snippet)
                                        val isValidUrl = SearchContextBuilder.isValidHttpUrl(source.url)

                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(6.dp))
                                                .then(
                                                    if (isValidUrl) {
                                                        Modifier.clickable {
                                                            try {
                                                                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(source.url))
                                                                context.startActivity(browserIntent)
                                                            } catch (e: Exception) {
                                                                // ignore if no browser app
                                                            }
                                                        }
                                                    } else Modifier
                                                )
                                                .padding(4.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = "[${index + 1}]",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                Text(
                                                    text = cleanTitle,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = if (isValidUrl) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1
                                                )
                                                if (source.domain.isNotBlank()) {
                                                    Text(
                                                        text = "(${source.domain})",
                                                        fontSize = 10.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                            if (cleanSnippet.isNotBlank()) {
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = cleanSnippet,
                                                    fontSize = 11.sp,
                                                    lineHeight = 15.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 3
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                
                if (!message.isUser && !isActivelyGenerating && parsed.mainContent.isNotBlank()) {
                    @Suppress("DEPRECATION")
                    val clipboardManager = LocalClipboardManager.current
                    val bubbleContext = LocalContext.current
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(parsed.mainContent))
                                Toast.makeText(bubbleContext, bubbleContext.getString(R.string.copied_response), Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = stringResource(R.string.copy_message),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }

                if (message.ocrText != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    var isExpanded by remember { mutableStateOf(false) }
                    Card(
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { isExpanded = !isExpanded },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.extracted_text_ocr),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Icon(
                                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = stringResource(R.string.toggle_extracted_text),
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            if (isExpanded) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = message.ocrText,
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TypingIndicatorBubble() {
    val infiniteTransition = rememberInfiniteTransition(label = "typing")
    val dotScales = List(3) { index ->
        infiniteTransition.animateFloat(
            initialValue = 0.4f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = keyframes {
                    durationMillis = 800
                    0.4f at 0
                    1f at 200 + (index * 150)
                    0.4f at 500 + (index * 150)
                },
                repeatMode = RepeatMode.Reverse
            ),
            label = "dot-"
        )
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start
    ) {
        Card(
            shape = RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.padding(vertical = 2.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                dotScales.forEach { scale ->
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = scale.value))
                    )
                }
            }
        }
    }
}

private data class SamplePromptItem(
    val prompt: String,
    val category: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

@Composable
fun EmptyStateOnboarding(
    modelState: ModelState,
    localModels: List<String>,
    onNavigateToModelManager: () -> Unit,
    onQuickPrompt: (String) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
        // Glowing tech emblem with AI sparkle
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
                            Color.Transparent
                        )
                    )
                )
                .border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = stringResource(R.string.engine_logo_desc),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(34.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = stringResource(R.string.welcome_title),
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            letterSpacing = (-0.5).sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.welcome_body),
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        Spacer(modifier = Modifier.height(28.dp))

        if (modelState is ModelState.Loaded) {
            Text(
                text = stringResource(R.string.try_sample),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.Start).padding(start = 4.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            val sampleItems = listOf(
                SamplePromptItem(stringResource(R.string.sample_code), stringResource(R.string.sample_code_cat), Icons.Default.Code),
                SamplePromptItem(stringResource(R.string.sample_science), stringResource(R.string.sample_science_cat), Icons.Default.Psychology),
                SamplePromptItem(stringResource(R.string.sample_food), stringResource(R.string.sample_food_cat), Icons.Default.Lightbulb)
            )
            sampleItems.forEach { item ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onQuickPrompt(item.prompt)
                        },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.category,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = item.prompt,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Medium,
                                lineHeight = 18.sp
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        } else if (modelState is ModelState.Error) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = stringResource(R.string.error),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.engine_error_title),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = modelState.message,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f),
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = onNavigateToModelManager,
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth().height(44.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.manage_models),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, Color(0xFFEFF6FF)),
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.model_required),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val bodyMsg = if (localModels.isEmpty()) {
                        stringResource(R.string.model_required_download)
                    } else {
                        stringResource(R.string.model_required_load)
                    }
                    Text(
                        text = bodyMsg,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = onNavigateToModelManager,
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth().height(44.dp)
                    ) {
                        Text(
                            text = if (localModels.isEmpty()) stringResource(R.string.go_to_downloader) else stringResource(R.string.select_load_model),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}
}

@Composable
private fun PulsingListeningIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Box(
        modifier = Modifier
            .size(28.dp)
            .graphicsLayer {
                scaleX = pulseScale
                scaleY = pulseScale
                alpha = pulseAlpha
            }
            .background(Color(0xFFEF4444), shape = CircleShape)
    )
}
