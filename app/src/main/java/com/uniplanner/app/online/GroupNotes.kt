package com.uniplanner.app.online

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uniplanner.app.ui.theme.Bricolage
import com.uniplanner.app.ui.theme.Ink
import com.uniplanner.app.ui.theme.Mono
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

private val noteColors = listOf(0xFFDCE3FC, 0xFFDDEBD0, 0xFFEADCF5, 0xFFF6E6C3, 0xFFFADBD2, 0xFFD7EEF0)

private fun colorOf(course: String) = Color(noteColors[(course.lowercase().hashCode() and 0x7fffffff) % noteColors.size])

/**
 * The group's notes: every message shared for a course, with a search and a filter per course.
 * Notes travel as encrypted chat messages, so they are as private as the rest of the group.
 */
@Composable
fun GroupNotesPage(vm: OnlineViewModel, kind: ChatKind, id: String, messages: List<ChatMessage>, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var course by remember { mutableStateOf<String?>(null) }
    var adding by remember { mutableStateOf(false) }
    val notes = messages.filter { it.course.isNotBlank() }
    val courses = notes.map { it.course }.distinctBy { it.lowercase() }.sortedBy { it.lowercase() }
    val shown = notes.filter { n ->
        (course == null || n.course.equals(course, ignoreCase = true)) &&
            (query.isBlank() || listOf(n.course, n.text, n.authorName, n.attachment?.fileName.orEmpty()).any { it.contains(query.trim(), ignoreCase = true) })
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.padding(top = 8.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
            Text("Apontamentos", fontFamily = Bricolage, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, modifier = Modifier.weight(1f))
            Button(onClick = { adding = true }) { Text("Partilhar") }
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                OutlinedTextField(
                    query, { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    placeholder = { Text("Procurar por cadeira, tema ou ficheiro") },
                )
            }
            if (courses.isNotEmpty()) {
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        item { FilterChip(selected = course == null, onClick = { course = null }, label = { Text("Todas") }) }
                        items(courses) { c ->
                            FilterChip(selected = course.equals(c, ignoreCase = true), onClick = { course = c }, label = { Text(c) })
                        }
                    }
                }
            }
            if (shown.isEmpty()) {
                item {
                    Text(
                        if (notes.isEmpty()) "Ainda não há apontamentos. Partilha resumos, exercícios ou ficheiros e escolhe a cadeira: aparecem aqui para todo o grupo."
                        else "Nada encontrado.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            }
            items(shown, key = { it.id }) { n ->
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(colorOf(n.course))
                        .clickable(enabled = n.attachment != null) {
                            n.attachment?.let { a -> scope.launch { vm.openAttachment(kind, id, a) } }
                        }
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(n.course, fontWeight = FontWeight.Bold, color = Ink, modifier = Modifier.weight(1f))
                        Text(DateFormat.getDateInstance(DateFormat.SHORT).format(Date(n.createdAt)), fontFamily = Mono, fontSize = 11.sp, color = Ink.copy(alpha = 0.6f))
                    }
                    n.attachment?.let { a ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.AttachFile, null, tint = Ink)
                            Text(a.fileName.ifBlank { "Foto" }, color = Ink, fontWeight = FontWeight.Medium)
                        }
                    }
                    if (n.text.isNotBlank()) Text(n.text, color = Ink, maxLines = 8)
                    Text("por ${n.authorName}", fontSize = 12.sp, color = Ink.copy(alpha = 0.6f))
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (adding) {
        AddNoteDialog(
            courses = courses,
            onDismiss = { adding = false },
            onText = { c, t -> vm.sendNote(kind, id, c, t); adding = false },
            onFile = { c, uri -> vm.sendFile(kind, id, uri, c); adding = false },
        )
    }
}

@Composable
private fun AddNoteDialog(
    courses: List<String>,
    onDismiss: () -> Unit,
    onText: (String, String) -> Unit,
    onFile: (String, android.net.Uri) -> Unit,
) {
    var course by remember { mutableStateOf(courses.firstOrNull().orEmpty()) }
    var text by remember { mutableStateOf("") }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && course.isNotBlank()) onFile(course, uri)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Partilhar apontamento") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (courses.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(courses) { c -> FilterChip(selected = c == course, onClick = { course = c }, label = { Text(c) }) }
                    }
                }
                OutlinedTextField(course, { course = it }, label = { Text("Cadeira") }, singleLine = true)
                OutlinedTextField(text, { text = it }, label = { Text("Resumo, dica ou link") }, minLines = 3)
                OutlinedButton(onClick = { pick.launch(arrayOf("*/*")) }, enabled = course.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.AttachFile, null)
                    Text("Enviar ficheiro (PDF, foto…)")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onText(course, text) }, enabled = course.isNotBlank() && text.isNotBlank()) { Text("Partilhar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
