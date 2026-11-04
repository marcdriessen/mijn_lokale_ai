package com.example.auralocalai.ui.components

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.auralocalai.R
import com.example.auralocalai.data.translate.PageTextExtractor
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * The app's own browser (Android WebView). Log in to a site here once: the session is kept and
 * also used when the app reads articles, so subscribed articles can be summarised. The app never
 * sees the password. Buttons: summarise the page with the local model, and save it in the library.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun InAppBrowser(
    url: String,
    onDismiss: () -> Unit,
    onSummarize: (suspend (title: String, text: String) -> String?)? = null,
    onSave: ((title: String, text: String, url: String) -> Unit)? = null
) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    var title by remember { mutableStateOf(url) }
    var loading by remember { mutableStateOf(true) }
    var summary by remember { mutableStateOf<String?>(null) }
    var summarizing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun close() {
        runCatching { CookieManager.getInstance().flush() }
        webView?.destroy()
        onDismiss()
    }

    /** Readable text of the page as it is shown (after login and scripts). */
    suspend fun pageText(): Pair<String, String> {
        val view = webView ?: return title to ""
        val raw = suspendCoroutine { cont ->
            view.evaluateJavascript("(function(){return document.documentElement.outerHTML;})()") { cont.resume(it) }
        }
        val html = runCatching { (Json.parseToJsonElement(raw) as? JsonPrimitive)?.contentOrNull }.getOrNull().orEmpty()
        val page = PageTextExtractor.extract(html, maxChars = 30_000)
        return (view.title ?: page.title).ifBlank { title } to page.text
    }

    BackHandler {
        val view = webView
        if (view != null && view.canGoBack()) view.goBack() else close()
    }

    Dialog(onDismissRequest = { close() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                    IconButton(onClick = { close() }) { Icon(Icons.Default.Close, stringResource(R.string.dismiss)) }
                    IconButton(onClick = { webView?.let { if (it.canGoBack()) it.goBack() } }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.browser_back))
                    }
                    Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    IconButton(onClick = { webView?.reload() }) { Icon(Icons.Default.Refresh, stringResource(R.string.browser_reload)) }
                    if (onSummarize != null) {
                        IconButton(onClick = {
                            scope.launch {
                                summarizing = true
                                val (t, text) = pageText()
                                summary = if (text.isBlank()) "" else onSummarize(t, text)
                                summarizing = false
                            }
                        }, enabled = !summarizing) { Icon(Icons.Default.AutoAwesome, stringResource(R.string.browser_summarize)) }
                    }
                    if (onSave != null) {
                        IconButton(onClick = {
                            scope.launch {
                                val (t, text) = pageText()
                                onSave(t, text, webView?.url ?: url)
                            }
                        }) { Icon(Icons.Default.BookmarkAdd, stringResource(R.string.browser_save)) }
                    }
                }
                if (loading || summarizing) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                AndroidView(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    factory = { context ->
                        WebView(context).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.loadWithOverviewMode = true
                            settings.useWideViewPort = true
                            CookieManager.getInstance().setAcceptCookie(true)
                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) { loading = true }
                                override fun onPageFinished(view: WebView?, url: String?) {
                                    loading = false
                                    title = view?.title?.ifBlank { url } ?: url.orEmpty()
                                    runCatching { CookieManager.getInstance().flush() }
                                }
                            }
                            webChromeClient = WebChromeClient()
                            loadUrl(url)
                            webView = this
                        }
                    }
                )
            }
        }
    }

    summary?.let { text ->
        AlertDialog(
            onDismissRequest = { summary = null },
            title = { Text(stringResource(R.string.browser_summary_title)) },
            text = {
                Column(modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    Text(text.ifBlank { stringResource(R.string.browser_no_text) })
                }
            },
            confirmButton = { TextButton(onClick = { summary = null }) { Text(stringResource(R.string.dismiss)) } }
        )
    }
}
