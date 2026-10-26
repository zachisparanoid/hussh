package com.hussh.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

/**
 * App UI typography uses the Material 3 type scale on the system font. The terminal surface uses
 * its own bundled monospace (see [com.hussh.ui.theme.TerminalFonts]) — deliberately separate so
 * UI chrome and terminal text can be tuned independently.
 */
val AppTypography = Typography(
    titleLarge = TextStyle(fontFamily = FontFamily.Default, fontSize = 22.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.Default, fontSize = 16.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.Default, fontSize = 16.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.Default, fontSize = 14.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.Default, fontSize = 14.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
)
