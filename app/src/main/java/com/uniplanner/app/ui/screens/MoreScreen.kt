package com.uniplanner.app.ui.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import com.uniplanner.app.ui.theme.AppStyle
import com.uniplanner.app.ui.theme.LocalUniLook
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
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Grade
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import com.uniplanner.app.ui.theme.Card
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
            MoreItem("flashcards", R.string.more_flashcards, R.string.more_flashcards_sub, Icons.Filled.Layers, Color(0xFFDB2777)),
            MoreItem("achievements", R.string.more_achievements, R.string.more_achievements_sub, Icons.Filled.EmojiEvents, Color(0xFFEA580C)),
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
            MoreItem("location", R.string.more_location, R.string.more_location_sub, Icons.Filled.LocationOn, Color(0xFFC94220)),
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
            SectionTitle(stringResource(group.title), Modifier.padding(top = 4.dp, start = 4.dp))
            when (LocalUniLook.current.style) {
                AppStyle.MARCA -> TileGrid(group.items, onOpen)
                AppStyle.METRO -> StationList(group.items, onOpen)
                else -> ListCard(group.items, onOpen)
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

/** The usual list: each item a row with its badge, name and a line under it. */
@Composable
private fun ListCard(items: List<MoreItem>, onOpen: (String) -> Unit) {
            Card(Modifier.fillMaxWidth()) {
                items.forEachIndexed { i, item ->
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

/** Play: the items as coloured tiles two by two, like the genres page of a music app. */
@Composable
private fun TileGrid(items: List<MoreItem>, onOpen: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { item ->
                    Box(
                        Modifier.weight(1f).height(96.dp).clip(RoundedCornerShape(8.dp)).background(item.tint)
                            .clickable { onOpen(item.route) }.padding(12.dp),
                    ) {
                        Text(
                            stringResource(item.title),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                            color = Color.White,
                            maxLines = 2,
                        )
                        Icon(
                            item.icon,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.align(Alignment.BottomEnd).offset(x = 14.dp, y = 10.dp).size(58.dp).rotate(25f),
                        )
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** Metro: the items are stops on one line, each with its station circle. */
@Composable
private fun StationList(items: List<MoreItem>, onOpen: (String) -> Unit) {
    val line = MaterialTheme.colorScheme.primary
    val ground = MaterialTheme.colorScheme.surface
    Column(Modifier.fillMaxWidth()) {
        items.forEachIndexed { i, item ->
            Row(
                Modifier.fillMaxWidth().height(64.dp).clickable { onOpen(item.route) }.drawBehind {
                    val x = 22.dp.toPx()
                    val top = if (i == 0) size.height / 2 else 0f
                    val bottom = if (i == items.lastIndex) size.height / 2 else size.height
                    drawLine(line, Offset(x, top), Offset(x, bottom), strokeWidth = 8.dp.toPx())
                    drawCircle(ground, 11.dp.toPx(), Offset(x, size.height / 2))
                    drawCircle(line, 11.dp.toPx(), Offset(x, size.height / 2), style = Stroke(4.dp.toPx()))
                },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.width(48.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(item.title).uppercase(), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Black))
                    Text(stringResource(item.subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                Box(Modifier.size(28.dp).background(item.tint, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(item.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}
