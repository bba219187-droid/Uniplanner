package com.uniplanner.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CourseDao {
    @Query("SELECT * FROM courses ORDER BY name")
    fun observeAll(): Flow<List<Course>>

    @Query("SELECT * FROM courses")
    suspend fun getAll(): List<Course>

    @Query("SELECT * FROM courses WHERE moodleCourseId = :moodleId LIMIT 1")
    suspend fun findByMoodleId(moodleId: Long): Course?

    @Insert
    suspend fun insert(course: Course): Long

    @Delete
    suspend fun delete(course: Course)
}

@Dao
interface DeadlineDao {
    @Query("SELECT * FROM deadlines ORDER BY dueAt")
    fun observeAll(): Flow<List<Deadline>>

    @Query("SELECT * FROM deadlines WHERE done = 0 AND dueAt >= :from ORDER BY dueAt")
    suspend fun getPendingFrom(from: Long): List<Deadline>

    @Query("SELECT * FROM deadlines WHERE id = :id")
    suspend fun getById(id: Long): Deadline?

    @Query("SELECT * FROM deadlines WHERE moodleAssignId = :moodleId LIMIT 1")
    suspend fun findByMoodleAssignId(moodleId: Long): Deadline?

    @Insert
    suspend fun insert(deadline: Deadline): Long

    @Update
    suspend fun update(deadline: Deadline)

    @Delete
    suspend fun delete(deadline: Deadline)
}

@Dao
interface StudySessionDao {
    @Query("SELECT * FROM study_sessions WHERE startedAt >= :from ORDER BY startedAt DESC")
    fun observeFrom(from: Long): Flow<List<StudySession>>

    @Query("SELECT * FROM study_sessions WHERE startedAt >= :from")
    suspend fun getFrom(from: Long): List<StudySession>

    @Insert
    suspend fun insert(session: StudySession): Long
}

@Dao
interface WorkoutDao {
    @Query("SELECT * FROM workouts ORDER BY startsAt")
    fun observeAll(): Flow<List<Workout>>

    @Query("SELECT * FROM workouts WHERE startsAt BETWEEN :from AND :to ORDER BY startsAt")
    suspend fun getBetween(from: Long, to: Long): List<Workout>

    @Insert
    suspend fun insert(workout: Workout): Long

    @Update
    suspend fun update(workout: Workout)

    @Delete
    suspend fun delete(workout: Workout)
}
