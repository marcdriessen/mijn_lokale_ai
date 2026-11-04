package com.example.auralocalai.ui.components

import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.example.auralocalai.R
import java.util.Locale

/** Reads text aloud with the phone's text-to-speech, in the language of the text. */
@Stable
class Speaker internal constructor() {
    internal var tts: TextToSpeech? = null
    internal var onMissingLanguage: () -> Unit = {}
    var isSpeaking by mutableStateOf(false)
        internal set

    internal var context: android.content.Context? = null

    fun speak(text: String, languageCode: String) {
        val engine = tts ?: return
        val ctx = context ?: return
        isSpeaking = TtsVoices.speak(ctx, engine, text, languageCode)
    }

    fun stop() {
        tts?.stop()
        isSpeaking = false
    }

    fun toggle(text: String, languageCode: String) = if (isSpeaking) stop() else speak(text, languageCode)
}

@Composable
fun rememberSpeaker(): Speaker {
    val context = LocalContext.current
    val speaker = remember { Speaker() }
    DisposableEffect(Unit) {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                speaker.tts = engine
                engine?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        if (engine?.isSpeaking != true) speaker.isSpeaking = false
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) { speaker.isSpeaking = false }
                })
            }
        }
        speaker.context = context
        speaker.onMissingLanguage = {
            Toast.makeText(context, context.getString(R.string.tts_language_missing), Toast.LENGTH_LONG).show()
        }
        onDispose {
            engine?.stop()
            engine?.shutdown()
            speaker.tts = null
        }
    }
    return speaker
}
