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
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import com.uniplanner.app.R
import com.uniplanner.app.ui.theme.Bricolage
import com.uniplanner.app.ui.theme.Coral
import com.uniplanner.app.ui.theme.Ink
import com.uniplanner.app.ui.theme.Mono
import com.uniplanner.app.ui.theme.Paper
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

/** The app's mark: the "U" catching the coral ball, on a dark ink tile like the launcher icon. */
@Composable
fun AppMark(size: Int = 76) {
    Box(
        Modifier.size(size.dp).clip(RoundedCornerShape((size * 0.28f).dp)).background(Ink),
        contentAlignment = Alignment.Center,
    ) {
        Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.size((size * 1.5f).dp))
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

private val burstEmojis = listOf("📚", "💪", "🍎", "👋", "📅", "⏱", "🎓", "🔥", "✏️", "🎧")
private val letterColors = listOf(Color(0xFFDCE3FC), Color(0xFFDDEBD0), Color(0xFFF6E6C3), Color(0xFFEADCF5), Coral)

/**
 * The frenetic opening, about 1.8 seconds: the mark spins in and bounces, the areas burst out of
 * it, the letters of the name slam down one by one and shake the screen, then a coral wave and a
 * paper wave sweep it away into the app.
 */
@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val name = stringResource(R.string.app_name)
    val mark = remember { Animatable(0f) }
    val burst = remember { Animatable(0f) }
    val letters = remember(name) { name.map { Animatable(0f) } }
    val shake = remember { Animatable(0f) }
    val tagline = remember { Animatable(0f) }
    val coral = remember { Animatable(0f) }
    val paper = remember { Animatable(0f) }
    val bouncy = spring<Float>(dampingRatio = 0.42f, stiffness = Spring.StiffnessMediumLow)

    LaunchedEffect(Unit) {
        launch { mark.animateTo(1f, bouncy) }
        launch {
            delay(220)
            burst.animateTo(1f, tween(750, easing = FastOutSlowInEasing))
        }
        letters.forEachIndexed { i, l ->
            launch {
                delay(480L + 45L * i)
                l.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium))
            }
        }
        launch {
            delay(480L + 45L * letters.size + 80)
            shake.animateTo(1f, tween(320, easing = LinearEasing))
            tagline.animateTo(1f, tween(220))
        }
        delay(1_400)
        launch { coral.animateTo(1f, tween(330, easing = FastOutSlowInEasing)) }
        delay(170)
        paper.animateTo(1f, tween(330, easing = FastOutSlowInEasing))
        onFinished()
    }

    val paperColor = MaterialTheme.colorScheme.background
    BoxWithConstraints(Modifier.fillMaxSize().background(Ink)) {
        val reach = kotlin.math.hypot(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
        // A decaying wobble while the last letters land.
        val jolt = if (shake.value in 0.001f..0.999f) sin(shake.value * 40f) * (1f - shake.value) * 22f else 0f

        // The areas fly out of the mark in every direction, spinning.
        burstEmojis.forEachIndexed { i, e ->
            val angle = i * (2 * PI / burstEmojis.size) + 0.3
            val t = burst.value
            Text(
                e,
                fontSize = 30.sp,
                modifier = Modifier.align(Alignment.Center).graphicsLayer {
                    translationX = (cos(angle) * t * reach * 0.42f).toFloat() + jolt
                    translationY = (sin(angle) * t * reach * 0.42f).toFloat()
                    rotationZ = t * 540f * (if (i % 2 == 0) 1 else -1)
                    alpha = if (t < 0.7f) minOf(1f, t * 5f) else (1f - t) / 0.3f
                    scaleX = 0.6f + t
                    scaleY = 0.6f + t
                },
            )
        }

        Column(
            Modifier.align(Alignment.Center).graphicsLayer { translationX = jolt },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.graphicsLayer {
                    scaleX = mark.value
                    scaleY = mark.value
                    rotationZ = (1f - mark.value) * -200f
                },
            ) { AppMark(96) }
            Spacer(Modifier.height(26.dp))
            Row {
                name.forEachIndexed { i, ch ->
                    val t = letters[i].value
                    Text(
                        ch.toString(),
                        fontFamily = Bricolage,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 46.sp,
                        letterSpacing = (-1).sp,
                        // Each letter lands in a card colour and settles to paper as the screen shakes.
                        color = lerp(letterColors[i % letterColors.size], Paper, shake.value),
                        modifier = Modifier.graphicsLayer {
                            translationY = (1f - t) * -260f
                            rotationZ = (1f - t) * (if (i % 2 == 0) 25f else -25f)
                            alpha = minOf(1f, t * 3f)
                        },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.splash_tagline),
                fontFamily = Mono,
                fontSize = 13.sp,
                color = Paper.copy(alpha = 0.7f * tagline.value),
            )
        }

        Text(
            stringResource(R.string.splash_copyright),
            fontFamily = Mono,
            fontSize = 11.sp,
            color = Paper.copy(alpha = 0.45f),
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 20.dp),
        )

        // Two waves from the centre: coral, then the app's own paper.
        Canvas(Modifier.fillMaxSize()) {
            if (coral.value > 0f) drawCircle(Coral, radius = reach * coral.value, center = center)
            if (paper.value > 0f) drawCircle(paperColor, radius = reach * paper.value, center = center)
        }
    }
}
