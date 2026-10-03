package com.uniplanner.app.health

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.uniplanner.app.R
import com.uniplanner.app.ui.theme.Mono
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class HealthMetric { WEIGHT, BMI, STEPS }

/** One value on one day, oldest first in every list. */
data class DayValue(val day: LocalDate, val value: Double, val weightId: Long? = null)

private enum class Range(val days: Long?, val label: Int) {
    WEEK(7, R.string.range_week), MONTH(30, R.string.range_month), YEAR(365, R.string.range_year), ALL(null, R.string.range_all)
}

private val shortDate = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
private val longDate = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())

fun Long.toDay(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()

private fun format(metric: HealthMetric, v: Double): String = when (metric) {
    HealthMetric.STEPS -> "%,d".format(v.toLong())
    else -> String.format(Locale.getDefault(), "%.1f", v)
}

/**
 * The whole history of a measure since the first day: a chart, a few numbers and every entry.
 * Opens when the weight, the BMI or the steps are tapped.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthHistorySheet(
    metric: HealthMetric,
    values: List<DayValue>,
    onDismiss: () -> Unit,
    onAddWeight: () -> Unit,
    onDeleteWeight: (Long) -> Unit,
    stepsHelp: (@Composable () -> Unit)? = null,
) {
    var range by rememberSaveable { mutableStateOf(Range.ALL) }
    val from = range.days?.let { LocalDate.now().minusDays(it - 1) }
    val shown = values.filter { from == null || !it.day.isBefore(from) }
    val color = when (metric) {
        HealthMetric.WEIGHT -> MaterialTheme.colorScheme.secondary
        HealthMetric.BMI -> MaterialTheme.colorScheme.tertiary
        HealthMetric.STEPS -> MaterialTheme.colorScheme.primary
    }
    val unit = when (metric) {
        HealthMetric.WEIGHT -> "kg"
        HealthMetric.BMI -> ""
        HealthMetric.STEPS -> stringResource(R.string.health_steps_unit)
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(
                            when (metric) {
                                HealthMetric.WEIGHT -> R.string.history_weight
                                HealthMetric.BMI -> R.string.history_bmi
                                HealthMetric.STEPS -> R.string.history_steps
                            },
                        ),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, null) }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Range.entries.forEach { r ->
                        FilterChip(selected = r == range, onClick = { range = r }, label = { Text(stringResource(r.label)) })
                    }
                }
            }
            item {
                if (shown.isEmpty()) {
                    Text(
                        stringResource(R.string.history_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 30.dp),
                    )
                } else {
                    HistoryChart(shown, color, bars = metric == HealthMetric.STEPS, metric = metric)
                }
            }
            if (shown.isNotEmpty()) {
                item { Summary(metric, shown, unit) }
            }
            stepsHelp?.let { help -> item { help() } }
            if (metric == HealthMetric.WEIGHT) {
                item {
                    Button(onClick = onAddWeight, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.health_weight_add)) }
                }
            }
            if (shown.isNotEmpty()) {
                item {
                    Text(stringResource(R.string.history_entries), style = MaterialTheme.typography.titleSmall)
                }
                items(shown.reversed()) { v ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(v.day.format(longDate), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Text("${format(metric, v.value)} $unit".trim(), fontFamily = Mono, style = MaterialTheme.typography.bodyMedium)
                        if (v.weightId != null && metric == HealthMetric.WEIGHT) {
                            IconButton(onClick = { onDeleteWeight(v.weightId) }) {
                                Icon(Icons.Filled.Close, stringResource(R.string.delete), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun Summary(metric: HealthMetric, shown: List<DayValue>, unit: String) {
    val cells: List<Pair<Int, String>> = if (metric == HealthMetric.STEPS) {
        val total = shown.sumOf { it.value }
        listOf(
            R.string.history_total to format(metric, total),
            R.string.history_average to format(metric, total / shown.size),
            R.string.history_best to format(metric, shown.maxOf { it.value }),
        )
    } else {
        val first = shown.first().value
        val last = shown.last().value
        val diff = last - first
        listOf(
            R.string.history_start to format(metric, first),
            R.string.history_now to format(metric, last),
            R.string.history_change to (if (diff > 0) "+" else "") + format(metric, diff),
        )
    }
    Row(Modifier.fillMaxWidth()) {
        cells.forEach { (label, value) ->
            Column(Modifier.weight(1f)) {
                Text(stringResource(label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.titleLarge, fontFamily = Mono, maxLines = 1)
                if (unit.isNotEmpty()) Text(unit, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** A chart with the highest and lowest value on the left and the first and last day underneath. */
@Composable
fun HistoryChart(values: List<DayValue>, color: Color, bars: Boolean, metric: HealthMetric) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val grid = MaterialTheme.colorScheme.outlineVariant
    val max = values.maxOf { it.value }
    val min = if (bars) 0.0 else values.minOf { it.value }
    // A little room above and below, so a flat line does not sit on the edge.
    val pad = if (bars) 0.0 else ((max - min) * 0.15).coerceAtLeast(0.5)
    val top = max + pad
    val bottom = (min - pad).let { if (!bars && it < 0) 0.0 else it }
    val span = (top - bottom).takeIf { it > 0 } ?: 1.0
    val firstDay = values.first().day
    val days = (values.last().day.toEpochDay() - firstDay.toEpochDay()).coerceAtLeast(1)

    Column {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.height(180.dp).padding(end = 6.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Text(format(metric, top), style = MaterialTheme.typography.labelSmall, color = muted, fontFamily = Mono)
                Text(format(metric, bottom), style = MaterialTheme.typography.labelSmall, color = muted, fontFamily = Mono)
            }
            Canvas(Modifier.weight(1f).height(180.dp)) {
                val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 8f))
                for (i in 0..3) {
                    val y = size.height * i / 3f
                    drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx(), pathEffect = dash)
                }
                fun yOf(v: Double) = (size.height - (v - bottom) / span * size.height).toFloat()
                fun xOf(d: LocalDate) =
                    if (values.size == 1) size.width / 2 else ((d.toEpochDay() - firstDay.toEpochDay()).toFloat() / days) * size.width
                if (bars) {
                    val w = (size.width / (days + 1)).coerceIn(2f, 28.dp.toPx()) * 0.7f
                    values.forEach { v ->
                        val x = xOf(v.day).coerceIn(w / 2, size.width - w / 2)
                        val y = yOf(v.value)
                        drawRoundRect(color, Offset(x - w / 2, y), Size(w, size.height - y), CornerRadius(w / 2, w / 2))
                    }
                } else {
                    val points = values.map { Offset(xOf(it.day), yOf(it.value)) }
                    val line = Path().apply {
                        moveTo(points.first().x, points.first().y)
                        points.drop(1).forEach { lineTo(it.x, it.y) }
                    }
                    val area = Path().apply {
                        addPath(line)
                        lineTo(points.last().x, size.height)
                        lineTo(points.first().x, size.height)
                        close()
                    }
                    drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = 0.28f), Color.Transparent)))
                    drawPath(line, color, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                    points.forEach { drawCircle(color, radius = 3.5.dp.toPx(), center = it) }
                    drawCircle(Color.White, radius = 3.dp.toPx(), center = points.last())
                    drawCircle(color, radius = 6.dp.toPx(), center = points.last(), style = Stroke(3.dp.toPx()))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text(values.first().day.format(shortDate), style = MaterialTheme.typography.labelSmall, color = muted, modifier = Modifier.weight(1f))
            Text(values.last().day.format(shortDate), style = MaterialTheme.typography.labelSmall, color = muted)
        }
    }
}

/** A button that links Health Connect, for phones whose step sensor the app cannot read. */
@Composable
fun HealthConnectButton(onClick: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            stringResource(R.string.health_connect_why),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.health_connect_link)) }
    }
}
