package com.uniplanner.app.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A highlighter stroke behind the lower part of the content, [fraction] of its width long:
 * the mark of the Play look, under the hours of the week.
 */
fun Modifier.highlighterStroke(color: Color, fraction: Float): Modifier = drawBehind {
    val w = size.width * fraction.coerceIn(0f, 1f) + 12.dp.toPx() * fraction.coerceIn(0f, 1f)
    if (w <= 0f) return@drawBehind
    val top = size.height * 0.5f
    val bottom = size.height * 0.94f
    val left = -6.dp.toPx()
    val right = left + w
    val wob = (bottom - top) * 0.12f
    val p = Path().apply {
        moveTo(left + 2f, top + wob)
        cubicTo(left + w * 0.3f, top - wob, left + w * 0.6f, top + wob * 0.5f, right, top)
        lineTo(right + 2f, bottom - wob)
        cubicTo(left + w * 0.7f, bottom + wob, left + w * 0.3f, bottom - wob * 0.4f, left, bottom)
        close()
    }
    drawPath(p, color)
}

/** A flat marker band behind text, like a highlighter pen on paper. */
fun Modifier.markerBand(color: Color, fraction: Float = 1f): Modifier = drawBehind {
    val w = (size.width + 8.dp.toPx()) * fraction.coerceIn(0f, 1f)
    if (w <= 0f) return@drawBehind
    drawRoundRect(
        color,
        topLeft = Offset(-4.dp.toPx(), size.height * 0.28f),
        size = Size(w, size.height * 0.62f),
        cornerRadius = CornerRadius(3.dp.toPx()),
    )
}

/** Soft colour from the top corner of a screen, like a music app takes the cover's colour. */
fun Modifier.wash(color: Color, strength: Float = 0.32f): Modifier = drawBehind {
    drawRect(
        Brush.radialGradient(
            listOf(color.copy(alpha = strength), Color.Transparent),
            center = Offset(size.width * 0.2f, 0f),
            radius = size.width * 1.1f,
        ),
    )
}

/** The look's big numbers. In the Riso look they print twice, a little out of register, until [aligned]. */
@Composable
fun BigNumber(
    text: String,
    modifier: Modifier = Modifier,
    size: TextUnit = 72.sp,
    color: Color = MaterialTheme.colorScheme.onSurface,
    aligned: Float = 1f,
) {
    val look = LocalUniLook.current
    val style = TextStyle(
        fontFamily = look.numbers,
        fontWeight = look.numbersWeight,
        fontSize = size,
        lineHeight = size * 1.0f,
        letterSpacing = if (look.style == AppStyle.CLASSICO) 0.sp else (-0.03f * size.value).sp,
        fontFeatureSettings = "tnum",
    )
    if (look.style == AppStyle.RISO) {
        val off by animateFloatAsState((1f - aligned.coerceIn(0f, 1f)) * 7f, tween(600), label = "register")
        Box(modifier) {
            Text(text, style = style, color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.85f), modifier = Modifier.offset(off.dp, (off * 0.6f).dp))
            Text(text, style = style, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.88f))
        }
    } else {
        Text(text, style = style, color = color, modifier = modifier)
    }
}

/** A course's progress, drawn the way the current look draws it. */
@Composable
fun CourseProgress(progress: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 10.dp) {
    val look = LocalUniLook.current
    val p by animateFloatAsState(progress.coerceIn(0f, 1f), tween(700), label = "progress")
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val ground = MaterialTheme.colorScheme.surface
    when (look.progress) {
        ProgressLook.MATERIAL -> LinearProgressIndicator(
            progress = { p },
            modifier = modifier.fillMaxWidth().height(6.dp),
            color = color,
            trackColor = color.copy(alpha = 0.2f),
        )
        ProgressLook.ROUNDED, ProgressLook.STROKE -> Box(modifier.fillMaxWidth().height(height).clip(CircleShape).background(track)) {
            Box(Modifier.fillMaxWidth(p).fillMaxHeight().clip(CircleShape).background(color))
        }
        ProgressLook.RIBBON -> Canvas(modifier.fillMaxWidth().height(height + 6.dp)) {
            val tail = size.height * 0.9f
            val body = size.width - tail
            val notch = Path().apply {
                moveTo(body, 0f); lineTo(size.width, 0f); lineTo(size.width - tail * 0.55f, size.height / 2f)
                lineTo(size.width, size.height); lineTo(body, size.height); close()
            }
            drawRect(lerp(color, ground, 0.72f), size = Size(body, size.height))
            drawPath(notch, if (p >= 1f) color else lerp(color, ground, 0.72f))
            drawRect(color, size = Size(body * p, size.height))
        }
        ProgressLook.LINE -> Canvas(modifier.fillMaxWidth().height(height + 6.dp)) {
            val y = size.height / 2f
            val r = size.height / 2.4f
            val start = r
            val end = size.width - r
            drawLine(track, Offset(start, y), Offset(end, y), strokeWidth = size.height * 0.28f, cap = StrokeCap.Round)
            val reach = start + (end - start) * p
            if (p > 0f) drawLine(color, Offset(start, y), Offset(reach, y), strokeWidth = size.height * 0.55f, cap = StrokeCap.Round)
            listOf(0f, 1f / 3f, 2f / 3f, 1f).forEach { s ->
                val x = start + (end - start) * s
                val passed = p >= s
                drawCircle(ground, r, Offset(x, y))
                drawCircle(if (passed) color else track, r, Offset(x, y), style = Stroke(width = r * 0.7f))
            }
        }
        ProgressLook.HALFTONE -> Canvas(modifier.fillMaxWidth().height(height)) {
            val step = 4.dp.toPx()
            val dot = 1.1.dp.toPx()
            var x = step / 2
            while (x < size.width) {
                var y = step / 2
                while (y < size.height) {
                    drawCircle(color.copy(alpha = 0.55f), dot, Offset(x, y))
                    y += step
                }
                x += step
            }
            drawRect(color.copy(alpha = 0.9f), size = Size(size.width * p, size.height))
        }
        ProgressLook.HIGHLIGHT -> Canvas(modifier.fillMaxWidth().height(height + 4.dp)) {
            drawRoundRect(track, size = size, cornerRadius = CornerRadius(3.dp.toPx()))
            if (p > 0f) {
                val w = size.width * p
                val path = Path().apply {
                    moveTo(0f, size.height * 0.1f); lineTo(w, 0f); lineTo(w - 3.dp.toPx(), size.height); lineTo(2.dp.toPx(), size.height * 0.92f); close()
                }
                drawPath(path, color)
            }
        }
    }
}
