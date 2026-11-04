package com.example.auralocalai.data.medical

import java.util.Locale
import kotlin.math.ln

/**
 * Offline keyword search over the user's own library (protocols, guidelines, notes).
 * Documents are split into passages; passages are ranked with a small TF-IDF score.
 * Pure Kotlin so it is unit-testable.
 */
object PassageIndex {

    private val STOPWORDS = setOf(
        // Dutch
        "de", "het", "een", "en", "van", "voor", "met", "bij", "aan", "dat", "die", "dit", "deze", "wat", "wie", "hoe",
        "waarom", "welke", "zijn", "wordt", "worden", "kan", "kunnen", "moet", "moeten", "niet", "geen", "ook", "als",
        "over", "naar", "uit", "door", "tot", "maar", "wel", "nog", "heeft", "hebben", "mijn", "patient", "patiënt",
        // English
        "the", "and", "for", "with", "what", "which", "how", "why", "are", "is", "was", "were", "can", "should",
        "not", "this", "that", "from", "into", "about", "does", "patients", "patient"
    )

    /** Splits text into passages of roughly [targetChars], preferring paragraph and sentence boundaries. */
    fun chunk(text: String, targetChars: Int = 700): List<String> {
        val normalized = text.replace("\r", "").replace(Regex("[ \t]+"), " ")
        val paragraphs = normalized.split(Regex("\n\\s*\n")).map { it.replace("\n", " ").trim() }.filter { it.isNotEmpty() }
        val passages = mutableListOf<String>()
        val current = StringBuilder()
        fun flush() {
            if (current.isNotBlank()) passages.add(current.toString().trim())
            current.clear()
        }
        for (paragraph in paragraphs) {
            if (paragraph.length > targetChars) {
                flush()
                val sentences = paragraph.split(Regex("(?<=[.!?])\\s+"))
                for (sentence in sentences) {
                    if (current.length + sentence.length > targetChars && current.isNotEmpty()) flush()
                    if (sentence.length > targetChars) {
                        sentence.chunked(targetChars).forEach { passages.add(it.trim()) }
                    } else {
                        current.append(sentence).append(' ')
                    }
                }
                flush()
            } else {
                if (current.length + paragraph.length > targetChars && current.isNotEmpty()) flush()
                current.append(paragraph).append("\n")
            }
        }
        flush()
        return passages.filter { it.length >= 20 }
    }

    fun terms(text: String): List<String> =
        text.lowercase(Locale.ROOT)
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.length >= 3 && it !in STOPWORDS }

    data class Hit(val documentName: String, val passage: String, val score: Double)

    /** Best matching passages across all documents (score > 0), best first. */
    fun search(documents: List<LibraryDocument>, query: String, limit: Int = 4): List<Hit> {
        val queryTerms = terms(query).distinct()
        if (queryTerms.isEmpty()) return emptyList()
        val all = documents.flatMap { doc -> doc.passages.map { doc.name to it } }
        if (all.isEmpty()) return emptyList()
        val passageTerms = all.map { (_, passage) -> terms(passage) }
        val documentFrequency = queryTerms.associateWith { term ->
            passageTerms.count { termsOfPassage -> termsOfPassage.any { it.startsWith(term) } }
        }
        val total = all.size.toDouble()
        return all.mapIndexed { index, (name, passage) ->
            val words = passageTerms[index]
            var score = 0.0
            for (term in queryTerms) {
                val tf = words.count { it.startsWith(term) }
                if (tf > 0) {
                    val idf = ln(1.0 + total / (1.0 + (documentFrequency[term] ?: 0)))
                    score += (1.0 + ln(tf.toDouble())) * idf
                }
            }
            Hit(name, passage, score)
        }.filter { it.score > 0.0 }
            .sortedByDescending { it.score }
            .take(limit)
    }
}
