package com.uniplanner.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class HealthTest {
    @Test
    fun bmiAndCategory() {
        val bmi = Health.bmi(72.0, 177.0)
        assertEquals(22.98, bmi, 0.01)
        assertEquals(BmiCategory.NORMAL, Health.category(bmi))
        assertEquals(BmiCategory.UNDER, Health.category(18.0))
        assertEquals(BmiCategory.OVER, Health.category(27.0))
        assertEquals(BmiCategory.OBESE, Health.category(31.0))
    }

    @Test
    fun workoutCaloriesDependOnKind() {
        assertEquals(350, Health.workoutKcal("Push peito", 60, 70.0))
        assertEquals(315, Health.workoutKcal("Corrida", 30, 70.0))
    }

    @Test
    fun dayBalance() {
        val b = DayBalance(eaten = 2000, resting = 2100, workouts = 300, steps = 200)
        assertEquals(2600, b.burned)
        assertEquals(600, b.deficit)
    }

    @Test
    fun readsMealPlan() {
        val plan = Health.parseMealPlan(
            """
            Plano alimentar
            08:00 Pequeno-almoço: aveia com iogurte 350 kcal
            - 1 peça de fruta
            10h30 Lanche - frutos secos
            13h Almoço: frango, arroz e salada (600 kcal)
            20.00 Jantar: peixe e legumes
            """.trimIndent(),
        )
        assertEquals(4, plan.size)
        assertEquals(MealDraft(480, "Pequeno-almoço", "aveia com iogurte, 1 peça de fruta", 350), plan[0])
        assertEquals(630, plan[1].minuteOfDay)
        assertEquals("Lanche", plan[1].name)
        assertEquals("frutos secos", plan[1].food)
        assertEquals(780, plan[2].minuteOfDay)
        assertEquals(600, plan[2].kcal)
        assertEquals("Jantar", plan[3].name)
    }

    @Test
    fun stepsGoToTheDayTheyAreRead() {
        val (first, none) = StepTracker.update(null, "2026-09-27", 1000)
        assertEquals(emptyMap<String, Int>(), none)
        val (second, add) = StepTracker.update(first, "2026-09-27", 1500)
        assertEquals(mapOf("2026-09-27" to 500), add)
        // Opened again the next evening: the day's walking counts for today, not for last night.
        val (third, nextDay) = StepTracker.update(second, "2026-09-28", 7500)
        assertEquals(mapOf("2026-09-28" to 6000), nextDay)
        // The phone restarted: the sensor starts again from zero.
        val (_, afterRestart) = StepTracker.update(third, "2026-09-28", 300)
        assertEquals(mapOf("2026-09-28" to 300), afterRestart)
    }
}
