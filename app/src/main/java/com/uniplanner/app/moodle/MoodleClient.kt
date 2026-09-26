package com.uniplanner.app.moodle

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.DataOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class MoodleSite(val siteName: String, val fullName: String, val userId: Long)

data class MoodleCourse(val id: Long, val name: String)

data class MoodleAssignment(
    val id: Long,
    val courseId: Long,
    val name: String,
    /** Epoch millis, or null when the teacher set no due date. */
    val dueAt: Long?,
    val acceptsFiles: Boolean,
)

enum class SubmissionState { NEW, DRAFT, SUBMITTED, UNKNOWN }

/** An error Moodle returned, with its own code so the UI can explain common cases. */
class MoodleException(val code: String, message: String) : Exception(message)

/**
 * Talks to a university's Moodle through its official web services, the same
 * API the Moodle mobile app uses. Works only when the site has mobile access on.
 */
class MoodleClient(site: String, private val token: String) {
    val siteUrl = normalizeSite(site)

    suspend fun siteInfo(): MoodleSite =
        MoodleParser.siteInfo(call("core_webservice_get_site_info", emptyMap()) as JSONObject)

    suspend fun courses(userId: Long): List<MoodleCourse> =
        MoodleParser.courses(call("core_enrol_get_users_courses", mapOf("userid" to userId.toString())) as JSONArray)

    suspend fun assignments(courseIds: List<Long>): List<MoodleAssignment> {
        if (courseIds.isEmpty()) return emptyList()
        val params = courseIds.mapIndexed { i, id -> "courseids[$i]" to id.toString() }.toMap()
        return MoodleParser.assignments(call("mod_assign_get_assignments", params) as JSONObject)
    }

    suspend fun submissionState(assignmentId: Long): SubmissionState =
        MoodleParser.submissionState(
            call("mod_assign_get_submission_status", mapOf("assignid" to assignmentId.toString())) as JSONObject,
        )

    /** Uploads the file, attaches it to the assignment and, if asked, submits it for grading. */
    suspend fun submitFile(
        assignmentId: Long,
        fileName: String,
        mimeType: String,
        content: InputStream,
        submitForGrading: Boolean,
    ) {
        val draftItemId = upload(fileName, mimeType, content)
        MoodleParser.checkWarnings(
            call(
                "mod_assign_save_submission",
                mapOf(
                    "assignmentid" to assignmentId.toString(),
                    "plugindata[files_filemanager]" to draftItemId.toString(),
                ),
            ),
        )
        if (submitForGrading) {
            MoodleParser.checkWarnings(
                call(
                    "mod_assign_submit_for_grading",
                    mapOf("assignmentid" to assignmentId.toString(), "acceptsubmissionstatement" to "1"),
                ),
            )
        }
    }

    private suspend fun call(function: String, params: Map<String, String>): Any = withContext(Dispatchers.IO) {
        val form = (params + mapOf("wstoken" to token, "wsfunction" to function, "moodlewsrestformat" to "json"))
            .entries.joinToString("&") { (k, v) -> "${enc(k)}=${enc(v)}" }
        val body = post(URL("$siteUrl/webservice/rest/server.php"), "application/x-www-form-urlencoded") {
            it.write(form.toByteArray())
        }
        MoodleParser.parseResponse(body)
    }

    private suspend fun upload(fileName: String, mimeType: String, content: InputStream): Long =
        withContext(Dispatchers.IO) {
            val boundary = "----UniPlanner${System.currentTimeMillis()}"
            val body = post(URL("$siteUrl/webservice/upload.php"), "multipart/form-data; boundary=$boundary") { out ->
                fun field(name: String, value: String) {
                    out.writeBytes("--$boundary\r\nContent-Disposition: form-data; name=\"$name\"\r\n\r\n$value\r\n")
                }
                field("token", token)
                field("filearea", "draft")
                field("itemid", "0")
                val safeName = fileName.replace("\"", "")
                out.writeBytes("--$boundary\r\nContent-Disposition: form-data; name=\"file_1\"; filename=\"")
                out.write(safeName.toByteArray())
                out.writeBytes("\"\r\nContent-Type: $mimeType\r\n\r\n")
                content.use { it.copyTo(out) }
                out.writeBytes("\r\n--$boundary--\r\n")
            }
            MoodleParser.uploadedItemId(MoodleParser.parseResponse(body))
        }

    companion object {
        /** Exchanges the student's Moodle username and password for a web service token. */
        suspend fun login(site: String, username: String, password: String): String = withContext(Dispatchers.IO) {
            val url = URL(
                "${normalizeSite(site)}/login/token.php",
            )
            val form = "username=${enc(username)}&password=${enc(password)}&service=moodle_mobile_app"
            val body = post(url, "application/x-www-form-urlencoded") { it.write(form.toByteArray()) }
            MoodleParser.token(body)
        }

        fun normalizeSite(site: String): String {
            var s = site.trim().trimEnd('/')
            if (!s.startsWith("http://") && !s.startsWith("https://")) s = "https://$s"
            return s
        }

        private fun enc(v: String) = URLEncoder.encode(v, "UTF-8")

        private fun post(url: URL, contentType: String, write: (DataOutputStream) -> Unit): String {
            val conn = url.openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.connectTimeout = 15_000
                conn.readTimeout = 60_000
                conn.setRequestProperty("Content-Type", contentType)
                DataOutputStream(conn.outputStream).use(write)
                val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
                    ?: throw MoodleException("http", "HTTP ${conn.responseCode}")
                return stream.bufferedReader().readText()
            } finally {
                conn.disconnect()
            }
        }
    }
}

/** JSON handling kept apart from networking so it can be unit-tested. */
object MoodleParser {
    fun parseResponse(body: String): Any {
        val trimmed = body.trim()
        if (trimmed.startsWith("[")) return JSONArray(trimmed)
        if (!trimmed.startsWith("{")) throw MoodleException("notmoodle", "Unexpected response from server")
        val o = JSONObject(trimmed)
        if (o.has("exception") || o.has("error")) {
            throw MoodleException(
                o.optString("errorcode", "error"),
                o.optString("message", o.optString("error", "Moodle error")),
            )
        }
        return o
    }

    fun token(body: String): String {
        val o = parseResponse(body) as? JSONObject ?: throw MoodleException("notmoodle", "Unexpected response")
        return o.optString("token").ifEmpty { throw MoodleException("notoken", "No token returned") }
    }

    fun siteInfo(o: JSONObject) = MoodleSite(
        siteName = o.optString("sitename"),
        fullName = o.optString("fullname"),
        userId = o.getLong("userid"),
    )

    fun courses(a: JSONArray): List<MoodleCourse> = (0 until a.length()).map {
        val c = a.getJSONObject(it)
        MoodleCourse(c.getLong("id"), c.optString("fullname").ifEmpty { c.optString("shortname") })
    }

    fun assignments(o: JSONObject): List<MoodleAssignment> {
        val result = mutableListOf<MoodleAssignment>()
        val courses = o.optJSONArray("courses") ?: return result
        for (i in 0 until courses.length()) {
            val course = courses.getJSONObject(i)
            val list = course.optJSONArray("assignments") ?: continue
            for (j in 0 until list.length()) {
                val a = list.getJSONObject(j)
                val due = a.optLong("duedate", 0L)
                result += MoodleAssignment(
                    id = a.getLong("id"),
                    courseId = a.optLong("course", course.getLong("id")),
                    name = a.optString("name"),
                    dueAt = if (due > 0) due * 1000 else null,
                    acceptsFiles = acceptsFiles(a.optJSONArray("configs")),
                )
            }
        }
        return result
    }

    private fun acceptsFiles(configs: JSONArray?): Boolean {
        if (configs == null) return true
        for (i in 0 until configs.length()) {
            val c = configs.getJSONObject(i)
            if (c.optString("plugin") == "file" && c.optString("subtype") == "assignsubmission" &&
                c.optString("name") == "enabled"
            ) {
                return c.optString("value") == "1"
            }
        }
        return false
    }

    fun submissionState(o: JSONObject): SubmissionState =
        when (o.optJSONObject("lastattempt")?.optJSONObject("submission")?.optString("status")) {
            "new" -> SubmissionState.NEW
            "draft" -> SubmissionState.DRAFT
            "submitted" -> SubmissionState.SUBMITTED
            null -> SubmissionState.NEW
            else -> SubmissionState.UNKNOWN
        }

    fun uploadedItemId(response: Any): Long {
        val first = (response as? JSONArray)?.optJSONObject(0)
            ?: throw MoodleException("upload", "Upload failed")
        if (first.has("error")) throw MoodleException(first.optString("errorcode", "upload"), first.optString("error"))
        return first.getLong("itemid")
    }

    /** save_submission and submit_for_grading answer with warnings instead of exceptions. */
    fun checkWarnings(response: Any) {
        val warnings = when (response) {
            is JSONArray -> response
            is JSONObject -> response.optJSONArray("warnings") ?: JSONArray()
            else -> JSONArray()
        }
        if (warnings.length() > 0) {
            val w = warnings.getJSONObject(0)
            throw MoodleException(w.optString("warningcode", "warning"), w.optString("message", "Moodle warning"))
        }
    }
}
