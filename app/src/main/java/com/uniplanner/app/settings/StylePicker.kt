package com.uniplanner.app.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uniplanner.app.R
import com.uniplanner.app.ui.theme.AppStyle
import com.uniplanner.app.ui.theme.LocalUniLook
import com.uniplanner.app.ui.theme.StylePrefs
import com.uniplanner.app.ui.theme.lookFor
import com.uniplanner.app.ui.theme.schemeFor
import com.uniplanner.app.ui.theme.typographyFor

/** One card per look, drawn in that look's own colours and letters, so the student sees it before choosing. */
@Composable
fun StylePicker() {
    val ctx = LocalContext.current
    val chosen by StylePrefs.flow(ctx).collectAsState()
    val dark = LocalUniLook.current.dark
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        AppStyle.entries.forEach { style ->
            val scheme = remember(style, dark) { schemeFor(style, dark, ctx) }
            val look = remember(style, dark, scheme) { lookFor(style, dark, scheme) }
            val type = remember(style) { typographyFor(style) }
            val on = style == (chosen ?: AppStyle.MARCA)
            val shape = RoundedCornerShape(22.dp)
            Row(
                Modifier.fillMaxWidth().clip(shape).background(scheme.background)
                    .border(if (on) 2.dp else 1.dp, if (on) scheme.onBackground else scheme.outlineVariant, shape)
                    .clickable(role = Role.RadioButton) { StylePrefs.set(ctx, style) }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.width(92.dp)) {
                    Text(
                        "9h40",
                        style = TextStyle(fontFamily = look.numbers, fontWeight = look.numbersWeight, fontSize = 30.sp, letterSpacing = (-0.8).sp),
                        color = scheme.onBackground,
                        maxLines = 1,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 6.dp)) {
                        look.inks.take(4).forEach { Box(Modifier.size(14.dp).clip(CircleShape).background(it)) }
                    }
                }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(stringResource(style.label), style = type.titleMedium, color = scheme.onBackground)
                    Text(stringResource(style.about), style = type.bodySmall, color = scheme.onSurfaceVariant)
                    Box(Modifier.padding(top = 8.dp).width(64.dp).height(6.dp).clip(CircleShape).background(look.accent))
                }
                if (on) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = scheme.onBackground,
                        modifier = Modifier.padding(start = 8.dp).size(24.dp),
                    )
                }
            }
        }
    }
}

