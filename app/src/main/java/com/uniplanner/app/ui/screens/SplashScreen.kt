package com.uniplanner.app.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uniplanner.app.R
import com.uniplanner.app.ui.theme.Bricolage
import com.uniplanner.app.ui.theme.Coral
import com.uniplanner.app.ui.theme.Ink
import com.uniplanner.app.ui.theme.Mono
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The four soft card colours used across the app for courses and areas. */
private data class AreaCard(val emoji: String, val label: Int, val color: Color)

private val areaCards = listOf(
    AreaCard("📚", R.string.tab_study, Color(0xFFDCE3FC)),
    AreaCard("💪", R.string.tab_gym, Color(0xFFDDEBD0)),
    AreaCard("🍎", R.string.tab_health, Color(0xFFF6E6C3)),
    AreaCard("👋", R.string.tab_social, Color(0xFFEADCF5)),
)

/** The app's mark: the cap on a dark ink tile, like the launcher icon. */
@Composable
fun AppMark(size: Int = 76) {
    Box(
        Modifier.size(size.dp).clip(RoundedCornerShape((size * 0.28f).dp)).background(Ink),
        contentAlignment = Alignment.Center,
    ) {
        Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.size((size * 1.15f).dp))
        Box(Modifier.align(Alignment.TopEnd).padding((size * 0.16f).dp).size((size * 0.12f).dp).clip(CircleShape).background(Coral))
    }
}

/** Four colour cards, one per area, that drop in one after the other. */
@Composable
fun AreaCards(modifier: Modifier = Modifier, animate: Boolean = true) {
    val shown = remember { areaCards.map { Animatable(if (animate) 0f else 1f) } }
    LaunchedEffect(Unit) {
        shown.forEachIndexed { i, a ->
            launch {
                delay(120L * i)
                a.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
            }
        }
    }
    Column(modifier.widthIn(max = 320.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        areaCards.chunked(2).forEachIndexed { row, pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEachIndexed { col, card ->
                    val t = shown[row * 2 + col].value
                    // Slight tilt, alternating, like cards dropped on a desk.
                    val tilt = if ((row + col) % 2 == 0) -3f else 3f
                    Column(
                        Modifier.weight(1f)
                            .graphicsLayer {
                                alpha = t
                                translationY = (1f - t) * 60f
                                rotationZ = tilt * t
                            }
                            .clip(RoundedCornerShape(22.dp))
                            .background(card.color)
                            .padding(16.dp),
                    ) {
                        Text(card.emoji, fontSize = 26.sp)
                        Spacer(Modifier.height(18.dp))
                        Text(
                            stringResource(card.label),
                            fontFamily = Bricolage,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = Ink,
                        )
                    }
                }
            }
        }
    }
}

/** Opening screen: the area cards, the name, a short line, and the copyright. */
@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val title = remember { Animatable(0f) }
    val bar = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(350)
        title.animateTo(1f, tween(550, easing = FastOutSlowInEasing))
    }
    LaunchedEffect(Unit) {
        bar.animateTo(1f, tween(1800, easing = FastOutSlowInEasing))
        delay(150)
        onFinished()
    }

    val colors = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxSize().background(colors.background).statusBarsPadding().navigationBarsPadding().padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        AreaCards()
        Spacer(Modifier.height(40.dp))
        Column(
            Modifier.graphicsLayer { translationY = (1f - title.value) * 30f }.alpha(title.value),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppMark(56)
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.app_name),
                fontFamily = Bricolage,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 40.sp,
                letterSpacing = (-1).sp,
                color = colors.onBackground,
            )
            Text(
                stringResource(R.string.splash_tagline),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(28.dp))
        // A thin coral line fills while the app gets ready.
        Box(Modifier.width(120.dp).height(4.dp).clip(CircleShape).background(colors.outlineVariant)) {
            Box(Modifier.fillMaxWidth(bar.value).height(4.dp).clip(CircleShape).background(colors.secondary))
        }
        Spacer(Modifier.weight(1f))
        Text(
            stringResource(R.string.splash_copyright),
            fontFamily = Mono,
            fontSize = 11.sp,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 20.dp),
        )
    }
}

