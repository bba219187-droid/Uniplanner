package com.uniplanner.app.moodle

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.uniplanner.app.data.AppDatabase
import com.uniplanner.app.data.Course
import com.uniplanner.app.data.Deadline
import com.uniplanner.app.data.DeadlineType
import com.uniplanner.app.reminders.ReminderScheduler
import com.uniplanner.app.ui.screens.CourseColors

data class MoodleAccount(val site: String, val token: String, val userId: Long, val siteName: String, val fullName: String)

data class ImportResult(val courses: Int, val deadlines: Int)

/** Keeps the Moodle token (never the password) encrypted on the phone. */
class MoodleRepository(private val context: Context) {
    private val prefs: SharedPreferences by lazy {
        EncryptedSharedPreferences.create(
            "moodle",
            MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun account(): MoodleAccount? {
        val token = prefs.getString("token", null) ?: return null
        return MoodleAccount(
            site = prefs.getString("site", "").orEmpty(),
            token = token,
            userId = prefs.getLong("userId", 0),
            siteName = prefs.getString("siteName", "").orEmpty(),
            fullName = prefs.getString("fullName", "").orEmpty(),
        )
    }

    fun client(): MoodleClient? = account()?.let { MoodleClient(it.site, it.token) }

    suspend fun connect(site: String, username: String, password: String): MoodleAccount =
        saveAccount(site, MoodleClient.login(site, username, password))

    /** Remembers the site and one-time passport until the university login page sends the student back. */
    fun startBrowserLogin(config: MoodlePublicConfig): String {
        val passport = MoodleSso.newPassport()
        pending.edit().putString("site", config.siteUrl).putString("passport", passport).apply()
        return MoodleSso.launchUrl(config, passport)
    }

    suspend fun finishBrowserLogin(callback: String): MoodleAccount {
        val site = pending.getString("site", null)
        val passport = pending.getString("passport", null)
        if (site == null || passport == null) throw MoodleException("ssofailed", "No sign-in in progress")
        val token = MoodleSso.tokenFromCallback(callback, site, passport)
        pending.edit().clear().apply()
        return saveAccount(site, token)
    }

    private val pending: SharedPreferences by lazy {
        context.getSharedPreferences("moodle_sso_pending", Context.MODE_PRIVATE)
    }

    private suspend fun saveAccount(site: String, token: String): MoodleAccount {
        val client = MoodleClient(site, token)
        val info = client.siteInfo()
        val account = MoodleAccount(client.siteUrl, token, info.userId, info.siteName, info.fullName)
        prefs.edit()
            .putString("site", account.site)
            .putString("token", account.token)
            .putLong("userId", account.userId)
            .putString("siteName", account.siteName)
            .putString("fullName", account.fullName)
            .apply()
        return account
    }

    fun disconnect() {
        prefs.edit().clear().apply()
        CalendarSyncWorker.cancel(context)
    }

    /** Moodle opened inside the app, for universities that switched off the Moodle app service. */
    fun webSite(): String? = prefs.getString("webSite", null)

    fun setWebSite(site: String?) {
        prefs.edit().putString("webSite", site).apply()
    }

    /** The private calendar export link; it lets anyone who has it read the student's Moodle calendar. */
    fun calendarLink(): String? = prefs.getString("calendarLink", null)

    fun setCalendarLink(link: String) {
        prefs.edit().putString("calendarLink", link).apply()
        CalendarSyncWorker.schedule(context)
    }

    /** Downloads the calendar link and adds or updates the upcoming deadlines it lists. */
    suspend fun syncCalendar(): ImportResult {
        val link = calendarLink() ?: return ImportResult(0, 0)
        return importCalendar(MoodleCalendar.parse(MoodleCalendar.fetch(link)))
    }

    private suspend fun importCalendar(events: List<CalendarEvent>): ImportResult {
        val db = AppDatabase.get(context)
        var newCourses = 0
        var newDeadlines = 0
        val now = System.currentTimeMillis()
        val existingCount = db.courses().getAll().size
        val courseIds = mutableMapOf<String, Long>()
        events.filter { it.startsAt > now }.forEach { event ->
            val courseName = event.course ?: "Moodle"
            val courseId = courseIds[courseName.lowercase()]
                ?: db.courses().getAll().firstOrNull { it.name.equals(courseName, ignoreCase = true) }?.id
                ?: run {
                    val color = CourseColors[(existingCount + newCourses) % CourseColors.size]
                    newCourses++
                    db.courses().insert(Course(name = courseName, color = color))
                }
            courseIds[courseName.lowercase()] = courseId
            val type = if (event.isTest) DeadlineType.TEST else DeadlineType.ASSIGNMENT
            val existing = db.deadlines().findByCalendarUid(event.uid)
            if (existing != null) {
                // Teachers move dates; follow them, but keep what the student changed on the item.
                if (existing.dueAt != event.startsAt || existing.title != event.title) {
                    val moved = existing.copy(dueAt = event.startsAt, title = event.title)
                    db.deadlines().update(moved)
                    ReminderScheduler.scheduleDeadline(context, moved)
                }
                return@forEach
            }
            val deadline = Deadline(
                courseId = courseId,
                type = type,
                title = event.title,
                dueAt = event.startsAt,
                calendarUid = event.uid,
            )
            val id = db.deadlines().insert(deadline)
            ReminderScheduler.scheduleDeadline(context, deadline.copy(id = id))
            newDeadlines++
        }
        return ImportResult(newCourses, newDeadlines)
    }

    /** Adds Moodle courses and upcoming assignments to the planner, skipping ones already imported. */
    suspend fun importDeadlines(courses: List<MoodleCourse>, assignments: List<MoodleAssignment>): ImportResult {
        val db = AppDatabase.get(context)
        var newCourses = 0
        var newDeadlines = 0
        val localIds = mutableMapOf<Long, Long>()
        val existingCount = db.courses().getAll().size
        courses.forEach { mc ->
            val local = db.courses().findByMoodleId(mc.id)
                ?: db.courses().getAll().firstOrNull { it.name.equals(mc.name, ignoreCase = true) }
            localIds[mc.id] = local?.id ?: run {
                val color = CourseColors[(existingCount + newCourses) % CourseColors.size]
                newCourses++
                db.courses().insert(Course(name = mc.name, color = color, moodleCourseId = mc.id))
            }
        }
        val now = System.currentTimeMillis()
        assignments.filter { it.dueAt != null && it.dueAt > now }.forEach { a ->
            val courseId = localIds[a.courseId] ?: return@forEach
            if (db.deadlines().findByMoodleAssignId(a.id) != null) return@forEach
            val deadline = Deadline(
                courseId = courseId,
                type = DeadlineType.ASSIGNMENT,
                title = a.name,
                dueAt = a.dueAt!!,
                moodleAssignId = a.id,
            )
            val id = db.deadlines().insert(deadline)
            ReminderScheduler.scheduleDeadline(context, deadline.copy(id = id))
            newDeadlines++
        }
        return ImportResult(newCourses, newDeadlines)
    }
}
