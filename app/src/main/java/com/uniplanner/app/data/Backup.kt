package com.uniplanner.app.data

import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject

/** Everything the student typed into the planner, as saved to their account. */
data class Snapshot(
    val courses: List<Course>,
    val deadlines: List<Deadline>,
    val studySessions: List<StudySession>,
    val workouts: List<Workout>,
    val exerciseSets: List<ExerciseSet> = emptyList(),
    val weights: List<WeightEntry> = emptyList(),
    val mealPlan: List<PlanMeal> = emptyList(),
    val food: List<FoodLog> = emptyList(),
    val steps: List<StepDay> = emptyList(),
    val notes: List<Note> = emptyList(),
) {
    /**
     * Nothing the student planned yet. Steps and weights are left out: a new phone gets them on its
     * own (the step sensor, the welcome questions), and that should not stop the account copy from
     * being restored.
     */
    val isEmpty: Boolean
        get() = courses.isEmpty() && deadlines.isEmpty() && studySessions.isEmpty() && workouts.isEmpty() &&
            exerciseSets.isEmpty() && mealPlan.isEmpty() && food.isEmpty() && notes.isEmpty()
}

object BackupCodec {
    const val FORMAT = 1
    /** How [pack] stores the text, so older copies saved as plain JSON can still be read. */
    const val PACKED = "gzip-base64"

    /**
     * Squeezes the JSON about ten times smaller, so years of workouts and meals stay well under
     * the size one account document can hold.
     */
    fun pack(json: String): String {
        val bytes = java.io.ByteArrayOutputStream()
        java.util.zip.GZIPOutputStream(bytes).use { it.write(json.toByteArray(Charsets.UTF_8)) }
        return java.util.Base64.getEncoder().encodeToString(bytes.toByteArray())
    }

    fun unpack(packed: String): String =
        java.util.zip.GZIPInputStream(java.util.Base64.getDecoder().decode(packed).inputStream())
            .use { it.readBytes().toString(Charsets.UTF_8) }

    fun encode(s: Snapshot): String = JSONObject()
        .put("format", FORMAT)
        .put("courses", JSONArray(s.courses.map { c ->
            JSONObject().put("id", c.id).put("name", c.name).put("teacher", c.teacher).put("credits", c.credits)
                .put("color", c.color).putOpt("moodleCourseId", c.moodleCourseId).putOpt("finalGrade", c.finalGrade)
        }))
        .put("deadlines", JSONArray(s.deadlines.map { d ->
            JSONObject().put("id", d.id).put("courseId", d.courseId).put("type", d.type.name).put("title", d.title)
                .put("dueAt", d.dueAt).put("weightPercent", d.weightPercent).put("notes", d.notes).put("done", d.done)
                .putOpt("grade", d.grade).putOpt("moodleAssignId", d.moodleAssignId).putOpt("calendarUid", d.calendarUid)
        }))
        .put("studySessions", JSONArray(s.studySessions.map { x ->
            JSONObject().put("id", x.id).put("courseId", x.courseId).put("startedAt", x.startedAt).put("minutes", x.minutes)
        }))
        .put("workouts", JSONArray(s.workouts.map { w ->
            JSONObject().put("id", w.id).put("title", w.title).put("startsAt", w.startsAt).put("minutes", w.minutes)
                .put("done", w.done)
        }))
        .put("exerciseSets", JSONArray(s.exerciseSets.map { x ->
            JSONObject().put("id", x.id).put("workoutId", x.workoutId).put("exercise", x.exercise).put("position", x.position)
                .put("reps", x.reps).put("weightKg", x.weightKg).put("done", x.done)
        }))
        .put("weights", JSONArray(s.weights.map { w -> JSONObject().put("id", w.id).put("at", w.at).put("kg", w.kg) }))
        .put("mealPlan", JSONArray(s.mealPlan.map { m ->
            JSONObject().put("id", m.id).put("minuteOfDay", m.minuteOfDay).put("name", m.name).put("food", m.food).put("kcal", m.kcal)
        }))
        .put("food", JSONArray(s.food.map { f ->
            JSONObject().put("id", f.id).put("at", f.at).put("name", f.name).put("kcal", f.kcal).putOpt("mealId", f.mealId)
        }))
        .put("steps", JSONArray(s.steps.map { d -> JSONObject().put("day", d.day).put("count", d.count) }))
        .put("notes", JSONArray(s.notes.map { n ->
            JSONObject().put("id", n.id).putOpt("courseId", n.courseId).put("topic", n.topic).put("lessonDate", n.lessonDate)
                .put("title", n.title).put("text", n.text).put("ink", n.ink).put("createdAt", n.createdAt).put("updatedAt", n.updatedAt)
        }))
        .toString()

    fun decode(text: String): Snapshot {
        val root = JSONObject(text)
        return Snapshot(
            courses = root.optJSONArray("courses").objects().map { o ->
                Course(
                    id = o.getLong("id"),
                    name = o.getString("name"),
                    teacher = o.optString("teacher"),
                    credits = o.optInt("credits"),
                    color = o.optLong("color", 0xFF3F51B5),
                    moodleCourseId = o.longOrNull("moodleCourseId"),
                    finalGrade = o.doubleOrNull("finalGrade"),
                )
            },
            deadlines = root.optJSONArray("deadlines").objects().map { o ->
                Deadline(
                    id = o.getLong("id"),
                    courseId = o.getLong("courseId"),
                    type = runCatching { DeadlineType.valueOf(o.getString("type")) }.getOrDefault(DeadlineType.ASSIGNMENT),
                    title = o.getString("title"),
                    dueAt = o.getLong("dueAt"),
                    weightPercent = o.optInt("weightPercent"),
                    notes = o.optString("notes"),
                    done = o.optBoolean("done"),
                    grade = o.doubleOrNull("grade"),
                    moodleAssignId = o.longOrNull("moodleAssignId"),
                    calendarUid = o.optString("calendarUid").takeIf { o.has("calendarUid") },
                )
            },
            studySessions = root.optJSONArray("studySessions").objects().map { o ->
                StudySession(o.getLong("id"), o.getLong("courseId"), o.getLong("startedAt"), o.getInt("minutes"))
            },
            workouts = root.optJSONArray("workouts").objects().map { o ->
                Workout(o.getLong("id"), o.getString("title"), o.getLong("startsAt"), o.optInt("minutes", 60), o.optBoolean("done"))
            },
            exerciseSets = root.optJSONArray("exerciseSets").objects().map { o ->
                ExerciseSet(
                    o.getLong("id"), o.getLong("workoutId"), o.getString("exercise"), o.optInt("position"),
                    o.optInt("reps"), o.optDouble("weightKg", 0.0), o.optBoolean("done"),
                )
            },
            weights = root.optJSONArray("weights").objects().map { o -> WeightEntry(o.getLong("id"), o.getLong("at"), o.getDouble("kg")) },
            mealPlan = root.optJSONArray("mealPlan").objects().map { o ->
                PlanMeal(o.getLong("id"), o.getInt("minuteOfDay"), o.getString("name"), o.optString("food"), o.optInt("kcal"))
            },
            food = root.optJSONArray("food").objects().map { o ->
                FoodLog(o.getLong("id"), o.getLong("at"), o.getString("name"), o.optInt("kcal"), o.longOrNull("mealId"))
            },
            steps = root.optJSONArray("steps").objects().map { o -> StepDay(o.getString("day"), o.getInt("count")) },
            notes = root.optJSONArray("notes").objects().map { o ->
                Note(
                    o.getLong("id"), o.longOrNull("courseId"), o.optString("topic"), o.getString("lessonDate"), o.optString("title"),
                    o.optString("text"), o.optString("ink"), o.optLong("createdAt"), o.optLong("updatedAt"),
                )
            },
        )
    }

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).map { getJSONObject(it) }

    private fun JSONObject.longOrNull(key: String): Long? = if (has(key) && !isNull(key)) getLong(key) else null
    private fun JSONObject.doubleOrNull(key: String): Double? = if (has(key) && !isNull(key)) getDouble(key) else null
}

suspend fun AppDatabase.snapshot(): Snapshot = Snapshot(
    courses().getAll(),
    deadlines().getAll(),
    studySessions().getAll(),
    workouts().getAll(),
    exerciseSets().getAll(),
    health().weights(),
    health().plan(),
    health().food(),
    health().steps(),
    notes().getAll(),
)

/** Replaces everything on this phone with the snapshot, keeping ids so links between items hold. */
suspend fun AppDatabase.replaceWith(s: Snapshot) = withTransaction {
    deadlines().deleteAll()
    studySessions().deleteAll()
    courses().deleteAll()
    workouts().deleteAll()
    courses().insertAll(s.courses)
    val courseIds = s.courses.map { it.id }.toSet()
    deadlines().insertAll(s.deadlines.filter { it.courseId in courseIds })
    studySessions().insertAll(s.studySessions.filter { it.courseId in courseIds })
    workouts().insertAll(s.workouts)
    exerciseSets().deleteAll()
    val workoutIds = s.workouts.map { it.id }.toSet()
    exerciseSets().insertAll(s.exerciseSets.filter { it.workoutId in workoutIds })
    health().clearWeights()
    health().insertWeights(s.weights)
    health().clearPlan()
    health().insertMeals(s.mealPlan)
    health().clearFood()
    health().insertFoods(s.food)
    health().clearSteps()
    health().putAllSteps(s.steps)
    notes().deleteAll()
    notes().insertAll(s.notes)
}
