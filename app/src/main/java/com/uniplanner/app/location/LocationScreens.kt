package com.uniplanner.app.location

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.uniplanner.app.R
import com.uniplanner.app.ui.screens.ScreenHeader
import com.uniplanner.app.ui.screens.SectionTitle

private fun ago(at: Long): String =
    DateUtils.getRelativeTimeSpanString(at, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()

/** Sharing choices, friends' last places and, for admins, the way into the overview. */
@Composable
fun LocationScreen(onOpenAdmin: () -> Unit, vm: LocationViewModel = viewModel()) {
    val context = LocalContext.current
    val choices by vm.choices.collectAsStateWithLifecycle()
    val friends by vm.friends.collectAsStateWithLifecycle()
    val isAdmin by vm.isAdmin.collectAsStateWithLifecycle()
    val c = choices ?: LocationChoices(stats = false, friends = false)
    var pending by remember { mutableStateOf<LocationChoices?>(null) }
    var allowed by remember { mutableStateOf(LocationShare.hasPermission(context)) }

    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        allowed = result.values.any { it }
        pending?.let { if (allowed) vm.setChoices(it) }
        pending = null
    }
    fun choose(next: LocationChoices) {
        val turningOn = (next.stats && !c.stats) || (next.friends && !c.friends)
        if (turningOn && !LocationShare.hasPermission(context)) {
            pending = next
            ask.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
        } else {
            vm.setChoices(next)
        }
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { ScreenHeader(stringResource(R.string.location_title)) }
        item {
            Text(
                stringResource(R.string.location_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!vm.signedIn) {
            item { Text(stringResource(R.string.location_sign_in), style = MaterialTheme.typography.bodyMedium) }
        } else {
            item {
                ChoiceRow(
                    title = stringResource(R.string.location_friends),
                    detail = stringResource(R.string.location_friends_detail),
                    checked = c.friends,
                    onChange = { choose(c.copy(friends = it)) },
                )
            }
            item {
                ChoiceRow(
                    title = stringResource(R.string.location_stats),
                    detail = stringResource(R.string.location_stats_detail),
                    checked = c.stats,
                    onChange = { choose(c.copy(stats = it)) },
                )
            }
            if ((c.stats || c.friends) && !allowed) {
                item { Text(stringResource(R.string.location_no_permission), color = MaterialTheme.colorScheme.error) }
            }
            item { SectionTitle(stringResource(R.string.location_friends_title)) }
            if (friends.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.location_friends_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(friends, key = { it.uid }) { f ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
                        .clickable {
                            val uri = Uri.parse("geo:${f.lat},${f.lng}?q=${f.lat},${f.lng}(${Uri.encode(f.name)})")
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                        }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
                        contentAlignment = Alignment.Center,
                    ) { Text(f.name.take(1).uppercase(), style = MaterialTheme.typography.titleMedium) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(f.name, style = MaterialTheme.typography.titleSmall)
                        Text(
                            listOf(f.city, ago(f.at)).filter { it.isNotBlank() }.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(Icons.Filled.Map, contentDescription = stringResource(R.string.location_open_map))
                }
            }
            if (isAdmin) {
                item {
                    Button(
                        onClick = onOpenAdmin,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.inverseSurface,
                            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                        ),
                    ) {
                        Text(stringResource(R.string.admin_title), modifier = Modifier.weight(1f))
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun ChoiceRow(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
            .clickable { onChange(!checked) }.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(10.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** The admin's overview: how many students share their city, and where they are. No names. */
@Composable
fun AdminScreen(vm: LocationViewModel = viewModel()) {
    val cities by vm.cities.collectAsStateWithLifecycle()
    val isAdmin by vm.isAdmin.collectAsStateWithLifecycle()
    val list = cities.orEmpty()
    val total = list.sumOf { it.students }
    val max = list.maxOfOrNull { it.students } ?: 1
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { ScreenHeader(stringResource(R.string.admin_title)) }
        if (!isAdmin) {
            item { Text(stringResource(R.string.admin_not_admin)) }
        } else {
            item {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(MaterialTheme.colorScheme.inverseSurface).padding(20.dp),
                ) {
                    Text(
                        stringResource(R.string.admin_sharing),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.7f),
                    )
                    Text("$total", style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.inversePrimary)
                    Text(
                        stringResource(R.string.admin_cities, list.size),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                    )
                }
            }
            items(list, key = { "${it.city}|${it.country}" }) { row ->
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row {
                        Text(
                            listOf(row.city, row.country).filter { it.isNotBlank() }.joinToString(", ")
                                .ifBlank { stringResource(R.string.admin_unknown) },
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f),
                        )
                        Text("${row.students}", style = MaterialTheme.typography.titleSmall)
                    }
                    Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.outlineVariant)) {
                        Box(
                            Modifier.fillMaxWidth(row.students.toFloat() / max).height(6.dp).clip(CircleShape)
                                .background(MaterialTheme.colorScheme.secondary),
                        )
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}
