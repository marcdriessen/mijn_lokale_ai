package com.example.auralocalai.data.briefing

import android.content.Context
import com.example.auralocalai.data.LlmInferenceEngine
import com.example.auralocalai.data.ModelPreset
import com.example.auralocalai.data.ModelRouting
import com.example.auralocalai.ui.LlmViewModel
import com.google.ai.edge.litertlm.ThinkingConfig
import java.io.File

/**
 * Loads the last used model in a background job (without the app's screens), runs one-shot
 * prompts and unloads it again. Used by the morning briefing.
 */
class BackgroundModel(private val context: Context) {

    private var engine: LlmInferenceEngine? = null

    /** Loads the last used model. Returns false when there is none or it does not fit in memory now. */
    suspend fun load(): Boolean {
        val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, "models")
        // With automatic model choice on, the briefing uses the best model for news.
        val routed = if (prefs.getBoolean(LlmViewModel.PREF_AUTO_MODEL_ROUTING, true)) {
            val task = ModelRouting.Task.NEWS
            val files = dir.listFiles()?.map { it.name }.orEmpty()
            ModelRouting.choose(task, ModelRouting.downloadedIds(files), prefs.getString(ModelRouting.prefKey(task), ModelRouting.AUTO), true)
                ?.let { ModelPreset.presetsById[it]?.fileName }
        } else null
        val fileName = routed ?: prefs.getString(PREF_LAST_MODEL_FILE, null) ?: return false
        val file = File(dir, fileName)
        if (!file.exists()) return false
        val candidate = LlmInferenceEngine(context)
        val result = candidate.loadModel(
            modelPath = file.absolutePath,
            preferredBackend = prefs.getString("preferred_backend", "AUTO") ?: "AUTO",
            contextTurns = prefs.getInt("context_window_size", 6)
        )
        return if (result.isSuccess) {
            engine = candidate
            true
        } else {
            candidate.unloadModel()
            false
        }
    }

    /** One prompt with its own system prompt; thinking is off. */
    suspend fun run(systemPrompt: String, prompt: String): String? {
        val active = engine ?: return null
        return try {
            active.resetConversation(systemPrompt = systemPrompt, initialHistory = emptyList())
            val builder = StringBuilder()
            active.generateResponse(
                prompt = prompt,
                turnThinkingConfig = ThinkingConfig(enableThinking = false, thinkingTokenBudget = -1)
            ).collect { builder.append(it) }
            LlmViewModel.stripThinkingBlocks(builder.toString()).ifBlank { null }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    suspend fun unload() {
        engine?.unloadModel()
        engine = null
    }

    companion object {
        const val PREF_LAST_MODEL_FILE = "last_model_file"
    }
}
