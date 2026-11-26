package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

enum class ThemeMode(val label: String) {
    SYSTEM("System"),
    DARK("Obsidian Dark"),
    LIGHT("Porcelain Light")
}

private val DarkColorScheme = darkColorScheme(
    primary = CobaltBright,
    onPrimary = Color.White,
    primaryContainer = CobaltContainerDark,
    onPrimaryContainer = Color(0xFFD6E4FF),
    secondary = CyanSoft,
    onSecondary = Color(0xFF002A33),
    secondaryContainer = CyanContainerDark,
    onSecondaryContainer = CyanSoft,
    tertiary = EmeraldSuccess,
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceElevated,
    onSurfaceVariant = DarkOnSurfaceMuted,
    surfaceContainerLow = DarkSurface,
    surfaceContainer = DarkSurfaceElevated,
    surfaceContainerHigh = DarkSurfaceHigher,
    outline = DarkOutline,
    error = CrimsonError,
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = CobaltPrimary,
    onPrimary = Color.White,
    primaryContainer = CobaltContainerLight,
    onPrimaryContainer = Color(0xFF082F73),
    secondary = CyanAccent,
    onSecondary = Color.White,
    secondaryContainer = CyanContainerLight,
    onSecondaryContainer = Color(0xFF083344),
    tertiary = EmeraldSuccess,
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceElevated,
    onSurfaceVariant = LightOnSurfaceMuted,
    surfaceContainerLow = LightSurface,
    surfaceContainer = LightSurfaceElevated,
    surfaceContainerHigh = LightSurfaceHigher,
    outline = LightOutline,
    error = CrimsonError,
    onError = Color.White
)

val ClipVaultShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun ClipVaultTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        isDark -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = ClipVaultShapes,
        content = content
    )
}
