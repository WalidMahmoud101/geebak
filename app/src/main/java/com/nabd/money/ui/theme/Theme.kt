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

val DarkPalette = Palette(
    isDark = true,
    bg = Color(0xFF080C17),
    surface = Color(0xFF0F1626),
    surfaceHigh = Color(0xFF141C2F),
    surfaceTop = Color(0xFF1B2540),
    stroke = Color(0xFF212C44),
    mint = Color(0xFF3EE6B0),
    cyan = Color(0xFF52B6F5),
    gold = Color(0xFFF2C25B),
    pink = Color(0xFFF27BB1),
    violet = Color(0xFFA18BFA),
    danger = Color(0xFFF46B83),
    text = Color(0xFFE9EEF9),
    textDim = Color(0xFF8D99B3),
    textFaint = Color(0xFF5B6884),
    hero = listOf(Color(0xFF0E3540), Color(0xFF13224A), Color(0xFF221A4A)),
    onBrand = Color(0xFF00261B),
)

val LightPalette = Palette(
    isDark = false,
    bg = Color(0xFFF3F5FA),
    surface = Color(0xFFFFFFFF),
    surfaceHigh = Color(0xFFFFFFFF),
    surfaceTop = Color(0xFFEDF1F8),
    stroke = Color(0xFFE1E6EF),
    mint = Color(0xFF0E9F7A),
    cyan = Color(0xFF1C7ED0),
    gold = Color(0xFFB7800A),
    pink = Color(0xFFC93D78),
    violet = Color(0xFF6A4FD8),
    danger = Color(0xFFD23B57),
    text = Color(0xFF111827),
    textDim = Color(0xFF5E6A82),
    textFaint = Color(0xFF9AA5BA),
    hero = listOf(Color(0xFFDDF7EE), Color(0xFFE2EEFF), Color(0xFFEDE6FF)),
    onBrand = Color(0xFF00261B),
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
    val OnAccent get() = if (palette.isDark) Color(0xFF00261B) else Color.White
    val isDark get() = palette.isDark
}

/** المزيج الأساسي ثابت في الوضعين علشان يفضل شكل البراند واحد */
val BrandGradient = Brush.linearGradient(listOf(Color(0xFF2EE6B0), Color(0xFF3DB2F5)))
val HeroGradient: Brush get() = Brush.linearGradient(NabdColors.palette.hero)

val NabdFont = FontFamily(
    Font(R.font.ibm_plex_arabic_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_arabic_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_arabic_semibold, FontWeight.SemiBold),
    Font(R.font.ibm_plex_arabic_bold, FontWeight.Bold),
)

private fun style(size: Int, weight: FontWeight, line: Int = (size * 1.45).toInt()) =
    TextStyle(fontFamily = NabdFont, fontSize = size.sp, fontWeight = weight, lineHeight = line.sp)

private val typography = Typography(
    displaySmall = style(32, FontWeight.Bold, 40),
    headlineSmall = style(22, FontWeight.Bold),
    titleLarge = style(19, FontWeight.Bold),
    titleMedium = style(16, FontWeight.SemiBold),
    titleSmall = style(14, FontWeight.SemiBold),
    bodyLarge = style(16, FontWeight.Normal),
    bodyMedium = style(14, FontWeight.Normal),
    bodySmall = style(12, FontWeight.Normal),
    labelLarge = style(14, FontWeight.Medium),
    labelMedium = style(12, FontWeight.Medium),
    labelSmall = style(11, FontWeight.Medium),
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
