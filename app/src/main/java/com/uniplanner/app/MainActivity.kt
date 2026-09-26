package com.uniplanner.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.uniplanner.app.ui.AppViewModel
import com.uniplanner.app.ui.screens.CoursesScreen
import com.uniplanner.app.ui.screens.DeadlinesScreen
import com.uniplanner.app.ui.screens.GymScreen
import com.uniplanner.app.ui.screens.StudyScreen
import com.uniplanner.app.ui.screens.WeekScreen
import com.uniplanner.app.ui.theme.UniPlannerTheme

class MainActivity : ComponentActivity() {
    private val askNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            UniPlannerTheme {
                UniPlannerRoot()
            }
        }
    }
}

private enum class Tab(val route: String, @StringRes val label: Int, val icon: ImageVector) {
    Week("week", R.string.tab_week, Icons.Filled.CalendarMonth),
    Courses("courses", R.string.tab_courses, Icons.Filled.School),
    Deadlines("deadlines", R.string.tab_deadlines, Icons.AutoMirrored.Filled.EventNote),
    Study("study", R.string.tab_study, Icons.Filled.Timer),
    Gym("gym", R.string.tab_gym, Icons.Filled.FitnessCenter),
}

@Composable
private fun UniPlannerRoot(vm: AppViewModel = viewModel()) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = current == tab.route,
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
            composable(Tab.Week.route) { WeekScreen(vm) }
            composable(Tab.Courses.route) { CoursesScreen(vm) }
            composable(Tab.Deadlines.route) { DeadlinesScreen(vm) }
            composable(Tab.Study.route) { StudyScreen(vm) }
            composable(Tab.Gym.route) { GymScreen(vm) }
        }
    }
}
