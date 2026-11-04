package com.example.auralocalai.data.privacy

/**
 * Removes directly identifying personal data from texts (dictated reports, OCR scans, imported
 * documents) before they are used or stored. Pure Kotlin, offline and deterministic.
 *
 * Replaced: BSN (with 11-check), dates, phone numbers, e-mail, postcodes, street + number, IBAN,
 * client/patient/file numbers and names after a salutation or role, names with initials, and any
 * extra names given (for example names found by the local model).
 * Bulgarian (Cyrillic) texts are covered too: ЕГН (with check digit), +359 phone numbers, dates with
 * Bulgarian month names, addresses (ул., бул., ж.к., бл., ап.) and names after г-н, г-жа, пациент, клиент.
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
    const val EGN = "[EGN]"

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

    // --- Bulgarian / Cyrillic ---
    private val NL = "(?<![\\p{L}\\d])"
    private val NR = "(?![\\p{L}\\d])"
    private val CYR_CAP = "[А-ЯЁІЇЄҐ][а-яёіїєґ'’-]+"
    private val CYR_NAME = "$CYR_CAP(?:-$CYR_CAP)?"
    private val CYR_ID_FIELD_RE = Regex(
        "(?iu)${NL}(ЕГН|ЛНЧ|лична карта|л\\.к\\.|номер на лична карта|паспорт|пациент\\s*№|клиент\\s*№|досие\\s*№|ИЗ\\s*№|история на заболяването\\s*№)\\s*[:#№]?\\s*[A-Za-zА-Яа-я0-9.\\- ]{0,20}\\d"
    )
    private val EGN_CANDIDATE_RE = Regex("(?<!\\d)\\d{10}(?!\\d)")
    private val BG_PHONE_RE = Regex("(?<![\\d+])(?:\\+359|00359)[\\s-]?\\(?0?\\)?[\\s-]?[2-9](?:[\\s-]?\\d){6,9}(?!\\d)")
    private val BG_MONTHS = "януари|февруари|март|април|май|юни|юли|август|септември|октомври|ноември|декември"
    private val BG_DATE_RE = Regex("(?iu)$NL\\d{1,2}\\s+(?:$BG_MONTHS)\\s+\\d{4}(?:\\s?г\\.?)?$NR|$NL\\d{1,2}\\.\\d{1,2}\\.\\d{2,4}\\s?г\\.")
    private val BG_ADDRESS_RE = Regex(
        "(?u)$NL(?:ул\\.|улица|бул\\.|булевард|ж\\.\\s?к\\.|жк|кв\\.|пл\\.)\\s*[„\"«]?(?:$CYR_CAP|\\d{1,3}-?\\p{L}*)(?:\\s(?:$CYR_CAP|[А-Я]\\.))*[“\"»]?(?:\\s*(?:№\\s*)?\\d{1,4}[А-Яа-яA-Za-z]?)?" +
            "(?:,?\\s*(?:бл|вх|ет|ап)\\.\\s*[0-9А-Яа-яA-Za-z]{1,4})*"
    )
    private val BG_BLOCK_RE = Regex("(?u)$NL(?:бл|вх|ет|ап)\\.\\s*[0-9А-Яа-яA-Za-z]{1,4}$NR")
    private val CYR_SALUTATION_PREFIX = "(?iu:(?:г-н|г-жа|г-ца|господин|госпожа|госпожица|пациент(?:ът|ка|ката)?|клиент(?:ът|ка|ката)?|име|имена|д-р)\\.?:?)"
    private val CYR_SALUTATION_RE = Regex("$NL$CYR_SALUTATION_PREFIX\\s+(?:[А-Я]\\.\\s?)*$CYR_NAME(?:\\s$CYR_NAME){0,2}")
    private val CYR_INITIALS_RE = Regex("$NL(?:[А-Я]\\.\\s?){1,3}$CYR_NAME")

    /** Check digit of the Bulgarian personal number (ЕГН), including a valid date of birth. */
    fun isValidEgn(digits: String): Boolean {
        if (digits.length != 10 || digits.any { !it.isDigit() }) return false
        val month = digits.substring(2, 4).toInt()
        val day = digits.substring(4, 6).toInt()
        if (month !in 1..12 && month !in 21..32 && month !in 41..52) return false
        if (day !in 1..31) return false
        val weights = intArrayOf(2, 4, 8, 5, 10, 9, 7, 3, 6)
        val sum = (0 until 9).sumOf { weights[it] * (digits[it] - '0') }
        val check = (sum % 11).let { if (it == 10) 0 else it }
        return check == digits[9] - '0'
    }

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
        text = CYR_ID_FIELD_RE.replace(text) { m ->
            count++
            m.groupValues[1] + ": " + (if (m.groupValues[1].equals("ЕГН", true)) EGN else NUMBER)
        }
        text = EGN_CANDIDATE_RE.replace(text) { m ->
            if (isValidEgn(m.value)) {
                count++
                EGN
            } else m.value
        }
        replace(BG_PHONE_RE, PHONE)
        replace(BG_DATE_RE, DATE)
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
        replace(BG_ADDRESS_RE, ADDRESS)
        replace(BG_BLOCK_RE, ADDRESS)
        text = CYR_SALUTATION_RE.replace(text) { m ->
            count++
            val prefix = Regex("^$CYR_SALUTATION_PREFIX\\s+").find(m.value)?.value ?: ""
            prefix + NAME
        }
        replace(CYR_INITIALS_RE, NAME)

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
