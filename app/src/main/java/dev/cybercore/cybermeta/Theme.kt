package dev.cybercore.cybermeta

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * A CYBERGRID palette: the same roles every Cybercore tool uses, so a
 * theme chosen here looks like it does in the TUI, the sites and the bar.
 */
class Palette(
    val id: String,
    val name: String,
    bg: Long, panel: Long, line: Long, white: Long, muted: Long,
    cyan: Long, hotPink: Long, purple: Long, acidGreen: Long, orange: Long, red: Long,
) {
    val bg = Color(bg)
    val panel = Color(panel)
    val line = Color(line)
    val fg = Color(white)
    val muted = Color(muted)
    /** Primary accent: kept / clean / safe. */
    val cyan = Color(cyan)
    /** Secondary accent: redacted / removed. */
    val pink = Color(hotPink)
    val purple = Color(purple)
    val green = Color(acidGreen)
    val orange = Color(orange)
    val red = Color(red)
    val raised get() = lerp(panel, fg, .05f)
}

/** cybermeta's own "Tag Redact" palette: teal kept, coral removed. */
val TagRedact = Palette(
    "tag-redact", "Tag Redact",
    0xFF070A09, 0xFF0E1614, 0xFF1E2D29, 0xFFEAFAF5, 0xFF7D9992,
    0xFF2DD4BF, 0xFFFF6B5C, 0xFF8B7CF6, 0xFF2DD4BF, 0xFFFFB347, 0xFFFF6B5C,
)

fun paletteById(id: String?): Palette =
    PaletteFamilies.asSequence().flatMap { it.second }.firstOrNull { it.id == id } ?: TagRedact

val LocalPalette = staticCompositionLocalOf { TagRedact }

/** The current palette, from anywhere in the UI. */
val P: Palette @Composable get() = LocalPalette.current

val Mono = FontFamily(Font(R.font.jetbrains_mono), Font(R.font.jetbrains_mono_bold, FontWeight.Bold))

fun severityColor(p: Palette, severity: String?): Color = when (severity) {
    "high" -> p.red
    "medium" -> p.orange
    "low" -> p.cyan
    else -> p.green
}

@Composable
fun CybermetaTheme(palette: Palette, content: @Composable () -> Unit) {
    val t = Typography()
    val mono = { s: TextStyle -> s.copy(fontFamily = Mono) }
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = palette.cyan, onPrimary = palette.bg,
            secondary = palette.pink, onSecondary = palette.bg,
            tertiary = palette.purple,
            background = palette.bg, onBackground = palette.fg,
            surface = palette.panel, onSurface = palette.fg,
            surfaceVariant = palette.raised, onSurfaceVariant = palette.muted,
            surfaceContainer = palette.panel, surfaceContainerHigh = palette.raised,
            surfaceContainerHighest = lerp(palette.panel, palette.fg, .08f),
            surfaceContainerLow = palette.panel, surfaceContainerLowest = palette.bg,
            outline = palette.line, outlineVariant = palette.line,
            error = palette.red,
        ),
        typography = Typography(
            displaySmall = mono(t.displaySmall).copy(fontWeight = FontWeight.Bold, letterSpacing = 2.sp),
            headlineMedium = mono(t.headlineMedium).copy(fontWeight = FontWeight.Bold),
            headlineSmall = mono(t.headlineSmall).copy(fontWeight = FontWeight.Bold),
            titleLarge = mono(t.titleLarge).copy(fontWeight = FontWeight.Bold),
            titleMedium = mono(t.titleMedium).copy(fontWeight = FontWeight.Bold),
            titleSmall = mono(t.titleSmall),
            bodyLarge = t.bodyLarge,
            bodyMedium = t.bodyMedium,
            bodySmall = t.bodySmall,
            labelLarge = mono(t.labelLarge).copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
            labelMedium = mono(t.labelMedium),
            labelSmall = mono(t.labelSmall).copy(letterSpacing = 1.sp),
        ),
    ) {
        CompositionLocalProvider(LocalPalette provides palette, LocalContentColor provides palette.fg, content = content)
    }
}
