package com.flipmate.app.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ═══════════════════════════════════════════════════════
//  DESIGN TOKENS — Professional Dark Trading Terminal
// ═══════════════════════════════════════════════════════

object TerminalColors {
    // Backgrounds (3 ton depth)
    val Base = Color(0xFF0B0E14)
    val Surface = Color(0xFF121620)
    val Elevated = Color(0xFF1A1F2E)
    val SurfaceVariant = Color(0xFF161B27)

    // Trading colors
    val LongGreen = Color(0xFF00D68F)
    val LongGreenDim = Color(0x2600D68F)
    val ShortRed = Color(0xFFFF3B5C)
    val ShortRedDim = Color(0x26FF3B5C)
    val MartingaleAmber = Color(0xFFFFB020)
    val MartingaleAmberDim = Color(0x26FFB020)

    // Text hierarchy
    val TextPrimary = Color(0xFFE8ECF4)
    val TextSecondary = Color(0xFF8B93A8)
    val TextMuted = Color(0xFF4E5668)
    val TextDisabled = Color(0xFF353B4A)

    // Borders & accents
    val Border = Color(0xFF252A3A)
    val BorderStrong = Color(0xFF343B4F)
    val AccentPurple = Color(0xFFA855F7)
    val AccentCyan = Color(0xFF22D3EE)

    // Status
    val WarningRed = Color(0xFFFF4040)
    val SuccessGreen = Color(0xFF00D68F)
    val PendingAmber = Color(0xFFFFB020)
}

val LocalTerminalColors = staticCompositionLocalOf { TerminalColors }

@Composable
fun FlipMateTheme(content: @Composable () -> Unit) {
    val darkColors = darkColorScheme(
        primary = TerminalColors.AccentPurple,
        onPrimary = Color.White,
        surface = TerminalColors.Surface,
        onSurface = TerminalColors.TextPrimary,
        surfaceVariant = TerminalColors.SurfaceVariant,
        onSurfaceVariant = TerminalColors.TextSecondary,
        background = TerminalColors.Base,
        onBackground = TerminalColors.TextPrimary,
        outline = TerminalColors.Border,
        outlineVariant = TerminalColors.BorderStrong,
        error = TerminalColors.ShortRed,
        onError = Color.White
    )
    MaterialTheme(
        colorScheme = darkColors,
        content = content
    )
}
