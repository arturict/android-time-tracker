package com.simonmettler.timetracker.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// A fixed brand palette instead of dynamic color: activity colors are the
// user's own, so the chrome around them stays neutral and predictable.
private val Light = lightColorScheme(
    primary = Color(0xFF0E6B60), onPrimary = Color.White,
    primaryContainer = Color(0xFFCDEDE6), onPrimaryContainer = Color(0xFF00201C),
    secondary = Color(0xFF4A5A66), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE3E8EC), onSecondaryContainer = Color(0xFF1A242C),
    background = Color(0xFFF5F6F4), onBackground = Color(0xFF15191B),
    surface = Color(0xFFF5F6F4), onSurface = Color(0xFF15191B),
    surfaceVariant = Color(0xFFE6E9E7), onSurfaceVariant = Color(0xFF5B6366),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFFBFBFA),
    surfaceContainer = Color(0xFFFFFFFF), surfaceContainerHigh = Color(0xFFEEF0EE),
    surfaceContainerHighest = Color(0xFFE6E9E7),
    outline = Color(0xFFC6CCCB), outlineVariant = Color(0xFFE1E5E3),
    error = Color(0xFFB3261E)
)

private val Dark = darkColorScheme(
    primary = Color(0xFF7FD8C8), onPrimary = Color(0xFF00382F),
    primaryContainer = Color(0xFF0F4F46), onPrimaryContainer = Color(0xFFCDEDE6),
    secondary = Color(0xFFB4C3CE), onSecondary = Color(0xFF1F2B33),
    secondaryContainer = Color(0xFF2A333A), onSecondaryContainer = Color(0xFFDCE5EC),
    background = Color(0xFF0D0F10), onBackground = Color(0xFFE6E9E8),
    surface = Color(0xFF0D0F10), onSurface = Color(0xFFE6E9E8),
    surfaceVariant = Color(0xFF23282A), onSurfaceVariant = Color(0xFF9EA7AA),
    surfaceContainerLowest = Color(0xFF08090A), surfaceContainerLow = Color(0xFF131617),
    surfaceContainer = Color(0xFF171A1C), surfaceContainerHigh = Color(0xFF1E2224),
    surfaceContainerHighest = Color(0xFF272C2E),
    outline = Color(0xFF3D4446), outlineVariant = Color(0xFF262B2D),
    error = Color(0xFFF2B8B5)
)

private val Base = Typography()
private val AppTypography = Typography(
    displayMedium = Base.displayMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-1.5).sp),
    displaySmall = Base.displaySmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-1).sp),
    headlineLarge = Base.headlineLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.8).sp),
    headlineMedium = Base.headlineMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.6).sp),
    headlineSmall = Base.headlineSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp),
    titleLarge = Base.titleLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
    titleMedium = Base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = Base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    labelMedium = Base.labelMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp),
    labelSmall = Base.labelSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
)

/** Tabular figures keep running timers from jittering sideways every second. */
val Numeric = TextStyle(fontFamily = FontFamily.Default, fontFeatureSettings = "tnum")

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp), large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun TrackerTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) Dark else Light, typography = AppTypography, shapes = AppShapes, content = content)
}
