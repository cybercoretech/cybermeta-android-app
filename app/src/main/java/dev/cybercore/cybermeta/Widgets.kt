package dev.cybercore.cybermeta

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The app's mark, alive: a photo with a redaction beam sweeping down it
 * and metadata bits dissolving off to the right. Drawn on a 108-unit grid,
 * the same geometry as the launcher icon.
 */
@Composable
fun ScrubMark(size: Dp, modifier: Modifier = Modifier, animate: Boolean = true) {
    val p = P
    val t = rememberInfiniteTransition(label = "scrub")
    val sweep by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Reverse), label = "sweep")
    val drift by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing)), label = "drift")
    val beamY = if (animate) 38f + sweep * 28f else 46f
    val phase = if (animate) drift else 0.3f
    Canvas(modifier.size(size)) {
        val u = this.size.minDimension / 108f
        fun o(x: Float, y: Float) = Offset(x * u, y * u)
        val frameTl = o(30f, 33f)
        val frameSize = Size(48f * u, 42f * u)
        val r = CornerRadius(6f * u)
        // Halo and photo.
        drawRoundRect(p.cyan.copy(alpha = .16f), frameTl, frameSize, r, style = Stroke(7f * u))
        drawRoundRect(p.panel, frameTl, frameSize, r)
        clipRect(30f * u, 33f * u, 78f * u, 75f * u) {
            val hills = Path().apply {
                moveTo(33.5f * u, 72.5f * u); lineTo(45f * u, 55f * u); lineTo(53f * u, 65f * u)
                lineTo(60f * u, 58f * u); lineTo(74.5f * u, 72.5f * u); close()
            }
            drawPath(hills, p.cyan.copy(alpha = .55f))
            drawCircle(p.cyan, 4f * u, o(43f, 41.5f))
            // The part above the beam has been scrubbed: a faint scan wash.
            drawRect(
                Brush.verticalGradient(listOf(Color.Transparent, p.pink.copy(alpha = .14f)), startY = 33f * u, endY = beamY * u),
                topLeft = o(30f, 33f), size = Size(48f * u, (beamY - 33f) * u),
            )
        }
        drawRoundRect(p.fg, frameTl, frameSize, r, style = Stroke(3.2f * u))
        // Beam.
        drawRoundRect(p.pink.copy(alpha = .22f), o(18.5f, beamY - 3.5f), Size(50f * u, 13f * u), CornerRadius(5f * u))
        drawRoundRect(p.pink, o(21.5f, beamY), Size(41f * u, 6f * u), CornerRadius(2.5f * u))
        // Bits, drifting up and right from the end of the beam and fading out.
        val bits = listOf(
            Triple(0f, 0f, 3f), Triple(.2f, -2.5f, 2.6f), Triple(.45f, 1f, 2.2f),
            Triple(.65f, -4f, 2.2f), Triple(.85f, -7f, 1.8f), Triple(.3f, 5f, 2f), Triple(.7f, 3f, 1.6f),
        )
        bits.forEachIndexed { i, (offset, dy, side) ->
            val k = (phase + offset) % 1f
            val x = 64f + k * 24f
            val y = beamY + dy - k * 6f
            val c = if (i >= 5) p.cyan else p.pink
            drawRect(c.copy(alpha = (1f - k).coerceIn(0f, 1f)), o(x, y), Size(side * u, side * u))
        }
    }
}

/** A faint survey grid with a slow scan line, behind every screen. */
@Composable
fun GridBackdrop(modifier: Modifier = Modifier) {
    val p = P
    val t = rememberInfiniteTransition(label = "grid")
    val scan by t.animateFloat(0f, 1f, infiniteRepeatable(tween(9000, easing = LinearEasing)), label = "scan")
    Canvas(modifier.fillMaxSize()) {
        drawRect(Brush.radialGradient(listOf(p.panel, p.bg), center = Offset(size.width * .5f, size.height * .2f), radius = size.maxDimension * .9f))
        val step = 28.dp.toPx()
        var x = 0f
        while (x < size.width) {
            drawLine(p.cyan.copy(alpha = .045f), Offset(x, 0f), Offset(x, size.height), 1f)
            x += step
        }
        var y = 0f
        while (y < size.height) {
            drawLine(p.cyan.copy(alpha = .045f), Offset(0f, y), Offset(size.width, y), 1f)
            y += step
        }
        val sy = scan * size.height
        drawRect(
            Brush.verticalGradient(listOf(Color.Transparent, p.cyan.copy(alpha = .07f), Color.Transparent), startY = sy - 60f, endY = sy + 60f),
            topLeft = Offset(0f, sy - 60f), size = Size(size.width, 120f),
        )
    }
}

@Composable
fun Panel(
    modifier: Modifier = Modifier,
    accent: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val p = P
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(p.panel.copy(alpha = .92f))
            .border(1.dp, (accent ?: p.line).copy(alpha = if (accent != null) .7f else 1f), shape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

fun riskLabel(risk: String?): String = when (risk) {
    "high" -> "HIGH RISK"
    "medium" -> "MEDIUM"
    "low" -> "LOW"
    else -> "CLEAN"
}

@Composable
fun RiskBadge(risk: String?, modifier: Modifier = Modifier, short: Boolean = false) {
    val c = severityColor(P, risk)
    Text(
        if (short) riskLabel(risk).removeSuffix(" RISK") else riskLabel(risk),
        modifier
            .clip(RoundedCornerShape(50))
            .background(c.copy(alpha = .16f))
            .border(1.dp, c.copy(alpha = .8f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 3.dp),
        color = c,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
    )
}

fun categoryIcon(category: String): ImageVector = when (category) {
    "location" -> Icons.Outlined.LocationOn
    "person" -> Icons.Outlined.Person
    "device" -> Icons.Outlined.PhotoCamera
    "time" -> Icons.Outlined.AccessTime
    "software" -> Icons.Outlined.Code
    "text" -> Icons.AutoMirrored.Outlined.Notes
    "thumbnail" -> Icons.Outlined.Image
    "hidden_data" -> Icons.Outlined.VisibilityOff
    else -> Icons.Outlined.Tune
}

fun categoryTitle(category: String): String = when (category) {
    "location" -> "Where you were"
    "person" -> "Who you are"
    "device" -> "Your camera"
    "time" -> "When"
    "software" -> "Software"
    "text" -> "Captions and comments"
    "thumbnail" -> "Hidden preview"
    "hidden_data" -> "Hidden data"
    else -> "Camera settings and more"
}

/** Order categories by how much they give away. */
val categoryOrder = listOf("location", "person", "hidden_data", "device", "time", "thumbnail", "text", "software", "other")

@Composable
fun CategoryChip(category: String, count: Int, severity: String) {
    val c = severityColor(P, severity)
    Row(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(c.copy(alpha = .10f))
            .border(1.dp, c.copy(alpha = .45f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(categoryIcon(category), null, tint = c, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text("$count", color = c, fontWeight = FontWeight.Bold, fontFamily = Mono, fontSize = 13.sp)
        Spacer(Modifier.width(4.dp))
        Text(categoryTitle(category).lowercase(), color = P.fg, fontSize = 12.sp)
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier.padding(top = 8.dp, bottom = 2.dp),
        color = P.muted,
        style = MaterialTheme.typography.labelSmall,
    )
}

/** A neon dot that pulses, for "working" states. */
@Composable
fun PulseDot(color: Color, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "pulse")
    val a by t.animateFloat(.25f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "a")
    Box(modifier.size(8.dp).clip(RoundedCornerShape(50)).background(color.copy(alpha = a)))
}

fun humanBytes(n: Long): String = when {
    n >= 1 shl 20 -> "%.1f MB".format(n / 1048576.0)
    n >= 1 shl 10 -> "%.1f KB".format(n / 1024.0)
    else -> "$n bytes"
}
