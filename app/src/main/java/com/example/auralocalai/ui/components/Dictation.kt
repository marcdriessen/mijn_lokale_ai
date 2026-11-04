package com.example.auralocalai.ui.components

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.auralocalai.AppLocale
import com.example.auralocalai.AppSecurity
import com.example.auralocalai.R

/**
 * Dictation inside the app (no Google pop-up). Prefers the on-device recogniser (offline, private);
 * falls back to the normal recogniser when the language is not installed on the phone (not in privacy mode).
 * Keeps listening until the user taps stop, so long reports can be dictated in one go.
 */
@Stable
class DictationController internal constructor() {
    var isListening by mutableStateOf(false)
        internal set
    var partial by mutableStateOf("")
        internal set
    /** True while the online (Google) recogniser is used because the offline one is not available. */
    var usingOnline by mutableStateOf(false)
        internal set
    internal var startAction: () -> Unit = {}
    internal var stopAction: () -> Unit = {}
    fun start() = startAction()
    fun stop() = stopAction()
    fun toggle() = if (isListening) stop() else start()
}

/** Language tag for dictation, e.g. "nl-NL" or "bg-BG". */
fun dictationLanguageTag(code: String): String = when (code) {
    "nl" -> "nl-NL"
    "bg" -> "bg-BG"
    "en" -> "en-US"
    "de" -> "de-DE"
    "pl" -> "pl-PL"
    else -> code
}

@Composable
fun rememberDictation(
    languageCode: String? = null,
    continuous: Boolean = true,
    onText: (String) -> Unit
): DictationController {
    val context = LocalContext.current
    val controller = remember { DictationController() }
    val currentOnText by rememberUpdatedState(onText)
    val language = dictationLanguageTag(languageCode ?: AppLocale.effectiveLanguage(context))
    var recognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }
    var wantListening by remember { mutableStateOf(false) }
    var forceOnline by remember(language) { mutableStateOf(false) }

    fun intent(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
    }

    fun release() {
        runCatching { recognizer?.cancel() }
        runCatching { recognizer?.destroy() }
        recognizer = null
    }

    lateinit var begin: () -> Unit

    fun create(ctx: Context): SpeechRecognizer? {
        val onDevice = !forceOnline && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(ctx)
        controller.usingOnline = !onDevice
        if (!onDevice) {
            if (AppSecurity.isPrivacyMode(ctx)) return null
            if (!SpeechRecognizer.isRecognitionAvailable(ctx)) return null
        }
        val r = if (onDevice && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(ctx)
        } else {
            SpeechRecognizer.createSpeechRecognizer(ctx)
        }
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {
                controller.partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                controller.partial = ""
                if (text.isNotBlank()) currentOnText(text.trim())
                if (continuous && wantListening) begin() else { wantListening = false; controller.isListening = false }
            }
            override fun onError(error: Int) {
                controller.partial = ""
                when (error) {
                    // Pauses: keep listening in continuous mode.
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                        if (continuous && wantListening) { begin(); return }
                    // Language not installed for offline recognition: ask Android to download it and use online meanwhile.
                    12, 13 -> if (!forceOnline) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            runCatching { r.triggerModelDownload(intent()) }
                        }
                        forceOnline = true
                        release()
                        if (AppSecurity.isPrivacyMode(context)) {
                            Toast.makeText(context, context.getString(R.string.dictation_offline_missing_privacy), Toast.LENGTH_LONG).show()
                        } else if (wantListening) {
                            Toast.makeText(context, context.getString(R.string.dictation_offline_missing), Toast.LENGTH_LONG).show()
                            begin()
                            return
                        }
                    }
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                        release()
                        if (wantListening) { begin(); return }
                    }
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                        Toast.makeText(context, context.getString(R.string.mic_permission_required), Toast.LENGTH_SHORT).show()
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER ->
                        Toast.makeText(context, context.getString(R.string.speech_error_network), Toast.LENGTH_SHORT).show()
                    else -> Toast.makeText(context, context.getString(R.string.speech_error_generic), Toast.LENGTH_SHORT).show()
                }
                wantListening = false
                controller.isListening = false
            }
        })
        return r
    }

    begin = {
        val r = recognizer ?: create(context).also { recognizer = it }
        if (r == null) {
            wantListening = false
            controller.isListening = false
            Toast.makeText(
                context,
                context.getString(if (AppSecurity.isPrivacyMode(context)) R.string.dictation_offline_missing_privacy else R.string.speech_not_available),
                Toast.LENGTH_LONG
            ).show()
        } else {
            try {
                controller.isListening = true
                r.startListening(intent())
            } catch (e: Exception) {
                wantListening = false
                controller.isListening = false
                Toast.makeText(context, context.getString(R.string.voice_input_unavailable, e.localizedMessage ?: ""), Toast.LENGTH_SHORT).show()
            }
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            wantListening = true
            begin()
        } else {
            Toast.makeText(context, context.getString(R.string.mic_permission_required), Toast.LENGTH_SHORT).show()
        }
    }

    controller.startAction = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            wantListening = true
            begin()
        } else {
            permission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    controller.stopAction = {
        wantListening = false
        runCatching { recognizer?.stopListening() }
        controller.isListening = false
    }

    DisposableEffect(language) {
        onDispose {
            wantListening = false
            release()
        }
    }
    return controller
}

/** Mic button that starts or stops dictation. */
@Composable
fun DictationButton(controller: DictationController, modifier: Modifier = Modifier) {
    if (controller.isListening) {
        Button(
            onClick = { controller.stop() },
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            modifier = modifier
        ) {
            Icon(Icons.Default.Stop, null, Modifier.size(16.dp))
            Text(stringResource(R.string.dictation_stop), modifier = Modifier.padding(start = 4.dp))
        }
    } else {
        OutlinedButton(onClick = { controller.start() }, shape = RoundedCornerShape(20.dp), modifier = modifier) {
            Icon(Icons.Default.Mic, null, Modifier.size(16.dp))
            Text(stringResource(R.string.report_dictate), modifier = Modifier.padding(start = 4.dp))
        }
    }
}

@Composable
fun DictationStatus(controller: DictationController, modifier: Modifier = Modifier) {
    if (!controller.isListening) return
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.errorContainer, modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Mic, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onErrorContainer)
                Text(
                    stringResource(if (controller.usingOnline) R.string.dictation_listening_online else R.string.dictation_listening_offline),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(start = 6.dp)
                )
            }
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            if (controller.partial.isNotBlank()) {
                Text(controller.partial, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
    }
}
