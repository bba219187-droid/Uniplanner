package com.uniplanner.app.ui.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Grade
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.uniplanner.app.BuildConfig
import com.uniplanner.app.R

private data class MoreItem(
    val route: String,
    @StringRes val title: Int,
    @StringRes val subtitle: Int,
    val icon: ImageVector,
    val tint: Color,
)

private data class MoreGroup(@StringRes val title: Int, val items: List<MoreItem>)

private val groups = listOf(
    MoreGroup(
        R.string.more_group_studies,
        listOf(
            MoreItem("courses", R.string.tab_courses, R.string.more_courses_sub, Icons.Filled.School, Color(0xFF4F46E5)),
            MoreItem("grades", R.string.more_grades, R.string.more_grades_sub, Icons.Filled.Grade, Color(0xFFF59E0B)),
            MoreItem("stats", R.string.more_stats, R.string.more_stats_sub, Icons.Filled.BarChart, Color(0xFF0D9488)),
        ),
    ),
    MoreGroup(
        R.string.more_group_connections,
        listOf(
            MoreItem("agenda", R.string.more_agenda, R.string.more_agenda_sub, Icons.Filled.EventAvailable, Color(0xFF0EA5E9)),
            MoreItem("moodle", R.string.more_moodle, R.string.more_moodle_sub, Icons.AutoMirrored.Filled.MenuBook, Color(0xFFF97316)),
        ),
    ),
    MoreGroup(
        R.string.more_group_account,
        listOf(
            MoreItem("backup", R.string.more_backup, R.string.more_backup_sub, Icons.Filled.CloudDone, Color(0xFF7C3AED)),
            MoreItem("settings", R.string.settings_title, R.string.more_settings_sub, Icons.Filled.Settings, Color(0xFF64748B)),
        ),
    ),
)

@Composable
fun MoreScreen(onOpen: (String) -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ScreenHeader(stringResource(R.string.tab_more))
        groups.forEach { group ->
            Text(
                stringResource(group.title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, start = 4.dp),
            )
            Card(Modifier.fillMaxWidth()) {
                group.items.forEachIndexed { i, item ->
                    if (i > 0) HorizontalDivider(Modifier.padding(start = 68.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    Row(
                        Modifier.fillMaxWidth().clickable { onOpen(item.route) }.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconBadge(item.icon, item.tint)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(item.title), style = MaterialTheme.typography.titleSmall)
                            Text(
                                stringResource(item.subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.more_version, BuildConfig.VERSION_NAME) + "\n" + stringResource(R.string.splash_copyright),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
        )
    }
}
