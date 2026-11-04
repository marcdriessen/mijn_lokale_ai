package com.example.auralocalai.data.work

import kotlinx.serialization.Serializable

/** Score band of a measurement scale (inclusive range). */
data class ScoreBand(val min: Int, val max: Int, val key: String)

object ScaleScoring {
    fun total(values: List<Int>): Int = values.sum()
    fun band(total: Int, bands: List<ScoreBand>): ScoreBand? = bands.firstOrNull { total in it.min..it.max }

    /** CIWA-Ar: 9 items 0–7 and orientation 0–4 (max 67). Bands as commonly used; follow local protocol. */
    val CIWA_BANDS = listOf(ScoreBand(0, 9, "mild"), ScoreBand(10, 18, "moderate"), ScoreBand(19, 67, "severe"))

    /** COWS: 11 items, max 48. */
    val COWS_BANDS = listOf(
        ScoreBand(0, 4, "none"),
        ScoreBand(5, 12, "mild"),
        ScoreBand(13, 24, "moderate"),
        ScoreBand(25, 36, "moderately_severe"),
        ScoreBand(37, 48, "severe")
    )

    /** AUDIT (WHO): 10 items, max 40. */
    val AUDIT_BANDS = listOf(
        ScoreBand(0, 7, "low"),
        ScoreBand(8, 15, "hazardous"),
        ScoreBand(16, 19, "harmful"),
        ScoreBand(20, 40, "dependence")
    )
}

@Serializable
data class Flashcard(
    val question: String,
    val answer: String,
    /** Leitner box 1..5: higher = known better, shown less often. */
    val box: Int = 1,
    val dueAt: Long = 0L
)

@Serializable
data class FlashcardDeck(
    val id: String,
    val name: String,
    val createdAt: Long,
    val cards: List<Flashcard>
)

object Flashcards {

    /** Parses lines like "Q: … | A: …" or "V: … | A: …" (also on two lines) from the model. */
    fun parse(raw: String, max: Int = 30): List<Flashcard> {
        val cards = mutableListOf<Flashcard>()
        var pendingQuestion: String? = null
        raw.lines().map { it.trim().trimStart('-', '*', '•', ' ').trim() }.filter { it.isNotEmpty() }.forEach { line ->
            val parts = line.split("|").map { it.trim() }
            if (parts.size >= 2 && isQuestion(parts[0]) && isAnswer(parts[1])) {
                cards += Flashcard(stripLabel(parts[0]), stripLabel(parts[1]))
                pendingQuestion = null
            } else if (isQuestion(line)) {
                pendingQuestion = stripLabel(line)
            } else if (isAnswer(line) && pendingQuestion != null) {
                cards += Flashcard(pendingQuestion!!, stripLabel(line))
                pendingQuestion = null
            }
        }
        return cards.filter { it.question.isNotBlank() && it.answer.isNotBlank() }.distinctBy { it.question.lowercase() }.take(max)
    }

    private val QUESTION_PREFIX = Regex("^(q|v|vraag|question|въпрос)\\s*\\d*\\s*[:.)]", RegexOption.IGNORE_CASE)
    private val ANSWER_PREFIX = Regex("^(a|antwoord|answer|отговор|о)\\s*\\d*\\s*[:.)]", RegexOption.IGNORE_CASE)

    private fun isQuestion(s: String) = QUESTION_PREFIX.containsMatchIn(s)
    private fun isAnswer(s: String) = ANSWER_PREFIX.containsMatchIn(s)
    private fun stripLabel(s: String) = s.replace(QUESTION_PREFIX, "").replace(ANSWER_PREFIX, "").trim().trim('*').trim()

    private val INTERVAL_DAYS = mapOf(1 to 0, 2 to 1, 3 to 3, 4 to 7, 5 to 16)

    /** Leitner step: known → next box (later), not known → back to box 1 (today). */
    fun review(card: Flashcard, known: Boolean, now: Long): Flashcard {
        val box = if (known) (card.box + 1).coerceAtMost(5) else 1
        val days = INTERVAL_DAYS[box] ?: 0
        return card.copy(box = box, dueAt = now + days * 24L * 60L * 60L * 1000L)
    }

    fun dueCards(deck: FlashcardDeck, now: Long): List<Int> =
        deck.cards.indices.filter { deck.cards[it].dueAt <= now }
}

object WorkPrompts {

    fun psychoEducationSystem(languageName: String, level: String): String =
        "You write psycho-education for clients in mental health and addiction care. Write in $languageName at language level $level: " +
            "short sentences, everyday words, a warm and respectful tone, short paragraphs with simple headings and practical tips. " +
            "Do not give medication doses, diagnoses or treatment decisions. End with one line that the reader can discuss this with their care provider."

    fun psychoEducationPrompt(topic: String): String =
        "Write a short hand-out (about 250–350 words) for a client about: $topic"

    /** Step 1: only correct the language of the notes (small models do this much better as a separate step). */
    fun spellingSystem(languageName: String): String =
        "You are a careful editor of nursing reports in $languageName. Correct ALL spelling mistakes, typing errors, grammar and punctuation. " +
            "Write complete, clear sentences in a professional, neutral tone. Keep every fact, number, time, dose and label such as [NAAM] exactly. " +
            "Do not add, remove or interpret anything. Reply with the corrected text only."

    fun spellingPrompt(notes: String): String = "Correct this report:\n\n$notes"

    /** Step 2: put the corrected text into the chosen structure. */
    fun reportSystem(languageName: String): String =
        "You are an experienced nurse who structures reports in $languageName. Use ONLY the facts in the text; do not add facts, " +
            "interpretations, diagnoses or advice. Keep labels such as [NAAM] unchanged. Write short, correct, professional sentences. " +
            "If a part has no information, write \"—\". Do not use markdown symbols such as ** or #."

    fun reportPrompt(notes: String, format: String): String = when (format) {
        "SOEP" -> "Sort the facts of the text below into the Dutch SOEP method. Every fact goes under exactly one heading:\n" +
            "S – Subjectief: only what the client SAYS or feels (complaints, pain, wishes, own words).\n" +
            "O – Objectief: what the nurse OBSERVED, MEASURED or DID (behaviour, vital signs, medication given, events, actions such as calling 112).\n" +
            "E – Evaluatie: the nurse's assessment of what happened and why (for example: cause found, effect of an action, current condition).\n" +
            "P – Plan: agreements, follow-up and actions still to be done. If none are written, write \"—\".\n\n" +
            "Example:\nText: Client says he slept badly and feels anxious. Pulse 96. Talked with him for 10 minutes, after that calmer. Doctor will see him tomorrow.\n" +
            "S – Subjectief:\nClient says he slept badly and feels anxious.\nO – Objectief:\nPulse 96. Conversation of 10 minutes held.\n" +
            "E – Evaluatie:\nAfter the conversation the client was calmer.\nP – Plan:\nThe doctor will see the client tomorrow.\n\n" +
            "Write the headings exactly as: S – Subjectief:, O – Objectief:, E – Evaluatie:, P – Plan: (each on its own line).\n\nText:\n$notes"
        "SOAP" -> "Structure this text as SOAP with the headings S (subjective), O (objective), A (assessment as written by the nurse), P (plan):\n\n$notes"
        else -> "Structure this text as SBAR with the headings S (situation), B (background), A (assessment as written by the nurse), R (recommendation):\n\n$notes"
    }

    fun flashcardSystem(languageName: String): String =
        "You make study flashcards in $languageName from a given text. Use only facts from the text. Reply only with lines in this exact format:\n" +
            "Q: <question> | A: <short answer>"

    fun flashcardPrompt(text: String, count: Int): String =
        "Make $count flashcards from this text:\n\n${text.take(6_000)}"
}
