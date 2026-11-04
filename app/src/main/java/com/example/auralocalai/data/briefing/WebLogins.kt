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

    /** True when the user has a session (cookies) for this domain in the in-app browser. */
    fun hasLogin(domain: String): Boolean =
        domain.isNotBlank() && (cookieFor("https://$domain/") != null || cookieFor("https://www.$domain/") != null)

    fun clearAll() {
        runCatching {
            android.webkit.CookieManager.getInstance().removeAllCookies(null)
            android.webkit.CookieManager.getInstance().flush()
        }
    }
}
