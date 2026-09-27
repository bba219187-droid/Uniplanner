package com.uniplanner.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** One set of an exercise done in a workout: how many reps with how much weight. */
@Entity(
    tableName = "exercise_sets",
    foreignKeys = [
        ForeignKey(
            entity = Workout::class,
            parentColumns = ["id"],
            childColumns = ["workoutId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("workoutId")],
)
data class ExerciseSet(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutId: Long,
    val exercise: String,
    /** Order of the set in the workout, so sets show in the order they were added. */
    val position: Int,
    val reps: Int,
    val weightKg: Double,
    val done: Boolean = false,
)

/** The student's body weight on a day, for the BMI and its history. */
@Entity(tableName = "weights")
data class WeightEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val at: Long,
    val kg: Double,
)

/** A meal in the nutritionist's plan, repeated every day at the same time. */
@Entity(tableName = "meal_plan")
data class PlanMeal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Minutes after midnight, 480 for 08:00. */
    val minuteOfDay: Int,
    val name: String,
    val food: String = "",
    val kcal: Int = 0,
)

/** Something the student ate, from the plan or not. */
@Entity(tableName = "food_log")
data class FoodLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val at: Long,
    val name: String,
    val kcal: Int,
    val mealId: Long? = null,
)

/** Steps counted on one day, "2026-09-27". */
@Entity(tableName = "steps")
data class StepDay(
    @PrimaryKey val day: String,
    val count: Int,
)

@Dao
interface ExerciseSetDao {
    @Query("SELECT * FROM exercise_sets ORDER BY workoutId, position")
    fun observeAll(): Flow<List<ExerciseSet>>

    @Query("SELECT * FROM exercise_sets")
    suspend fun getAll(): List<ExerciseSet>

    @Query("SELECT * FROM exercise_sets WHERE workoutId = :workoutId ORDER BY position")
    suspend fun forWorkout(workoutId: Long): List<ExerciseSet>

    @Query("DELETE FROM exercise_sets WHERE workoutId = :workoutId AND exercise = :exercise")
    suspend fun deleteExercise(workoutId: Long, exercise: String)

    @Query("SELECT COALESCE(MAX(position), -1) FROM exercise_sets WHERE workoutId = :workoutId")
    suspend fun lastPosition(workoutId: Long): Int

    @Insert
    suspend fun insert(set: ExerciseSet): Long

    @Insert
    suspend fun insertAll(sets: List<ExerciseSet>)

    @Update
    suspend fun update(set: ExerciseSet)

    @Delete
    suspend fun delete(set: ExerciseSet)

    @Query("DELETE FROM exercise_sets")
    suspend fun deleteAll()
}

@Dao
interface HealthDao {
    @Query("SELECT * FROM weights ORDER BY at")
    fun observeWeights(): Flow<List<WeightEntry>>

    @Query("SELECT * FROM weights ORDER BY at")
    suspend fun weights(): List<WeightEntry>

    @Insert
    suspend fun insertWeight(w: WeightEntry)

    @Insert
    suspend fun insertWeights(w: List<WeightEntry>)

    @Delete
    suspend fun deleteWeight(w: WeightEntry)

    @Query("SELECT * FROM meal_plan ORDER BY minuteOfDay")
    fun observePlan(): Flow<List<PlanMeal>>

    @Query("SELECT * FROM meal_plan ORDER BY minuteOfDay")
    suspend fun plan(): List<PlanMeal>

    @Insert
    suspend fun insertMeal(m: PlanMeal): Long

    @Insert
    suspend fun insertMeals(m: List<PlanMeal>)

    @Update
    suspend fun updateMeal(m: PlanMeal)

    @Delete
    suspend fun deleteMeal(m: PlanMeal)

    @Query("DELETE FROM meal_plan")
    suspend fun clearPlan()

    @Query("SELECT * FROM food_log WHERE at >= :from ORDER BY at")
    fun observeFood(from: Long): Flow<List<FoodLog>>

    @Query("SELECT * FROM food_log")
    suspend fun food(): List<FoodLog>

    @Insert
    suspend fun insertFood(f: FoodLog)

    @Insert
    suspend fun insertFoods(f: List<FoodLog>)

    @Delete
    suspend fun deleteFood(f: FoodLog)

    @Query("SELECT * FROM steps ORDER BY day")
    fun observeSteps(): Flow<List<StepDay>>

    @Query("SELECT * FROM steps")
    suspend fun steps(): List<StepDay>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putSteps(s: StepDay)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putAllSteps(s: List<StepDay>)

    @Query("DELETE FROM weights")
    suspend fun clearWeights()

    @Query("DELETE FROM food_log")
    suspend fun clearFood()

    @Query("DELETE FROM steps")
    suspend fun clearSteps()
}
