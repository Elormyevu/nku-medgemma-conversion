package com.nku.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Nku Sentinel — Design Token System
 *
 * Centralizes all colors, gradients, and theme tokens.
 * USER-1: Supports light/dark/system theme toggle.
 * Audit Fix: F-10 (LOW) — Replace hardcoded hex colors.
 */

object NkuColors {
    // ── Brand ──
    val Primary = Color(0xFF4CAF50)         // Green — health/vitality
    val Secondary = Color(0xFF2196F3)       // Blue — trust/medical
    val Accent = Color(0xFF00BCD4)          // Cyan — accent elements

    // ── Surfaces ──
    val Surface = Color(0xFF1A1A2E)         // Dark navy surface
    val Background = Color(0xFF0F0F1A)      // Deepest background
    val SurfaceVariant = Color(0xFF252540)  // Card/elevated surface
    val SurfaceContainer = Color(0xFF16213E)// Container background

    // ── Light Mode Surfaces ──
    val LightSurface = Color(0xFFF5F5F5)
    val LightBackground = Color(0xFFFFFFFF)
    val LightSurfaceVariant = Color(0xFFE8E8EC)
    val LightSurfaceContainer = Color(0xFFECEFF1)

    // ── Text ──
    val OnSurface = Color(0xFFE0E0E0)       // Primary text
    val OnSurfaceMuted = Color(0xFFAAAAAA)  // Secondary/muted text
    val OnPrimary = Color(0xFFFFFFFF)       // Text on primary
    val LightOnSurface = Color(0xFF1A1A2E)  // Dark text for light mode
    val LightOnSurfaceMuted = Color(0xFF666666)

    // ── Triage Severity ──
    val TriageGreen = Color(0xFF4CAF50)     // Low severity
    val TriageYellow = Color(0xFFFFEB3B)    // Medium severity
    val TriageOrange = Color(0xFFFF9800)    // High severity
    val TriageRed = Color(0xFFF44336)       // Critical severity

    // ── Status ──
    val Success = Color(0xFF4CAF50)
    val Warning = Color(0xFFFF9800)
    val Error = Color(0xFFF44336)
    val Info = Color(0xFF2196F3)

    // ── Gradients ──
    val BackgroundGradient = listOf(Background, SurfaceContainer, Surface)
    val CardGradient = listOf(SurfaceVariant, Surface)

    // ── Sensor-specific ──
    val HeartRate = Color(0xFFE91E63)       // Pink — cardiac
    val Pallor = Color(0xFFFF5722)          // Deep orange — anemia
    val Edema = Color(0xFF9C27B0)           // Purple — preeclampsia

    // ── UI Components (F-UI-2: Replace hardcoded hex colors) ──
    val CardBackground = Color(0xFF252540)  // Standard card background
    val InstructionCard = Color(0xFF2D2D44) // How-it-works / instruction cards
    val InactiveElement = Color(0xFF3D3D5C) // Inactive buttons, unfocused borders
    val InactiveText = Color(0xFF666666)    // Disabled/incomplete text
    val ProgressBackground = Color(0xFF1E1E38) // Progress indicator card
    val CompletedCard = Color(0xFF1A2A1A)   // Green-tinted completed item card
    val AccentCyan = Color(0xFF00D4FF)      // Bright cyan for TTS/voice buttons
    val ListeningIndicator = Color(0xFFFF5722) // Recording/listening state
    val MutedBlue = Color(0xFF90CAF9)       // Secondary text highlight
}

private val NkuDarkColorScheme = darkColorScheme(
    primary = NkuColors.Primary,
    secondary = NkuColors.Secondary,
    surface = NkuColors.Surface,
    background = NkuColors.Background,
    onSurface = NkuColors.OnSurface,
    onPrimary = NkuColors.OnPrimary,
    surfaceVariant = NkuColors.SurfaceVariant,
    error = NkuColors.Error,
)

private val NkuLightColorScheme = lightColorScheme(
    primary = NkuColors.Primary,
    secondary = NkuColors.Secondary,
    surface = NkuColors.LightSurface,
    background = NkuColors.LightBackground,
    onSurface = NkuColors.LightOnSurface,
    onPrimary = NkuColors.OnPrimary,
    surfaceVariant = NkuColors.LightSurfaceVariant,
    error = NkuColors.Error,
)

/**
 * Nku Sentinel theme wrapper.
 * USER-1: Supports isDarkTheme parameter. Defaults to system setting.
 */
@Composable
fun NkuTheme(
    isDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (isDarkTheme) NkuDarkColorScheme else NkuLightColorScheme,
        content = content
    )
}
