package com.uniplanner.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.uniplanner.app.moodle.MoodleScreen
import com.uniplanner.app.moodle.MoodleSso
import com.uniplanner.app.moodle.MoodleSsoCallback
import com.uniplanner.app.moodle.MoodleWebScreen
import com.uniplanner.app.online.BackupChoicePrompt
import com.uniplanner.app.online.BackupScreen
import com.uniplanner.app.online.OnlineViewModel
import com.uniplanner.app.phone.AgendaScreen
import com.uniplanner.app.phone.AgendaSync
import com.uniplanner.app.online.SocialScreen
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
import com.uniplanner.app.update.AvailableUpdate
import com.uniplanner.app.update.UpdateChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val askNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        handleLink(intent)
        setContent {
            UniPlannerTheme {
                var splash by rememberSaveable { mutableStateOf(true) }
                AnimatedContent(
                    targetState = splash,
                    transitionSpec = { fadeIn(tween(450)) togetherWith fadeOut(tween(450)) },
                    label = "splash",
                ) { showSplash ->
                    if (showSplash) SplashScreen(onFinished = { splash = false }) else UniPlannerRoot()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Picks up tests and deadlines the student moved in the phone's agenda.
        lifecycleScope.launch(Dispatchers.IO) { runCatching { AgendaSync.pullChanges(applicationContext) } }
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

private enum class Tab(val route: String, @StringRes val label: Int, val icon: ImageVector) {
    Study("study", R.string.tab_study, Icons.Filled.Timer),
    Activities("activities", R.string.tab_activities, Icons.AutoMirrored.Filled.EventNote),
    Gym("gym", R.string.tab_gym, Icons.Filled.FitnessCenter),
    Social("social", R.string.tab_social, Icons.Filled.Group),
    More("more", R.string.tab_more, Icons.Filled.Menu),
}

/** Screens reached from the More tab; the More tab stays highlighted while they are open. */
private val moreRoutes = setOf("more", "courses", "moodle", "moodle_web", "moodle_calendar", "grades", "stats", "backup", "agenda")

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
            NavigationBar {
                Tab.entries.forEach { tab ->
                    val selected = if (tab == Tab.More) current in moreRoutes
                    else backStack?.destination?.hierarchy?.any { it.route == tab.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            nav.navigate(tab.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(stringResource(tab.label)) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            nav,
            startDestination = Tab.Study.route,
            modifier = Modifier.padding(padding),
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
                )
            }
            composable(Tab.Social.route) { SocialScreen(online) }
            composable(Tab.More.route) { MoreScreen(onOpen = { nav.navigate(it) }) }
            composable("courses") { CoursesScreen(vm) }
            composable(Tab.Gym.route) { GymScreen(vm) }
            composable("agenda") { AgendaScreen() }
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
