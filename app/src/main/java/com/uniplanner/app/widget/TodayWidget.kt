package com.uniplanner.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.uniplanner.app.MainActivity
import com.uniplanner.app.R
import com.uniplanner.app.data.AppDatabase
import com.uniplanner.app.timetable.TimetableStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Home screen widget: today's classes and what is due soon. Refreshes every half hour and on open. */
class TodayWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                render(context, manager, ids)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        /** Asks every widget on the home screen to redraw, after the timetable or the tasks change. */
        fun refresh(ctx: Context) {
            val manager = AppWidgetManager.getInstance(ctx)
            val ids = manager.getAppWidgetIds(ComponentName(ctx, TodayWidget::class.java))
            if (ids.isEmpty()) return
            ctx.sendBroadcast(
                Intent(ctx, TodayWidget::class.java)
                    .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids),
            )
        }

        private fun clock(m: Int) = "%02d:%02d".format(m / 60, m % 60)

        private suspend fun render(ctx: Context, manager: AppWidgetManager, ids: IntArray) {
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)
            val minute = LocalTime.now(zone).let { it.hour * 60 + it.minute }
            val classes = TimetableStore.get(ctx).slots.filter { it.day == today.dayOfWeek.value }.sortedBy { it.start }
            val classLines = classes.filter { it.end >= minute }.take(4).map { c ->
                val room = if (c.room.isNotBlank()) " · ${c.room}" else ""
                "${clock(c.start)}  ${c.course}$room"
            }
            val now = System.currentTimeMillis()
            val week = today.plusDays(7).atStartOfDay(zone).toInstant().toEpochMilli()
            val due = runCatching { AppDatabase.get(ctx).deadlines().getPendingFrom(now) }.getOrDefault(emptyList())
                .filter { it.dueAt < week }.take(3)
            val dueLines = due.map { d ->
                val days = java.time.temporal.ChronoUnit.DAYS.between(today, java.time.Instant.ofEpochMilli(d.dueAt).atZone(zone).toLocalDate())
                val whenText = when (days) {
                    0L -> "hoje"
                    1L -> "amanhã"
                    else -> "em $days dias"
                }
                "• ${d.title} — $whenText"
            }
            val body = buildString {
                if (classLines.isEmpty()) append("Sem mais aulas hoje 🎉") else append(classLines.joinToString("\n"))
                if (dueLines.isNotEmpty()) append("\n\n").append(dueLines.joinToString("\n"))
            }
            val open = PendingIntent.getActivity(
                ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            ids.forEach { id ->
                val views = RemoteViews(ctx.packageName, R.layout.widget_today)
                views.setTextViewText(
                    R.id.widget_date,
                    today.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)).replaceFirstChar { it.titlecase() },
                )
                views.setTextViewText(R.id.widget_body, body)
                views.setOnClickPendingIntent(R.id.widget_root, open)
                manager.updateAppWidget(id, views)
            }
        }
    }
}
