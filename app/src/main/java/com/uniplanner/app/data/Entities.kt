package com.uniplanner.app.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "courses")
data class Course(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val teacher: String = "",
    val credits: Int = 0,
    val color: Long = 0xFF3F51B5,
)

enum class DeadlineType { TEST, ASSIGNMENT }

@Entity(
    tableName = "deadlines",
    foreignKeys = [
        ForeignKey(
            entity = Course::class,
            parentColumns = ["id"],
            childColumns = ["courseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("courseId")],
)
data class Deadline(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val courseId: Long,
    val type: DeadlineType,
    val title: String,
    /** Epoch millis of the test start or the submission deadline. */
    val dueAt: Long,
    val weightPercent: Int = 0,
    val notes: String = "",
    val done: Boolean = false,
    val grade: Double? = null,
)

@Entity(
    tableName = "study_sessions",
    foreignKeys = [
        ForeignKey(
            entity = Course::class,
            parentColumns = ["id"],
            childColumns = ["courseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("courseId")],
)
data class StudySession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val courseId: Long,
    val startedAt: Long,
    val minutes: Int,
)

@Entity(tableName = "workouts")
data class Workout(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val startsAt: Long,
    val minutes: Int = 60,
    val done: Boolean = false,
)
