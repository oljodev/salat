package no.jodal.salat.apps.brems.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import no.jodal.salat.resources.Res
import no.jodal.salat.resources.inter_bold
import no.jodal.salat.resources.inter_display_light
import no.jodal.salat.resources.inter_display_semibold
import no.jodal.salat.resources.inter_medium
import no.jodal.salat.resources.inter_regular
import no.jodal.salat.resources.inter_semibold
import org.jetbrains.compose.resources.Font

/**
 * Fargene i Brems. Lyst eller mørkt etter telefonen, med indigo som eneste aksentfarge. Pausen har
 * sine egne farger, se [Dusk].
 */
@Immutable
data class BremsColors(
    val background: Color,
    val card: Color,
    val cardHigh: Color,
    val text: Color,
    val textDim: Color,
    val textFaint: Color,
    val line: Color,
    val accent: Color,
    val accentSoft: Color,
    val onAccent: Color,
    val warn: Color,
)

private val Light = BremsColors(
    background = Color(0xFFF4F4F7),
    card = Color(0xFFFFFFFF),
    cardHigh = Color(0xFFEFEFF3),
    text = Color(0xFF111116),
    textDim = Color(0xFF6C6C78),
    textFaint = Color(0xFFA4A4AF),
    line = Color(0xFFE7E7EC),
    accent = Color(0xFF4F46E5),
    accentSoft = Color(0xFFECEBFE),
    onAccent = Color(0xFFFFFFFF),
    warn = Color(0xFFE5533D),
)

private val Dark = BremsColors(
    background = Color(0xFF000000),
    card = Color(0xFF131316),
    cardHigh = Color(0xFF1E1E23),
    text = Color(0xFFF4F4F7),
    textDim = Color(0xFF8D8D98),
    textFaint = Color(0xFF52525B),
    line = Color(0xFF25252B),
    accent = Color(0xFF8B87FF),
    accentSoft = Color(0xFF1C1B3A),
    onAccent = Color(0xFF0B0A1F),
    warn = Color(0xFFFF7A66),
)

/** Skumring: fargene på pauseskjermen, alltid mørk, fra dyp indigo til en varm glød nederst. */
object Dusk {
    val Top = Color(0xFF07061A)
    val Middle = Color(0xFF17124A)
    val Bottom = Color(0xFF2B1A5E)
    val Violet = Color(0xFF7B6CFF)
    val Lilac = Color(0xFFB9AEFF)
    val Ember = Color(0xFFFF8A6B)
    val Text = Color(0xFFFFFFFF)
    val TextDim = Color(0xB3FFFFFF)
    val TextFaint = Color(0x66FFFFFF)
    val Glass = Color(0x1AFFFFFF)
    val GlassLine = Color(0x26FFFFFF)
}

private val LocalBremsColors = staticCompositionLocalOf { Light }

/** Fargene til temaet som gjelder. */
val palette: BremsColors
    @Composable
    @ReadOnlyComposable
    get() = LocalBremsColors.current

/** Inter til tekst, Inter Display til overskrifter og de store tallene. */
@Composable
private fun bremsTypography(): Typography {
    val display = FontFamily(
        Font(Res.font.inter_display_light, FontWeight.Light),
        Font(Res.font.inter_display_semibold, FontWeight.SemiBold),
    )
    val text = FontFamily(
        Font(Res.font.inter_regular, FontWeight.Normal),
        Font(Res.font.inter_medium, FontWeight.Medium),
        Font(Res.font.inter_semibold, FontWeight.SemiBold),
        Font(Res.font.inter_bold, FontWeight.Bold),
    )
    fun d(size: Int, spacing: Double, weight: FontWeight = FontWeight.SemiBold, line: Int = (size * 1.12).toInt()) =
        TextStyle(fontFamily = display, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp, letterSpacing = spacing.sp, fontFeatureSettings = "tnum")
    fun t(size: Int, weight: FontWeight, line: Int, spacing: Double = 0.0) =
        TextStyle(fontFamily = text, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp, letterSpacing = spacing.sp)
    return Typography(
        displayLarge = d(112, -5.0, FontWeight.Light),
        displayMedium = d(56, -2.2),
        displaySmall = d(40, -1.4),
        headlineLarge = d(34, -1.0, line = 40),
        headlineMedium = d(28, -0.8, line = 34),
        headlineSmall = d(24, -0.6, line = 30),
        titleLarge = d(20, -0.4, line = 26),
        titleMedium = t(16, FontWeight.SemiBold, 21, -0.2),
        titleSmall = t(14, FontWeight.SemiBold, 19, -0.1),
        bodyLarge = t(16, FontWeight.Normal, 23, -0.2),
        bodyMedium = t(14, FontWeight.Normal, 20, -0.1),
        bodySmall = t(12, FontWeight.Normal, 16),
        labelLarge = t(14, FontWeight.Medium, 18, -0.1),
        labelMedium = t(12, FontWeight.Medium, 16).copy(fontFeatureSettings = "tnum"),
        labelSmall = t(11, FontWeight.SemiBold, 14, 0.6),
    )
}

@Composable
fun BremsTheme(
    dark: Boolean = isSystemInDarkTheme(),
    /** En annen aksentfarge, for mini-apper som låner Brems sitt utseende. */
    accent: Color? = null,
    content: @Composable () -> Unit,
) {
    val base = if (dark) Dark else Light
    val c = if (accent == null) {
        base
    } else {
        base.copy(accent = accent, accentSoft = accent.copy(alpha = if (dark) 0.2f else 0.12f), onAccent = Color.White)
    }
    val scheme = if (dark) {
        darkColorScheme(primary = c.accent, onPrimary = c.onAccent, background = c.background, surface = c.background, onSurface = c.text, error = c.warn)
    } else {
        lightColorScheme(primary = c.accent, onPrimary = c.onAccent, background = c.background, surface = c.background, onSurface = c.text, error = c.warn)
    }
    MaterialTheme(colorScheme = scheme, typography = bremsTypography()) {
        val selection = TextSelectionColors(handleColor = c.accent, backgroundColor = c.accent.copy(alpha = 0.25f))
        CompositionLocalProvider(LocalBremsColors provides c, LocalTextSelectionColors provides selection, content = content)
    }
}
