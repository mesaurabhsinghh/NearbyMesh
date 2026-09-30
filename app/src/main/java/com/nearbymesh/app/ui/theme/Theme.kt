package com.nearbymesh.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Brand Colors from Design Reference
val BrandEmerald = Color(0xFF00C853)
val BrandEmeraldLight = Color(0xFFE8F5E9)
val BrandEmeraldDark = Color(0xFF004D26)
val BrandEmeraldGlow = Color(0xFF00E676)

val BrandCyan = Color(0xFF00B0FF)
val BrandCyanGlow = Color(0xFF00E5FF)
val BrandCyanDark = Color(0xFF003750)

val BrandAmber = Color(0xFFFFAB00)
val BrandRed = Color(0xFFFF1744)
val BrandRedDark = Color(0xFF38080E)

// Clean Light Theme Colors (Matching Image 1)
val LightBg = Color(0xFFF7F9FB)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceSubtle = Color(0xFFF0F4F8)
val LightCardBorder = Color(0xFFE2E8F0)
val LightTextPrimary = Color(0xFF0F172A)
val LightTextSecondary = Color(0xFF64748B)
val LightTextMuted = Color(0xFF94A3B8)
val LightBubbleIncoming = Color(0xFFF1F5F9)
val LightBubbleOutgoing = Color(0xFF00C853)

// Cosmic Dark Theme Colors (Matching Image 2 & 3)
val DarkBg = Color(0xFF0A0E17)
val DarkSurface = Color(0xFF131926)
val DarkSurfaceSubtle = Color(0xFF1A2234)
val DarkCardBorder = Color(0xFF222D42)
val DarkTextPrimary = Color(0xFFF8FAFC)
val DarkTextSecondary = Color(0xFF94A3B8)
val DarkTextMuted = Color(0xFF64748B)
val DarkBubbleIncoming = Color(0xFF1E293B)
val DarkBubbleOutgoing = Color(0xFF00A344)

// Color aliases for cross-screen compatibility
val NeonGreen = BrandEmeraldGlow
val ElectricCyan = BrandCyanGlow
val CyberAmber = BrandAmber
val EmergencyRed = BrandRed
val DarkBackground = DarkBg
val DarkSurfaceElevated = DarkSurfaceSubtle
val DarkBorder = DarkCardBorder
val TextPrimary = DarkTextPrimary
val TextSecondary = DarkTextSecondary
val TextMuted = DarkTextMuted

private val LightColorScheme = lightColorScheme(
    primary = BrandEmerald,
    onPrimary = Color.White,
    primaryContainer = BrandEmeraldLight,
    onPrimaryContainer = BrandEmeraldDark,
    secondary = BrandCyan,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE1F5FE),
    onSecondaryContainer = Color(0xFF01579B),
    tertiary = BrandAmber,
    error = BrandRed,
    background = LightBg,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceSubtle,
    onSurfaceVariant = LightTextSecondary,
    outline = LightCardBorder
)

private val DarkColorScheme = darkColorScheme(
    primary = BrandEmeraldGlow,
    onPrimary = Color.Black,
    primaryContainer = BrandEmeraldDark,
    onPrimaryContainer = BrandEmeraldGlow,
    secondary = BrandCyanGlow,
    onSecondary = Color.Black,
    secondaryContainer = BrandCyanDark,
    onSecondaryContainer = BrandCyanGlow,
    tertiary = BrandAmber,
    error = BrandRed,
    background = DarkBg,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceSubtle,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkCardBorder
)

enum class AppThemeMode {
    LIGHT,
    DARK,
    SYSTEM
}

@Composable
fun NearbyMeshTheme(
    themeMode: AppThemeMode = AppThemeMode.DARK,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = if (isDark) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}
