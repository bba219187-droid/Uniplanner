package com.uniplanner.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import com.uniplanner.app.ui.theme.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uniplanner.app.R
import com.uniplanner.app.data.Course
import com.uniplanner.app.data.Deadline
import com.uniplanner.app.domain.CourseGrade
import com.uniplanner.app.domain.GradedItem
import com.uniplanner.app.domain.Grades
import com.uniplanner.app.ui.AppViewModel

@Composable
fun GradesScreen(vm: AppViewModel) {
    val courses by vm.courses.collectAsStateWithLifecycle()
    val deadlines by vm.deadlines.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Course?>(null) }

    if (courses.isEmpty()) {
        EmptyState(stringResource(R.string.grades_empty))
        return
    }
    val byCourse = deadlines.groupBy { it.courseId }
    val grades = courses.associate { c ->
        c.id to Grades.courseGrade(
            c.id, c.credits, c.finalGrade,
            byCourse[c.id].orEmpty().mapNotNull { d -> d.grade?.let { GradedItem(it, d.weightPercent) } },
        )
    }
    val finalAverage = Grades.average(grades.values.toList(), finalOnly = true)
    val currentAverage = Grades.average(grades.values.toList())

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { ScreenHeader(stringResource(R.string.more_grades)) }
        item {
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.grades_average), style = MaterialTheme.typography.titleMedium)
                    Text(
                        finalAverage?.let(Grades::format) ?: "–",
                        style = MaterialTheme.typography.displaySmall,
                    )
                    if (currentAverage != null && currentAverage != finalAverage) {
                        Text(
                            stringResource(R.string.grades_current_average, Grades.format(currentAverage)),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Text(stringResource(R.string.grades_average_help), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item { NeededCalculator(courses, grades) }
        items(courses, key = { it.id }) { course ->
            CourseGradeCard(course, grades.getValue(course.id)) { editing = course }
        }
        item { Spacer(Modifier.padding(8.dp)) }
    }

    editing?.let { course ->
        EditGradesDialog(
            course = course,
            items = byCourse[course.id].orEmpty().sortedBy { it.dueAt },
            onDismiss = { editing = null },
            onSave = { updatedCourse, updatedItems ->
                vm.updateCourse(updatedCourse)
                updatedItems.forEach(vm::updateDeadline)
                editing = null
            },
        )
    }
}

@Composable
private fun CourseGradeCard(course: Course, grade: CourseGrade, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            ColorDot(course.color)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(course.name, style = MaterialTheme.typography.titleMedium)
                val details = buildList {
                    add(
                        if (course.credits > 0) stringResource(R.string.course_credits, course.credits)
                        else stringResource(R.string.grades_no_credits),
                    )
                    if (grade.grade != null && grade.estimated && grade.gradedPercent > 0) {
                        add(stringResource(R.string.grades_graded_percent, grade.gradedPercent))
                    } else if (grade.grade != null && grade.estimated) {
                        add(stringResource(R.string.grades_partial))
                    }
                }.joinToString(" · ")
                Text(details, style = MaterialTheme.typography.bodySmall)
            }
            Text(
                grade.grade?.let { (if (grade.estimated) "~" else "") + Grades.format(it) } ?: "–",
                style = MaterialTheme.typography.headlineSmall,
                color = if (grade.estimated) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
            )
        }
    }
}

private fun parseGrade(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0 }

private fun gradeText(value: Double?): String = value?.let(Grades::format).orEmpty()

@Composable
private fun EditGradesDialog(
    course: Course,
    items: List<Deadline>,
    onDismiss: () -> Unit,
    onSave: (Course, List<Deadline>) -> Unit,
) {
    var finalGrade by remember { mutableStateOf(gradeText(course.finalGrade)) }
    var credits by remember { mutableStateOf(if (course.credits > 0) course.credits.toString() else "") }
    val itemGrades = remember { mutableStateMapOf<Long, String>().apply { items.forEach { put(it.id, gradeText(it.grade)) } } }
    val itemWeights = remember {
        mutableStateMapOf<Long, String>().apply { items.forEach { put(it.id, if (it.weightPercent > 0) it.weightPercent.toString() else "") } }
    }
    val numberKeys = KeyboardOptions(keyboardType = KeyboardType.Decimal)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(course.name) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        finalGrade, { finalGrade = it.take(5) },
                        label = { Text(stringResource(R.string.grades_final)) },
                        singleLine = true, keyboardOptions = numberKeys, modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        credits, { credits = it.filter(Char::isDigit).take(3) },
                        label = { Text(stringResource(R.string.course_credits_label)) },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }
                if (items.isNotEmpty()) {
                    Text(stringResource(R.string.grades_items), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.grades_items_help), style = MaterialTheme.typography.bodySmall)
                }
                items.forEach { d ->
                    Text(d.title, style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            itemGrades[d.id].orEmpty(), { itemGrades[d.id] = it.take(5) },
                            label = { Text(stringResource(R.string.grades_grade)) },
                            singleLine = true, keyboardOptions = numberKeys, modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            itemWeights[d.id].orEmpty(), { itemWeights[d.id] = it.filter(Char::isDigit).take(3) },
                            label = { Text(stringResource(R.string.grades_weight)) },
                            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val updatedCourse = course.copy(
                    finalGrade = parseGrade(finalGrade),
                    credits = credits.toIntOrNull() ?: 0,
                )
                val updatedItems = items.mapNotNull { d ->
                    val changed = d.copy(
                        grade = parseGrade(itemGrades[d.id].orEmpty()),
                        weightPercent = (itemWeights[d.id]?.toIntOrNull() ?: 0).coerceIn(0, 100),
                    )
                    changed.takeIf { it != d }
                }
                onSave(updatedCourse, updatedItems)
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** "How much do I need?": pick a course, the exam's weight and the grade wanted. */
@Composable
private fun NeededCalculator(courses: List<Course>, grades: Map<Long, com.uniplanner.app.domain.CourseGrade>) {
    var courseId by remember { mutableStateOf(courses.firstOrNull()?.id) }
    val g = courseId?.let { grades[it] }
    var current by remember(courseId) { mutableStateOf(g?.takeIf { it.estimated }?.grade?.let(Grades::format).orEmpty()) }
    var graded by remember(courseId) { mutableStateOf((g?.gradedPercent?.takeIf { it in 1..99 } ?: 0).toString()) }
    var exam by remember(courseId) { mutableStateOf((100 - (g?.gradedPercent?.takeIf { it in 1..99 } ?: 0)).toString()) }
    var target by remember { mutableStateOf("9,5") }
    val need = Grades.needed(
        parseGrade(current) ?: 0.0,
        graded.toIntOrNull() ?: 0,
        exam.toIntOrNull() ?: 0,
        parseGrade(target) ?: 9.5,
    )
    Column(
        Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(24.dp))
            .background(androidx.compose.ui.graphics.Color(0xFFF6E6C3)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("🎯 Quanto preciso no exame?", style = MaterialTheme.typography.titleMedium, color = com.uniplanner.app.ui.theme.Ink)
        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(courses, key = { it.id }) { c ->
                androidx.compose.material3.FilterChip(selected = c.id == courseId, onClick = { courseId = c.id }, label = { Text(c.name, maxLines = 1) })
            }
        }
        androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallField(current, { current = it }, "Nota até agora", Modifier.weight(1f))
            SmallField(graded, { graded = it }, "% já avaliada", Modifier.weight(1f))
        }
        androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallField(exam, { exam = it }, "% do exame", Modifier.weight(1f))
            SmallField(target, { target = it }, "Nota que queres", Modifier.weight(1f))
        }
        val ink = com.uniplanner.app.ui.theme.Ink
        when {
            need == null -> Text("Indica quanto vale o exame.", color = ink.copy(alpha = 0.7f))
            need <= 0 -> Text("Já tens a nota garantida! 🎉", style = MaterialTheme.typography.titleLarge, color = ink)
            need > 20 -> Text("Precisarias de ${Grades.format(need)}: não dá só com o exame. Fala com o professor sobre recurso.", color = ink)
            else -> Text(
                "Precisas de ${Grades.format(Math.ceil(need * 10) / 10)} no exame",
                style = MaterialTheme.typography.titleLarge,
                color = ink,
            )
        }
    }
}

@Composable
private fun SmallField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier) {
    androidx.compose.material3.OutlinedTextField(
        value, onChange, label = { Text(label) }, singleLine = true, modifier = modifier,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
    )
}
