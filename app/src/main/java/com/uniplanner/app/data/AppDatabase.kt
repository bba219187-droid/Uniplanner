package com.uniplanner.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Converters {
    @TypeConverter
    fun fromType(type: DeadlineType): String = type.name

    @TypeConverter
    fun toType(value: String): DeadlineType = DeadlineType.valueOf(value)
}

@Database(
    entities = [
        Course::class, Deadline::class, StudySession::class, Workout::class,
        ExerciseSet::class, WeightEntry::class, PlanMeal::class, FoodLog::class, StepDay::class,
    ],
    version = 5,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun courses(): CourseDao
    abstract fun deadlines(): DeadlineDao
    abstract fun studySessions(): StudySessionDao
    abstract fun workouts(): WorkoutDao
    abstract fun exerciseSets(): ExerciseSetDao
    abstract fun health(): HealthDao

    companion object {
        /** Version 2 remembers which Moodle course and assignment an item came from. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE courses ADD COLUMN moodleCourseId INTEGER")
                db.execSQL("ALTER TABLE deadlines ADD COLUMN moodleAssignId INTEGER")
            }
        }

        /** Version 3 remembers which Moodle calendar event a deadline came from. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE deadlines ADD COLUMN calendarUid TEXT")
            }
        }

        /** Version 4 keeps each course's final grade for the average. */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE courses ADD COLUMN finalGrade REAL")
            }
        }

        /** Version 5 adds the sets done in each workout and the health pages: weight, meal plan, food and steps. */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `exercise_sets` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`workoutId` INTEGER NOT NULL, `exercise` TEXT NOT NULL, `position` INTEGER NOT NULL, " +
                        "`reps` INTEGER NOT NULL, `weightKg` REAL NOT NULL, `done` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`workoutId`) REFERENCES `workouts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_exercise_sets_workoutId` ON `exercise_sets` (`workoutId`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `weights` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`at` INTEGER NOT NULL, `kg` REAL NOT NULL)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `meal_plan` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`minuteOfDay` INTEGER NOT NULL, `name` TEXT NOT NULL, `food` TEXT NOT NULL, `kcal` INTEGER NOT NULL)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `food_log` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`at` INTEGER NOT NULL, `name` TEXT NOT NULL, `kcal` INTEGER NOT NULL, `mealId` INTEGER)",
                )
                db.execSQL("CREATE TABLE IF NOT EXISTS `steps` (`day` TEXT NOT NULL, `count` INTEGER NOT NULL, PRIMARY KEY(`day`))")
            }
        }

        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "uniplanner.db",
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5).build().also { instance = it }
            }
    }
}
