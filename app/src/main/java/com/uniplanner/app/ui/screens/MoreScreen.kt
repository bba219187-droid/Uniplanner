package com.uniplanner.app.ui.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Grade
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.uniplanner.app.BuildConfig
import com.uniplanner.app.R

private data class MoreItem(val route: String, @StringRes val title: Int, @StringRes val subtitle: Int, val icon: ImageVector)

private val items = listOf(
    MoreItem("courses", R.string.tab_courses, R.string.more_courses_sub, Icons.Filled.School),
    MoreItem("moodle", R.string.more_moodle, R.string.more_moodle_sub, Icons.AutoMirrored.Filled.MenuBook),
    MoreItem("grades", R.string.more_grades, R.string.more_grades_sub, Icons.Filled.Grade),
    MoreItem("stats", R.string.more_stats, R.string.more_stats_sub, Icons.Filled.BarChart),
    MoreItem("gym", R.string.tab_gym, R.string.more_gym_sub, Icons.Filled.FitnessCenter),
    MoreItem("backup", R.string.more_backup, R.string.more_backup_sub, Icons.Filled.CloudDone),
)

@Composable
fun MoreScreen(onOpen: (String) -> Unit) {
    Column {
        items.forEach { item ->
            ListItem(
                headlineContent = { Text(stringResource(item.title)) },
                supportingContent = { Text(stringResource(item.subtitle)) },
                leadingContent = { Icon(item.icon, contentDescription = null) },
                modifier = Modifier.fillMaxWidth().clickable { onOpen(item.route) },
            )
            HorizontalDivider()
        }
        ListItem(
            headlineContent = { Text(stringResource(R.string.more_version, BuildConfig.VERSION_NAME)) },
        )
    }
}
