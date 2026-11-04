package com.example.auralocalai.data.briefing

/**
 * Sites the user logged in to in the app's own browser (WebView). Their cookies are sent along
 * when the app reads an article, so subscribed (paid) articles can be read and summarised too.
 * Passwords are never seen or stored by the app; the site's own login page is used.
 */
object WebLogins {

    /** Cookie header for a page, from the in-app browser (null outside Android or without cookies). */
    fun cookieFor(url: String): String? = runCatching {
        android.webkit.CookieManager.getInstance().getCookie(url)?.takeIf { it.isNotBlank() }
    }.getOrNull()

    /**
     * True when the user is logged in to this domain in the in-app browser. Ordinary cookies
     * (cookie consent, statistics, bot protection) are set by every visit and do not count.
     */
    fun hasLogin(domain: String): Boolean =
        domain.isNotBlank() && (looksLoggedIn(cookieFor("https://$domain/")) || looksLoggedIn(cookieFor("https://www.$domain/")))

    private val LOGIN_NAME = Regex("sess|auth|token|login|logged|jwt|subscri|member|abonn|user_?id|account|piano|tp_|_sid\b|^sid\b|remember", RegexOption.IGNORE_CASE)
    private val NOT_LOGIN = Regex("consent|cookie_?law|gdpr|didomi|optanon|euconsent|__cf|cf_clearance|_ga|_gid|_gcl|_fb|_hj|utm|ad_?id|^_pk|matomo", RegexOption.IGNORE_CASE)

    /** True when a cookie header contains a cookie whose name points to a login (session, token, subscriber). */
    fun looksLoggedIn(cookieHeader: String?): Boolean =
        cookieHeader.orEmpty().split(';').map { it.substringBefore('=').trim() }
            .any { name -> name.isNotEmpty() && LOGIN_NAME.containsMatchIn(name) && !NOT_LOGIN.containsMatchIn(name) }

    fun clearAll() {
        runCatching {
            android.webkit.CookieManager.getInstance().removeAllCookies(null)
            android.webkit.CookieManager.getInstance().flush()
        }
    }
}
