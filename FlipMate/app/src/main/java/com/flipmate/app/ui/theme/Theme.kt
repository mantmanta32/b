package com.flipmate.app.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ═══════════════════════════════════════════════════════
//  DESIGN TOKENS — Pastel Pink/Purple Light Theme
// ═══════════════════════════════════════════════════════

// Zemin ve Yüzeyler
val PastelBg = Color(0xFFFAF5F8)
val PastelSurface = Color(0xFFFFFFFF)
val PastelSurfaceVariant = Color(0xFFF3E8EE)
val PastelBorder = Color(0xFFEADCF3)

// Ana Vurgu (Accent) Renkleri
val PastelPrimary = Color(0xFF8E528D)
val PastelPrimaryContainer = Color(0xFFF5E6F2)
val PastelSecondary = Color(0xFFC27BA0)
val PastelTextPrimary = Color(0xFF2C222E)
val PastelTextSecondary = Color(0xFF7D6E7C)

// Fonksiyonel Renkler (Pastel Dünyaya Uyarlanmış)
val SoftLongGreen = Color(0xFF3FA77C)
val SoftLongGreenBg = Color(0xFFE8F5EE)
val SoftShortRed = Color(0xFFE56B6F)
val SoftShortRedBg = Color(0xFFFDECEE)
val SoftWarningYellow = Color(0xFFE8A838)

private val LightColorScheme = lightColorScheme(
    primary = PastelPrimary,
    onPrimary = Color.White,
    primaryContainer = PastelPrimaryContainer,
    onPrimaryContainer = PastelPrimary,
    secondary = PastelSecondary,
    background = PastelBg,
    surface = PastelSurface,
    surfaceVariant = PastelSurfaceVariant,
    onSurface = PastelTextPrimary,
    onSurfaceVariant = PastelTextSecondary
)

@Composable
fun FlipMateTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        content = content
    )
}
