package com.uniplanner.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCodecTest {
    @Test
    fun everythingSurvivesTheRoundTrip() {
        val snapshot = Snapshot(
            courses = listOf(Course(3, "Cálculo", "Ana", 6, 0xFF3F51B5, moodleCourseId = 9, finalGrade = 14.5)),
            deadlines = listOf(
                Deadline(7, 3, DeadlineType.TEST, "Teste 1", 1_760_000_000_000, 40, "cap. 1-3", done = true, grade = 13.0),
                Deadline(8, 3, DeadlineType.ASSIGNMENT, "TP", 1_761_000_000_000, calendarUid = "5@ipca"),
            ),
            studySessions = listOf(StudySession(1, 3, 1_759_000_000_000, 50)),
            workouts = listOf(Workout(2, "Pernas", 1_759_500_000_000, 75, done = true)),
        )
        assertEquals(snapshot, BackupCodec.decode(BackupCodec.encode(snapshot)))
    }

    @Test
    fun emptyBackupIsEmpty() {
        assertTrue(BackupCodec.decode("""{"format":1}""").isEmpty)
    }
}
