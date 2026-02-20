package com.nku.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Nku Sentinel — Design Token System
 *
 * Centralizes all colors, gradients, and theme tokens.
 * Now theme-aware via CompositionLocal — all `NkuColors.*` references
 * automatically resolve to the correct palette for Light/Dark mode.
 */

data class NkuColorPalette(
    // ── Brand ──
    val Primary: Color = Color(0xFF4CAF50),
    val Secondary: Color = Color(0xFF2196F3),
    val Accent: Color = Color(0xFF00BCD4),

    // ── Surfaces ──
    val Surface: Color,
    val Background: Color,
    val SurfaceVariant: Color,
    val SurfaceContainer: Color,

    // ── Text ──
    val OnSurface: Color,
    val OnSurfaceMuted: Color,
    val OnPrimary: Color = Color(0xFFFFFFFF),

    // ── Triage Severity ──
    val TriageGreen: Color = Color(0xFF4CAF50),
    val TriageYellow: Color = Color(0xFFFFEB3B),
    val TriageOrange: Color = Color(0xFFFF9800),
    val TriageRed: Color = Color(0xFFF44336),

    // ── Status ──
    val Success: Color = Color(0xFF4CAF50),
    val Warning: Color = Color(0xFFFF9800),
    val Error: Color = Color(0xFFF44336),
    val Info: Color = Color(0xFF2196F3),

    // ── Sensor-specific ──
    val HeartRate: Color = Color(0xFFE91E63),
    val Pallor: Color = Color(0xFFFF5722),
    val Edema: Color = Color(0xFF9C27B0),

    // ── UI Components ──
    val CardBackground: Color,
    val InstructionCard: Color,
    val InactiveElement: Color,
    val InactiveText: Color,
    val ProgressBackground: Color,
    val CompletedCard: Color,
    val AccentCyan: Color = Color(0xFF00D4FF),
    val ListeningIndicator: Color = Color(0xFFFF5722),
    val MutedBlue: Color = Color(0xFF90CAF9),

    // ── Generic text ──
    val TextPrimary: Color,
    val TextSecondary: Color,
)

private val DarkNkuColors = NkuColorPalette(
    Surface = Color(0xFF1A1A2E),
    Background = Color(0xFF0F0F1A),
    SurfaceVariant = Color(0xFF252540),
    SurfaceContainer = Color(0xFF16213E),
    OnSurface = Color(0xFFE0E0E0),
    OnSurfaceMuted = Color(0xFFAAAAAA),
    CardBackground = Color(0xFF252540),
    InstructionCard = Color(0xFF2D2D44),
    InactiveElement = Color(0xFF3D3D5C),
    InactiveText = Color(0xFF666666),
    ProgressBackground = Color(0xFF1E1E38),
    CompletedCard = Color(0xFF1A2A1A),
    TextPrimary = Color.White,
    TextSecondary = Color.Gray,
)

private val LightNkuColors = NkuColorPalette(
    Surface = Color(0xFFF5F5F5),
    Background = Color(0xFFFFFFFF),
    SurfaceVariant = Color(0xFFE8E8EC),
    SurfaceContainer = Color(0xFFECEFF1),
    OnSurface = Color(0xFF1A1A2E),
    OnSurfaceMuted = Color(0xFF666666),
    CardBackground = Color(0xFFE8E8EC),
    InstructionCard = Color(0xFFDEDEE8),
    InactiveElement = Color(0xFFB0B0C0),
    InactiveText = Color(0xFF999999),
    ProgressBackground = Color(0xFFE0E0EC),
    CompletedCard = Color(0xFFD4ECD4),
    TextPrimary = Color(0xFF1A1A2E),
    TextSecondary = Color(0xFF555555),
    MutedBlue = Color(0xFF1565C0),
    AccentCyan = Color(0xFF0097A7),
)

val LocalNkuColors = staticCompositionLocalOf { DarkNkuColors }

/**
 * Backward-compatible static accessor — delegates to the CompositionLocal at runtime.
 * All existing `NkuColors.*` references in screens now resolve to the current theme.
 *
 * NOTE: For Compose code, prefer `NkuColors` (which is the object below).
 * The object delegates each property to LocalNkuColors.current.
 */
object NkuColors {
    // These properties are accessed inside @Composable functions via
    // LocalNkuColors.current.  We keep this object so that all existing
    // call-sites (`NkuColors.Primary`, etc.) compile without changes.
    // Non-composable code paths will get the DarkNkuColors defaults.
    val Primary get() = _current.Primary
    val Secondary get() = _current.Secondary
    val Accent get() = _current.Accent
    val Surface get() = _current.Surface
    val Background get() = _current.Background
    val SurfaceVariant get() = _current.SurfaceVariant
    val SurfaceContainer get() = _current.SurfaceContainer
    val OnSurface get() = _current.OnSurface
    val OnSurfaceMuted get() = _current.OnSurfaceMuted
    val OnPrimary get() = _current.OnPrimary
    val TriageGreen get() = _current.TriageGreen
    val TriageYellow get() = _current.TriageYellow
    val TriageOrange get() = _current.TriageOrange
    val TriageRed get() = _current.TriageRed
    val Success get() = _current.Success
    val Warning get() = _current.Warning
    val Error get() = _current.Error
    val Info get() = _current.Info
    val HeartRate get() = _current.HeartRate
    val Pallor get() = _current.Pallor
    val Edema get() = _current.Edema
    val CardBackground get() = _current.CardBackground
    val InstructionCard get() = _current.InstructionCard
    val InactiveElement get() = _current.InactiveElement
    val InactiveText get() = _current.InactiveText
    val ProgressBackground get() = _current.ProgressBackground
    val CompletedCard get() = _current.CompletedCard
    val AccentCyan get() = _current.AccentCyan
    val ListeningIndicator get() = _current.ListeningIndicator
    val MutedBlue get() = _current.MutedBlue
    val TextPrimary get() = _current.TextPrimary
    val TextSecondary get() = _current.TextSecondary

    // ── Gradients ── (dynamic based on current palette)
    val BackgroundGradient get() = listOf(_current.Background, _current.SurfaceContainer, _current.Surface)
    val CardGradient get() = listOf(_current.SurfaceVariant, _current.Surface)

    // Thread-local holder — set by NkuTheme before composing child content.
    @PublishedApi
    internal var _current: NkuColorPalette = DarkNkuColors
}

private val NkuDarkColorScheme = darkColorScheme(
    primary = DarkNkuColors.Primary,
    secondary = DarkNkuColors.Secondary,
    surface = DarkNkuColors.Surface,
    background = DarkNkuColors.Background,
    onSurface = DarkNkuColors.OnSurface,
    onPrimary = DarkNkuColors.OnPrimary,
    surfaceVariant = DarkNkuColors.SurfaceVariant,
    error = DarkNkuColors.Error,
)

private val NkuLightColorScheme = lightColorScheme(
    primary = LightNkuColors.Primary,
    secondary = LightNkuColors.Secondary,
    surface = LightNkuColors.Surface,
    background = LightNkuColors.Background,
    onSurface = LightNkuColors.OnSurface,
    onPrimary = LightNkuColors.OnPrimary,
    surfaceVariant = LightNkuColors.SurfaceVariant,
    error = LightNkuColors.Error,
)

/**
 * Nku Sentinel theme wrapper.
 * Provides both MaterialTheme colorScheme AND NkuColors palette.
 */
@Composable
fun NkuTheme(
    isDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val palette = if (isDarkTheme) DarkNkuColors else LightNkuColors
    NkuColors._current = palette

    CompositionLocalProvider(LocalNkuColors provides palette) {
        MaterialTheme(
            colorScheme = if (isDarkTheme) NkuDarkColorScheme else NkuLightColorScheme,
            content = content
        )
    }
}
