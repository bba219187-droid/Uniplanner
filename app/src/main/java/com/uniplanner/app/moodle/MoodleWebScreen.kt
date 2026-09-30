package com.uniplanner.app.moodle

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.uniplanner.app.R
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray

/** Receives the calendar export link the student opened inside the Moodle page. */
object MoodleCalendarCapture {
    val link = MutableStateFlow<String?>(null)
}

/**
 * The university's Moodle, opened inside the app with the student's normal login. Used where the
 * Moodle app service is switched off (as at IPCA), so assignments are still handed in from here.
 * With [calendarPage] it opens Moodle's calendar export and hands back the link it shows.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MoodleWebScreen(calendarPage: Boolean, onClose: () -> Unit) {
    val context = LocalContext.current
    val site = remember {
        val repo = MoodleRepository(context)
        runCatching { repo.webSite() ?: repo.account()?.site }.getOrNull()
    }
    if (site == null) {
        LaunchedEffect(Unit) { onClose() }
        return
    }
    var progress by remember { mutableIntStateOf(0) }
    var canGoBack by remember { mutableStateOf(false) }
    var pendingFiles by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        pendingFiles?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data))
        pendingFiles = null
    }

    val webView = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            // Some university login pages refuse browsers that say they are an app's web view.
            settings.userAgentString = settings.userAgentString.replace("; wv", "")
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val url = request.url
                    if (url.scheme == "http" || url.scheme == "https") return false
                    // mailto:, tel:, intent: and the like belong to other apps.
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url)) }
                    return true
                }

                override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                    canGoBack = view.canGoBack()
                }

                override fun onPageFinished(view: WebView, url: String?) {
                    CookieManager.getInstance().flush()
                    if (url?.contains("/calendar/export") != true) return
                    view.evaluateJavascript("document.documentElement.outerHTML") { result ->
                        val html = runCatching { JSONArray("[$result]").getString(0) }.getOrNull() ?: return@evaluateJavascript
                        val link = MoodleCalendar.findExportLink(html) ?: return@evaluateJavascript
                        MoodleCalendarCapture.link.value = link
                        if (calendarPage) onClose()
                    }
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView, newProgress: Int) {
                    progress = newProgress
                }

                override fun onShowFileChooser(
                    view: WebView,
                    callback: ValueCallback<Array<Uri>>,
                    params: FileChooserParams,
                ): Boolean {
                    pendingFiles?.onReceiveValue(null)
                    pendingFiles = callback
                    return try {
                        filePicker.launch(params.createIntent())
                        true
                    } catch (e: ActivityNotFoundException) {
                        pendingFiles = null
                        false
                    }
                }
            }
            setDownloadListener { url, userAgent, disposition, mime, _ ->
                download(context, url, userAgent, disposition, mime)
            }
            loadUrl(if (calendarPage) "$site/calendar/export.php" else "$site/my/")
        }
    }

    DisposableEffect(webView) {
        onDispose {
            pendingFiles?.onReceiveValue(null)
            webView.destroy()
        }
    }
    BackHandler(enabled = canGoBack) { webView.goBack() }

    Column(Modifier.fillMaxSize()) {
        if (progress in 1..99) LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
        AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())
    }
}

/** Saves files from Moodle (slides, statements) to Downloads, keeping the student's login. */
private fun download(context: Context, url: String, userAgent: String, disposition: String?, mime: String?) {
    val name = URLUtil.guessFileName(url, disposition, mime)
    try {
        val request = DownloadManager.Request(Uri.parse(url))
            .addRequestHeader("Cookie", CookieManager.getInstance().getCookie(url).orEmpty())
            .addRequestHeader("User-Agent", userAgent)
            .setMimeType(mime)
            .setTitle(name)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
        context.getSystemService(DownloadManager::class.java).enqueue(request)
        Toast.makeText(context, context.getString(R.string.moodle_web_downloading, name), Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }
}
