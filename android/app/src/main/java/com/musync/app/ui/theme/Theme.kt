package com.musync.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = AppleMusicPink,
    onPrimary = TextWhite,
    primaryContainer = SurfaceBlack,
    onPrimaryContainer = TextWhite,
    secondary = AppleMusicRed,
    onSecondary = BackgroundDark,
    tertiary = AppleMusicPink,
    background = BackgroundDark,
    onBackground = TextWhite,
    surface = SurfaceDark,
    onSurface = TextWhite,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = TextSecondary
)

private val AmoledColorScheme = darkColorScheme(
    primary = AppleMusicPink,
    onPrimary = Color.White,
    primaryContainer = Color.Black,
    onPrimaryContainer = Color.White,
    secondary = AppleMusicRed,
    onSecondary = Color.Black,
    tertiary = AppleMusicPink,
    background = Color.Black,
    onBackground = Color.White,
    surface = Color.Black,
    onSurface = Color.White,
    surfaceVariant = Color(0xFF0F0F0F),
    onSurfaceVariant = Color(0xFFCCCCCC)
)

private val LightColorScheme = lightColorScheme(
    primary = AppleMusicPink,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF2F2F7),
    onPrimaryContainer = Color(0xFF1C1C1E),
    secondary = AppleMusicRed,
    onSecondary = Color.White,
    tertiary = AppleMusicPink,
    background = Color(0xFFF8F8FA),
    onBackground = Color(0xFF111111),
    surface = Color.White,
    onSurface = Color(0xFF111111),
    surfaceVariant = Color(0xFFE5E5EA),
    onSurfaceVariant = Color(0xFF666666)
)

@Composable
fun MusyncTheme(
    themeMode: String = "Dark",
    content: @Composable () -> Unit
) {
    val isSystemDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        "Light" -> false
        "System" -> isSystemDark
        else -> true
    }

    val colorScheme = when {
        themeMode == "AMOLED" -> AmoledColorScheme
        !isDark -> LightColorScheme
        else -> DarkColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = colorScheme.background.toArgb()
                window.navigationBarColor = colorScheme.background.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !isDark
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !isDark
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

