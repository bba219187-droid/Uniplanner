package com.uniplanner.app.notes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uniplanner.app.R
import com.uniplanner.app.data.AppDatabase
import com.uniplanner.app.data.Course
import com.uniplanner.app.data.Note
import com.uniplanner.app.online.OnlineViewModel
import com.uniplanner.app.ui.screens.ColorDot
import com.uniplanner.app.ui.screens.PillTabs
import com.uniplanner.app.ui.screens.ScreenHeader
import com.uniplanner.app.ui.screens.SectionTitle
import com.uniplanner.app.ui.theme.frame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Saves outlive the editor, so leaving a note right after writing never loses the last words. */
private val saveScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

private val inkColors = listOf(0xFF111111, 0xFF1E40AF, 0xFFDC2626, 0xFF15803D, 0xFFEA580C, 0xFF7C3AED)
private val inkWidths = listOf(1.5f, 3f, 6f)
private const val ERASER = 2

@Composable
fun NotesScreen(online: OnlineViewModel) {
    val ctx = LocalContext.current
    val db = remember { AppDatabase.get(ctx) }
    val notes by db.notes().observeAll().collectAsState(initial = emptyList())
    val courses by db.courses().observeAll().collectAsState(initial = emptyList())
    var openId by rememberSaveable { mutableStateOf<Long?>(null) }
    val scope = rememberCoroutineScope()
    val newNote: (Long?) -> Unit = { courseId ->
        scope.launch {
            val recent = notes.firstOrNull { it.courseId == courseId }
            openId = db.notes().upsert(Note(courseId = courseId, topic = recent?.topic.orEmpty(), lessonDate = LocalDate.now().toString()))
        }
    }
    val open = notes.firstOrNull { it.id == openId }
    var sharing by remember { mutableStateOf<List<Note>?>(null) }
    val share: (List<Note>) -> Unit = { sharing = it }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth >= 840.dp) {
            Row(Modifier.fillMaxSize()) {
                NoteList(notes, courses, openId, onOpen = { openId = it }, onNew = newNote, onShare = share, modifier = Modifier.weight(0.4f))
                Box(Modifier.weight(0.6f).fillMaxHeight()) {
                    if (open != null) {
                        key(open.id) { NoteEditor(open, courses, notes, onClose = { openId = null }, showBack = false, onShare = share) }
                    } else {
                        Text(
                            stringResource(R.string.notes_pick),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        )
                    }
                }
            }
        } else if (open != null) {
            BackHandler { openId = null }
            key(open.id) { NoteEditor(open, courses, notes, onClose = { openId = null }, showBack = true, onShare = share) }
        } else {
            NoteList(notes, courses, null, onOpen = { openId = it }, onNew = newNote, onShare = share, modifier = Modifier.fillMaxSize())
        }
    }
    sharing?.let { list -> ShareDialog(online, list, courses, onDismiss = { sharing = null }) }
}

/**
 * Sends the notes' text to a friend or a group as a course note, so it also shows on the group's
 * notes page. Messages are end-to-end encrypted like the rest of the chat. Drawings stay on the phone.
 */
@Composable
private fun ShareDialog(online: OnlineViewModel, notes: List<Note>, courses: List<Course>, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val chats by online.conversations.collectAsState()
    val noCourse = stringResource(R.string.notes_title)
    val course = notes.firstNotNullOfOrNull { n -> courses.firstOrNull { it.id == n.courseId }?.name } ?: notes.firstOrNull()?.topic?.ifBlank { null } ?: noCourse
    val text = notes.sortedBy { it.lessonDate }.joinToString("\n\n") { n ->
        listOf(
            n.title.ifBlank { null }?.let { "# $it" },
            listOf(n.topic.ifBlank { null }, prettyDate(n.lessonDate)).filterNotNull().joinToString(" · "),
            n.text.trim().ifBlank { null },
        ).filterNotNull().joinToString("\n")
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.notes_share_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (notes.any { it.ink.isNotEmpty() }) {
                    Text(stringResource(R.string.notes_share_ink), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (chats.isEmpty()) Text(stringResource(R.string.notes_share_none))
                LazyColumn(Modifier.height(320.dp)) {
                    items(chats, key = { it.kind.name + it.id }) { c ->
                        Text(
                            c.title,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable {
                                online.sendNote(c.kind, c.id, course, text)
                                android.widget.Toast.makeText(ctx, ctx.getString(R.string.notes_share_sent, c.title), android.widget.Toast.LENGTH_SHORT).show()
                                onDismiss()
                            }.padding(12.dp),
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.notes_cancel)) } },
    )
}

@Composable
private fun NoteList(
    notes: List<Note>,
    courses: List<Course>,
    selected: Long?,
    onOpen: (Long) -> Unit,
    onNew: (Long?) -> Unit,
    onShare: (List<Note>) -> Unit,
    modifier: Modifier,
) {
    var courseFilter by rememberSaveable { mutableStateOf<Long?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var byClass by rememberSaveable { mutableIntStateOf(0) }
    val noTopic = stringResource(R.string.notes_no_topic)
    val shown = notes.filter { n ->
        (courseFilter == null || n.courseId == courseFilter) &&
            (query.isBlank() || listOf(n.title, n.text, n.topic).any { it.contains(query.trim(), ignoreCase = true) })
    }
    val groups = if (byClass == 0) {
        shown.groupBy { it.topic.ifBlank { noTopic } }.toList().sortedBy { it.first.lowercase() }
    } else {
        shown.groupBy { prettyDate(it.lessonDate) }.toList()
    }
    LazyColumn(modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { ScreenHeader(stringResource(R.string.notes_title), stringResource(R.string.notes_sub)) }
        item {
            Button(onClick = { onNew(courseFilter) }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Icon(Icons.Filled.Add, null)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.notes_new))
            }
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(courseFilter == null, { courseFilter = null }, label = { Text(stringResource(R.string.notes_all)) })
                courses.forEach { c ->
                    FilterChip(
                        courseFilter == c.id,
                        { courseFilter = c.id },
                        label = { Text(c.name, maxLines = 1) },
                        leadingIcon = { ColorDot(c.color) },
                    )
                }
            }
        }
        item {
            OutlinedTextField(
                query,
                { query = it },
                placeholder = { Text(stringResource(R.string.notes_search)) },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            PillTabs(
                listOf(stringResource(R.string.notes_by_topic), stringResource(R.string.notes_by_class)),
                byClass,
                { byClass = it },
            )
        }
        if (shown.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.notes_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            }
        }
        groups.forEach { (label, list) ->
            item(key = "g-$label") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle(label, Modifier.weight(1f).padding(top = 8.dp, start = 4.dp))
                    IconButton(onClick = { onShare(list) }) { Icon(Icons.Filled.Share, stringResource(R.string.notes_share)) }
                }
            }
            items(list, key = { it.id }) { n ->
                val course = courses.firstOrNull { it.id == n.courseId }
                val sel = n.id == selected
                Column(
                    Modifier.fillMaxWidth()
                        .frame(if (sel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface, 18.dp, outlined = true)
                        .clickable { onOpen(n.id) }
                        .padding(14.dp),
                ) {
                    Text(
                        n.title.ifBlank { n.text.lineSequence().firstOrNull { it.isNotBlank() } ?: stringResource(R.string.notes_untitled) },
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        course?.let { ColorDot(it.color) }
                        Text(
                            listOfNotNull(
                                course?.name ?: stringResource(R.string.notes_no_course),
                                if (byClass == 0) prettyDate(n.lessonDate) else n.topic.ifBlank { null },
                                if (n.ink.isNotEmpty()) "✏️" else null,
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

private fun prettyDate(iso: String): String =
    runCatching { LocalDate.parse(iso).format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) }.getOrDefault(iso)

/** What the pen does in the drawing: the strokes, plus the undo and redo history. */
private class Ink(initial: List<InkStroke>) {
    var strokes by mutableStateOf(initial)
    val undoStack = mutableStateListOf<List<InkStroke>>()
    val redoStack = mutableStateListOf<List<InkStroke>>()

    fun change(next: List<InkStroke>, record: Boolean = true) {
        if (record) {
            undoStack.add(strokes)
            redoStack.clear()
        }
        strokes = next
    }

    fun undo() {
        val prev = undoStack.removeLastOrNull() ?: return
        redoStack.add(strokes)
        strokes = prev
    }

    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.add(strokes)
        strokes = next
    }
}

@Composable
private fun NoteEditor(note: Note, courses: List<Course>, all: List<Note>, onClose: () -> Unit, showBack: Boolean, onShare: (List<Note>) -> Unit) {
    val ctx = LocalContext.current
    val db = remember { AppDatabase.get(ctx) }
    // Keyed on the id so switching notes on a tablet starts fresh instead of carrying the old text.
    var title by remember(note.id) { mutableStateOf(note.title) }
    var topic by remember(note.id) { mutableStateOf(note.topic) }
    var courseId by remember(note.id) { mutableStateOf(note.courseId) }
    var date by remember(note.id) { mutableStateOf(note.lessonDate) }
    var text by remember(note.id) { mutableStateOf(note.text) }
    val ink = remember(note.id) { Ink(InkCodec.decode(note.ink)) }
    var drawing by rememberSaveable(note.id) { mutableIntStateOf(if (note.text.isEmpty() && note.ink.isNotEmpty()) 1 else 0) }
    var confirmDelete by remember { mutableStateOf(false) }
    var deleted by remember(note.id) { mutableStateOf(false) }

    val current = { note.copy(courseId = courseId, topic = topic.trim(), lessonDate = date, title = title, text = text, ink = InkCodec.encode(ink.strokes)) }
    val latest by rememberUpdatedState(current)
    val save: () -> Unit = {
        val n = latest()
        if (!deleted && n != note) saveScope.launch { db.notes().upsert(n.copy(updatedAt = System.currentTimeMillis())) }
    }
    LaunchedEffect(note.id, title, topic, courseId, date, text, ink.strokes) {
        delay(800)
        save()
    }
    DisposableEffect(note.id) {
        onDispose {
            val n = latest()
            // A note left empty is not kept.
            if (!deleted && n.title.isBlank() && n.text.isBlank() && n.ink.isEmpty()) {
                saveScope.launch { db.notes().delete(n.id) }
            } else {
                save()
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (showBack) IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.notes_back)) }
            OutlinedTextField(
                title,
                { title = it },
                placeholder = { Text(stringResource(R.string.notes_field_title)) },
                singleLine = true,
                textStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { onShare(listOf(current())) }) { Icon(Icons.Filled.Share, stringResource(R.string.notes_share)) }
            IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.DeleteOutline, stringResource(R.string.notes_delete)) }
        }
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            var menu by remember { mutableStateOf(false) }
            Box {
                val course = courses.firstOrNull { it.id == courseId }
                OutlinedButton(onClick = { menu = true }) {
                    course?.let { ColorDot(it.color); Spacer(Modifier.width(6.dp)) }
                    Text(course?.name ?: stringResource(R.string.notes_no_course), maxLines = 1)
                }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem({ Text(stringResource(R.string.notes_no_course)) }, { courseId = null; menu = false })
                    courses.forEach { c -> DropdownMenuItem({ Text(c.name) }, { courseId = c.id; menu = false }, leadingIcon = { ColorDot(c.color) }) }
                }
            }
            OutlinedButton(onClick = {
                val d = runCatching { LocalDate.parse(date) }.getOrDefault(LocalDate.now())
                android.app.DatePickerDialog(ctx, { _, y, m, day -> date = LocalDate.of(y, m + 1, day).toString() }, d.year, d.monthValue - 1, d.dayOfMonth).show()
            }) { Text(prettyDate(date)) }
            OutlinedTextField(
                topic,
                { topic = it },
                placeholder = { Text(stringResource(R.string.notes_field_topic)) },
                singleLine = true,
                modifier = Modifier.width(220.dp),
            )
        }
        val topics = all.filter { it.courseId == courseId && it.topic.isNotBlank() }.map { it.topic }.distinct().filter { it != topic.trim() }.take(8)
        if (topics.isNotEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                topics.forEach { t -> FilterChip(false, { topic = t }, label = { Text(t, maxLines = 1) }) }
            }
        }
        PillTabs(listOf(stringResource(R.string.notes_write), stringResource(R.string.notes_draw)), drawing, { drawing = it })
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (drawing == 0) {
                OutlinedTextField(
                    text,
                    { text = it },
                    placeholder = { Text(stringResource(R.string.notes_text_hint)) },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                DrawingPad(ink)
            }
        }
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SoonButton(Icons.Filled.AutoAwesome, stringResource(R.string.notes_ai_summary))
            SoonButton(Icons.Filled.Mic, stringResource(R.string.notes_ai_record))
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.notes_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    deleted = true
                    confirmDelete = false
                    saveScope.launch { db.notes().delete(note.id) }
                    onClose()
                }) { Text(stringResource(R.string.notes_delete_yes)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.notes_cancel)) } },
        )
    }
}

/** A feature that needs the AI, shown so the student knows it is coming. */
@Composable
private fun SoonButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    OutlinedButton(onClick = {}, enabled = false) {
        Icon(icon, null, Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, maxLines = 1)
        Spacer(Modifier.width(6.dp))
        Text(
            stringResource(R.string.notes_soon),
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.secondaryContainer)
                .padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun DrawingPad(ink: Ink) {
    var tool by rememberSaveable { mutableIntStateOf(InkStroke.PEN) }
    var color by rememberSaveable { mutableStateOf(inkColors[0]) }
    var widthIx by rememberSaveable { mutableIntStateOf(1) }
    var stylusSeen by rememberSaveable { mutableStateOf(false) }
    var penOnly by rememberSaveable { mutableStateOf(true) }
    val live = remember { mutableStateListOf<Offset>() }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            ToolButton(Icons.Filled.Edit, stringResource(R.string.notes_pen), tool == InkStroke.PEN) { tool = InkStroke.PEN }
            ToolButton(Icons.Filled.Brush, stringResource(R.string.notes_marker), tool == InkStroke.MARKER) { tool = InkStroke.MARKER }
            ToolButton(Icons.Filled.CleaningServices, stringResource(R.string.notes_eraser), tool == ERASER) { tool = ERASER }
            Spacer(Modifier.width(6.dp))
            inkColors.forEach { c ->
                Box(
                    Modifier.padding(3.dp).size(28.dp).clip(CircleShape).background(Color(c))
                        .border(if (c == color) 3.dp else 0.dp, MaterialTheme.colorScheme.primary, CircleShape)
                        .clickable { color = c; if (tool == ERASER) tool = InkStroke.PEN },
                )
            }
            Spacer(Modifier.width(6.dp))
            inkWidths.forEachIndexed { i, w ->
                Box(
                    Modifier.size(36.dp).clip(CircleShape)
                        .background(if (i == widthIx) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                        .clickable { widthIx = i },
                    contentAlignment = Alignment.Center,
                ) { Box(Modifier.size((w * 2.5f).dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface)) }
            }
            IconButton(onClick = { ink.undo() }, enabled = ink.undoStack.isNotEmpty()) { Icon(Icons.AutoMirrored.Filled.Undo, stringResource(R.string.notes_undo)) }
            IconButton(onClick = { ink.redo() }, enabled = ink.redoStack.isNotEmpty()) { Icon(Icons.AutoMirrored.Filled.Redo, stringResource(R.string.notes_redo)) }
            if (stylusSeen) {
                FilterChip(penOnly, { penOnly = !penOnly }, label = { Text(stringResource(R.string.notes_pen_only)) })
            }
        }
        val toolNow by rememberUpdatedState(tool)
        val colorNow by rememberUpdatedState(color)
        val widthNow by rememberUpdatedState(inkWidths[widthIx])
        val penOnlyNow by rememberUpdatedState(stylusSeen && penOnly)
        Canvas(
            Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White).clipToBounds()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val stylus = down.type == PointerType.Stylus || down.type == PointerType.Eraser
                        if (stylus && !stylusSeen) stylusSeen = true
                        // Palm rejection: with a stylus around, the hand resting on the screen does not draw.
                        if (!stylus && penOnlyNow) return@awaitEachGesture
                        val erasing = toolNow == ERASER || down.type == PointerType.Eraser
                        val before = ink.strokes
                        live.clear()
                        var pos = down.position
                        if (!erasing) live.add(pos / density) else ink.change(erase(ink.strokes, pos / density), record = false)
                        down.consume()
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            pos = change.position
                            if (erasing) ink.change(erase(ink.strokes, pos / density), record = false) else live.add(pos / density)
                            change.consume()
                        }
                        if (erasing) {
                            if (ink.strokes.size != before.size) {
                                val after = ink.strokes
                                ink.strokes = before
                                ink.change(after)
                            }
                        } else if (live.isNotEmpty()) {
                            val xy = FloatArray(live.size * 2)
                            live.forEachIndexed { i, p -> xy[i * 2] = p.x; xy[i * 2 + 1] = p.y }
                            val marker = toolNow == InkStroke.MARKER
                            ink.change(
                                ink.strokes + InkStroke(
                                    toolNow,
                                    (if (marker) Color(colorNow).copy(alpha = 0.35f) else Color(colorNow)).toArgb(),
                                    if (marker) widthNow * 4 else widthNow,
                                    xy,
                                ),
                            )
                            live.clear()
                        }
                    }
                },
        ) {
            val line = 32.dp.toPx()
            var y = line
            while (y < size.height) {
                drawLine(Color(0xFFE2E8F0), Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                y += line
            }
            ink.strokes.forEach { s -> drawInk(s.xy, Color(s.color), s.width) }
            if (live.isNotEmpty()) {
                val xy = FloatArray(live.size * 2)
                live.forEachIndexed { i, p -> xy[i * 2] = p.x; xy[i * 2 + 1] = p.y }
                val marker = tool == InkStroke.MARKER
                drawInk(xy, if (marker) Color(color).copy(alpha = 0.35f) else Color(color), if (marker) inkWidths[widthIx] * 4 else inkWidths[widthIx])
            }
        }
    }
}

/** Strokes with a point under the eraser are taken out whole. */
private fun erase(strokes: List<InkStroke>, at: Offset): List<InkStroke> {
    val r = 12f
    val hit = strokes.filter { s ->
        var i = 0
        var found = false
        while (!found && i + 1 < s.xy.size) {
            val dx = s.xy[i] - at.x
            val dy = s.xy[i + 1] - at.y
            found = dx * dx + dy * dy < (r + s.width / 2) * (r + s.width / 2)
            i += 2
        }
        found
    }
    return if (hit.isEmpty()) strokes else strokes - hit.toSet()
}

/** Draws one stroke; [xy] and [width] are in dp. */
private fun DrawScope.drawInk(xy: FloatArray, color: Color, width: Float) {
    val d = density
    if (xy.size == 2) {
        drawCircle(color, width * d / 2, Offset(xy[0] * d, xy[1] * d))
        return
    }
    val path = Path()
    path.moveTo(xy[0] * d, xy[1] * d)
    var i = 2
    while (i + 1 < xy.size) {
        path.lineTo(xy[i] * d, xy[i + 1] * d)
        i += 2
    }
    drawPath(path, color, style = Stroke(width * d, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

@Composable
private fun ToolButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, on: Boolean, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.clip(CircleShape).background(if (on) MaterialTheme.colorScheme.primaryContainer else Color.Transparent),
    ) { Icon(icon, label) }
}
