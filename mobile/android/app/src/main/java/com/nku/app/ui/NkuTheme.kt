package com.nku.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Nku Sentinel — Design Token System
 *
 * Centralizes all colors, gradients, and theme tokens.
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

    // ── Text ──
    val OnSurface = Color(0xFFE0E0E0)       // Primary text
    val OnSurfaceMuted = Color(0xFFAAAAAA)  // Secondary/muted text
    val OnPrimary = Color(0xFFFFFFFF)       // Text on primary

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

@Composable
fun NkuTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NkuDarkColorScheme,
        content = content
    )
}
