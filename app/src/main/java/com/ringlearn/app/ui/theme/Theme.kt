package com.ringlearn.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.ringlearn.app.domain.model.ThemeMode

/**
 * Material 3 动态配色。
 * 浅色模式以天蓝 #0EA5E9 为主色、青绿 #14B8A6 为辅助色；
 * 深色模式使用提亮后的变体，保证对比度与可读性。
 */
private val LightColorScheme = lightColorScheme(
    primary = Sky500,
    onPrimary = Color.White,
    primaryContainer = Sky200,
    onPrimaryContainer = Sky900,
    inversePrimary = Sky300,
    secondary = Teal500,
    onSecondary = Color.White,
    secondaryContainer = Teal200,
    onSecondaryContainer = Teal900,
    tertiary = Indigo500,
    onTertiary = Color.White,
    tertiaryContainer = Indigo200,
    onTertiaryContainer = Indigo900,
    background = Slate50,
    onBackground = Slate900,
    surface = Slate50,
    onSurface = Slate900,
    surfaceVariant = Slate200,
    onSurfaceVariant = Slate600,
    surfaceTint = Sky500,
    inverseSurface = Slate900,
    inverseOnSurface = Slate100,
    error = Red600,
    onError = Color.White,
    errorContainer = Red200,
    onErrorContainer = Red900,
    outline = Slate400,
    outlineVariant = Slate300,
    scrim = Color.Black,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Slate100,
    surfaceContainer = Slate100,
    surfaceContainerHigh = Slate200,
    surfaceContainerHighest = Slate300
)

private val DarkColorScheme = darkColorScheme(
    primary = Sky300,
    onPrimary = Sky900,
    primaryContainer = Sky700,
    onPrimaryContainer = Sky200,
    inversePrimary = Sky500,
    secondary = Teal300,
    onSecondary = Teal900,
    secondaryContainer = Teal700,
    onSecondaryContainer = Teal200,
    tertiary = Indigo300,
    onTertiary = Indigo900,
    tertiaryContainer = Indigo700,
    onTertiaryContainer = Indigo200,
    background = Slate900,
    onBackground = Slate200,
    surface = Slate900,
    onSurface = Slate200,
    surfaceVariant = Slate800,
    onSurfaceVariant = Slate400,
    surfaceTint = Sky300,
    inverseSurface = Slate200,
    inverseOnSurface = Slate900,
    error = Red300,
    onError = Red900,
    errorContainer = Red800,
    onErrorContainer = Red200,
    outline = Slate500,
    outlineVariant = Slate700,
    scrim = Color.Black,
    surfaceContainerLowest = Slate950,
    surfaceContainerLow = Slate800,
    surfaceContainer = Slate800,
    surfaceContainerHigh = Slate700,
    surfaceContainerHighest = Slate700
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
