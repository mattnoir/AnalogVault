package com.analogvault.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ─── Palette ─────────────────────────────────────────────────────────────────
val Bg        = Color(0xFF0E0C0A)
val Bg2       = Color(0xFF181512)
val Bg3       = Color(0xFF221E18)
val Bg4       = Color(0xFF2E2820)
val Border    = Color(0xFF3A3228)
val Amber     = Color(0xFFD4935A)
val AmberBright = Color(0xFFF0B06A)
val AmberDark = Color(0xFF7A5030)
val TextPrimary   = Color(0xFFE8DDD0)
val TextSecondary = Color(0xFFA09080)
val TextTertiary  = Color(0xFF6A5A4A)
val GreenOk   = Color(0xFF7EC982)
val BlueInfo  = Color(0xFF6AB0D4)
val RedErr    = Color(0xFFC45050)
val OrangeWarn= Color(0xFFE0A952)

private val darkColorScheme = darkColorScheme(
    primary          = Amber,
    onPrimary        = Bg,
    primaryContainer = Bg4,
    onPrimaryContainer = AmberBright,
    secondary        = AmberDark,
    onSecondary      = TextPrimary,
    secondaryContainer = Bg3,
    onSecondaryContainer = TextSecondary,
    background       = Bg,
    onBackground     = TextPrimary,
    surface          = Bg2,
    onSurface        = TextPrimary,
    surfaceVariant   = Bg3,
    onSurfaceVariant = TextSecondary,
    outline          = Border,
    outlineVariant   = AmberDark,
    error            = RedErr,
    onError          = Bg
)

@Composable
fun AnalogVaultTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme,
        typography = Typography(
            // Use system fonts; in production swap for DM Serif Display / DM Mono via downloadable fonts
            displayLarge = MaterialTheme.typography.displayLarge,
            bodyLarge = MaterialTheme.typography.bodyLarge
        ),
        content = content
    )
}
