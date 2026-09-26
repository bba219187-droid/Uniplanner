package com.uniplanner.app.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

val CourseColors = listOf(
    0xFF3949AB, 0xFF00897B, 0xFFE53935, 0xFFFB8C00,
    0xFF8E24AA, 0xFF43A047, 0xFF6D4C41, 0xFF1E88E5,
)

fun formatDateTime(millis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(millis))

/** Opens the system date picker, then the time picker, and returns the chosen moment. */
fun pickDateTime(context: Context, initial: Long, onPicked: (Long) -> Unit) {
    val cal = Calendar.getInstance().apply { timeInMillis = initial }
    DatePickerDialog(
        context,
        { _, y, m, d ->
            TimePickerDialog(
                context,
                { _, h, min ->
                    cal.set(y, m, d, h, min, 0)
                    cal.set(Calendar.MILLISECOND, 0)
                    onPicked(cal.timeInMillis)
                },
                cal.get(Calendar.HOUR_OF_DAY),
                cal.get(Calendar.MINUTE),
                true,
            ).show()
        },
        cal.get(Calendar.YEAR),
        cal.get(Calendar.MONTH),
        cal.get(Calendar.DAY_OF_MONTH),
    ).show()
}

@Composable
fun ColorDot(color: Long, modifier: Modifier = Modifier) {
    Box(modifier.size(12.dp).background(Color(color), CircleShape))
}

@Composable
fun EmptyState(text: String) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = modifier.padding(vertical = 8.dp))
}
