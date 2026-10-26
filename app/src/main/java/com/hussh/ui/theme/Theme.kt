package com.hussh.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Selectable app "chrome" themes. These style the Material 3 UI shell only; terminal colors are a
 * completely separate concept (see [com.hussh.ui.theme.TerminalTheme]).
 */
enum class AppTheme(val displayName: String) {
    SYSTEM_DYNAMIC("System (Material You)"),
    HUSSH_DARK("Hussh Dark"),
    MIDNIGHT_BLUE("Midnight Blue"),
    SOLARIZED("Solarized"),
    AMOLED("Pure AMOLED Black"),
    LIGHT("Light"),
}

private val HusshDarkScheme = darkColorScheme(
    primary = HusshAmber,
    onPrimary = NeutralBlack,
    primaryContainer = HusshAmberDark,
    onPrimaryContainer = NeutralBlack,
    secondary = HusshAmber,
    background = HusshSurface,
    onBackground = HusshOnSurface,
    surface = HusshSurface,
    onSurface = HusshOnSurface,
    surfaceVariant = HusshSurfaceVariant,
    onSurfaceVariant = HusshOnSurfaceVariant,
    outline = HusshOutline,
    error = HusshError,
)

private val HusshLightScheme = lightColorScheme(
    primary = HusshAmberDark,
    onPrimary = NeutralWhite,
    secondary = HusshAmberDark,
    background = Color(0xFFFBFBFC),
    surface = Color(0xFFF3F4F6),
    error = Color(0xFFBA1A1A),
)

private val MidnightScheme = darkColorScheme(
    primary = MidnightPrimary,
    onPrimary = NeutralWhite,
    background = MidnightSurface,
    surface = MidnightSurface,
    surfaceVariant = MidnightSurfaceVariant,
    onSurface = Color(0xFFD6E0F5),
    outline = Color(0xFF2C3A5A),
)

private val SolarizedDarkScheme = darkColorScheme(
    primary = SolarizedBlue,
    onPrimary = SolarizedBase3,
    background = SolarizedBase03,
    surface = SolarizedBase02,
    surfaceVariant = SolarizedBase02,
    onSurface = Color(0xFF93A1A1),
)

private val SolarizedLightScheme = lightColorScheme(
    primary = SolarizedBlue,
    background = SolarizedBase3,
    surface = SolarizedBase2,
)

private val AmoledScheme = darkColorScheme(
    primary = HusshAmber,
    onPrimary = NeutralBlack,
    background = AmoledBlack,
    surface = AmoledBlack,
    surfaceVariant = AmoledSurfaceVariant,
    onSurface = Color(0xFFEAEAEA),
    outline = Color(0xFF222222),
)

@Composable
fun HusshTheme(
    appTheme: AppTheme = AppTheme.HUSSH_DARK,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme: ColorScheme = when (appTheme) {
        AppTheme.SYSTEM_DYNAMIC ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } else {
                if (darkTheme) HusshDarkScheme else HusshLightScheme
            }
        AppTheme.HUSSH_DARK -> if (darkTheme) HusshDarkScheme else HusshLightScheme
        AppTheme.MIDNIGHT_BLUE -> MidnightScheme
        AppTheme.SOLARIZED -> if (darkTheme) SolarizedDarkScheme else SolarizedLightScheme
        AppTheme.AMOLED -> AmoledScheme
        AppTheme.LIGHT -> HusshLightScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            val lightBars = colorScheme.background.luminance() > 0.5f
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = lightBars
                isAppearanceLightNavigationBars = lightBars
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content,
    )
}
