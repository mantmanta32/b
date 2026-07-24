package com.flipmate.app.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ═══════════════════════════════════════════════════════
//  DESIGN TOKENS — Pastel Pink/Purple Light Theme
// ═══════════════════════════════════════════════════════

object TerminalColors {
    // Backgrounds (soft pastel layers)
    val Base = Color(0xFFFDF2F8)
    val Surface = Color(0xFFFFFFFF)
    val Elevated = Color(0xFFF6EEFF)
    val SurfaceVariant = Color(0xFFF5EBFF)

    // Trading colors
    val LongGreen = Color(0xFF10B981)
    val LongGreenDim = Color(0x1F10B981)
    val ShortRed = Color(0xFFF43F5E)
    val ShortRedDim = Color(0x1FF43F5E)
    val MartingaleAmber = Color(0xFFF59E0B)
    val MartingaleAmberDim = Color(0x1FF59E0B)

    // Text hierarchy (no black — deep purple tones)
    val TextPrimary = Color(0xFF4A3868)
    val TextSecondary = Color(0xFF6B5B8A)
    val TextMuted = Color(0xFF9689B5)
    val TextDisabled = Color(0xFFC4B8D8)

    // Borders & accents
    val Border = Color(0xFFE8D5F5)
    val BorderStrong = Color(0xFFD4B8EE)
    val AccentPurple = Color(0xFFC026D3)
    val AccentViolet = Color(0xFFA855F7)
    val AccentPink = Color(0xFFF472B6)
    val AccentCyan = Color(0xFF818CF8)

    // Status
    val WarningRed = Color(0xFFE11D48)
    val SuccessGreen = Color(0xFF059669)
    val PendingAmber = Color(0xFFD97706)
}

val LocalTerminalColors = staticCompositionLocalOf { TerminalColors }

@Composable
fun FlipMateTheme(content: @Composable () -> Unit) {
    val lightColors = lightColorScheme(
        primary = TerminalColors.AccentPurple,
        onPrimary = Color.White,
        primaryContainer = TerminalColors.Elevated,
        onPrimaryContainer = TerminalColors.TextPrimary,
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
        colorScheme = lightColors,
        content = content
    )
}
