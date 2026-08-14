package com.ringlearn.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.ringlearn.app.domain.model.ThemeMode

/**
 * 全局配色（saku 风格）：品牌青 + 暖黄点缀 + 深墨蓝文字。
 * 浅色为浅蓝白底 + 白卡片；深色为墨蓝底 + 提亮青色，保证对比度与可读性。
 */
private val LightColorScheme = lightColorScheme(
    primary = SakuCyanDeep,
    onPrimary = Color.White,
    primaryContainer = SakuCyanSoft,
    onPrimaryContainer = SakuInk,
    inversePrimary = SakuCyan,
    secondary = SakuCyan,
    onSecondary = SakuInk,
    secondaryContainer = SakuCyanSoft,
    onSecondaryContainer = SakuInk,
    tertiary = SakuYellow,
    onTertiary = SakuInk,
    tertiaryContainer = SakuYellowSoft,
    onTertiaryContainer = SakuInk,
    background = SakuBg,
    onBackground = SakuInk,
    surface = Color(0xFFF7FBFC),
    onSurface = SakuInk,
    surfaceVariant = Color(0xFFE0EEF4),
    onSurfaceVariant = Color(0xFF4A6675),
    surfaceTint = SakuCyanDeep,
    inverseSurface = SakuInk,
    inverseOnSurface = Color(0xFFEAF6FC),
    error = Color(0xFFDC2626),
    onError = Color.White,
    errorContainer = Color(0xFFFECACA),
    onErrorContainer = Color(0xFF7F1D1D),
    outline = Color(0xFF4A6675),
    outlineVariant = SakuCardBorder,
    scrim = Color.Black,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF7FBFC),
    surfaceContainer = SakuCyanSoft,
    surfaceContainerHigh = Color(0xFFDDF2FA),
    surfaceContainerHighest = Color(0xFFCBEAF6)
)

private val DarkColorScheme = darkColorScheme(
    primary = SakuCyan,
    onPrimary = Color(0xFF12303C),
    primaryContainer = SakuInk,
    onPrimaryContainer = Color(0xFFEAF6FC),
    inversePrimary = SakuCyanDeep,
    secondary = SakuCyan,
    onSecondary = Color(0xFF12303C),
    secondaryContainer = SakuInk,
    onSecondaryContainer = Color(0xFFEAF6FC),
    tertiary = SakuYellow,
    onTertiary = SakuInk,
    tertiaryContainer = Color(0xFF4A3E12),
    onTertiaryContainer = SakuYellow,
    background = Color(0xFF112833),
    onBackground = Color(0xFFEAF6FC),
    surface = Color(0xFF16323F),
    onSurface = Color(0xFFEAF6FC),
    surfaceVariant = SakuInk,
    onSurfaceVariant = Color(0xFFB9D4E0),
    surfaceTint = SakuCyan,
    inverseSurface = Color(0xFFEAF6FC),
    inverseOnSurface = SakuInk,
    error = Color(0xFFF87171),
    onError = Color(0xFF7F1D1D),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFECACA),
    outline = Color(0xFF7FA3B3),
    outlineVariant = Color(0x1FEAF6FC),
    scrim = Color.Black,
    surfaceContainerLowest = Color(0xFF0B1E27),
    surfaceContainerLow = Color(0xFF112833),
    surfaceContainer = Color(0xFF16323F),
    surfaceContainerHigh = SakuInk,
    surfaceContainerHighest = SakuInk
)

@Composable
fun RingLearnTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = AppTypography,
        content = content
    )
}
