package com.uniplanner.app.moodle

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** One event from a Moodle calendar export, already cleaned up for the planner. */
data class CalendarEvent(
    val uid: String,
    val title: String,
    /** Course short name from Moodle, or null for personal and site events. */
    val course: String?,
    val startsAt: Long,
    val isTest: Boolean,
)

/**
 * Reads the calendar link Moodle gives in Calendar > Export calendar. It works even when the
 * university has switched off the Moodle app service, because it needs no sign-in, only the link.
 */
object MoodleCalendar {
    /** Moodle pages contain the link with &amp; escapes; this finds it and returns it clean. */
    private val exportLink = Regex("""https?://[^\s"'<>]+/calendar/export_execute\.php\?[^\s"'<>]+""")

    fun findExportLink(text: String): String? =
        exportLink.find(text)?.value?.replace("&amp;", "&")

    /** Accepts what a student pastes: webcal:// links, stray spaces, or a whole sentence around the link. */
    fun normalizeLink(input: String): String? {
        val text = input.trim().replace(Regex("^webcals?://"), "https://")
        findExportLink(text)?.let { return it }
        val url = text.substringBefore(' ')
        return if (url.startsWith("https://") || url.startsWith("http://")) url else null
    }

    suspend fun fetch(link: String): String = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val connection = URL(link).openConnection() as HttpURLConnection
        connection.connectTimeout = 20_000
        connection.readTimeout = 30_000
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            if (!body.contains("BEGIN:VCALENDAR")) throw MoodleException("notcalendar", "Not a calendar link")
            body
        } finally {
            connection.disconnect()
        }
    }

    /** Events that are only a reminder that something opened are left out: nothing is due then. */
    private val opens = listOf(" opens", " abre", " is open", " está aberto", " está aberta")
    private val dueSuffixes = listOf(
        " is due", " should be completed", " closes", " termina", " fecha", " deve ser entregue", " deve ser concluído",
    )
    private val testWords = listOf("teste", "exame", "frequência", "frequencia", "quiz", "test", "exam", "prova")

    fun parse(ics: String, zone: ZoneId = ZoneId.systemDefault()): List<CalendarEvent> {
        val lines = ics.replace("\r\n", "\n").replace("\n ", "").replace("\n\t", "").split("\n")
        val events = mutableListOf<CalendarEvent>()
        var fields: MutableMap<String, Pair<String, String>>? = null
        for (line in lines) {
            when {
                line == "BEGIN:VEVENT" -> fields = mutableMapOf()
                line == "END:VEVENT" -> {
                    fields?.let { f -> toEvent(f, zone)?.let(events::add) }
                    fields = null
                }
                fields != null && ':' in line -> {
                    val head = line.substringBefore(':')
                    val name = head.substringBefore(';').uppercase()
                    fields.putIfAbsent(name, head to line.substringAfter(':'))
                }
            }
        }
        return events
    }

    private fun toEvent(fields: Map<String, Pair<String, String>>, zone: ZoneId): CalendarEvent? {
        val uid = fields["UID"]?.second?.trim() ?: return null
        val summary = unescape(fields["SUMMARY"]?.second.orEmpty()).trim()
        val (startHead, startValue) = fields["DTSTART"] ?: return null
        val startsAt = parseDate(startHead, startValue.trim(), zone) ?: return null
        val lower = summary.lowercase()
        if (opens.any { lower.endsWith(it) }) return null
        val course = fields["CATEGORIES"]?.second?.let(::unescape)?.split(',')?.firstOrNull()?.trim()
            ?.takeIf { it.isNotEmpty() }
        val suffix = dueSuffixes.firstOrNull { lower.endsWith(it) }
        val title = if (suffix != null) summary.dropLast(suffix.length).trim() else summary
        val closes = suffix == " closes" || suffix == " fecha"
        val isTest = closes || testWords.any { word -> Regex("\\b$word\\b").containsMatchIn(title.lowercase()) }
        return CalendarEvent(uid, title.ifEmpty { summary }, course, startsAt, isTest)
    }

    private fun parseDate(head: String, value: String, zone: ZoneId): Long? = runCatching {
        val params = head.split(';').drop(1).associate { it.substringBefore('=').uppercase() to it.substringAfter('=') }
        when {
            params["VALUE"] == "DATE" || value.length == 8 ->
                // An all-day entry is due by the end of that day.
                LocalDate.parse(value, DateTimeFormatter.BASIC_ISO_DATE).atTime(LocalTime.of(23, 59)).atZone(zone)
            value.endsWith("Z") -> LocalDateTime.parse(value.dropLast(1), stamp).atZone(ZoneOffset.UTC)
            else -> LocalDateTime.parse(value, stamp).atZone(params["TZID"]?.let(ZoneId::of) ?: zone)
        }.toInstant().toEpochMilli()
    }.getOrNull()

    private val stamp = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")

    private fun unescape(text: String) = text
        .replace("\\n", " ").replace("\\N", " ").replace("\\,", ",").replace("\\;", ";").replace("\\\\", "\\")
}
