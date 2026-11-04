package com.example.auralocalai.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.auralocalai.AppLocale
import com.example.auralocalai.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CoachTurn(
    val fromLearner: Boolean,
    val text: String,
    val reply: com.example.auralocalai.data.coach.CoachReply? = null
)

data class CoachUiState(
    val targetLanguage: String = "bg",
    val level: String = "A2",
    val scenario: String = "",
    val turns: List<CoachTurn> = emptyList(),
    val busy: Boolean = false,
    val message: String? = null
)

/** Language coach: practise conversations; the coach corrects mistakes and replies in the target language. */
class CoachViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(CoachUiState())
    val state: StateFlow<CoachUiState> = _state.asStateFlow()

    private fun str(id: Int): String = AppLocale.localized(getApplication<Application>()).getString(id)

    fun setTarget(code: String) = _state.update { it.copy(targetLanguage = code, turns = emptyList()) }
    fun setLevel(level: String) = _state.update { it.copy(level = level) }
    fun setScenario(scenario: String) = _state.update { it.copy(scenario = scenario, turns = emptyList()) }
    fun reset() = _state.update { it.copy(turns = emptyList()) }
    fun clearMessage() = _state.update { it.copy(message = null) }

    fun send(text: String, llm: LlmViewModel) {
        val clean = text.trim()
        if (clean.isEmpty() || _state.value.busy) return
        if (!llm.isModelReady) {
            _state.update { it.copy(message = str(R.string.translate_need_model)) }
            return
        }
        val s = _state.value
        val target = AppLocale.languageNameForModel(s.targetLanguage) ?: "Bulgarian"
        val native = AppLocale.languageNameForModel(AppLocale.effectiveLanguage(getApplication())).takeIf { it != target } ?: "Dutch"
        val history = (s.turns + CoachTurn(true, clean)).takeLast(8).joinToString("\n") { t ->
            (if (t.fromLearner) "Learner: " else "Coach: ") + t.text
        }
        _state.update { it.copy(turns = it.turns + CoachTurn(true, clean), busy = true) }
        val system = "You are a friendly, patient language coach for $target at level ${s.level}. The learner's own language is $native. " +
            "Answer in exactly this format, each label on its own line:\n" +
            "CORRECTION: the learner's last message corrected in $target, or OK when it has no real mistakes (ignore capital letters and punctuation)\n" +
            "EXPLANATION: one short sentence in $native about the mistake (empty when OK)\n" +
            "REPLY: one or two short sentences in $target that continue the conversation and end with a simple question\n" +
            "TRANSLATION: the REPLY translated into $native\n" +
            "Use simple words for level ${s.level}. Do not use markdown." +
            (if (s.scenario.isNotBlank()) " Situation: ${s.scenario}." else "")
        viewModelScope.launch {
            val answer = llm.runOneShotPrompt(system, "Conversation so far:\n$history\n\nRespond to the learner's last message.")
            _state.update {
                it.copy(
                    busy = false,
                    turns = if (answer != null) {
                        val parsed = com.example.auralocalai.data.coach.CoachReply.parse(answer, clean)
                        it.turns + CoachTurn(false, parsed.reply.ifBlank { answer.trim() }, parsed)
                    } else it.turns,
                    message = if (answer == null) str(R.string.translate_ai_failed) else null
                )
            }
        }
    }
}
