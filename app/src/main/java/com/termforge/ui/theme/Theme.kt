package com.termforge.ui.theme

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
 * completely separate concept (see [com.termforge.ui.theme.TerminalTheme]).
 */
enum class AppTheme(val displayName: String) {
    SYSTEM_DYNAMIC("System (Material You)"),
    FORGE_DARK("Forge Dark"),
    MIDNIGHT_BLUE("Midnight Blue"),
    SOLARIZED("Solarized"),
    AMOLED("Pure AMOLED Black"),
    LIGHT("Light"),
}

private val ForgeDarkScheme = darkColorScheme(
    primary = ForgeAmber,
    onPrimary = NeutralBlack,
    primaryContainer = ForgeAmberDark,
    onPrimaryContainer = NeutralBlack,
    secondary = ForgeAmber,
    background = ForgeSurface,
    onBackground = ForgeOnSurface,
    surface = ForgeSurface,
    onSurface = ForgeOnSurface,
    surfaceVariant = ForgeSurfaceVariant,
    onSurfaceVariant = ForgeOnSurfaceVariant,
    outline = ForgeOutline,
    error = ForgeError,
)

private val ForgeLightScheme = lightColorScheme(
    primary = ForgeAmberDark,
    onPrimary = NeutralWhite,
    secondary = ForgeAmberDark,
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
    primary = ForgeAmber,
    onPrimary = NeutralBlack,
    background = AmoledBlack,
    surface = AmoledBlack,
    surfaceVariant = AmoledSurfaceVariant,
    onSurface = Color(0xFFEAEAEA),
    outline = Color(0xFF222222),
)

@Composable
fun TermForgeTheme(
    appTheme: AppTheme = AppTheme.FORGE_DARK,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme: ColorScheme = when (appTheme) {
        AppTheme.SYSTEM_DYNAMIC ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } else {
                if (darkTheme) ForgeDarkScheme else ForgeLightScheme
            }
        AppTheme.FORGE_DARK -> if (darkTheme) ForgeDarkScheme else ForgeLightScheme
        AppTheme.MIDNIGHT_BLUE -> MidnightScheme
        AppTheme.SOLARIZED -> if (darkTheme) SolarizedDarkScheme else SolarizedLightScheme
        AppTheme.AMOLED -> AmoledScheme
        AppTheme.LIGHT -> ForgeLightScheme
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
