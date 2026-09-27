package com.uniplanner.app.health

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.uniplanner.app.data.AppDatabase
import com.uniplanner.app.data.FoodLog
import com.uniplanner.app.data.PlanMeal
import com.uniplanner.app.data.StepDay
import com.uniplanner.app.data.WeightEntry
import com.uniplanner.app.data.Workout
import com.uniplanner.app.domain.DayBalance
import com.uniplanner.app.domain.Health
import com.uniplanner.app.domain.MealDraft
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Reading a plan from a photo or PDF: busy, the meals found, or nothing found. */
sealed interface PlanImportState {
    data object Reading : PlanImportState
    data class Found(val meals: List<MealDraft>) : PlanImportState
    data object NothingFound : PlanImportState
}

class HealthViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx = app.applicationContext
    private val db = AppDatabase.get(app)
    private val dao = db.health()
    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.now(zone)
    private val startOfDay = today.atStartOfDay(zone).toInstant().toEpochMilli()

    val profile: StateFlow<BodyProfile?> = HealthPrefs.profileFlow(ctx)
    val reminders: StateFlow<Boolean?> = HealthPrefs.remindersFlow(ctx)

    val weights: StateFlow<List<WeightEntry>> =
        dao.observeWeights().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val plan: StateFlow<List<PlanMeal>> =
        dao.observePlan().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val foodToday: StateFlow<List<FoodLog>> =
        dao.observeFood(startOfDay).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val steps: StateFlow<List<StepDay>> =
        dao.observeSteps().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val stepsToday: StateFlow<Int> =
        steps.map { list -> list.firstOrNull { it.day == today.toString() }?.count ?: 0 }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val workoutsToday: StateFlow<List<Workout>> =
        db.workouts().observeAll().map { list ->
            list.filter { it.done && Instant.ofEpochMilli(it.startsAt).atZone(zone).toLocalDate() == today }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Today's calories. Without a full profile it assumes 170 cm and 20 years. */
    val balance: StateFlow<DayBalance> =
        combine(weights, profile, foodToday, workoutsToday, stepsToday) { ws, p, food, workouts, stepCount ->
            val kg = ws.lastOrNull()?.kg ?: Health.DEFAULT_WEIGHT_KG
            val height = p?.heightCm ?: 170.0
            val age = p?.birthYear?.let { today.year - it } ?: 20
            DayBalance(
                eaten = food.sumOf { it.kcal },
                resting = Health.restingKcal(kg, height, age, p?.sex ?: com.uniplanner.app.domain.Sex.MALE),
                workouts = workouts.sumOf { Health.workoutKcal(it.title, it.minutes, kg) },
                steps = Health.stepsKcal(stepCount, kg),
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DayBalance(0, 0, 0, 0))

    private val _import = MutableStateFlow<PlanImportState?>(null)
    val import: StateFlow<PlanImportState?> = _import

    fun saveProfile(p: BodyProfile) = HealthPrefs.saveProfile(ctx, p)

    fun addWeight(kg: Double) = viewModelScope.launch {
        dao.insertWeight(WeightEntry(at = System.currentTimeMillis(), kg = kg))
    }

    fun deleteWeight(w: WeightEntry) = viewModelScope.launch { dao.deleteWeight(w) }

    fun saveMeal(meal: PlanMeal) = viewModelScope.launch {
        val saved = if (meal.id == 0L) meal.copy(id = dao.insertMeal(meal)) else meal.also { dao.updateMeal(it) }
        MealReminders.schedule(ctx, saved)
    }

    fun deleteMeal(meal: PlanMeal) = viewModelScope.launch {
        dao.deleteMeal(meal)
        MealReminders.rescheduleAll(ctx)
    }

    /** Swaps the whole plan for the meals read from the nutritionist's plan. */
    fun replacePlan(meals: List<MealDraft>) = viewModelScope.launch {
        dao.clearPlan()
        dao.insertMeals(meals.map { PlanMeal(minuteOfDay = it.minuteOfDay, name = it.name, food = it.food, kcal = it.kcal) })
        MealReminders.rescheduleAll(ctx)
        _import.value = null
    }

    /** Ticks a meal of the plan as eaten today, or unticks it. */
    fun toggleEaten(meal: PlanMeal) = viewModelScope.launch {
        val logged = foodToday.value.filter { it.mealId == meal.id }
        if (logged.isEmpty()) {
            dao.insertFood(FoodLog(at = System.currentTimeMillis(), name = meal.name, kcal = meal.kcal, mealId = meal.id))
        } else {
            logged.forEach { dao.deleteFood(it) }
        }
    }

    fun addFood(name: String, kcal: Int) = viewModelScope.launch {
        dao.insertFood(FoodLog(at = System.currentTimeMillis(), name = name.trim(), kcal = kcal))
    }

    fun deleteFood(f: FoodLog) = viewModelScope.launch { dao.deleteFood(f) }

    fun setReminders(on: Boolean) = viewModelScope.launch {
        HealthPrefs.setMealReminders(ctx, on)
        MealReminders.rescheduleAll(ctx)
    }

    fun refreshSteps() = viewModelScope.launch {
        Steps.schedule(ctx)
        runCatching { Steps.refresh(ctx) }
    }

    fun importFrom(uri: Uri) = viewModelScope.launch {
        _import.value = PlanImportState.Reading
        val meals = runCatching { Health.parseMealPlan(PlanImport.readText(ctx, uri)) }.getOrDefault(emptyList())
        _import.value = if (meals.isEmpty()) PlanImportState.NothingFound else PlanImportState.Found(meals)
    }

    fun importText(text: String) {
        val meals = Health.parseMealPlan(text)
        _import.value = if (meals.isEmpty()) PlanImportState.NothingFound else PlanImportState.Found(meals)
    }

    fun closeImport() {
        _import.value = null
    }
}
