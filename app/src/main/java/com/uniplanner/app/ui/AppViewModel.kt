package com.uniplanner.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.uniplanner.app.data.AppDatabase
import com.uniplanner.app.data.Course
import com.uniplanner.app.data.Deadline
import com.uniplanner.app.data.ExerciseSet
import com.uniplanner.app.domain.Health
import kotlinx.coroutines.flow.map
import com.uniplanner.app.data.StudySession
import com.uniplanner.app.data.Workout
import com.uniplanner.app.domain.PlanCourse
import com.uniplanner.app.domain.Planning
import com.uniplanner.app.domain.WeekPlan
import com.uniplanner.app.reminders.ReminderScheduler
import com.uniplanner.app.reminders.toPlan
import com.uniplanner.app.settings.PersonalSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZoneId

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)
    private val zone = ZoneId.systemDefault()
    private val weekStart = Planning.weekStart(System.currentTimeMillis(), zone)

    val courses: StateFlow<List<Course>> =
        db.courses().observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val deadlines: StateFlow<List<Deadline>> =
        db.deadlines().observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val workouts: StateFlow<List<Workout>> =
        db.workouts().observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val exerciseSets: StateFlow<List<ExerciseSet>> =
        db.exerciseSets().observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The latest recorded body weight, for calorie estimates. */
    val bodyWeight: StateFlow<Double> =
        db.health().observeWeights().map { it.lastOrNull()?.kg ?: Health.DEFAULT_WEIGHT_KG }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Health.DEFAULT_WEIGHT_KG)

    val studyThisWeek: StateFlow<List<StudySession>> =
        db.studySessions().observeFrom(weekStart)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val allStudy: StateFlow<List<StudySession>> =
        db.studySessions().observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val weekPlan: StateFlow<WeekPlan?> =
        combine(courses, deadlines, PersonalSettings.flow(app)) { cs, ds, personal ->
            val now = System.currentTimeMillis()
            Planning.buildWeekPlan(
                now = now,
                zone = zone,
                courses = cs.map { PlanCourse(it.id, it.name) },
                pending = ds.filter { !it.done && it.dueAt >= now }.map { it.toPlan() },
                basePerCourse = Planning.basePerCourse(personal?.weeklyStudyHours, cs.size),
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun addCourse(name: String, teacher: String, credits: Int, color: Long) = viewModelScope.launch {
        db.courses().insert(Course(name = name.trim(), teacher = teacher.trim(), credits = credits, color = color))
    }

    fun deleteCourse(course: Course) = viewModelScope.launch {
        deadlines.value.filter { it.courseId == course.id }
            .forEach { ReminderScheduler.cancelDeadline(getApplication(), it.id) }
        db.courses().delete(course)
    }

    fun updateCourse(course: Course) = viewModelScope.launch { db.courses().update(course) }

    fun updateDeadline(deadline: Deadline) = viewModelScope.launch { db.deadlines().update(deadline) }

    fun addDeadline(deadline: Deadline) = viewModelScope.launch {
        val id = db.deadlines().insert(deadline)
        ReminderScheduler.scheduleDeadline(getApplication(), deadline.copy(id = id))
    }

    fun setDeadlineDone(deadline: Deadline, done: Boolean) = viewModelScope.launch {
        val updated = deadline.copy(done = done)
        db.deadlines().update(updated)
        ReminderScheduler.scheduleDeadline(getApplication(), updated)
    }

    fun deleteDeadline(deadline: Deadline) = viewModelScope.launch {
        ReminderScheduler.cancelDeadline(getApplication(), deadline.id)
        db.deadlines().delete(deadline)
    }

    fun logStudy(courseId: Long, startedAt: Long, minutes: Int) = viewModelScope.launch {
        if (minutes > 0) db.studySessions().insert(StudySession(courseId = courseId, startedAt = startedAt, minutes = minutes))
    }

    fun addWorkout(title: String, startsAt: Long, minutes: Int) = viewModelScope.launch {
        db.workouts().insert(Workout(title = title.trim(), startsAt = startsAt, minutes = minutes))
    }

    fun setWorkoutDone(workout: Workout, done: Boolean) = viewModelScope.launch {
        db.workouts().update(workout.copy(done = done))
    }

    fun deleteWorkout(workout: Workout) = viewModelScope.launch {
        db.workouts().delete(workout)
    }

    /** Adds [count] sets of an exercise to a workout, all with the same reps and weight to start with. */
    fun addSets(workoutId: Long, exercise: String, reps: Int, weightKg: Double, count: Int) = viewModelScope.launch {
        var position = db.exerciseSets().lastPosition(workoutId)
        repeat(count.coerceIn(1, 10)) {
            db.exerciseSets().insert(
                ExerciseSet(workoutId = workoutId, exercise = exercise.trim(), position = ++position, reps = reps, weightKg = weightKg),
            )
        }
    }

    fun updateSet(set: ExerciseSet) = viewModelScope.launch { db.exerciseSets().update(set) }

    fun deleteSet(set: ExerciseSet) = viewModelScope.launch { db.exerciseSets().delete(set) }

    fun updateWorkout(workout: Workout) = viewModelScope.launch { db.workouts().update(workout) }

    /**
     * Creates a workout and, when [copyFrom] is given, the same exercises and sets as that earlier
     * workout, not yet ticked. [onCreated] gets the new id, to open it straight away.
     */
    fun createWorkout(title: String, startsAt: Long, minutes: Int, copyFrom: Long?, onCreated: (Long) -> Unit = {}) =
        viewModelScope.launch {
            val id = db.workouts().insert(Workout(title = title.trim(), startsAt = startsAt, minutes = minutes))
            if (copyFrom != null) {
                db.exerciseSets().insertAll(
                    db.exerciseSets().forWorkout(copyFrom).map { it.copy(id = 0, workoutId = id, done = false) },
                )
            }
            onCreated(id)
        }

    fun deleteExercise(workoutId: Long, exercise: String) = viewModelScope.launch {
        db.exerciseSets().deleteExercise(workoutId, exercise)
    }
}
