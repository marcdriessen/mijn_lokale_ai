package com.example.auralocalai.data.briefing

/**
 * Full article text: quality check of extracted text and feed autodetection. Pure Kotlin (tested).
 * Extracted text is only used temporarily for the model; it is not stored in the briefing.
 */
object ArticleContent {

    const val STATUS_FULL = "full"
    const val STATUS_FEED = "feed"
    const val STATUS_SUMMARY_ONLY = "summary_only"
    /** Behind a paywall (or blocked) and no free version of the same news was found. */
    const val STATUS_PAYWALL = "paywall"

    private val NOISE = listOf(
        "cookie", "cookies", "accepteer", "accept all", "privacy", "abonneer", "subscribe", "log in", "inloggen",
        "javascript", "advertentie", "advertisement", "бисквитки", "абонирай"
    )

    /**
     * True when the text looks like a real article: long enough, several paragraphs, and not mainly
     * cookie notices, log-in walls or navigation.
     */
    fun isUsable(text: String): Boolean {
        val paragraphs = text.split(Regex("\n\\s*\n")).map { it.trim() }.filter { it.length >= 40 }
        if (text.length < 500 || paragraphs.size < 3) return false
        val noisy = paragraphs.count { p -> NOISE.any { p.lowercase().contains(it) } }
        return noisy * 3 < paragraphs.size
    }

    /** Whether the reader can actually read an article page. */
    enum class Access { FREE, PAYWALL, BLOCKED, UNREADABLE }

    private val BLOCK_MARKERS = listOf(
        "attention required! | cloudflare", "you have been blocked", "just a moment...", "enable javascript and cookies to continue",
        "access denied", "verify you are human", "captcha"
    )
    private val PAYWALL_MARKERS = listOf(
        // structured data / meta
        "\"isaccessibleforfree\":false", "\"isaccessibleforfree\":\"false\"", "content_tier\" content=\"locked", "content_tier\" content=\"metered",
        // English
        "subscribe to read", "subscribe to continue", "to continue reading", "this article is for subscribers", "subscriber-only", "subscribers only",
        // Dutch
        "alleen voor abonnees", "lees verder met een abonnement", "word abonnee", "log in om verder te lezen", "dit is een premium artikel", "plus-artikel",
        // Bulgarian
        "само за абонати", "абонирайте се, за да прочетете", "абонирай се, за да прочетеш", "съдържанието е достъпно само", "за да прочетете цялата статия",
        "платено съдържание", "достъпно за абонати", "premium-content-paywall", "paywall-overlay", "paywall-container", "стани абонат", "вече сте абонат", "вече имате абонамент", "отключи статията",
        "прочетете цялата статия с абонамент", "тази статия е достъпна само"
    )

    /**
     * Decides from the page and its extracted text whether the article can be read. Paywall words in a
     * footer do not count when the full text is there; they count when the text is short or missing.
     */
    fun access(html: String, text: String): Access {
        val page = html.take(400_000).lowercase().replace(" ", "")
        val pageSpaced = html.take(400_000).lowercase()
        if (BLOCK_MARKERS.any { pageSpaced.contains(it) } && !isUsable(text)) return Access.BLOCKED
        val paywallHint = PAYWALL_MARKERS.any { m -> page.contains(m.replace(" ", "")) }
        return when {
            paywallHint && !isUsable(text) -> Access.PAYWALL
            paywallHint && page.contains("\"isaccessibleforfree\":false") -> Access.PAYWALL
            !isUsable(text) -> Access.UNREADABLE
            else -> Access.FREE
        }
    }

    private val LINK_TAG = Regex("<link\\b[^>]*>", RegexOption.IGNORE_CASE)
    private val ATTR = Regex("(\\w+)\\s*=\\s*[\"']([^\"']*)[\"']")

    /** Finds RSS/Atom feeds announced in a web page (<link rel="alternate" type="application/rss+xml">). */
    fun detectFeeds(html: String, pageUrl: String): List<String> =
        LINK_TAG.findAll(html.take(200_000)).mapNotNull { tag ->
            val attrs = ATTR.findAll(tag.value).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
            val type = attrs["type"]?.lowercase().orEmpty()
            if (attrs["rel"]?.lowercase()?.contains("alternate") != true) return@mapNotNull null
            if ("rss" !in type && "atom" !in type) return@mapNotNull null
            attrs["href"]?.let { resolve(pageUrl, RssParser.decodeEntities(it)) }
        }.distinct().toList()

    /** Makes a relative link absolute. */
    fun resolve(base: String, href: String): String = when {
        href.startsWith("http://") || href.startsWith("https://") -> href
        href.startsWith("//") -> base.substringBefore("//") + href
        href.startsWith("/") -> base.substringBefore("//") + "//" + base.substringAfter("//").substringBefore('/') + href
        else -> base.substringBeforeLast('/') + "/" + href
    }

    /** True when a downloaded body is a feed (RSS, Atom or RDF) rather than a web page. */
    fun looksLikeFeed(body: String): Boolean {
        val head = body.take(2_000).lowercase()
        return "<rss" in head || "<feed" in head || "<rdf:rdf" in head
    }
}
