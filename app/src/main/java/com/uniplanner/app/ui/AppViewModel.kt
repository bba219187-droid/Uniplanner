package com.uniplanner.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.uniplanner.app.data.AppDatabase
import com.uniplanner.app.data.Course
import com.uniplanner.app.data.Deadline
import com.uniplanner.app.data.StudySession
import com.uniplanner.app.data.Workout
import com.uniplanner.app.domain.PlanCourse
import com.uniplanner.app.domain.Planning
import com.uniplanner.app.domain.WeekPlan
import com.uniplanner.app.reminders.ReminderScheduler
import com.uniplanner.app.reminders.toPlan
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

    val studyThisWeek: StateFlow<List<StudySession>> =
        db.studySessions().observeFrom(weekStart)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val weekPlan: StateFlow<WeekPlan?> =
        combine(courses, deadlines) { cs, ds ->
            val now = System.currentTimeMillis()
            Planning.buildWeekPlan(
                now = now,
                zone = zone,
                courses = cs.map { PlanCourse(it.id, it.name) },
                pending = ds.filter { !it.done && it.dueAt >= now }.map { it.toPlan() },
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
}
