package com.example.auralocalai.data.briefing

/**
 * Sites where most articles are behind a paywall. Their items are skipped in briefings,
 * because the reader cannot open them (Google News often links to these).
 */
object Paywall {

    val DOMAINS = setOf(
        // Netherlands / Belgium
        "nrc.nl", "volkskrant.nl", "fd.nl", "trouw.nl", "parool.nl", "telegraaf.nl", "ad.nl", "destentor.nl", "tubantia.nl",
        "bndestem.nl", "pzc.nl", "ed.nl", "gelderlander.nl", "bd.nl", "limburger.nl", "lc.nl", "dvhn.nl", "standaard.be", "tijd.be",
        "ftm.nl", "mt.nl",
        // International
        "nytimes.com", "wsj.com", "ft.com", "bloomberg.com", "economist.com", "washingtonpost.com", "thetimes.co.uk",
        "telegraph.co.uk", "theatlantic.com", "newyorker.com", "barrons.com", "handelsblatt.com", "faz.net", "sueddeutsche.de",
        "lemonde.fr", "lefigaro.fr", "nzz.ch", "politico.eu", "statnews.com", "thelancet.com", "nejm.org", "bmj.com",
        // Bulgaria (Dnevnik and Capital: full articles for subscribers only)
        "capital.bg", "dnevnik.bg"
    )

    /** Domain of a URL without "www." (empty when it cannot be read). */
    fun domainOf(url: String): String =
        url.substringAfter("://", url).substringBefore('/').substringBefore('?').substringBefore(':').lowercase().removePrefix("www.")

    fun isPaywalled(item: NewsItem): Boolean {
        val domains = listOf(domainOf(item.link), item.publisherDomain.lowercase().removePrefix("www."))
        return domains.any { d -> d.isNotEmpty() && DOMAINS.any { p -> d == p || d.endsWith(".$p") } }
    }
}
