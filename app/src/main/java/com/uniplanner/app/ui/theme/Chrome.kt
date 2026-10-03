package com.uniplanner.app.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * What each look builds the screens with, beyond colours and letters: the frame of a block,
 * the bar of tabs, the title of a screen, the switches, the icon badges and the paper behind.
 */

/**
 * The frame of a block on any screen, in the current look: Play is flat like a music app,
 * Ribbons cut a corner and hang a stripe, Metro is a sign, Riso prints a shadow out of
 * register, Marker tapes a sheet of paper, Classic is a raised Material card, Paper is the old card.
 * [radius] and [outlined] keep the Paper look exactly as it was.
 */
@Composable
fun Modifier.frame(color: Color = MaterialTheme.colorScheme.surface, radius: Dp = 24.dp, outlined: Boolean = false): Modifier {
    val look = LocalUniLook.current
    val colors = MaterialTheme.colorScheme
    return when (look.style) {
        AppStyle.PAPEL -> {
            val shape = RoundedCornerShape(radius)
            (if (outlined) border(1.dp, colors.outlineVariant, shape) else this).clip(shape).background(color)
        }
        AppStyle.CLASSICO -> {
            val shape = RoundedCornerShape(minOf(radius, 16.dp))
            shadow(2.dp, shape, clip = false).clip(shape).background(color)
        }
        AppStyle.MARCA -> clip(RoundedCornerShape(8.dp)).background(color)
        AppStyle.FITAS -> {
            val shape = CutCornerShape(topEnd = 18.dp)
            val stripe = colors.primary
            clip(shape).background(color).drawBehind {
                drawRect(stripe, size = Size(6.dp.toPx(), size.height))
            }.padding(start = 6.dp)
        }
        AppStyle.METRO -> {
            val shape = RoundedCornerShape(4.dp)
            val rule = colors.onSurface
            border(2.dp, rule, shape).clip(shape).background(color).drawBehind {
                drawRect(look.accent, size = Size(size.width, 5.dp.toPx()))
            }.padding(top = 5.dp)
        }
        AppStyle.RISO -> {
            val shape = RoundedCornerShape(4.dp)
            val ink = colors.secondary
            drawBehind {
                drawRoundRect(ink.copy(alpha = 0.9f), topLeft = Offset(5.dp.toPx(), 5.dp.toPx()), size = size, cornerRadius = CornerRadius(4.dp.toPx()))
            }.border(2.dp, colors.onBackground, shape).clip(shape).background(color)
        }
        AppStyle.MARCADOR -> {
            val shape = RoundedCornerShape(3.dp)
            val tape = look.accent.copy(alpha = 0.7f)
            shadow(3.dp, shape, clip = false).clip(shape).background(color).drawWithContent {
                drawContent()
                val w = 56.dp.toPx()
                drawRect(tape, topLeft = Offset(size.width / 2 - w / 2, 0f), size = Size(w, 9.dp.toPx()))
            }
        }
    }
}

/** One tab of the bar at the bottom. */
class TabItem(val icon: ImageVector, val label: String, val on: Boolean, val onClick: () -> Unit)

/** The bar of tabs at the bottom, drawn the way the current look draws it. */
@Composable
fun TabBar(tabs: List<TabItem>) {
    when (LocalUniLook.current.style) {
        AppStyle.PAPEL -> PillBar(tabs)
        AppStyle.MARCA -> PlayBar(tabs)
        AppStyle.CLASSICO -> NavigationBar {
            tabs.forEach { t ->
                NavigationBarItem(
                    selected = t.on,
                    onClick = t.onClick,
                    icon = { Icon(t.icon, contentDescription = null) },
                    label = { Text(t.label, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 11.sp) },
                )
            }
        }
        AppStyle.METRO -> MetroBar(tabs)
        AppStyle.RISO -> RisoBar(tabs)
        AppStyle.MARCADOR -> MarkerBar(tabs)
        AppStyle.FITAS -> RibbonBar(tabs)
    }
}

/** Paper: the dark pill floating above the bottom; the open tab sits in a light circle and jumps. */
@Composable
private fun PillBar(tabs: List<TabItem>) {
    val colors = MaterialTheme.colorScheme
    val look = LocalUniLook.current
    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 12.dp)) {
        Row(
            Modifier.fillMaxWidth().height(64.dp).clip(CircleShape).background(colors.inverseSurface).padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEach { t ->
                val bg by animateColorAsState(if (t.on) look.accent else Color.Transparent, label = "tabBg")
                val fg by animateColorAsState(if (t.on) look.onAccent else colors.inverseOnSurface.copy(alpha = 0.6f), label = "tabFg")
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier.fillMaxWidth().widthIn(max = 48.dp).aspectRatio(1f).clip(CircleShape).background(bg)
                            .clickable(role = Role.Tab, onClick = t.onClick),
                        contentAlignment = Alignment.Center,
                    ) {
                        val jump by androidx.compose.animation.core.animateFloatAsState(
                            if (t.on) 1f else 0f,
                            spring(dampingRatio = 0.32f, stiffness = Spring.StiffnessMedium),
                            label = "tabJump",
                        )
                        Icon(
                            t.icon,
                            contentDescription = t.label,
                            tint = fg,
                            modifier = Modifier.graphicsLayer {
                                scaleX = 1f + 0.18f * jump
                                scaleY = 1f + 0.18f * jump
                                rotationZ = (1f - jump) * if (t.on) -30f else 0f
                            },
                        )
                    }
                }
            }
        }
    }
}

/** Play: a full-width bar fading out of the screen, icons over small names, like a music app. */
@Composable
private fun PlayBar(tabs: List<TabItem>) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth()
            .background(Brush.verticalGradient(listOf(colors.background.copy(alpha = 0f), colors.background.copy(alpha = 0.96f), colors.background)))
            .navigationBarsPadding().padding(top = 18.dp, bottom = 6.dp),
    ) {
        tabs.forEach { t ->
            val fg by animateColorAsState(if (t.on) colors.onBackground else colors.onSurfaceVariant, label = "playFg")
            Column(
                Modifier.weight(1f).clickable(role = Role.Tab, onClick = t.onClick).padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(t.icon, contentDescription = null, tint = fg, modifier = Modifier.size(if (t.on) 27.dp else 24.dp))
                Text(
                    t.label,
                    color = fg,
                    fontSize = 10.sp,
                    fontWeight = if (t.on) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
    }
}

/** Metro: the tabs are stations on a line; the open one is a big yellow stop with its name. */
@Composable
private fun MetroBar(tabs: List<TabItem>) {
    val colors = MaterialTheme.colorScheme
    val look = LocalUniLook.current
    val line = colors.primary
    Box(
        Modifier.fillMaxWidth().background(colors.surface).navigationBarsPadding().height(76.dp)
            .drawBehind {
                val y = 26.dp.toPx()
                val inset = size.width / tabs.size / 2
                drawLine(line, Offset(inset, y), Offset(size.width - inset, y), strokeWidth = 7.dp.toPx())
            },
    ) {
        Row(Modifier.fillMaxWidth()) {
            tabs.forEach { t ->
                val dot by animateDpAsState(if (t.on) 34.dp else 18.dp, label = "station")
                Column(
                    Modifier.weight(1f).clickable(role = Role.Tab, onClick = t.onClick),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.height(52.dp), contentAlignment = Alignment.Center) {
                        Box(
                            Modifier.size(dot).clip(CircleShape).background(if (t.on) look.accent else colors.surface)
                                .border(if (t.on) 3.dp else 4.dp, if (t.on) colors.onSurface else line, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (t.on) Icon(t.icon, contentDescription = null, tint = look.onAccent, modifier = Modifier.size(18.dp))
                        }
                    }
                    Text(
                        t.label.uppercase(),
                        fontSize = 9.sp,
                        fontWeight = if (t.on) FontWeight.Black else FontWeight.SemiBold,
                        letterSpacing = 0.6.sp,
                        color = if (t.on) colors.onSurface else colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                    )
                }
            }
        }
    }
}

/** Riso: a printed block with a misregistered shadow; the open tab is a solid block of yellow ink. */
@Composable
private fun RisoBar(tabs: List<TabItem>) {
    val colors = MaterialTheme.colorScheme
    val look = LocalUniLook.current
    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 14.dp, end = 19.dp, top = 6.dp, bottom = 17.dp)) {
        Row(
            Modifier.fillMaxWidth().height(62.dp)
                .drawBehind { drawRect(colors.secondary, topLeft = Offset(5.dp.toPx(), 5.dp.toPx()), size = size) }
                .background(colors.surface).border(2.dp, colors.onBackground),
        ) {
            tabs.forEachIndexed { i, t ->
                if (i > 0) Box(Modifier.width(2.dp).height(62.dp).background(colors.onBackground))
                Column(
                    Modifier.weight(1f).height(62.dp).background(if (t.on) look.accent else Color.Transparent)
                        .clickable(role = Role.Tab, onClick = t.onClick),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(t.icon, contentDescription = null, tint = if (t.on) look.onAccent else colors.onSurface)
                    if (t.on) Text(t.label.uppercase(), fontSize = 9.sp, fontWeight = FontWeight.Black, color = look.onAccent, maxLines = 1)
                }
            }
        }
    }
}

/** Marker: a strip of paper with a dashed edge; the open tab gets a highlighter swipe and a handwritten name. */
@Composable
private fun MarkerBar(tabs: List<TabItem>) {
    val colors = MaterialTheme.colorScheme
    val look = LocalUniLook.current
    val edge = colors.onSurface.copy(alpha = 0.35f)
    Row(
        Modifier.fillMaxWidth().background(colors.surface)
            .drawBehind {
                drawLine(edge, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)))
            }
            .navigationBarsPadding().padding(vertical = 8.dp),
    ) {
        tabs.forEach { t ->
            Column(
                Modifier.weight(1f).clickable(role = Role.Tab, onClick = t.onClick),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.size(44.dp, 34.dp).then(if (t.on) Modifier.markerBand(look.accent) else Modifier),
                    contentAlignment = Alignment.Center,
                ) { Icon(t.icon, contentDescription = null, tint = colors.onSurface.copy(alpha = if (t.on) 1f else 0.55f)) }
                Text(
                    t.label,
                    style = TextStyle(fontFamily = Caveat, fontSize = 15.sp),
                    color = colors.onSurface.copy(alpha = if (t.on) 1f else 0.55f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Ribbons: each tab is a bookmark hanging from the top edge; the open one drops down in red. */
@Composable
private fun RibbonBar(tabs: List<TabItem>) {
    val colors = MaterialTheme.colorScheme
    val look = LocalUniLook.current
    val rule = colors.onSurface
    Row(
        Modifier.fillMaxWidth().background(colors.surface)
            .drawBehind { drawRect(rule, size = Size(size.width, 3.dp.toPx())) }
            .navigationBarsPadding().height(72.dp),
    ) {
        tabs.forEach { t ->
            val drop by animateDpAsState(if (t.on) 60.dp else 38.dp, spring(dampingRatio = 0.5f), label = "ribbon")
            Box(
                Modifier.weight(1f).height(72.dp).clickable(role = Role.Tab, onClick = t.onClick),
                contentAlignment = Alignment.TopCenter,
            ) {
                val fill = if (t.on) look.accent else colors.surfaceVariant
                Box(
                    Modifier.width(40.dp).height(drop).drawBehind {
                        val notch = 9.dp.toPx()
                        val p = Path().apply {
                            moveTo(0f, 0f); lineTo(size.width, 0f); lineTo(size.width, size.height)
                            lineTo(size.width / 2, size.height - notch); lineTo(0f, size.height); close()
                        }
                        drawPath(p, fill)
                    },
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Icon(
                        t.icon,
                        contentDescription = t.label,
                        tint = if (t.on) look.onAccent else colors.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 14.dp).size(20.dp),
                    )
                }
            }
        }
    }
}

/** The title at the top of a main screen, as the current look writes it. */
@Composable
fun ThemedHeader(title: String, subtitle: String?, modifier: Modifier = Modifier) {
    val look = LocalUniLook.current
    val colors = MaterialTheme.colorScheme
    val type = MaterialTheme.typography
    val base = modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp)
    when (look.style) {
        AppStyle.PAPEL -> {
            // The title drops in with a bounce each time the screen opens, echoing the entrance.
            val drop = remember { Animatable(0f) }
            LaunchedEffect(Unit) { drop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow)) }
            Column(
                base.graphicsLayer {
                    translationY = (1f - drop.value) * -60f
                    rotationZ = (1f - drop.value) * -4f
                    alpha = drop.value.coerceIn(0f, 1f)
                },
            ) {
                if (subtitle != null) Text(subtitle, style = type.labelLarge, color = colors.primary)
                Text(title, style = type.headlineMedium)
            }
        }
        AppStyle.MARCA -> Column(base.padding(top = 8.dp)) {
            Text(title, style = TextStyle(fontFamily = look.numbers, fontWeight = FontWeight.Black, fontSize = 34.sp, letterSpacing = (-1).sp))
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = type.labelLarge,
                    color = colors.onSurface,
                    modifier = Modifier.padding(top = 10.dp).clip(CircleShape).background(colors.surfaceContainerHigh).padding(horizontal = 14.dp, vertical = 7.dp),
                )
            }
        }
        AppStyle.CLASSICO -> Column(base.padding(top = 12.dp, bottom = 4.dp)) {
            Text(title, style = type.headlineLarge.copy(fontWeight = FontWeight.Normal))
            if (subtitle != null) Text(subtitle, style = type.bodyMedium, color = colors.onSurfaceVariant)
        }
        AppStyle.METRO -> Column(base) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(38.dp).clip(RoundedCornerShape(8.dp)).background(colors.primary), contentAlignment = Alignment.Center) {
                    Text(title.take(1).uppercase(), color = colors.onPrimary, fontWeight = FontWeight.Black, fontSize = 20.sp)
                }
                Spacer(Modifier.width(12.dp))
                Text(title, style = type.headlineMedium.copy(fontWeight = FontWeight.Black))
            }
            Box(Modifier.padding(top = 10.dp).fillMaxWidth().height(4.dp).background(look.accent))
            if (subtitle != null) Text(subtitle.uppercase(), style = type.labelMedium, letterSpacing = 1.2.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
        }
        AppStyle.RISO -> Column(base) {
            val style = TextStyle(fontFamily = look.numbers, fontWeight = FontWeight.Black, fontSize = 38.sp, lineHeight = 38.sp, letterSpacing = (-1).sp)
            Box {
                Text(title.uppercase(), style = style, color = colors.secondary.copy(alpha = 0.85f), modifier = Modifier.offset(4.dp, 3.dp))
                Text(title.uppercase(), style = style, color = colors.primary.copy(alpha = 0.9f))
            }
            if (subtitle != null) {
                Text(
                    subtitle.uppercase(),
                    style = type.labelLarge.copy(fontWeight = FontWeight.Black),
                    color = look.onAccent,
                    modifier = Modifier.padding(top = 8.dp).background(look.accent).padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
        AppStyle.MARCADOR -> Column(base) {
            if (subtitle != null) Text(subtitle, style = type.labelLarge, color = colors.onSurfaceVariant)
            Text(
                title,
                style = TextStyle(fontFamily = Caveat, fontSize = 44.sp, lineHeight = 46.sp),
                modifier = Modifier.markerBand(look.accent),
            )
        }
        AppStyle.FITAS -> Column(base) {
            val ribbon = colors.primary
            Box(
                Modifier.drawBehind {
                    val tail = size.height * 0.5f
                    val p = Path().apply {
                        moveTo(0f, 0f); lineTo(size.width + tail, 0f); lineTo(size.width, size.height / 2)
                        lineTo(size.width + tail, size.height); lineTo(0f, size.height); close()
                    }
                    drawPath(p, ribbon)
                }.padding(start = 14.dp, end = 10.dp, top = 6.dp, bottom = 4.dp),
            ) {
                Text(title.uppercase(), style = TextStyle(fontFamily = look.numbers, fontWeight = FontWeight.Black, fontSize = 34.sp, letterSpacing = 0.5.sp), color = colors.onPrimary)
            }
            if (subtitle != null) Text(subtitle, style = type.labelLarge, color = colors.primary, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

/** A small heading inside a screen. */
@Composable
fun ThemedSection(text: String, modifier: Modifier = Modifier) {
    val look = LocalUniLook.current
    val type = MaterialTheme.typography
    val m = modifier.padding(vertical = 8.dp)
    when (look.style) {
        AppStyle.METRO, AppStyle.FITAS -> Text(text.uppercase(), style = type.titleSmall.copy(fontWeight = FontWeight.Black, letterSpacing = 1.4.sp), modifier = m)
        AppStyle.RISO -> Text(text.uppercase(), style = type.titleMedium.copy(fontWeight = FontWeight.Black), modifier = m)
        AppStyle.MARCADOR -> Text(text, style = TextStyle(fontFamily = Caveat, fontSize = 26.sp), modifier = m)
        AppStyle.MARCA -> Text(text, style = type.titleLarge.copy(fontWeight = FontWeight.Black), modifier = m)
        else -> Text(text, style = type.titleMedium, modifier = m)
    }
}

/** Two or three options to switch between, as the current look draws a switch. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ThemedTabs(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val look = LocalUniLook.current
    val colors = MaterialTheme.colorScheme
    val type = MaterialTheme.typography
    when (look.style) {
        AppStyle.PAPEL -> Row(modifier.fillMaxWidth().background(colors.surfaceVariant, CircleShape).padding(4.dp)) {
            options.forEachIndexed { i, label ->
                val on = i == selected
                Box(
                    Modifier.weight(1f).clip(CircleShape)
                        .background(if (on) colors.surfaceContainerHighest else Color.Transparent, CircleShape)
                        .clickable { onSelect(i) }.padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(label, style = type.labelLarge, color = if (on) colors.primary else colors.onSurfaceVariant) }
            }
        }
        AppStyle.CLASSICO -> SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth()) {
            options.forEachIndexed { i, label ->
                SegmentedButton(
                    selected = i == selected,
                    onClick = { onSelect(i) },
                    shape = SegmentedButtonDefaults.itemShape(i, options.size),
                ) { Text(label, maxLines = 1) }
            }
        }
        AppStyle.MARCA -> Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEachIndexed { i, label ->
                val on = i == selected
                Text(
                    label,
                    style = type.labelLarge,
                    color = if (on) look.onAccent else colors.onSurface,
                    modifier = Modifier.clip(CircleShape).background(if (on) look.accent else colors.surfaceContainerHigh)
                        .clickable { onSelect(i) }.padding(horizontal = 16.dp, vertical = 9.dp),
                )
            }
        }
        AppStyle.METRO -> Row(modifier.fillMaxWidth()) {
            options.forEachIndexed { i, label ->
                val on = i == selected
                Column(Modifier.weight(1f).clickable { onSelect(i) }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        label.uppercase(),
                        style = type.labelLarge.copy(fontWeight = FontWeight.Black, letterSpacing = 1.sp),
                        color = if (on) colors.onSurface else colors.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 10.dp),
                    )
                    Box(Modifier.fillMaxWidth().height(if (on) 5.dp else 1.dp).background(if (on) colors.primary else colors.outlineVariant))
                }
            }
        }
        AppStyle.RISO -> Row(modifier.fillMaxWidth().border(2.dp, colors.onBackground)) {
            options.forEachIndexed { i, label ->
                val on = i == selected
                if (i > 0) Box(Modifier.width(2.dp).height(42.dp).background(colors.onBackground))
                Box(
                    Modifier.weight(1f).height(42.dp).background(if (on) look.accent else colors.surface).clickable { onSelect(i) },
                    contentAlignment = Alignment.Center,
                ) { Text(label.uppercase(), style = type.labelLarge.copy(fontWeight = FontWeight.Black), color = if (on) look.onAccent else colors.onSurface) }
            }
        }
        AppStyle.MARCADOR -> Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            options.forEachIndexed { i, label ->
                val on = i == selected
                Text(
                    label,
                    style = TextStyle(fontFamily = Caveat, fontSize = 22.sp),
                    color = colors.onSurface.copy(alpha = if (on) 1f else 0.5f),
                    modifier = Modifier.clickable { onSelect(i) }.padding(horizontal = 8.dp, vertical = 6.dp)
                        .then(if (on) Modifier.markerBand(look.accent) else Modifier),
                )
            }
        }
        AppStyle.FITAS -> Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEachIndexed { i, label ->
                val on = i == selected
                Box(
                    Modifier.weight(1f).clip(CutCornerShape(topEnd = 12.dp, bottomStart = 12.dp))
                        .background(if (on) colors.primary else colors.surfaceVariant).clickable { onSelect(i) }.padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(label.uppercase(), style = type.labelLarge.copy(fontWeight = FontWeight.Black), color = if (on) colors.onPrimary else colors.onSurfaceVariant) }
            }
        }
    }
}

/** An icon on a small badge, used in lists and menus, shaped by the current look. */
@Composable
fun ThemedBadge(icon: ImageVector, tint: Color, modifier: Modifier = Modifier) {
    val look = LocalUniLook.current
    val colors = MaterialTheme.colorScheme
    val (box, iconTint) = when (look.style) {
        AppStyle.PAPEL -> modifier.size(40.dp).background(tint.copy(alpha = 0.15f), RoundedCornerShape(12.dp)) to tint
        AppStyle.CLASSICO -> modifier.size(40.dp).background(tint.copy(alpha = 0.15f), CircleShape) to tint
        AppStyle.MARCA -> modifier.size(44.dp).clip(RoundedCornerShape(4.dp))
            .background(Brush.linearGradient(listOf(tint, androidx.compose.ui.graphics.lerp(tint, Color.Black, 0.45f)))) to Color.White
        AppStyle.METRO -> modifier.size(36.dp).background(tint, CircleShape).border(3.dp, colors.surface, CircleShape) to Color.White
        AppStyle.RISO -> modifier.size(40.dp)
            .drawBehind { drawRect(tint, topLeft = Offset(3.dp.toPx(), 3.dp.toPx()), size = size) }
            .background(colors.surface).border(2.dp, colors.onBackground) to colors.onSurface
        AppStyle.MARCADOR -> modifier.size(40.dp).drawBehind {
            drawCircle(tint.copy(alpha = 0.45f), radius = size.minDimension / 2.1f, center = Offset(size.width * 0.55f, size.height * 0.55f))
        } to colors.onSurface
        AppStyle.FITAS -> modifier.size(40.dp).clip(CutCornerShape(topEnd = 12.dp)).background(tint) to Color.White
    }
    Box(box, contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp)) }
}

/** The paper behind every screen: squared paper for Marker, a dot screen for Riso, a glow for Play. */
fun Modifier.screenTexture(look: UniLook, ink: Color, glow: Color): Modifier = when (look.style) {
    AppStyle.MARCADOR -> drawBehind {
        val step = 22.dp.toPx()
        val c = ink.copy(alpha = 0.07f)
        var x = 0f
        while (x < size.width) { drawLine(c, Offset(x, 0f), Offset(x, size.height), 1f); x += step }
        var y = 0f
        while (y < size.height) { drawLine(c, Offset(0f, y), Offset(size.width, y), 1f); y += step }
        drawLine(Color(0xFFFF6B8A).copy(alpha = 0.35f), Offset(30.dp.toPx(), 0f), Offset(30.dp.toPx(), size.height), 2f)
    }
    AppStyle.RISO -> drawBehind {
        val step = 9.dp.toPx()
        val c = ink.copy(alpha = 0.05f)
        var y = 0f
        var row = 0
        while (y < size.height) {
            var x = if (row % 2 == 0) 0f else step / 2
            while (x < size.width) { drawCircle(c, 1.4.dp.toPx(), Offset(x, y)); x += step }
            y += step; row++
        }
    }
    AppStyle.MARCA -> drawBehind {
        drawRect(Brush.verticalGradient(listOf(glow.copy(alpha = 0.28f), Color.Transparent), endY = size.height * 0.45f))
    }
    AppStyle.METRO -> drawBehind {
        // A faint map line running down the side of every screen.
        drawLine(glow.copy(alpha = 0.12f), Offset(size.width - 18.dp.toPx(), 0f), Offset(size.width - 18.dp.toPx(), size.height), 10.dp.toPx())
    }
    else -> this
}

/**
 * A card in the current look. Paper and Classic keep Material's own card; the others draw
 * their [frame]. Same arguments as Material's card, so screens only change the import.
 */
@Composable
fun Card(
    modifier: Modifier = Modifier,
    colors: androidx.compose.material3.CardColors = androidx.compose.material3.CardDefaults.cardColors(),
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    when (LocalUniLook.current.style) {
        AppStyle.PAPEL, AppStyle.CLASSICO -> androidx.compose.material3.Card(modifier, colors = colors, content = content)
        else -> androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides colors.contentColor) {
            Column(modifier.frame(colors.containerColor, 16.dp), content = content)
        }
    }
}
