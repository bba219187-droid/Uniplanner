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
    entities = [Course::class, Deadline::class, StudySession::class, Workout::class],
    version = 3,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun courses(): CourseDao
    abstract fun deadlines(): DeadlineDao
    abstract fun studySessions(): StudySessionDao
    abstract fun workouts(): WorkoutDao

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

        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "uniplanner.db",
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instance = it }
            }
    }
}
