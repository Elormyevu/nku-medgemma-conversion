package com.nku.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * ScreeningEntity — persists completed screening sessions.
 *
 * Each row represents one patient screening workflow (HR + pallor + edema).
 * Data survives app restarts so CHWs don't lose field work.
 */
@Entity(tableName = "screenings")
data class ScreeningEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),

    // Cardio
    val heartRateBpm: Float? = null,
    val heartRateConfidence: Float? = null,

    // Anemia
    val pallorSeverity: String? = null,    // NORMAL, MILD, MODERATE, SEVERE

    // Preeclampsia
    val edemaSeverity: String? = null,     // NORMAL, MILD, MODERATE, SIGNIFICANT

    // Triage
    val triageLevel: String? = null,       // AI triage result summary
    val symptoms: String? = null,          // Patient-reported symptoms

    // Context
    val language: String = "en",
    val isPregnant: Boolean = false,
    val gestationalWeeks: Int? = null
)
