package com.example.auralocalai.data.medical

import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.io.InputStream
import java.text.Normalizer
import java.util.Locale
import java.util.zip.GZIPInputStream
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Pure helpers for the offline knowledge base (no Android types, unit-testable).
 * [normalize] must stay identical to `normaliseer()` in tools/maak_kennisbank.py.
 */
object KnowledgeQuery {

    private val STOPWORDS = setOf(
        "de", "het", "een", "en", "van", "voor", "met", "bij", "aan", "dat", "die", "dit", "deze", "wat", "wie", "hoe",
        "waarom", "welke", "welk", "zijn", "wordt", "worden", "werd", "kan", "kunnen", "moet", "moeten", "niet", "geen",
        "ook", "als", "over", "naar", "uit", "door", "tot", "maar", "wel", "nog", "heeft", "hebben", "mijn", "jouw",
        "zij", "hij", "ik", "je", "we", "wij", "er", "om", "op", "in", "te", "is", "of", "dan", "wanneer", "waar",
        "patient", "patienten", "client", "clienten", "iemand", "mensen", "persoon", "personen", "goed", "beste",
        "zou", "zouden", "mag", "mogen", "gaat", "gaan", "doen", "doe", "veel", "meer", "minder", "andere", "elke",
        "werkt", "werken", "beter", "slechter", "helpt", "helpen", "verband", "tussen", "herken", "herkent", "herkennen",
        "verschil", "rol", "belangrijk", "belangrijkste", "enkele", "vaak", "eigenlijk", "precies", "graag", "weten",
        "the", "and", "for", "with", "what", "which", "how", "why", "are", "was", "were", "can", "should", "not",
        "this", "that", "from", "into", "about", "does", "patients"
    )

    /** Lower case, accents removed, only letters and digits (everything else becomes a space). */
    fun normalize(text: String): String {
        val decomposed = Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        val noMarks = decomposed.replace(Regex("\\p{Mn}+"), "")
        return noMarks.replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim().replace(Regex(" +"), " ")
    }

    /** Distinct search terms of a question (at most [max]); short words and stop words are dropped. */
    fun terms(question: String, max: Int = 8): List<String> =
        normalize(question).split(' ')
            .filter { it.length >= 3 && it !in STOPWORDS }
            .map { stem(it) }
            .distinct()
            .take(max)

    /** Very light Dutch stemming so "verslaafden" also finds "verslaving"-like forms via prefix search. */
    fun stem(word: String): String {
        if (word.length <= 5) return word
        for (suffix in listOf("heden", "ingen", "ische", "isch", "eren", "ende", "en", "es", "e", "s")) {
            if (word.endsWith(suffix) && word.length - suffix.length >= 5) return word.dropLast(suffix.length)
        }
        return word
    }

    /**
     * Possible splits of a Dutch compound ("alcoholonthouding" → "alcohol" + "onthouding"), longest first part
     * last. Both parts have at least 4 letters; a linking "s" ("opiaatsverslaving") is also tried.
     */
    fun compoundSplits(word: String): List<Pair<String, String>> {
        if (word.length < 8) return emptyList()
        val result = mutableListOf<Pair<String, String>>()
        for (i in 4..word.length - 4) {
            val head = word.substring(0, i)
            val tail = word.substring(i)
            result += head to tail
            if (tail.startsWith("s") && tail.length > 4) result += head to tail.substring(1)
        }
        return result
    }

    /** FTS4 prefix query: all terms (AND) or any term (OR). Terms are normalized, so no escaping is needed. */
    fun matchAll(terms: List<String>): String = terms.joinToString(" ") { "$it*" }
    fun matchAny(terms: List<String>): String = terms.joinToString(" OR ") { "$it*" }

    /**
     * TF-IDF score of one entry. [documentFrequency] per term and [total] come from the index.
     * Terms in the question count extra; entries that cover more of the query terms rank higher.
     */
    fun score(terms: List<String>, questionText: String, fullText: String, documentFrequency: Map<String, Int>, total: Int): Double {
        val words = normalize(fullText).split(' ')
        val questionWords = normalize(questionText).split(' ')
        var score = 0.0
        var matched = 0
        for (term in terms) {
            val df = documentFrequency[term] ?: 0
            if (df == 0) continue
            val idf = ln(1.0 + total.toDouble() / (1.0 + df))
            val tf = words.count { it.startsWith(term) }
            if (tf > 0) {
                matched++
                score += (1.0 + ln(tf.toDouble())) * idf
                if (questionWords.any { it.startsWith(term) }) score += 0.7 * idf
            }
        }
        if (matched == 0) return 0.0
        val usable = terms.count { (documentFrequency[it] ?: 0) > 0 }.coerceAtLeast(1)
        val coverage = matched.toDouble() / usable
        val lengthPenalty = sqrt((words.size / 120.0).coerceAtLeast(1.0))
        return score * coverage * coverage / lengthPenalty
    }
}

/**
 * Offline knowledge base for mental health and addiction care: a read-only SQLite file with an FTS4
 * index, built by tools/maak_kennisbank.py from Dutch (machine-translated) Q&A datasets.
 */
class KnowledgeBase(baseDir: File) {

    data class Entry(
        val question: String,
        val answer: String,
        val title: String,
        val source: String,
        val specialty: String,
        val url: String,
        val year: String
    )

    data class Info(val count: Int, val built: String, val sizeBytes: Long)

    private val dir = File(baseDir, "kennisbank").apply { mkdirs() }
    val file = File(dir, FILE_NAME)
    private var db: SQLiteDatabase? = null

    val isInstalled: Boolean get() = file.exists() && file.length() > 0

    @Synchronized
    private fun open(): SQLiteDatabase? {
        db?.let { if (it.isOpen) return it }
        if (!isInstalled) return null
        return SQLiteDatabase.openDatabase(
            file.path, null,
            SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
        ).also { db = it }
    }

    @Synchronized
    fun close() {
        db?.close()
        db = null
    }

    fun info(): Info? = try {
        open()?.let { database ->
            Info(meta(database, "aantal")?.toIntOrNull() ?: 0, meta(database, "gebouwd").orEmpty(), file.length())
        }
    } catch (_: Exception) {
        null
    }

    private fun meta(database: SQLiteDatabase, key: String): String? =
        database.rawQuery("SELECT waarde FROM meta WHERE sleutel = ?", arrayOf(key)).use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }

    /** Best matching entries for a question, best first. */
    fun search(question: String, limit: Int = 4): List<Entry> {
        val database = open() ?: return emptyList()
        val terms = KnowledgeQuery.terms(question)
        if (terms.isEmpty()) return emptyList()
        val total = meta(database, "aantal")?.toIntOrNull() ?: 100_000
        val cache = HashMap<String, Int>()
        fun count(term: String): Int = cache.getOrPut(term) {
            database.rawQuery("SELECT count(*) FROM kennis WHERE kennis MATCH ?", arrayOf("$term*")).use { c ->
                if (c.moveToFirst()) c.getInt(0) else 0
            }
        }
        // A compound that does not occur ("opiaatverslaving") is replaced by its parts ("opiaat", "verslaving").
        val expanded = terms.flatMap { term ->
            if (count(term) > 0) listOf(term)
            else KnowledgeQuery.compoundSplits(term).firstOrNull { (head, tail) -> count(head) > 0 && count(tail) > 0 }
                ?.toList() ?: listOf(term)
        }.distinct()
        val frequency = expanded.associateWith { count(it) }
        // Rarest (most specific) terms first; terms that occur in most entries say little.
        val useful = expanded.filter { (frequency[it] ?: 0) > 0 }.sortedBy { frequency[it] }
        if (useful.isEmpty()) return emptyList()

        val candidates = LinkedHashMap<Long, Entry>()
        fun collect(match: String, max: Int) {
            database.rawQuery(
                "SELECT docid, vraag, antwoord, titel, bron, specialisme, url, jaar FROM kennis WHERE kennis MATCH ? LIMIT $max",
                arrayOf(match)
            ).use { c ->
                while (c.moveToNext()) {
                    candidates.getOrPut(c.getLong(0)) {
                        Entry(c.getString(1), c.getString(2), c.getString(3), c.getString(4), c.getString(5), c.getString(6), c.getString(7))
                    }
                }
            }
        }
        // 1) all terms; 2) all terms but one (so one rare word does not dominate);
        // 3) drop the most common terms one by one; 4) any of the rarest terms.
        collect(KnowledgeQuery.matchAll(useful), 300)
        if (useful.size > 1) useful.forEach { left -> collect(KnowledgeQuery.matchAll(useful - left), 200) }
        var subset = useful
        while (subset.isNotEmpty() && candidates.size < 60) {
            collect(KnowledgeQuery.matchAll(subset), 400)
            if (subset.size == 1) break
            subset = subset.dropLast(1)
        }
        if (candidates.size < 20) collect(KnowledgeQuery.matchAny(useful.take(4)), 600)

        return candidates.values
            .map { it to KnowledgeQuery.score(useful, it.question, "${it.question} ${it.title} ${it.answer}", frequency, total) }
            .filter { it.second > 0.0 }
            .sortedByDescending { it.second }
            .distinctBy { KnowledgeQuery.normalize(it.first.answer).take(120) }
            .take(limit)
            .map { it.first }
    }

    /**
     * Installs a knowledge base from a stream (.sqlite, or .gz when [gzipped]). Writes to a temporary
     * file, checks that it is a valid knowledge base and only then replaces the current one.
     */
    fun install(input: InputStream, gzipped: Boolean, onProgress: (Long) -> Unit = {}): Info {
        val temp = File(dir, "$FILE_NAME.tmp")
        temp.delete()
        try {
            (if (gzipped) GZIPInputStream(input, 64 * 1024) else input).use { source ->
                temp.outputStream().buffered(256 * 1024).use { out ->
                    val buffer = ByteArray(256 * 1024)
                    var written = 0L
                    var lastReport = 0L
                    while (true) {
                        val read = source.read(buffer)
                        if (read < 0) break
                        if (Thread.currentThread().isInterrupted) throw InterruptedException()
                        out.write(buffer, 0, read)
                        written += read
                        if (written - lastReport > 4 * 1024 * 1024) {
                            lastReport = written
                            onProgress(written)
                        }
                    }
                }
            }
            val count = SQLiteDatabase.openDatabase(temp.path, null, SQLiteDatabase.OPEN_READONLY).use { check ->
                val schema = meta(check, "schema") ?: throw IllegalArgumentException("not a knowledge base")
                require(schema == SCHEMA_VERSION) { "unsupported version $schema" }
                check.rawQuery("SELECT count(*) FROM kennis", null).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }
            }
            require(count > 0) { "empty knowledge base" }
            synchronized(this) {
                close()
                file.delete()
                if (!temp.renameTo(file)) throw IllegalStateException("rename failed")
            }
            return info() ?: Info(count, "", file.length())
        } finally {
            temp.delete()
        }
    }

    @Synchronized
    fun delete() {
        close()
        file.delete()
    }

    companion object {
        const val FILE_NAME = "kennisbank.sqlite"
        const val SCHEMA_VERSION = "1"
        const val DOWNLOAD_URL = "https://github.com/marcdriessen/mijn_lokale_ai/releases/download/kennisbank/kennisbank.sqlite.gz"
    }
}
