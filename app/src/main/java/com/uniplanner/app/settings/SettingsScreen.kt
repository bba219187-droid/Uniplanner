package com.uniplanner.app.settings

import android.app.Activity
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uniplanner.app.BuildConfig
import com.uniplanner.app.R
import com.uniplanner.app.ui.screens.PillTabs
import com.uniplanner.app.ui.screens.ScreenHeader
import com.uniplanner.app.ui.theme.Bricolage
import com.uniplanner.app.ui.theme.Ink
import com.uniplanner.app.ui.theme.Mono

/** One tile of the settings grid; [route] opens another screen instead of a section here. */
private data class Section(val key: String, val emoji: String, val title: String, val sub: String, val color: Color, val route: String? = null)

private val sections = listOf(
    Section("look", "🎨", "Aparência", "Temas, claro ou escuro", Color(0xFFEADCF5)),
    Section("language", "🌍", "Idioma", "Português ou inglês", Color(0xFFDCE3FC)),
    Section("calls", "📞", "Chamadas", "Toque das chamadas", Color(0xFFDDEBD0)),
    Section("alerts", "🔔", "Notificações", "Avisos de aulas, refeições e mensagens", Color(0xFFF6E6C3)),
    Section("me", "🧭", "As minhas áreas", "Refazer o questionário inicial", Color(0xFFFADBD2)),
    Section("account", "☁️", "Conta e cópia", "Guardar e repor os teus dados", Color(0xFFD7EEF0), route = "backup"),
    Section("privacy", "📍", "Localização", "Partilha com amigos", Color(0xFFE4E4DC), route = "location"),
    Section("about", "✨", "Sobre", "Versão e novidades", Color(0xFFEFE7DA)),
)

@Composable
fun SettingsScreen(onOpen: (String) -> Unit = {}) {
    val ctx = LocalContext.current
    val personal by PersonalSettings.flow(ctx).collectAsState()
    var editingProfile by remember { mutableStateOf(false) }
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler(enabled = open != null) { open = null }

    AnimatedContent(
        targetState = open,
        transitionSpec = {
            val dir = if (targetState != null) 1 else -1
            (slideInHorizontally(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)) { it * dir / 2 } +
                scaleIn(initialScale = 0.94f) + fadeIn()) togetherWith
                (slideOutHorizontally { -it * dir / 4 } + fadeOut())
        },
        label = "settings",
    ) { key ->
        val section = sections.firstOrNull { it.key == key }
        if (section == null) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ScreenHeader(stringResource(R.string.settings_title))
                ProfileCard(personal ?: Personal()) { editingProfile = true }
                sections.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        pair.forEach { s ->
                            Tile(s, Modifier.weight(1f)) { if (s.route != null) onOpen(s.route) else open = s.key }
                        }
                    }
                }
                Text(
                    "UniPlanner ${BuildConfig.VERSION_NAME} · ${stringResource(R.string.splash_copyright)}",
                    fontFamily = Mono,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        } else {
            SectionPage(section, onBack = { open = null })
        }
    }

    if (editingProfile) {
        ProfileDialog(personal ?: Personal(), onDismiss = { editingProfile = false }) {
            PersonalSettings.save(ctx, it)
            editingProfile = false
        }
    }
}

@Composable
private fun Tile(s: Section, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.aspectRatio(1.05f).clip(RoundedCornerShape(26.dp)).background(s.color).clickable(onClick = onClick).padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
            Text(s.emoji, fontSize = 22.sp)
        }
        Column {
            Text(s.title, fontFamily = MaterialTheme.typography.headlineMedium.fontFamily, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Ink, maxLines = 1)
            Text(s.sub, fontSize = 12.sp, color = Ink.copy(alpha = 0.65f), maxLines = 2, lineHeight = 15.sp)
        }
    }
}

@Composable
private fun SectionPage(s: Section, onBack: () -> Unit) {
    val ctx = LocalContext.current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
        }
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(s.color).padding(20.dp),
        ) {
            Text(s.emoji, fontSize = 34.sp)
            Spacer(Modifier.height(8.dp))
            Text(s.title, fontFamily = MaterialTheme.typography.headlineMedium.fontFamily, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp, color = Ink)
            Text(s.sub, color = Ink.copy(alpha = 0.7f))
        }
        when (s.key) {
            "look" -> Look()
            "language" -> Language()
            "calls" -> Tones()
            "alerts" -> Group {
                Item("Notificações da app", "Escolhe quais avisos recebes e o som de cada um") {
                    ctx.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            }
            "me" -> Group {
                Item(stringResource(R.string.settings_redo), stringResource(R.string.settings_redo_sub)) { PersonalSettings.restart(ctx) }
            }
            "about" -> About()
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Group(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surface),
    ) { content() }
}

@Composable
private fun Item(title: String, sub: String? = null, selected: Boolean? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (selected != null) {
            RadioButton(selected = selected, onClick = null)
        } else {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Look() {
    val ctx = LocalContext.current
    val theme by AppSettings.themeFlow(ctx).collectAsState()
    val modes = ThemeMode.entries
    Text(stringResource(R.string.style_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 4.dp))
    Text(
        stringResource(R.string.style_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp),
    )
    StylePicker()
    Text(stringResource(R.string.style_mode), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 4.dp, top = 8.dp))
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
}

@Composable
private fun Language() {
    val ctx = LocalContext.current
    val language = AppSettings.language(ctx)
    Group {
        AppSettings.languages.forEachIndexed { i, tag ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            val title = when (tag) {
                "" -> stringResource(R.string.lang_system)
                "pt" -> "Português"
                else -> "English"
            }
            Item(title, if (tag.isEmpty()) stringResource(R.string.lang_system_sub) else null, selected = tag == language) {
                if (tag != language) {
                    AppSettings.setLanguage(ctx, tag)
                    (ctx as? Activity)?.recreate()
                }
            }
        }
    }
}

@Composable
private fun Tones() {
    val ctx = LocalContext.current
    var tone by remember { mutableStateOf(com.uniplanner.app.calls.Ringtones.get(ctx)) }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { com.uniplanner.app.calls.Ringtones.stop() } }
    Text(
        "Toca num toque para o ouvires.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp),
    )
    Group {
        com.uniplanner.app.calls.CallTone.entries.forEachIndexed { i, t ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Item(stringResource(t.label), selected = t == tone) {
                tone = t
                com.uniplanner.app.calls.Ringtones.set(ctx, t)
                com.uniplanner.app.calls.Ringtones.play(ctx, t, preview = true)
            }
        }
    }
}

@Composable
private fun About() {
    val ctx = LocalContext.current
    val notes = remember { com.uniplanner.app.update.WhatsNew.installed(ctx) }
    Group {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Versão ${BuildConfig.VERSION_NAME}", fontFamily = Mono)
            Text(stringResource(R.string.splash_copyright), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    notes?.let { n ->
        Group {
            Column(Modifier.padding(18.dp)) {
                Text(n.title.ifBlank { "Novidades" }, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(4.dp))
                com.uniplanner.app.update.NotesBody(n, Modifier.padding(top = 8.dp))
            }
        }
    }
}
