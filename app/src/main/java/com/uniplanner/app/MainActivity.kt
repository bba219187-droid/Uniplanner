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
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.material.icons.filled.CalendarMonth
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
import com.uniplanner.app.ui.screens.StatsScreen
import com.uniplanner.app.ui.screens.StudyScreen
import com.uniplanner.app.ui.screens.WeekScreen
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
                UniPlannerRoot()
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
    Week("week", R.string.tab_week, Icons.Filled.CalendarMonth),
    Activities("activities", R.string.tab_activities, Icons.AutoMirrored.Filled.EventNote),
    Study("study", R.string.tab_study, Icons.Filled.Timer),
    Social("social", R.string.tab_social, Icons.Filled.Group),
    More("more", R.string.tab_more, Icons.Filled.Menu),
}

/** Screens reached from the More tab; the More tab stays highlighted while they are open. */
private val moreRoutes = setOf("more", "courses", "gym", "moodle", "moodle_web", "moodle_calendar", "grades", "stats", "backup", "agenda")

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
        NavHost(nav, startDestination = Tab.Week.route, modifier = Modifier.padding(padding)) {
            composable(Tab.Week.route) { WeekScreen(vm, onOpenAgenda = { nav.navigate("agenda") }) }
            composable(Tab.Activities.route) { ActivitiesScreen(vm, onOpenAgenda = { nav.navigate("agenda") }) }
            composable(Tab.Study.route) { StudyScreen(vm) }
            composable(Tab.Social.route) { SocialScreen(online) }
            composable(Tab.More.route) { MoreScreen(onOpen = { nav.navigate(it) }) }
            composable("courses") { CoursesScreen(vm) }
            composable("gym") { GymScreen(vm) }
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
