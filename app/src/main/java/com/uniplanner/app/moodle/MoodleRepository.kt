package com.uniplanner.app.moodle

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
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
            context,
            "moodle",
            MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
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

    suspend fun connect(site: String, username: String, password: String): MoodleAccount {
        val token = MoodleClient.login(site, username, password)
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
