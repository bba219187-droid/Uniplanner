package com.uniplanner.app.domain

import kotlin.math.roundToInt

enum class BmiCategory { UNDER, NORMAL, OVER, OBESE }

enum class Sex { MALE, FEMALE }

/** A meal read from a nutritionist's plan, before it is saved. */
data class MealDraft(val minuteOfDay: Int, val name: String, val food: String, val kcal: Int)

/** What was eaten and burned on one day, in kcal. A positive [deficit] means more burned than eaten. */
data class DayBalance(val eaten: Int, val resting: Int, val workouts: Int, val steps: Int) {
    val burned: Int get() = resting + workouts + steps
    val deficit: Int get() = burned - eaten
}

/**
 * Health numbers. Calories are estimates: training uses MET values (energy per kg per hour for a
 * kind of activity), resting energy uses the Mifflin-St Jeor formula.
 */
object Health {
    /** Weight used for calories until the student records their own. */
    const val DEFAULT_WEIGHT_KG = 70.0

    fun bmi(weightKg: Double, heightCm: Double): Double {
        val m = heightCm / 100
        return if (m <= 0) 0.0 else weightKg / (m * m)
    }

    fun category(bmi: Double): BmiCategory = when {
        bmi < 18.5 -> BmiCategory.UNDER
        bmi < 25 -> BmiCategory.NORMAL
        bmi < 30 -> BmiCategory.OVER
        else -> BmiCategory.OBESE
    }

    /** MET for a workout, guessed from its name; weight training when nothing else matches. */
    fun met(title: String): Double {
        val t = title.lowercase()
        fun has(vararg words: String) = words.any { it in t }
        return when {
            has("corr", "run", "sprint") -> 9.0
            has("bicicl", "bike", "cycl", "spinning") -> 7.5
            has("nata", "swim", "piscina") -> 7.0
            has("futebol", "football", "soccer", "basket", "padel", "ténis", "tenis", "tennis") -> 7.0
            has("hiit", "crossfit", "funcional", "circuit") -> 8.0
            has("cardio", "elí", "eli", "remo", "row") -> 6.5
            has("caminh", "walk", "passeio") -> 3.5
            has("yoga", "pilates", "along", "stretch") -> 3.0
            else -> 5.0
        }
    }

    fun workoutKcal(title: String, minutes: Int, weightKg: Double): Int =
        (met(title) * weightKg * minutes / 60.0).roundToInt()

    /** About 0.04 kcal a step for someone of 70 kg, more for heavier people. */
    fun stepsKcal(steps: Int, weightKg: Double): Int = (steps * 0.00057 * weightKg).roundToInt()

    /** Energy the body uses at rest in a day, times a light-activity factor. */
    fun restingKcal(weightKg: Double, heightCm: Double, age: Int, sex: Sex): Int {
        val bmr = 10 * weightKg + 6.25 * heightCm - 5 * age + if (sex == Sex.MALE) 5 else -161
        return (bmr * 1.2).roundToInt()
    }

    private val timePattern = Regex("""(?<![\d])([01]?\d|2[0-3])\s*(?:[:h.]\s*([0-5]\d))?\s*(?:h|horas)?(?![\d])""")
    private val kcalPattern = Regex("""(\d{2,4})\s*(?:kcal|cal)""", RegexOption.IGNORE_CASE)

    /**
     * Reads a meal plan from text, such as one typed in or read from a photo: every line that starts
     * with a time becomes a meal, and lines after it without a time add to its food.
     */
    fun parseMealPlan(text: String): List<MealDraft> {
        val meals = mutableListOf<MealDraft>()
        for (raw in text.lines()) {
            val line = raw.trim().trimStart('-', '•', '*', '·').trim()
            if (line.isEmpty()) continue
            val time = timePattern.find(line)?.takeIf { it.range.first <= 2 && (it.groupValues[2].isNotEmpty() || "h" in it.value.lowercase()) }
            val kcal = kcalPattern.find(line)?.groupValues?.get(1)?.toInt() ?: 0
            val rest = (if (time != null) line.substring(time.range.last + 1) else line)
                .replace(kcalPattern, "").trim().trimStart(':', '-', '–', '—', '|').trim().trimEnd(',', ';', '(', ')').trim()
            if (time != null) {
                val minute = time.groupValues[1].toInt() * 60 + (time.groupValues[2].toIntOrNull() ?: 0)
                // "Pequeno-almoço: aveia" or "Lanche - fruta": a dash inside a word is not a separator.
                val sep = listOf(":", " - ", " – ", " — ", "|").map { rest.indexOf(it) to it }.filter { it.first > 0 }.minByOrNull { it.first }
                val (name, food) = if (sep != null) {
                    rest.substring(0, sep.first).trim() to rest.substring(sep.first + sep.second.length).trim()
                } else {
                    rest to ""
                }
                meals += MealDraft(minute, name.ifBlank { "Refeição" }, food, kcal)
            } else if (meals.isNotEmpty()) {
                val last = meals.removeAt(meals.lastIndex)
                meals += last.copy(
                    food = listOf(last.food, rest).filter { it.isNotBlank() }.joinToString(", "),
                    kcal = last.kcal + kcal,
                )
            }
        }
        return meals.sortedBy { it.minuteOfDay }
    }
}

/** The step sensor's reading the last time it was read, and on which day. */
data class StepReading(val day: String, val counter: Long)

/**
 * The phone's step sensor counts every step since it was switched on. Android only lets the app
 * read it while the app is open, so readings can be a day or more apart. Steps since the last
 * reading go to that day when it is still the same day, and to today otherwise: most of them
 * were walked closer to now than to the evening the app was last opened.
 */
object StepTracker {
    /** The new reading to keep and the steps to add, by day. */
    fun update(last: StepReading?, today: String, counter: Long): Pair<StepReading, Map<String, Int>> {
        val now = StepReading(today, counter)
        if (last == null) return now to emptyMap()
        // After a restart the sensor counts from zero again.
        val steps = if (counter >= last.counter) counter - last.counter else counter
        if (steps <= 0) return now to emptyMap()
        return now to mapOf(today to steps.toInt())
    }
}
