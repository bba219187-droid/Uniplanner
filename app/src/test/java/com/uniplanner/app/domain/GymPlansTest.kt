package com.uniplanner.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GymPlansTest {
    @Test
    fun everyPlanIsConsistent() {
        assertEquals(GymPlans.all.size, GymPlans.all.map { it.id }.toSet().size)
        GymPlans.all.forEach { p ->
            assertEquals(p.id, p.weekdays.size, p.rotation.size)
            assertEquals(p.id, p.perWeek, p.rotation.size)
            assertTrue(p.id, p.rotation.all { it in p.days.indices })
            p.days.forEach { d ->
                // Exercise names are list keys on the plan sheet.
                assertEquals(d.name, d.exercises.size, d.exercises.map { it.name }.toSet().size)
                assertTrue(d.name, d.exercises.all { it.sets > 0 && it.reps > 0 && it.repsMax >= it.reps })
            }
        }
    }

    @Test
    fun nextGoesRoundTheRotation() {
        val ppl = GymPlans.byId("ppl")!!
        assertEquals("Push", GymPlans.next(ppl, emptyList()).name)
        assertEquals("Pull", GymPlans.next(ppl, listOf("PPL · Push")).name)
        assertEquals("Push", GymPlans.next(ppl, listOf("PPL · Pernas")).name)
    }
}
