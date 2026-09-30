package com.uniplanner.app.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * One class in the weekly timetable. [key] names a class that came from an import (the school's
 * portal, a calendar link or a photo), so a later import can tell it apart; hand-made classes have
 * none. An imported class the student [edited] keeps the student's version on later imports.
 */
data class ClassSlot(
    val id: String,
    /** 1 = Monday … 7 = Sunday. */
    val day: Int,
    val start: Int,
    val end: Int,
    val course: String,
    val room: String,
    val group: String = "",
    val key: String? = null,
    val edited: Boolean = false,
)

/** A class read from an import, before it gets an id. */
data class ImportedClass(val day: Int, val start: Int, val end: Int, val course: String, val room: String, val group: String = "") {
    val key: String get() = "$day|$start|${course.lowercase().trim()}"
}

object Timetable {
    /**
     * Puts a new import together with what the student already has: classes they made or edited
     * stay as they are, classes they deleted stay deleted, and everything else follows the school.
     */
    fun merge(current: List<ClassSlot>, imported: List<ImportedClass>, deletedKeys: Set<String>, newId: () -> String): List<ClassSlot> {
        val kept = current.filter { it.key == null || it.edited }
        val keptKeys = kept.mapNotNull { it.key }.toSet()
        val fresh = imported.distinctBy { it.key }
            .filter { it.key !in keptKeys && it.key !in deletedKeys }
            .map { c ->
                // The same class keeps its id, so its reminder is not set up twice.
                val old = current.firstOrNull { it.key == c.key }
                ClassSlot(old?.id ?: newId(), c.day, c.start, c.end, c.course, c.room, c.group, c.key)
            }
        return (kept + fresh).sortedWith(compareBy({ it.day }, { it.start }))
    }

    /** The next time this class starts, from [now]. */
    fun nextStart(slot: ClassSlot, now: LocalDateTime): LocalDateTime {
        val today = now.toLocalDate()
        var date = today.plusDays(((slot.day - today.dayOfWeek.value + 7) % 7).toLong())
        var at = date.atStartOfDay().plusMinutes(slot.start.toLong())
        if (!at.isAfter(now)) {
            date = date.plusWeeks(1)
            at = date.atStartOfDay().plusMinutes(slot.start.toLong())
        }
        return at
    }

    /** The class going on now, or the next one to start. */
    fun upcoming(slots: List<ClassSlot>, now: LocalDateTime): Pair<ClassSlot, LocalDateTime>? {
        val minute = now.hour * 60 + now.minute
        slots.firstOrNull { it.day == now.dayOfWeek.value && minute in it.start until it.end }?.let {
            return it to now.toLocalDate().atStartOfDay().plusMinutes(it.start.toLong())
        }
        return slots.map { it to nextStart(it, now) }.minByOrNull { it.second }
    }

    fun time(minute: Int) = "%02d:%02d".format(minute / 60, minute % 60)

    /** "09:30", "9h30", "9.30" or "9" as minutes of the day. */
    fun parseTime(text: String): Int? {
        val m = Regex("""^\s*(\d{1,2})(?:\s*[:hH.]\s*(\d{2}))?\s*$""").find(text) ?: return null
        val h = m.groupValues[1].toInt()
        val min = m.groupValues[2].ifEmpty { "0" }.toInt()
        return if (h in 0..23 && min in 0..59) h * 60 + min else null
    }

    /**
     * A block of the school's timetable, as its lines of text: the course first, then the room and
     * the class group, as in "Lab Eletrónica - T1EEC2" on IPCA's SIGA.
     */
    fun fromCell(day: Int, start: Int, end: Int, lines: List<String>): ImportedClass? {
        val clean = lines.map { it.replace(Regex("\\s+"), " ").trim() }.filter { it.isNotEmpty() }
        if (clean.isEmpty() || day !in 1..7 || end <= start) return null
        if (clean.size == 1) return ImportedClass(day, start, end, clean[0], "")
        val last = clean.last()
        val cut = last.lastIndexOf(" - ")
        val (room, group) = if (cut > 0) last.substring(0, cut).trim() to last.substring(cut + 3).trim() else last to ""
        return ImportedClass(day, start, end, clean.dropLast(1).joinToString(" "), room, group)
    }

    // --- Calendar links (iCal) -------------------------------------------------------------------

    /**
     * Classes from a calendar link. A weekly repeating event is one class. Events that do not repeat
     * become weekly classes from the next two weeks (or the last two, between terms), since school
     * calendars often list every week's class on its own.
     */
    fun fromIcs(ics: String, zone: ZoneId = ZoneId.systemDefault(), today: LocalDate = LocalDate.now(zone)): List<ImportedClass> {
        val lines = ics.replace("\r\n", "\n").replace("\n ", "").replace("\n\t", "").split("\n")
        val events = mutableListOf<Map<String, Pair<String, String>>>()
        var fields: MutableMap<String, Pair<String, String>>? = null
        for (line in lines) {
            when {
                line == "BEGIN:VEVENT" -> fields = mutableMapOf()
                line == "END:VEVENT" -> {
                    fields?.let(events::add)
                    fields = null
                }
                fields != null && ':' in line -> {
                    val head = line.substringBefore(':')
                    fields.putIfAbsent(head.substringBefore(';').uppercase(), head to line.substringAfter(':'))
                }
            }
        }
        data class Timed(val start: ZonedDateTime, val end: ZonedDateTime, val summary: String, val location: String, val weekly: Boolean)
        val timed = events.mapNotNull { f ->
            val (sh, sv) = f["DTSTART"] ?: return@mapNotNull null
            val start = icsDate(sh, sv.trim(), zone) ?: return@mapNotNull null
            val end = f["DTEND"]?.let { (eh, ev) -> icsDate(eh, ev.trim(), zone) } ?: start.plusHours(1)
            val rule = f["RRULE"]?.second.orEmpty().uppercase()
            val until = Regex("UNTIL=(\\d{8})").find(rule)?.groupValues?.get(1)
                ?.let { LocalDate.parse(it, DateTimeFormatter.BASIC_ISO_DATE) }
            val weekly = "FREQ=WEEKLY" in rule && (until == null || !until.isBefore(today))
            Timed(start, end, unescape(f["SUMMARY"]?.second.orEmpty()), unescape(f["LOCATION"]?.second.orEmpty()), weekly)
        }.filter { it.end.isAfter(it.start) && it.summary.isNotBlank() && it.start.toLocalDate() == it.end.toLocalDate() }
        val single = timed.filter { !it.weekly }
        val ahead = single.filter { it.start.toLocalDate() in today..today.plusDays(13) }
        val window = ahead.ifEmpty { single.filter { it.start.toLocalDate() in today.minusDays(14)..today } }
        return (timed.filter { it.weekly } + window).map { t ->
            val s = t.start.toLocalTime()
            val e = t.end.toLocalTime()
            val (room, group) = splitRoom(t.location)
            ImportedClass(t.start.dayOfWeek.value, s.hour * 60 + s.minute, e.hour * 60 + e.minute, t.summary.trim(), room, group)
        }.distinctBy { it.key }
    }

    private fun splitRoom(location: String): Pair<String, String> {
        val cut = location.lastIndexOf(" - ")
        return if (cut > 0) location.substring(0, cut).trim() to location.substring(cut + 3).trim() else location.trim() to ""
    }

    private val stamp = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")

    private fun icsDate(head: String, value: String, zone: ZoneId): ZonedDateTime? = runCatching {
        val params = head.split(';').drop(1).associate { it.substringBefore('=').uppercase() to it.substringAfter('=') }
        when {
            params["VALUE"] == "DATE" || value.length == 8 -> null
            value.endsWith("Z") -> LocalDateTime.parse(value.dropLast(1), stamp).atZone(ZoneOffset.UTC).withZoneSameInstant(zone)
            else -> LocalDateTime.parse(value, stamp).atZone(params["TZID"]?.let(ZoneId::of) ?: zone).withZoneSameInstant(zone)
        }
    }.getOrNull()

    private fun unescape(text: String) = text
        .replace("\\n", " ").replace("\\N", " ").replace("\\,", ",").replace("\\;", ";").replace("\\\\", "\\")

    // --- Photos and PDFs -------------------------------------------------------------------------

    private val dayWords = listOf(
        listOf("segunda", "seg", "monday", "mon"),
        listOf("terça", "terca", "ter", "tuesday", "tue"),
        listOf("quarta", "qua", "wednesday", "wed"),
        listOf("quinta", "qui", "thursday", "thu"),
        listOf("sexta", "sex", "friday", "fri"),
        listOf("sábado", "sabado", "sáb", "sab", "saturday", "sat"),
        listOf("domingo", "dom", "sunday", "sun"),
    )

    fun dayOf(word: String): Int? {
        val w = word.lowercase().trim().trimEnd('.', ':', ',')
        val i = dayWords.indexOfFirst { names -> names.any { w == it || (it.length > 3 && w.startsWith(it)) } }
        return if (i >= 0) i + 1 else null
    }

    private val range = Regex("""(\d{1,2}\s*[:hH.]\s*\d{2})\s*(?:-|–|—|a|às|to)\s*(\d{1,2}\s*[:hH.]\s*\d{2})""")

    /**
     * Classes from the text read off a photo or PDF, when it is written as lines such as
     * "Segunda 09:00-11:00 Cálculo Sala B2.14" or a day heading followed by its classes. Grids that
     * text recognition breaks apart may give nothing; the student checks the result before saving.
     */
    fun fromText(text: String): List<ImportedClass> {
        var day: Int? = null
        val out = mutableListOf<ImportedClass>()
        for (raw in text.lines()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            val words = line.split(Regex("\\s+"))
            dayOf(words.first())?.let { day = it }
            val m = range.find(line) ?: continue
            val d = day ?: continue
            val start = parseTime(m.groupValues[1].replace(" ", "")) ?: continue
            val end = parseTime(m.groupValues[2].replace(" ", "")) ?: continue
            var rest = line.removeRange(m.range).trim()
            if (dayOf(rest.substringBefore(' ')) != null) rest = rest.substringAfter(' ', "").trim()
            rest = rest.trim('-', '–', '|', ',', ' ')
            if (rest.isEmpty() || end <= start) continue
            val roomMatch = Regex("""(?i)\b(sala|lab\w*|audit\w*|anfiteatro|room)\b.*$""").find(rest)
            val course = (if (roomMatch != null) rest.substring(0, roomMatch.range.first) else rest).trim(' ', '-', ',')
            val (room, group) = splitRoom(roomMatch?.value.orEmpty())
            if (course.isNotEmpty()) out += ImportedClass(d, start, end, course, room, group)
        }
        return out.distinctBy { it.key }
    }

    fun dayOfWeek(day: Int): DayOfWeek = DayOfWeek.of(day.coerceIn(1, 7))
}
