package com.uniplanner.app.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uniplanner.app.R
import kotlinx.coroutines.launch

/** The soft card colours of the design, for the profile picture. */
val AvatarColors = listOf(Color(0xFFDCE3FC), Color(0xFFDDEBD0), Color(0xFFEADCF5), Color(0xFFF6E6C3), Color(0xFFF8D9CE))
val AvatarEmojis = listOf("", "🎓", "📚", "💻", "⚡", "🔬", "🎨", "⚽", "🏋️", "🎧", "☕", "🚀", "🌱", "🦊", "🐼")

/** The student's picture: their emoji, or the first letter of their name, on their colour. */
@Composable
fun Avatar(p: Personal, size: Dp, fallback: String = "") {
    rememberOwnPhoto()?.let { RoundPhoto(it, size); return }
    val letter = (p.firstName.ifBlank { fallback }).take(1).uppercase().ifBlank { "?" }
    Box(
        Modifier.size(size).clip(CircleShape).background(AvatarColors[p.avatarColor.coerceIn(0, AvatarColors.lastIndex)]),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            p.avatar.ifBlank { letter },
            fontSize = (size.value * 0.45f).sp,
            color = Color(0xFF17161B),
        )
    }
}

/** The top of the settings: who the student is, tap to edit. */
@Composable
fun ProfileCard(p: Personal, onEdit: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp))
            .clickable(onClick = onEdit).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(p, 60.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(p.name.ifBlank { stringResource(R.string.profile_add_name) }, style = MaterialTheme.typography.titleLarge)
            val detail = listOfNotNull(
                p.course.takeIf { it.isNotBlank() },
                p.year?.let { stringResource(R.string.profile_year_n, it) },
                p.university.takeIf { it.isNotBlank() },
            ).joinToString(" · ")
            Text(
                detail.ifBlank { stringResource(R.string.profile_hint) },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(Icons.Filled.Edit, stringResource(R.string.profile_edit), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ProfileDialog(initial: Personal, onDismiss: () -> Unit, onSave: (Personal) -> Unit) {
    var p by remember { mutableStateOf(initial) }
    var year by remember { mutableStateOf(initial.year?.toString().orEmpty()) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val hasPhoto = rememberOwnPhoto() != null
    val pick = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia(),
    ) { uri -> if (uri != null) scope.launch { ProfilePhoto.set(ctx, uri) } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.profile_edit)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Avatar(p, 72.dp) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    TextButton(onClick = {
                        pick.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }) { Text(stringResource(if (hasPhoto) R.string.profile_photo_change else R.string.profile_photo_add)) }
                    if (hasPhoto) TextButton(onClick = { ProfilePhoto.remove(ctx) }) { Text(stringResource(R.string.profile_photo_remove)) }
                }
                Text(
                    stringResource(R.string.profile_photo_friends),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    AvatarColors.forEachIndexed { i, c ->
                        Box(
                            Modifier.size(30.dp).clip(CircleShape).background(c)
                                .border(2.dp, if (i == p.avatarColor) MaterialTheme.colorScheme.onSurface else Color.Transparent, CircleShape)
                                .clickable { p = p.copy(avatarColor = i) },
                        )
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AvatarEmojis.forEach { e ->
                        FilterChip(
                            selected = p.avatar == e,
                            onClick = { p = p.copy(avatar = e) },
                            label = { Text(e.ifBlank { stringResource(R.string.profile_letter) }) },
                        )
                    }
                }
                OutlinedTextField(
                    p.name, { p = p.copy(name = it.take(40)) },
                    label = { Text(stringResource(R.string.profile_field_name)) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                )
                OutlinedTextField(
                    p.university, { p = p.copy(university = it.take(60)) },
                    label = { Text(stringResource(R.string.profile_university)) }, singleLine = true,
                )
                OutlinedTextField(
                    p.course, { p = p.copy(course = it.take(80)) },
                    label = { Text(stringResource(R.string.profile_course)) }, singleLine = true,
                )
                OutlinedTextField(
                    year, { year = it.filter(Char::isDigit).take(1) },
                    label = { Text(stringResource(R.string.profile_year)) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(p.copy(year = year.toIntOrNull()?.takeIf { it > 0 })) }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
