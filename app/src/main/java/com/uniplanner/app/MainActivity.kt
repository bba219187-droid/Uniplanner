package com.uniplanner.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.padding
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.uniplanner.app.ui.screens.WorkoutScreen
import com.uniplanner.app.moodle.MoodleScreen
import com.uniplanner.app.moodle.MoodleSso
import com.uniplanner.app.moodle.MoodleSsoCallback
import com.uniplanner.app.moodle.MoodleWebScreen
import com.uniplanner.app.online.BackupChoicePrompt
import com.uniplanner.app.online.BackupScreen
import com.uniplanner.app.online.OnlineViewModel
import com.uniplanner.app.phone.AgendaScreen
import com.uniplanner.app.phone.AgendaSync
import com.uniplanner.app.settings.AppSettings
import com.uniplanner.app.settings.SettingsScreen
import com.uniplanner.app.settings.ThemeMode
import androidx.compose.foundation.isSystemInDarkTheme
import com.uniplanner.app.online.SocialScreen
import com.uniplanner.app.online.ChatScreen
import com.uniplanner.app.online.ChatKind
import androidx.compose.foundation.layout.consumeWindowInsets
import com.uniplanner.app.ui.AppViewModel
import com.uniplanner.app.ui.screens.CoursesScreen
import com.uniplanner.app.ui.screens.ActivitiesScreen
import com.uniplanner.app.ui.screens.GradesScreen
import com.uniplanner.app.ui.screens.GymScreen
import com.uniplanner.app.ui.screens.MoreScreen
import com.uniplanner.app.ui.screens.SplashScreen
import com.uniplanner.app.ui.screens.StatsScreen
import com.uniplanner.app.ui.screens.StudyScreen
import com.uniplanner.app.ui.theme.UniPlannerTheme
import com.uniplanner.app.health.HealthScreen
import com.uniplanner.app.settings.Area
import com.uniplanner.app.settings.OnboardingScreen
import com.uniplanner.app.settings.PersonalSettings
import com.uniplanner.app.health.Steps
import com.uniplanner.app.location.AdminScreen
import com.uniplanner.app.location.LocationScreen
import com.uniplanner.app.location.LocationShare
import com.uniplanner.app.update.AvailableUpdate
import com.uniplanner.app.update.UpdateChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val askNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppSettings.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Permissions are asked at the end of the welcome questions; later opens only ask what is still missing.
        if (PersonalSettings.get(this).done && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        // While the app is on screen, the shared position stays fresh for friends and the admins' map.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (true) {
                    runCatching { LocationShare.refresh(applicationContext) }
                    delay(LocationShare.REFRESH_MS)
                }
            }
        }
        handleLink(intent)
        setContent {
            val mode by AppSettings.themeFlow(applicationContext).collectAsState()
            val dark = when (mode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                else -> isSystemInDarkTheme()
            }
            // The status bar icons follow the app's theme, not only the phone's.
            LaunchedEffect(dark) {
                val bars = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { dark }
                enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
            }
            UniPlannerTheme(dark) {
                var splash by rememberSaveable { mutableStateOf(true) }
                AnimatedContent(
                    targetState = splash,
                    transitionSpec = { fadeIn(tween(450)) togetherWith fadeOut(tween(450)) },
                    label = "splash",
                ) { showSplash ->
                    val personal by PersonalSettings.flow(applicationContext).collectAsState()
                    when {
                        showSplash -> SplashScreen(onFinished = { splash = false })
                        personal?.done == false -> OnboardingScreen(onFinished = {})
                        else -> UniPlannerRoot()
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Picks up tests and deadlines the student moved in the phone's agenda.
        lifecycleScope.launch(Dispatchers.IO) { runCatching { AgendaSync.pullChanges(applicationContext) } }
        lifecycleScope.launch(Dispatchers.IO) { runCatching { Steps.refresh(applicationContext) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleLink(intent)
    }

    private fun handleLink(intent: Intent?) {
        val link = intent?.data ?: return
        if (link.scheme == MoodleSso.SCHEME) MoodleSsoCallback.link.value = link.toString()
    }
}

private enum class Tab(val route: String, @StringRes val label: Int, val icon: ImageVector, val area: Area? = null) {
    Study("study", R.string.tab_study, Icons.Filled.Timer),
    Activities("activities", R.string.tab_activities, Icons.AutoMirrored.Filled.EventNote),
    Gym("gym", R.string.tab_gym, Icons.Filled.FitnessCenter, Area.GYM),
    Social("social", R.string.tab_social, Icons.Filled.Group, Area.FRIENDS),
    Health("health", R.string.tab_health, Icons.Filled.FavoriteBorder, Area.HEALTH),
    More("more", R.string.tab_more, Icons.Filled.Settings),
}

/** Screens reached from the More tab; the More tab stays highlighted while they are open. */
private val moreRoutes = setOf("more", "courses", "moodle", "moodle_web", "moodle_calendar", "grades", "stats", "backup", "agenda", "settings", "location", "admin")


@Composable
private fun UniPlannerRoot(vm: AppViewModel = viewModel(), online: OnlineViewModel = viewModel()) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route

    UpdatePrompt()
    BackupChoicePrompt()

    // Coming back from the university login page: show the Moodle screen, which finishes the sign-in.
    val moodleLink by MoodleSsoCallback.link.collectAsState()
    LaunchedEffect(moodleLink) {
        if (moodleLink != null && current != "moodle") nav.navigate("moodle") { launchSingleTop = true }
    }

    Scaffold(
        bottomBar = {
            // A chat takes the whole screen, like in a messaging app.
            if (current?.startsWith("chat/") != true) FloatingTabBar(
                selected = { tab ->
                    if (tab == Tab.More) current in moreRoutes
                    else if (tab == Tab.Gym && current?.startsWith("workout") == true) true
                    else if (tab == Tab.Social && current?.startsWith("chat/") == true) true
                    else backStack?.destination?.hierarchy?.any { it.route == tab.route } == true
                },
                onSelect = { tab ->
                    // The first tab sits at the bottom of the back stack, so going to it means going back
                    // to it. Restoring its saved state instead would bring back the screen opened on top.
                    if (tab == Tab.Study) {
                        if (!nav.popBackStack(Tab.Study.route, inclusive = false)) nav.navigate(Tab.Study.route)
                    } else {
                        nav.navigate(tab.route) {
                            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
            )
        },
    ) { padding ->
        NavHost(
            nav,
            startDestination = Tab.Study.route,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
            enterTransition = { slideIn(direction(initialState.destination.route, targetState.destination.route)) },
            exitTransition = { slideOut(direction(initialState.destination.route, targetState.destination.route)) },
            popEnterTransition = { slideIn(-1) },
            popExitTransition = { slideOut(-1) },
        ) {
            composable(Tab.Activities.route) { ActivitiesScreen(vm, onOpenAgenda = { nav.navigate("agenda") }) }
            composable(Tab.Study.route) {
                StudyScreen(
                    vm,
                    onOpenCourses = { nav.navigate("courses") },
                    onOpenAgenda = { nav.navigate("agenda") },
                    onOpenStats = { nav.navigate("stats") },
                    onOpenProfile = { nav.navigate(Tab.More.route) { launchSingleTop = true } },
                )
            }
            composable(Tab.Social.route) { SocialScreen(online, onOpenChat = { kind, id -> nav.navigate("chat/${kind.name}/$id") }) }
            composable("chat/{kind}/{id}") { entry ->
                ChatScreen(
                    online,
                    kind = ChatKind.valueOf(entry.arguments?.getString("kind") ?: ChatKind.FRIEND.name),
                    id = entry.arguments?.getString("id").orEmpty(),
                    onBack = { nav.popBackStack() },
                )
            }
            composable(Tab.More.route) { MoreScreen(onOpen = { nav.navigate(it) }) }
            composable(Tab.Health.route) { HealthScreen() }
            composable("location") { LocationScreen(onOpenAdmin = { nav.navigate("admin") }) }
            composable("admin") { AdminScreen() }
            composable("courses") { CoursesScreen(vm) }
            composable(Tab.Gym.route) { GymScreen(vm, onOpenWorkout = { nav.navigate("workout/$it") }) }
            composable("workout/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                WorkoutScreen(vm, entry.arguments?.getLong("id") ?: 0L, onBack = { nav.popBackStack() })
            }
            composable("agenda") { AgendaScreen() }
            composable("settings") { SettingsScreen() }
            composable("grades") { GradesScreen(vm) }
            composable("stats") { StatsScreen(vm) }
            composable("backup") {
                BackupScreen(online, onSignIn = {
                    nav.navigate(Tab.Social.route) {
                        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                    }
                })
            }
            composable("moodle") { MoodleScreen(onOpenWeb = { calendar -> nav.navigate(if (calendar) "moodle_calendar" else "moodle_web") }) }
            composable("moodle_web") { MoodleWebScreen(calendarPage = false, onClose = { nav.popBackStack() }) }
            composable("moodle_calendar") { MoodleWebScreen(calendarPage = true, onClose = { nav.popBackStack() }) }
        }
    }
}

/** The dark pill of tabs floating above the bottom of the screen; the open tab sits in a light circle. */
@Composable
private fun FloatingTabBar(selected: (Tab) -> Boolean, onSelect: (Tab) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().height(64.dp).clip(CircleShape).background(colors.inverseSurface).padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val areas = PersonalSettings.flow(LocalContext.current).collectAsState().value?.areas ?: Area.entries.toSet()
            Tab.entries.filter { it.area == null || it.area in areas }.forEach { tab ->
                val on = selected(tab)
                val bg by animateColorAsState(if (on) colors.inverseOnSurface else Color.Transparent, label = "tabBg")
                val fg by animateColorAsState(
                    if (on) colors.inverseSurface else colors.inverseOnSurface.copy(alpha = 0.6f),
                    label = "tabFg",
                )
                Box(
                    Modifier.size(48.dp).clip(CircleShape).background(bg).clickable(role = Role.Tab) { onSelect(tab) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(tab.icon, contentDescription = stringResource(tab.label), tint = fg)
                }
            }
        }
    }
}

private val tabRoutes = Tab.entries.map { it.route }

/**
 * 1 slides the new screen in from the right, -1 from the left. Between tabs it follows their order
 * in the bottom bar; opening a screen from a tab goes forward, going back to a tab goes back.
 */
private fun direction(from: String?, to: String?): Int {
    val a = tabRoutes.indexOf(from)
    val b = tabRoutes.indexOf(to)
    return when {
        a >= 0 && b >= 0 -> if (b >= a) 1 else -1
        b >= 0 -> -1
        else -> 1
    }
}

private const val SLIDE_MS = 320

private fun slideIn(direction: Int): EnterTransition =
    slideInHorizontally(tween(SLIDE_MS, easing = FastOutSlowInEasing)) { width -> direction * width } +
        fadeIn(tween(SLIDE_MS))

private fun slideOut(direction: Int): ExitTransition =
    slideOutHorizontally(tween(SLIDE_MS, easing = FastOutSlowInEasing)) { width -> -direction * width / 3 } +
        fadeOut(tween(SLIDE_MS))

/** Checks once per launch whether a newer build was published and offers to install it. */
@Composable
private fun UpdatePrompt() {
    val context = LocalContext.current
    var update by remember { mutableStateOf<AvailableUpdate?>(null) }
    LaunchedEffect(Unit) { update = UpdateChecker.check() }
    update?.let { u ->
        AlertDialog(
            onDismissRequest = { update = null },
            title = { Text(stringResource(R.string.update_title)) },
            text = { Text(stringResource(R.string.update_text, u.versionName)) },
            confirmButton = {
                TextButton(onClick = {
                    UpdateChecker.openDownload(context)
                    update = null
                }) { Text(stringResource(R.string.update_now)) }
            },
            dismissButton = { TextButton(onClick = { update = null }) { Text(stringResource(R.string.update_later)) } },
        )
    }
}
