package com.uniplanner.app.settings

import android.app.Activity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.uniplanner.app.R
import com.uniplanner.app.ui.screens.PillTabs
import com.uniplanner.app.ui.screens.ScreenHeader

@Composable
fun SettingsScreen() {
    val ctx = LocalContext.current
    val theme by AppSettings.themeFlow(ctx).collectAsState()
    val language = AppSettings.language(ctx)
    val personal by PersonalSettings.flow(ctx).collectAsState()
    var editingProfile by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ScreenHeader(stringResource(R.string.settings_title))
        ProfileCard(personal ?: Personal()) { editingProfile = true }

        Text(
            stringResource(R.string.settings_theme),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, start = 4.dp),
        )
        val modes = ThemeMode.entries
        PillTabs(
            listOf(stringResource(R.string.theme_system), stringResource(R.string.theme_light), stringResource(R.string.theme_dark)),
            selected = modes.indexOf(theme ?: ThemeMode.SYSTEM),
            onSelect = { AppSettings.setTheme(ctx, modes[it]) },
        )
        Text(
            stringResource(R.string.settings_theme_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp),
        )

        Text(
            stringResource(R.string.settings_language),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 16.dp, start = 4.dp),
        )
        Card(Modifier.fillMaxWidth()) {
            AppSettings.languages.forEachIndexed { i, tag ->
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                val title = when (tag) {
                    "" -> stringResource(R.string.lang_system)
                    "pt" -> "Português"
                    else -> "English"
                }
                Row(
                    Modifier.fillMaxWidth().clickable {
                        if (tag != language) {
                            AppSettings.setLanguage(ctx, tag)
                            (ctx as? Activity)?.recreate()
                        }
                    }.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.titleSmall)
                        if (tag.isEmpty()) {
                            Text(
                                stringResource(R.string.lang_system_sub),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    RadioButton(selected = tag == language, onClick = null)
                }
            }
        }

        Text(
            stringResource(R.string.settings_personal),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 16.dp, start = 4.dp),
        )
        Card(Modifier.fillMaxWidth().clickable { PersonalSettings.restart(ctx) }) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(stringResource(R.string.settings_redo), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(R.string.settings_redo_sub),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    if (editingProfile) {
        ProfileDialog(personal ?: Personal(), onDismiss = { editingProfile = false }) {
            PersonalSettings.save(ctx, it)
            editingProfile = false
        }
    }
}
