package com.uniplanner.app.settings

import android.Manifest
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.uniplanner.app.health.Steps
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.uniplanner.app.R
import com.uniplanner.app.data.AppDatabase
import com.uniplanner.app.data.WeightEntry
import com.uniplanner.app.domain.Sex
import com.uniplanner.app.health.BodyProfile
import com.uniplanner.app.health.HealthPrefs
import com.uniplanner.app.location.LocationChoices
import com.uniplanner.app.location.LocationShare
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class Question { AREAS, STUDY_HOURS, STUDY_TIME, GYM_KINDS, GYM_FREQ, HEALTH_GOAL, HEALTH_BODY, HEALTH_STEPS, FRIENDS, DONE }

/** A line in the conversation: the app asking, or the student's answer. */
data class Bubble(val id: Int, val mine: Boolean, val text: String)

private fun questionsFor(areas: Set<Area>): List<Question> = buildList {
    add(Question.AREAS)
    if (Area.STUDY in areas) { add(Question.STUDY_HOURS); add(Question.STUDY_TIME) }
    if (Area.GYM in areas) { add(Question.GYM_KINDS); add(Question.GYM_FREQ) }
    if (Area.HEALTH in areas) { add(Question.HEALTH_GOAL); add(Question.HEALTH_BODY); add(Question.HEALTH_STEPS) }
    // Always asked, so every student decides about location on the first open.
    add(Question.FRIENDS)
    add(Question.DONE)
}

/**
 * What the welcome chat has asked and answered so far. It lives in a ViewModel so turning the
 * phone, or the permission dialogs at the end, do not start the questions again.
 */
class OnboardingModel(app: Application) : AndroidViewModel(app) {
    private val ctx = app.applicationContext
    var draft by mutableStateOf(PersonalSettings.get(ctx))
    // Someone whose profile came back from the account only answers the location again.
    var question by mutableStateOf(if (Welcome.isReturning(ctx)) Question.FRIENDS else Question.AREAS)
        private set
    val bubbles = mutableStateListOf<Bubble>()
    private var nextId = 0
    var location by mutableStateOf(LocationShare.choicesFlow(ctx).value ?: LocationChoices(stats = false, friends = false))

    fun say(mine: Boolean, text: String) {
        bubbles += Bubble(nextId++, mine, text)
    }

    fun start(hello: String, first: String) {
        if (bubbles.isNotEmpty()) return
        say(false, hello)
        viewModelScope.launch {
            delay(500)
            say(false, first)
        }
    }

    fun answer(text: String, update: (Personal) -> Personal, textOf: (Question) -> String) {
        draft = update(draft)
        say(true, text)
        val steps = questionsFor(draft.areas)
        val next = steps.getOrElse(steps.indexOf(question) + 1) { Question.DONE }
        question = next
        viewModelScope.launch {
            delay(450)
            say(false, textOf(next))
        }
    }

    /** Saves the answers once the permission dialogs are over, and gets ready for a next time. */
    fun complete() {
        LocationShare.setChoicesLater(ctx, location, exactAgreed = true)
        Steps.schedule(ctx)
        PersonalSettings.save(ctx, draft.copy(done = true))
        Welcome.setReturning(ctx, false)
        bubbles.clear()
        question = Question.AREAS
    }
}

/**
 * The welcome chat: a few questions, one at a time, that set the study goal, the best hours to
 * study, favourite workouts, health goals and what to share with friends.
 */
@Composable
fun OnboardingScreen(onFinished: () -> Unit, model: OnboardingModel = viewModel(key = "onboarding")) {
    val ctx = LocalContext.current
    val draft = model.draft
    val question = model.question
    val bubbles = model.bubbles
    val list = rememberLazyListState()

    val firstName = FirebaseAuth.getInstance().currentUser?.displayName?.substringBefore(' ')?.takeIf { it.isNotBlank() }
    val hello = if (firstName != null) stringResource(R.string.ob_hello_name, firstName) else stringResource(R.string.ob_hello)
    val texts = mapOf(
        Question.AREAS to stringResource(R.string.ob_q_areas),
        Question.STUDY_HOURS to stringResource(R.string.ob_q_study_hours),
        Question.STUDY_TIME to stringResource(R.string.ob_q_study_time),
        Question.GYM_KINDS to stringResource(R.string.ob_q_gym_kinds),
        Question.GYM_FREQ to stringResource(R.string.ob_q_gym_freq),
        Question.HEALTH_GOAL to stringResource(R.string.ob_q_health_goal),
        Question.HEALTH_BODY to stringResource(R.string.ob_q_health_body),
        Question.HEALTH_STEPS to stringResource(R.string.ob_q_steps),
        Question.FRIENDS to stringResource(R.string.ob_q_friends),
        Question.DONE to stringResource(R.string.ob_done),
    )

    val returning = remember { Welcome.isReturning(ctx) }
    val backName = draft.firstName.ifBlank { firstName.orEmpty() }
    val back = stringResource(R.string.ob_welcome_back, if (backName.isBlank()) "" else ", $backName")
    LaunchedEffect(Unit) {
        if (returning) model.start(back, texts.getValue(Question.FRIENDS)) else model.start(hello, texts.getValue(Question.AREAS))
    }
    LaunchedEffect(bubbles.size) {
        if (bubbles.isNotEmpty()) list.animateScrollToItem(bubbles.lastIndex)
    }

    val order = questionsFor(draft.areas)
    fun answer(text: String, update: (Personal) -> Personal) = model.answer(text, update) { texts.getValue(it) }

    fun done() {
        model.complete()
        onFinished()
    }
    // Every permission is asked in one go at the end, after the student has answered.
    val askAll = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { done() }
    fun chooseLocation(c: LocationChoices) {
        model.location = c
    }
    fun finish() {
        val wanted = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
            if (Area.HEALTH in draft.areas || Area.GYM in draft.areas) Steps.permission?.let { add(it) }
            // Calls with friends need the microphone and camera.
            if (Area.FRIENDS in draft.areas) {
                add(Manifest.permission.RECORD_AUDIO)
                add(Manifest.permission.CAMERA)
            }
            if (model.location.friends || model.location.overview) {
                add(Manifest.permission.ACCESS_COARSE_LOCATION)
                add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            if (Area.STUDY in draft.areas) {
                add(Manifest.permission.READ_CALENDAR)
                add(Manifest.permission.WRITE_CALENDAR)
            }
        }.filter { ContextCompat.checkSelfPermission(ctx, it) != PackageManager.PERMISSION_GRANTED }
        if (wanted.isEmpty()) done() else askAll.launch(wanted.toTypedArray())
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            LinearProgressIndicator(
                progress = { (order.indexOf(question) + 1f) / order.size },
                modifier = Modifier.weight(1f).height(6.dp).clip(CircleShape),
                color = MaterialTheme.colorScheme.secondary,
                trackColor = MaterialTheme.colorScheme.outlineVariant,
                drawStopIndicator = {},
            )
            TextButton(onClick = { finish() }) { Text(stringResource(R.string.ob_skip)) }
        }
        LazyColumn(
            state = list,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(bubbles, key = { it.id }) { b -> ChatBubble(b) }
            item { Spacer(Modifier.height(8.dp)) }
        }
        AnimatedContent(
            targetState = question,
            transitionSpec = { (fadeIn(tween(250)) + slideInVertically { it / 3 }) togetherWith fadeOut(tween(150)) },
            label = "answers",
        ) { q ->
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Answers(q, draft, onAnswer = ::answer, onLocation = ::chooseLocation, onFinish = ::finish)
            }
        }
    }
}

@Composable
private fun ChatBubble(b: Bubble) {
    Box(Modifier.fillMaxWidth(), contentAlignment = if (b.mine) Alignment.CenterEnd else Alignment.CenterStart) {
        Text(
            b.text,
            style = MaterialTheme.typography.bodyLarge,
            color = if (b.mine) MaterialTheme.colorScheme.inverseOnSurface else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.widthIn(max = 290.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 20.dp, topEnd = 20.dp,
                        bottomStart = if (b.mine) 20.dp else 6.dp,
                        bottomEnd = if (b.mine) 6.dp else 20.dp,
                    ),
                )
                .background(if (b.mine) MaterialTheme.colorScheme.inverseSurface else MaterialTheme.colorScheme.surfaceContainerHighest)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun PrimaryButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        ),
    ) { Text(text, style = MaterialTheme.typography.titleSmall) }
}

/** Chips that answer at once with a single tap. */
@Composable
private fun <T> OneOf(options: List<Pair<T, String>>, onPick: (T, String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            FilterChip(selected = false, onClick = { onPick(value, label) }, label = { Text(label, style = MaterialTheme.typography.titleSmall) })
        }
    }
}

@Composable
private fun Answers(
    q: Question,
    draft: Personal,
    onAnswer: (String, (Personal) -> Personal) -> Unit,
    onLocation: (LocationChoices) -> Unit,
    onFinish: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val cont = stringResource(R.string.ob_continue)
    when (q) {
        Question.AREAS -> {
            val labels = mapOf(
                Area.STUDY to stringResource(R.string.ob_area_study),
                Area.GYM to stringResource(R.string.ob_area_gym),
                Area.HEALTH to stringResource(R.string.ob_area_health),
                Area.FRIENDS to stringResource(R.string.ob_area_friends),
            )
            var picked by remember { mutableStateOf(draft.areas) }
            var secondary by remember { mutableStateOf(draft.secondary) }
            Text("Onde estudas?", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !secondary, onClick = { secondary = false }, label = { Text("🎓 Universidade", style = MaterialTheme.typography.titleSmall) })
                FilterChip(selected = secondary, onClick = { secondary = true }, label = { Text("🏫 Secundário", style = MaterialTheme.typography.titleSmall) })
            }
            Text(stringResource(R.string.ob_pick_many), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Area.entries.forEach { a ->
                    FilterChip(
                        selected = a in picked,
                        onClick = { picked = if (a in picked) picked - a else picked + a },
                        label = { Text(labels.getValue(a), style = MaterialTheme.typography.titleSmall) },
                    )
                }
            }
            PrimaryButton(cont, enabled = picked.isNotEmpty()) {
                onAnswer(Area.entries.filter { it in picked }.joinToString(", ") { labels.getValue(it) }) { it.copy(areas = picked, secondary = secondary) }
            }
        }
        Question.STUDY_HOURS -> OneOf(listOf(5, 10, 15, 20, 30).map { it to "$it h" }) { h, label ->
            onAnswer(label) { it.copy(weeklyStudyHours = h) }
        }
        Question.STUDY_TIME -> OneOf(
            listOf(
                StudyTime.MORNING to stringResource(R.string.ob_time_morning),
                StudyTime.AFTERNOON to stringResource(R.string.ob_time_afternoon),
                StudyTime.EVENING to stringResource(R.string.ob_time_evening),
                StudyTime.ANY to stringResource(R.string.ob_time_any),
            ),
        ) { t, label -> onAnswer(label) { it.copy(studyTime = t) } }
        Question.GYM_KINDS -> {
            val kinds = stringArrayResource(R.array.gym_kinds).toList() + stringArrayResource(R.array.ob_more_sports)
            var picked by remember { mutableStateOf(draft.gymKinds.filter { it in kinds }.toSet()) }
            Text(stringResource(R.string.ob_pick_many), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                kinds.forEach { k ->
                    FilterChip(selected = k in picked, onClick = { picked = if (k in picked) picked - k else picked + k }, label = { Text(k) })
                }
            }
            PrimaryButton(cont, enabled = picked.isNotEmpty()) {
                val ordered = kinds.filter { it in picked }
                onAnswer(ordered.joinToString(", ")) { it.copy(gymKinds = ordered) }
            }
        }
        Question.GYM_FREQ -> OneOf((2..6).map { it to stringResource(R.string.ob_times_week, it) }) { n, label ->
            onAnswer(label) { it.copy(gymPerWeek = n) }
        }
        Question.HEALTH_GOAL -> OneOf(
            listOf(
                HealthGoal.LOSE to stringResource(R.string.ob_goal_lose),
                HealthGoal.KEEP to stringResource(R.string.ob_goal_keep),
                HealthGoal.GAIN to stringResource(R.string.ob_goal_gain),
            ),
        ) { g, label -> onAnswer(label) { it.copy(healthGoal = g) } }
        Question.HEALTH_BODY -> {
            val profile = HealthPrefs.profileFlow(ctx).value
            var height by remember { mutableStateOf(profile?.heightCm?.toInt()?.toString().orEmpty()) }
            var weight by remember { mutableStateOf("") }
            var year by remember { mutableStateOf(profile?.birthYear?.toString().orEmpty()) }
            var sex by remember { mutableStateOf(profile?.sex ?: Sex.MALE) }
            val h = height.toDoubleOrNull()?.takeIf { it in 100.0..250.0 }
            val w = weight.replace(',', '.').toDoubleOrNull()?.takeIf { it in 25.0..300.0 }
            val y = year.toIntOrNull()?.takeIf { it in 1920..2020 }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    height, { height = it.filter(Char::isDigit).take(3) }, label = { Text("cm") }, singleLine = true,
                    modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedTextField(
                    weight, { weight = it.take(5) }, label = { Text("kg") }, singleLine = true,
                    modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                OutlinedTextField(
                    year, { year = it.filter(Char::isDigit).take(4) }, label = { Text(stringResource(R.string.ob_year)) }, singleLine = true,
                    modifier = Modifier.weight(1.2f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(sex == Sex.MALE, { sex = Sex.MALE }, { Text(stringResource(R.string.health_sex_male)) })
                FilterChip(sex == Sex.FEMALE, { sex = Sex.FEMALE }, { Text(stringResource(R.string.health_sex_female)) })
            }
            val skip = stringResource(R.string.ob_later)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { onAnswer(skip) { it } }) { Text(skip) }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = {
                        HealthPrefs.saveProfile(ctx, BodyProfile(h, sex, y))
                        if (w != null) {
                            scope.launch(Dispatchers.IO) {
                                AppDatabase.get(ctx).health().insertWeight(WeightEntry(at = System.currentTimeMillis(), kg = w))
                            }
                        }
                        onAnswer("${h?.toInt()} cm · ${weight} kg · $y") { it }
                    },
                    enabled = h != null && w != null && y != null,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.inverseSurface,
                        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    ),
                ) { Text(cont) }
            }
        }
        Question.HEALTH_STEPS -> OneOf(listOf(6000, 8000, 10000, 12000).map { it to "%,d".format(it) }) { n, label ->
            onAnswer(label) { it.copy(stepGoal = n) }
        }
        Question.FRIENDS -> OneOf(
            listOf(
                LocationChoices(stats = true, friends = true, map = true) to stringResource(R.string.ob_yes_share),
                LocationChoices(stats = false, friends = true) to stringResource(R.string.ob_friends_only),
                LocationChoices(stats = true, friends = false) to stringResource(R.string.ob_city_only),
                LocationChoices(stats = false, friends = false) to stringResource(R.string.ob_not_now),
            ),
        ) { c, label ->
            onLocation(c)
            onAnswer(label) { it }
        }
        Question.DONE -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondary))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.ob_redo_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            PrimaryButton(stringResource(R.string.ob_start)) { onFinish() }
        }
    }
}
