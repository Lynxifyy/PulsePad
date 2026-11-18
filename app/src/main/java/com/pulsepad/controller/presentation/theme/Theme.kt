package com.pulsepad.controller.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val PulsePadDarkColors = darkColorScheme(
    primary = NeonCyan,
    onPrimary = Charcoal,
    primaryContainer = NeonCyanDark,
    onPrimaryContainer = OnDarkHigh,
    secondary = ElectricBlue,
    onSecondary = Charcoal,
    secondaryContainer = ElectricBlueDark,
    onSecondaryContainer = OnDarkHigh,
    tertiary = SuccessGreen,
    onTertiary = Charcoal,
    background = Charcoal,
    onBackground = OnDarkHigh,
    surface = Charcoal,
    onSurface = OnDarkHigh,
    surfaceVariant = CharcoalSurfaceVariant,
    onSurfaceVariant = OnDarkMedium,
    surfaceContainer = CharcoalElevated,
    surfaceContainerHigh = CharcoalSurfaceVariant,
    surfaceContainerHighest = CharcoalOutline,
    surfaceContainerLow = CharcoalElevated,
    surfaceContainerLowest = Charcoal,
    outline = CharcoalOutline,
    outlineVariant = CharcoalSurfaceVariant,
    error = ErrorRed,
    onError = Charcoal
)

private val PulsePadLightColors = lightColorScheme(
    primary = NeonCyanDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFA5F3FC),
    onPrimaryContainer = Color(0xFF083344),
    secondary = ElectricBlueDark,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDBEAFE),
    onSecondaryContainer = Color(0xFF10275A),
    tertiary = Color(0xFF047857),
    onTertiary = Color.White,
    background = LightSurface,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceContainer = Color(0xFFEDF1F6),
    surfaceContainerHigh = LightSurfaceVariant,
    surfaceContainerHighest = LightOutline,
    surfaceContainerLow = Color(0xFFF1F4F8),
    surfaceContainerLowest = Color.White,
    outline = LightOutline,
    outlineVariant = Color(0xFFCFD7E0),
    error = Color(0xFFB3261E),
    onError = Color.White
)

@Composable
fun PulsePadTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) PulsePadDarkColors else PulsePadLightColors,
        typography = PulsePadTypography,
        content = content
    )
}
