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

    fun reportSystem(languageName: String): String =
        "You are an experienced nurse who writes clear, professional reports in $languageName. " +
            "Rewrite the notes into correct $languageName: fix all spelling, grammar and punctuation errors, write complete sentences, " +
            "and use professional, neutral, objective wording (describe behaviour, do not judge). " +
            "Use ONLY the facts in the notes; do not add facts, interpretations or diagnoses. Keep labels such as [NAAM] unchanged. " +
            "If a part is missing, write \"—\". Do not use markdown symbols such as ** or #."

    fun reportPrompt(notes: String, format: String): String = when (format) {
        "SOEP" -> "Structure these notes with the Dutch SOEP method. Use exactly these four headings, each on its own line, " +
            "and write the content below each heading:\n" +
            "S – Subjectief: what the client says or experiences (complaints, wishes, own words).\n" +
            "O – Objectief: what was observed or measured (behaviour, appearance, measurements, scores).\n" +
            "E – Evaluatie: the nurse's evaluation as written in the notes (progress towards goals, analysis).\n" +
            "P – Plan: agreements, actions and follow-up.\n" +
            "Keep the client's words where possible. Do not invent anything; write \"—\" when a part is missing.\n\n$notes"
        "SOAP" -> "Structure these notes as SOAP with the headings S (subjective), O (objective), A (assessment as written by the nurse), P (plan):\n\n$notes"
        else -> "Structure these notes as SBAR with the headings S (situation), B (background), A (assessment as written by the nurse), R (recommendation):\n\n$notes"
    }

    fun flashcardSystem(languageName: String): String =
        "You make study flashcards in $languageName from a given text. Use only facts from the text. Reply only with lines in this exact format:\n" +
            "Q: <question> | A: <short answer>"

    fun flashcardPrompt(text: String, count: Int): String =
        "Make $count flashcards from this text:\n\n${text.take(6_000)}"
}
