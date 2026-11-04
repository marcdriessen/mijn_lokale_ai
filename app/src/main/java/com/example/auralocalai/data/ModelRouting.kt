package com.example.auralocalai.data

/**
 * Chooses the best downloaded model for each kind of task (translation, medical questions,
 * reports, ...). Pure Kotlin so it can be unit tested. Only one model fits in memory at a time,
 * so the app switches models when a task needs another one (this takes 10-30 seconds).
 */
object ModelRouting {

    enum class Task(val key: String) {
        CHAT("chat"),
        MEDICAL("medical"),
        TRANSLATE("translate"),
        REPORT("report"),
        COACH("coach"),
        NEWS("news")
    }

    /** Setting value: pick the best downloaded model automatically. */
    const val AUTO = "auto"
    /** Setting value: always use whatever model is loaded (no switching). */
    const val CURRENT = "current"

    /**
     * Preferred models per task, best first. Large models (Qwen3 8B, Gemma 4 12B) are left out
     * because they are slow; they can still be chosen by hand per task.
     */
    val PREFERENCES: Map<Task, List<String>> = mapOf(
        Task.CHAT to listOf("gemma4-e4b", "qwen3.5-4b", "ministral-3-3b", "gemma4-e2b", "qwen3-4b", "phi-4-mini", "qwen3-1.7b"),
        Task.MEDICAL to listOf("medgemma-1.5-4b", "gemma4-e4b", "qwen3.5-4b", "phi-4-mini", "ministral-3-3b", "gemma4-e2b", "qwen3-4b"),
        // Hy-MT2 is a pure translation model: best for translating, unsuitable for other tasks.
        Task.TRANSLATE to listOf("hy-mt2-1.8b", "gemma4-e4b", "ministral-3-3b", "qwen3.5-4b", "gemma4-e2b"),
        Task.REPORT to listOf("gemma4-e4b", "ministral-3-3b", "qwen3.5-4b", "gemma4-e2b", "qwen3-4b"),
        Task.COACH to listOf("gemma4-e4b", "ministral-3-3b", "gemma4-e2b", "qwen3.5-4b"),
        Task.NEWS to listOf("gemma4-e4b", "ministral-3-3b", "qwen3.5-4b", "gemma4-e2b", "qwen3-4b")
    )

    /** Preference key for the per-task choice. */
    fun prefKey(task: Task): String = "model_for_${task.key}"

    /** Best downloaded model for [task] when choosing automatically; null when none of the preferred ones is present. */
    fun recommended(task: Task, downloadedIds: Set<String>): String? =
        PREFERENCES[task].orEmpty().firstOrNull { it in downloadedIds }

    /**
     * The model id to use for [task], or null to keep the loaded model.
     * [choice] is [AUTO], [CURRENT] or a model id; a chosen model that is not downloaded falls back to automatic.
     */
    fun choose(task: Task, downloadedIds: Set<String>, choice: String?, enabled: Boolean): String? {
        if (!enabled) return null
        return when (val c = choice ?: AUTO) {
            CURRENT -> null
            AUTO -> recommended(task, downloadedIds)
            else -> if (c in downloadedIds) c else recommended(task, downloadedIds)
        }
    }

    /** Preset ids whose model file is present, from the list of downloaded file names. */
    fun downloadedIds(fileNames: Collection<String>): Set<String> =
        ModelPreset.presets.filter { it.fileName in fileNames }.map { it.id }.toSet()
}
