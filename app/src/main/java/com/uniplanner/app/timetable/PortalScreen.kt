package com.uniplanner.app.timetable

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.uniplanner.app.R

private const val IPCA_SIGA = "https://siga.ipca.pt/netpa"

/**
 * The school's portal (IPCA's SIGA or another), opened inside the app. The student signs in and
 * opens the timetable page; the button reads it and remembers the page, so the app can read it
 * again every morning with the same login.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PortalScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val saved = remember { TimetableStore.get(context).let { if (it.source == TimetableSource.PORTAL) it.link else "" } }
    var address by remember { mutableStateOf(saved.ifEmpty { null }) }
    var typing by remember { mutableStateOf(IPCA_SIGA) }
    var progress by remember { mutableIntStateOf(0) }
    var reading by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    var imported by remember { mutableStateOf(false) }

    if (address == null) {
        AlertDialog(
            onDismissRequest = onClose,
            title = { Text(stringResource(R.string.portal_address_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.portal_address_help), style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(typing, { typing = it }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(enabled = typing.isNotBlank(), onClick = {
                    val t = typing.trim()
                    address = if (t.startsWith("http")) t else "https://$t"
                }) { Text(stringResource(R.string.portal_open)) }
            },
            dismissButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.cancel)) } },
        )
        return
    }

    val web = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            settings.userAgentString = settings.userAgentString.replace("; wv", "")
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webViewClient = WebViewClient()
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView, newProgress: Int) {
                    progress = newProgress
                }
            }
            loadUrl(address!!)
        }
    }
    DisposableEffect(web) {
        onDispose {
            CookieManager.getInstance().flush()
            web.destroy()
        }
    }
    BackHandler { if (web.canGoBack()) web.goBack() else onClose() }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, stringResource(R.string.back)) }
            Text(stringResource(R.string.portal_hint), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        }
        if (progress in 1..99) LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
        AndroidView(factory = { web }, modifier = Modifier.weight(1f).fillMaxWidth())
        Button(
            enabled = !reading,
            onClick = {
                reading = true
                web.evaluateJavascript(TimetableSync.READ_PAGE_JS) { json ->
                    reading = false
                    val found = TimetableSync.parsePageResult(json)
                    if (found.isEmpty()) {
                        result = context.getString(R.string.portal_not_found)
                    } else {
                        TimetableStore.imported(context, found, TimetableSource.PORTAL, web.url ?: address!!)
                        CookieManager.getInstance().flush()
                        TimetableSync.schedule(context)
                        imported = true
                        result = context.resources.getQuantityString(R.plurals.portal_imported, found.size, found.size)
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(12.dp),
        ) { Text(stringResource(R.string.portal_read)) }
    }
    result?.let {
        AlertDialog(
            onDismissRequest = { result = null; if (imported) onClose() },
            text = { Text(it) },
            confirmButton = { TextButton(onClick = { result = null; if (imported) onClose() }) { Text(stringResource(R.string.ok)) } },
        )
    }
}
