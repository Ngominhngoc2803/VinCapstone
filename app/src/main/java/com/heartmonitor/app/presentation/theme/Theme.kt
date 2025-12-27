package com.heartmonitor.app.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Primary Orange palette
val OrangePrimary = Color(0xFFFF6B35)
val OrangeLight = Color(0xFFFFA366)
val OrangeDark = Color(0xFFCC4400)

// Status colors
val GreenGood = Color(0xFF4CAF50)
val RedWarning = Color(0xFFF44336)
val YellowCaution = Color(0xFFFFC107)

// Background colors
val BackgroundCream = Color(0xFFFFF8F0)
val SurfaceWhite = Color(0xFFFFFFFF)
val SurfaceCard = Color(0xFFFFFBF7)

// Text colors
val TextPrimary = Color(0xFF1A1A1A)
val TextSecondary = Color(0xFF666666)
val TextTertiary = Color(0xFF999999)

// Chart colors
val ChartRed = Color(0xFFE57373)
val ChartRedLight = Color(0xFFFFCDD2)

private val LightColorScheme = lightColorScheme(
    primary = OrangePrimary,
    onPrimary = Color.White,
    primaryContainer = OrangeLight,
    onPrimaryContainer = Color.White,
    secondary = GreenGood,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC8E6C9),
    onSecondaryContainer = Color(0xFF1B5E20),
    tertiary = YellowCaution,
    onTertiary = Color.Black,
    error = RedWarning,
    onError = Color.White,
    background = BackgroundCream,
    onBackground = TextPrimary,
    surface = SurfaceWhite,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceCard,
    onSurfaceVariant = TextSecondary,
    outline = Color(0xFFE0E0E0),
    outlineVariant = Color(0xFFF0F0F0)
)

private val DarkColorScheme = darkColorScheme(
    primary = OrangeLight,
    onPrimary = Color.Black,
    primaryContainer = OrangeDark,
    onPrimaryContainer = Color.White,
    secondary = GreenGood,
    onSecondary = Color.Black,
    background = Color(0xFF121212),
    onBackground = Color.White,
    surface = Color(0xFF1E1E1E),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF2C2C2C),
    onSurfaceVariant = Color(0xFFB0B0B0)
)

@Composable
fun HeartHealthMonitorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
