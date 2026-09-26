package com.uniplanner.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class Converters {
    @TypeConverter
    fun fromType(type: DeadlineType): String = type.name

    @TypeConverter
    fun toType(value: String): DeadlineType = DeadlineType.valueOf(value)
}

@Database(
    entities = [Course::class, Deadline::class, StudySession::class, Workout::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun courses(): CourseDao
    abstract fun deadlines(): DeadlineDao
    abstract fun studySessions(): StudySessionDao
    abstract fun workouts(): WorkoutDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "uniplanner.db",
                ).build().also { instance = it }
            }
    }
}
