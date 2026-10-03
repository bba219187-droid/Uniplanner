package com.uniplanner.app.location

import com.uniplanner.app.ui.theme.frame
import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.uniplanner.app.R
import com.uniplanner.app.ui.screens.ScreenHeader
import com.uniplanner.app.ui.screens.SectionTitle
import kotlinx.coroutines.delay

private fun ago(at: Long): String =
    DateUtils.getRelativeTimeSpanString(at, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()

/** Sharing choices, friends' last places and, for admins, the way into the overview. */
@Composable
fun LocationScreen(onOpenAdmin: () -> Unit, vm: LocationViewModel = viewModel()) {
    val context = LocalContext.current
    val choices by vm.choices.collectAsStateWithLifecycle()
    val friends by vm.friends.collectAsStateWithLifecycle()
    val isAdmin by vm.isAdmin.collectAsStateWithLifecycle()
    val signedIn by vm.signedIn.collectAsStateWithLifecycle()
    val c = choices ?: LocationChoices(stats = false, friends = false)
    // Read again whenever the switches change.
    val exact = remember(c) { LocationShare.isExact(context) }
    var pending by remember { mutableStateOf<LocationChoices?>(null) }
    var allowed by remember { mutableStateOf(LocationShare.hasPermission(context)) }
    var denied by remember { mutableStateOf(false) }
    // Picks up a permission given in the phone's settings while the app was in the background.
    LaunchedEffect(Unit) {
        while (true) {
            allowed = LocationShare.hasPermission(context)
            if (allowed) denied = false
            delay(1_000)
        }
    }

    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        allowed = result.values.any { it }
        denied = !allowed
        pending?.let { if (allowed) vm.setChoices(it) }
        pending = null
    }
    fun choose(next: LocationChoices) {
        val turningOn = (next.stats && !c.stats) || (next.friends && !c.friends) || (next.map && !c.map)
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
        if (!signedIn) {
            item { Text(stringResource(R.string.location_sign_in), style = MaterialTheme.typography.bodyMedium) }
        } else {
            item {
                ChoiceRow(
                    title = stringResource(R.string.location_friends),
                    detail = stringResource(if (c.friends && !exact) R.string.location_friends_detail_area else R.string.location_friends_detail),
                    checked = c.friends,
                    onChange = { choose(c.copy(friends = it)) },
                )
            }
            item {
                ChoiceRow(
                    title = stringResource(R.string.location_map),
                    detail = stringResource(R.string.location_map_detail),
                    checked = c.map,
                    onChange = { choose(c.copy(map = it)) },
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
            if (denied || ((c.overview || c.friends) && !allowed)) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.location_no_permission), color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                            runCatching { context.startActivity(intent) }
                        }) { Text(stringResource(R.string.location_open_settings)) }
                    }
                }
            }
            item { SectionTitle(stringResource(R.string.location_friends_title)) }
            if (friends.isNotEmpty()) {
                item {
                    PinMap(
                        pins = friends.map { f ->
                            MapPin(f.uid, f.lat, f.lng, title = f.name, snippet = listOf(f.city, ago(f.at)).filter { it.isNotBlank() }.joinToString(" · "))
                        },
                        dark = MaterialTheme.colorScheme.background.luminance() < 0.5f,
                        pinColor = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.fillMaxWidth().height(320.dp).clip(RoundedCornerShape(24.dp)),
                    )
                }
            }
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
                    Modifier.fillMaxWidth().frame(MaterialTheme.colorScheme.surfaceContainerHigh, 20.dp, outlined = true)
                        .clickable {
                            val uri = Uri.parse("geo:${f.lat},${f.lng}?q=${f.lat},${f.lng}(${Uri.encode(f.name)})")
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                        }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val photo = com.uniplanner.app.settings.rememberFriendPhoto(f.uid)
                    if (photo != null) com.uniplanner.app.settings.RoundPhoto(photo, 40.dp)
                    else Box(
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
        Modifier.fillMaxWidth().frame(MaterialTheme.colorScheme.surfaceContainerHigh, 20.dp, outlined = true)
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

/** The admin's overview: a map of the students who agreed to be on it, and students per city. No names. */
@Composable
fun AdminScreen(vm: LocationViewModel = viewModel()) {
    val overview by vm.overview.collectAsStateWithLifecycle()
    val isAdmin by vm.isAdmin.collectAsStateWithLifecycle()
    val list = overview?.cities.orEmpty()
    val dots = overview?.dots.orEmpty()
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
                    Modifier.fillMaxWidth().frame(MaterialTheme.colorScheme.inverseSurface, 28.dp).padding(20.dp),
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
                    Text(
                        pluralStringResource(R.plurals.admin_on_map, dots.size, dots.size),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                    )
                }
            }
            item {
                PinMap(
                    pins = dots,
                    dark = MaterialTheme.colorScheme.background.luminance() < 0.5f,
                    pinColor = MaterialTheme.colorScheme.secondary,
                    dots = true,
                    modifier = Modifier.fillMaxWidth().height(380.dp).clip(RoundedCornerShape(24.dp)),
                )
            }
            items(list, key = { "${it.city}|${it.country}" }) { row ->
                Column(
                    Modifier.fillMaxWidth().frame(MaterialTheme.colorScheme.surfaceContainerHigh, 18.dp).padding(14.dp),
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

/**
 * Asked once of students who shared their area with friends before exact positions existed: they
 * agreed to about 100 m, so the exact position waits for their yes.
 */
@Composable
fun ExactLocationQuestion() {
    val context = LocalContext.current
    var show by remember { mutableStateOf(LocationShare.shouldAskExact(context)) }
    if (!show) return
    fun answer(yes: Boolean) {
        LocationShare.answerExact(context, yes)
        show = false
    }
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.exact_q_title)) },
        text = { Text(stringResource(R.string.exact_q_text)) },
        confirmButton = { TextButton(onClick = { answer(true) }) { Text(stringResource(R.string.exact_q_yes)) } },
        dismissButton = { TextButton(onClick = { answer(false) }) { Text(stringResource(R.string.exact_q_no)) } },
    )
}
