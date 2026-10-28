package com.nabd.money.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.nabd.money.R

/** ألوان التطبيق في الوضعين. الألوان المميزة أغمق شوية في الفاتح علشان تبقى مقروءة ومريحة */
data class Palette(
    val isDark: Boolean,
    val bg: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val surfaceTop: Color,
    val stroke: Color,
    val mint: Color,
    val cyan: Color,
    val gold: Color,
    val pink: Color,
    val violet: Color,
    val danger: Color,
    val text: Color,
    val textDim: Color,
    val textFaint: Color,
    val hero: List<Color>,
    val onBrand: Color,
)

// لون أساسي واحد (أخضر هادي) + محايد. باقي الألوان للمعنى بس (دخل، تحذير، أغراض)
val DarkPalette = Palette(
    isDark = true,
    bg = Color(0xFF0B0E14),
    surface = Color(0xFF12161F),
    surfaceHigh = Color(0xFF161B26),
    surfaceTop = Color(0xFF1E2431),
    stroke = Color(0xFF232A38),
    mint = Color(0xFF3DD6A3),
    cyan = Color(0xFF6AA6FF),
    gold = Color(0xFFE6B84F),
    pink = Color(0xFFF08AA4),
    violet = Color(0xFFA898FF),
    danger = Color(0xFFF07171),
    text = Color(0xFFEEF1F6),
    textDim = Color(0xFF98A2B3),
    textFaint = Color(0xFF5F6B7E),
    hero = listOf(Color(0xFF123A32), Color(0xFF0F2E2A)),
    onBrand = Color(0xFF04261C),
)

val LightPalette = Palette(
    isDark = false,
    bg = Color(0xFFF4F5F9),
    surface = Color(0xFFFFFFFF),
    surfaceHigh = Color(0xFFFFFFFF),
    surfaceTop = Color(0xFFF0F2F6),
    stroke = Color(0xFFE6E9F0),
    mint = Color(0xFF0E9A76),
    cyan = Color(0xFF2F6FDB),
    gold = Color(0xFFB9841A),
    pink = Color(0xFFD14D72),
    violet = Color(0xFF6B5BD6),
    danger = Color(0xFFD64545),
    text = Color(0xFF121826),
    textDim = Color(0xFF667085),
    textFaint = Color(0xFFA0A8B8),
    hero = listOf(Color(0xFF0F5C4A), Color(0xFF0C4B3D)),
    onBrand = Color.White,
)

/**
 * كل الشاشات بتقرا الألوان من هنا. القيمة state، فلما الوضع يتغير كل حاجة بتترسم من جديد،
 * حتى الرسوم اللي جوه Canvas.
 */
object NabdColors {
    var palette by mutableStateOf(DarkPalette)

    val Bg get() = palette.bg
    val Surface get() = palette.surface
    val SurfaceHigh get() = palette.surfaceHigh
    val SurfaceTop get() = palette.surfaceTop
    val Stroke get() = palette.stroke
    val Mint get() = palette.mint
    val Cyan get() = palette.cyan
    val Gold get() = palette.gold
    val Pink get() = palette.pink
    val Violet get() = palette.violet
    val Danger get() = palette.danger
    val Text get() = palette.text
    val TextDim get() = palette.textDim
    val TextFaint get() = palette.textFaint
    val OnBrand get() = palette.onBrand
    /** النص فوق زرار بلون واحد (Mint/Cyan) */
    val OnAccent get() = if (palette.isDark) Color(0xFF04261C) else Color.White
    val isDark get() = palette.isDark
}

/** زرار أساسي: لون واحد سادة (اتسمى Gradient من النسخ القديمة) */
val BrandGradient: Brush get() = androidx.compose.ui.graphics.SolidColor(NabdColors.Mint)
val HeroGradient: Brush get() = Brush.linearGradient(NabdColors.palette.hero)

val NabdFont = FontFamily(
    Font(R.font.ibm_plex_arabic_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_arabic_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_arabic_semibold, FontWeight.SemiBold),
    Font(R.font.ibm_plex_arabic_bold, FontWeight.Bold),
)

private fun style(size: Int, weight: FontWeight, line: Int = (size * 1.4).toInt()) =
    TextStyle(fontFamily = NabdFont, fontSize = size.sp, fontWeight = weight, lineHeight = line.sp)

private val typography = Typography(
    displaySmall = style(28, FontWeight.Bold, 36),
    headlineSmall = style(20, FontWeight.Bold),
    titleLarge = style(17, FontWeight.Bold),
    titleMedium = style(15, FontWeight.SemiBold),
    titleSmall = style(13, FontWeight.SemiBold),
    bodyLarge = style(15, FontWeight.Normal),
    bodyMedium = style(13, FontWeight.Normal),
    bodySmall = style(11, FontWeight.Normal),
    labelLarge = style(13, FontWeight.Medium),
    labelMedium = style(11, FontWeight.Medium),
    labelSmall = style(10, FontWeight.Medium),
)

enum class ThemeMode(val ar: String) { SYSTEM("تلقائي"), LIGHT("فاتح"), DARK("داكن") }

@Composable
fun NabdTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val p = if (dark) DarkPalette else LightPalette
    SideEffect { NabdColors.palette = p }
    NabdColors.palette = p
    val scheme = if (dark) {
        darkColorScheme(
            primary = p.mint, onPrimary = p.onBrand, primaryContainer = p.mint.copy(alpha = 0.2f),
            onPrimaryContainer = p.mint, secondary = p.cyan, tertiary = p.gold,
            background = p.bg, onBackground = p.text, surface = p.surface, onSurface = p.text,
            surfaceVariant = p.surfaceHigh, onSurfaceVariant = p.textDim,
            surfaceContainerLowest = p.bg, surfaceContainerLow = p.surface, surfaceContainer = p.surface,
            surfaceContainerHigh = p.surfaceHigh, surfaceContainerHighest = p.surfaceTop,
            outline = p.stroke, outlineVariant = p.stroke, error = p.danger,
        )
    } else {
        lightColorScheme(
            primary = p.mint, onPrimary = Color.White, primaryContainer = p.mint.copy(alpha = 0.14f),
            onPrimaryContainer = p.mint, secondary = p.cyan, tertiary = p.gold,
            background = p.bg, onBackground = p.text, surface = p.surface, onSurface = p.text,
            surfaceVariant = p.surfaceTop, onSurfaceVariant = p.textDim,
            surfaceContainerLowest = Color.White, surfaceContainerLow = p.surface, surfaceContainer = p.surface,
            surfaceContainerHigh = p.surfaceTop, surfaceContainerHighest = p.surfaceTop,
            outline = p.stroke, outlineVariant = p.stroke, error = p.danger,
        )
    }
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}
