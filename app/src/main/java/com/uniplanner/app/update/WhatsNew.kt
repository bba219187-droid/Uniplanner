package com.uniplanner.app.update

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.uniplanner.app.BuildConfig
import com.uniplanner.app.R
import org.json.JSONObject

/** The notes of the installed version, shown once after an update, like a phone's "what's new". */
object WhatsNew {
    private const val PREFS = "whats_new"

    fun installed(ctx: Context): ReleaseNotes? = runCatching {
        ReleaseNotes.parse(JSONObject(ctx.assets.open("novidades.json").bufferedReader().use { it.readText() }))
    }.getOrNull()

    /** The notes to show now, or null. A fresh install starts without them; the welcome covers that. */
    fun pending(ctx: Context): ReleaseNotes? {
        val notes = installed(ctx) ?: return null
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getString("seen", null) == notes.id) return null
        val info = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        if (info.firstInstallTime == info.lastUpdateTime) {
            seen(ctx, notes)
            return null
        }
        return notes
    }

    fun seen(ctx: Context, notes: ReleaseNotes) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("seen", notes.id).apply()
    }
}

/** Shown once after updating: what changed in this version. */
@Composable
fun WhatsNewPrompt() {
    val ctx = LocalContext.current
    var notes by remember { mutableStateOf(WhatsNew.pending(ctx)) }
    notes?.let { n ->
        val close = {
            WhatsNew.seen(ctx, n)
            notes = null
        }
        AlertDialog(
            onDismissRequest = close,
            title = { Text(n.title.ifBlank { stringResource(R.string.whats_new_title, BuildConfig.VERSION_NAME) }) },
            text = {
                Column {
                    Text(
                        stringResource(R.string.whats_new_version, BuildConfig.VERSION_NAME),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    NotesBody(n, Modifier.padding(top = 8.dp))
                }
            },
            confirmButton = { TextButton(onClick = close) { Text(stringResource(R.string.whats_new_ok)) } },
        )
    }
}

/** The introduction and the list of changes, scrollable when long. */
@Composable
fun NotesBody(notes: ReleaseNotes, modifier: Modifier = Modifier) {
    Column(
        modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (notes.intro.isNotBlank()) Text(notes.intro, style = MaterialTheme.typography.bodyLarge)
        notes.items.forEach { item ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("•", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
                Text(item, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
