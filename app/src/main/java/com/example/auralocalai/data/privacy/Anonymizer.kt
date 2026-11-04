package com.example.auralocalai.data.privacy

/**
 * Removes directly identifying personal data from texts (dictated reports, OCR scans, imported
 * documents) before they are used or stored. Pure Kotlin, offline and deterministic.
 *
 * Replaced: BSN (with 11-check), dates, phone numbers, e-mail, postcodes, street + number, IBAN,
 * client/patient/file numbers and names after a salutation or role, names with initials, and any
 * extra names given (for example names found by the local model).
 */
object Anonymizer {

    data class Result(val text: String, val replacements: Int)

    const val NAME = "[NAAM]"
    const val BSN = "[BSN]"
    const val DATE = "[DATUM]"
    const val PHONE = "[TELEFOON]"
    const val EMAIL = "[E-MAIL]"
    const val POSTCODE = "[POSTCODE]"
    const val ADDRESS = "[ADRES]"
    const val IBAN = "[IBAN]"
    const val NUMBER = "[NUMMER]"

    private val PARTICLES = "(?:van der |van den |van de |van |de |der |den |ter |ten |te |op den |in 't |'t )"
    private val CAP = "[A-ZÀ-ÖØ-Þ][a-zà-öø-ÿ'-]+"
    private val CAP_NAME = "$CAP(?:-$CAP)?"

    private val EMAIL_RE = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
    private val IBAN_RE = Regex("\\b[A-Z]{2}\\d{2}\\s?[A-Z]{4}(?:\\s?\\d{2,4}){2,5}\\b")
    private val ID_FIELD_RE = Regex(
        "(?i)\\b(cli[eë]ntnummer|cli[eë]nt-?nr\\.?|pati[eë]ntnummer|pati[eë]nt-?nr\\.?|dossiernummer|dossier-?nr\\.?|registratienummer|zorgverzekeringsnummer|polisnummer|BSN|burgerservicenummer)\\s*[:#]?\\s*[A-Za-z0-9.\\- ]{3,20}\\d"
    )
    private val BSN_CANDIDATE_RE = Regex("(?<![\\d.])\\d{4}[ .]?\\d{2}[ .]?\\d{3}(?![\\d.])|(?<![\\d.])\\d{3}[ .]?\\d{3}[ .]?\\d{3}(?![\\d.])|(?<!\\d)\\d{8,9}(?!\\d)")
    private val PHONE_RE = Regex("(?<![\\d])(?:\\+31\\s?\\(?0?\\)?\\s?|0031\\s?|0)(?:[1-9][\\d\\s-]{7,11}\\d)(?![\\d])")
    private val MONTHS = "januari|februari|maart|april|mei|juni|juli|augustus|september|oktober|november|december|jan|feb|mrt|apr|jun|jul|aug|sep|sept|okt|nov|dec|january|february|march|may|june|july|october"
    private val DATE_RE = Regex(
        "(?i)\\b(?:\\d{1,2}[-/.]\\d{1,2}[-/.]\\d{2,4}|\\d{4}-\\d{2}-\\d{2}|\\d{1,2}\\s+(?:$MONTHS)\\.?\\s+\\d{4})\\b"
    )
    private val POSTCODE_RE = Regex("\\b[1-9]\\d{3}\\s?(?!SA|SD|SS)[A-Z]{2}\\b")
    private val STREET_RE = Regex(
        "\\b$CAP(?:\\s$CAP)*(?:straat|laan|weg|plein|gracht|dijk|kade|singel|hof|park|steeg|pad|dreef|baan|markt|wal)\\s+\\d{1,5}\\s?[a-zA-Z]?(?:[-\\s]\\d{1,4})?\\b"
    )
    private val SALUTATION_RE = Regex(
        "(?i:\\b(?:dhr|mevr|mw|mr|mrs|ms|meneer|mevrouw|de heer|cli[eë]nt|pati[eë]nt|cli[eë]nte|bewoner|deelnemer|naam)\\b\\.?:?)\\s+(?:[A-Z]\\.\\s?)*$PARTICLES?$CAP_NAME(?:\\s$PARTICLES?$CAP_NAME)?"
    )
    private val INITIALS_RE = Regex("\\b(?:[A-Z]\\.\\s?){1,4}$PARTICLES?$CAP_NAME")

    /** 11-check (elfproef) for Dutch BSN numbers. */
    fun isValidBsn(digits: String): Boolean {
        val d = digits.filter { it.isDigit() }.padStart(9, '0')
        if (d.length != 9 || d.all { it == '0' }) return false
        var sum = 0
        for (i in 0 until 8) sum += (9 - i) * (d[i] - '0')
        sum -= (d[8] - '0')
        return sum % 11 == 0
    }

    fun anonymize(input: String, extraNames: Collection<String> = emptyList()): Result {
        var count = 0
        var text = input

        fun replace(regex: Regex, label: String, keepPrefix: Boolean = false) {
            text = regex.replace(text) { m ->
                count++
                if (keepPrefix) {
                    // Keep the salutation/role word, replace only the name after it.
                    val value = m.value
                    val prefix = Regex("^(?i:(?:dhr|mevr|mw|mr|mrs|ms|meneer|mevrouw|de heer|cli[eë]nt|pati[eë]nt|cli[eë]nte|bewoner|deelnemer|naam)\\.?:?\\s+)").find(value)?.value ?: ""
                    prefix + label
                } else label
            }
        }

        replace(EMAIL_RE, EMAIL)
        replace(IBAN_RE, IBAN)
        // Labelled identifiers keep their label.
        text = ID_FIELD_RE.replace(text) { m ->
            count++
            m.groupValues[1] + ": " + (if (m.groupValues[1].contains("bsn", true) || m.groupValues[1].contains("burgerservice", true)) BSN else NUMBER)
        }
        text = BSN_CANDIDATE_RE.replace(text) { m ->
            val digits = m.value.filter { it.isDigit() }
            if ((digits.length == 9 || digits.length == 8) && isValidBsn(digits)) {
                count++
                BSN
            } else m.value
        }
        replace(DATE_RE, DATE)
        replace(PHONE_RE, PHONE)
        replace(POSTCODE_RE, POSTCODE)
        replace(STREET_RE, ADDRESS)
        replace(SALUTATION_RE, NAME, keepPrefix = true)
        replace(INITIALS_RE, NAME)

        // Names found elsewhere (e.g. by the local model), whole words only, longest first.
        extraNames.map { it.trim() }
            .filter { it.length >= 2 && it.any { c -> c.isUpperCase() } && !it.startsWith("[") }
            .distinct()
            .sortedByDescending { it.length }
            .forEach { name ->
                val re = Regex("(?<![\\p{L}])" + Regex.escape(name) + "(?![\\p{L}])")
                text = re.replace(text) {
                    count++
                    NAME
                }
            }
        return Result(text, count)
    }

    /** Prompt for the local model to list remaining person names (it never rewrites the text itself). */
    const val NAME_FINDER_SYSTEM =
        "You find personal names in a text. Reply with ONLY a comma-separated list of the person names (first names, surnames or full names) that occur in the text, exactly as written. Reply NONE if there are none. Do not list labels in square brackets, professions, medicines, places or organisations."

    fun nameFinderPrompt(text: String): String = "Text:\n${text.take(4_000)}"

    fun parseNames(raw: String?): List<String> {
        if (raw.isNullOrBlank() || raw.trim().equals("NONE", ignoreCase = true)) return emptyList()
        return raw.split(',', '\n', ';').map { it.trim().trim('.', '"', '\'', '-', '*', ' ') }
            .filter { it.length in 2..40 && it.first().isUpperCase() && !it.equals("NONE", true) && !it.startsWith("[") }
            .distinct()
    }
}
