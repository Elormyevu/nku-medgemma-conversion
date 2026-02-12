package com.nku.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * ScreeningExporter — Export screening history to CSV for clinic integration.
 *
 * Generates a standards-compliant CSV file with all screening data from the
 * Room database, suitable for import into clinic EHR systems or DHIS2.
 *
 * Finding 9 fix: Added consent check before export. CSV contains PHI-like
 * data (symptoms, recommendations) that should not be shared without
 * explicit user acknowledgment.
 */
object ScreeningExporter {

    /**
     * Check if user has previously acknowledged the data sharing notice.
     * Uses SharedPreferences to persist the acknowledgment.
     */
    fun hasUserConsent(context: Context): Boolean {
        val prefs = context.getSharedPreferences("nku_export_prefs", Context.MODE_PRIVATE)
        return prefs.getBoolean("export_consent_given", false)
    }

    /**
     * Record that user has acknowledged the data sharing notice.
     */
    fun setUserConsent(context: Context, consented: Boolean) {
        val prefs = context.getSharedPreferences("nku_export_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("export_consent_given", consented).apply()
    }

    /**
     * Export all screenings to a CSV file and return a shareable Intent.
     *
     * Finding 9 fix: Caller must verify consent via hasUserConsent() before
     * invoking this method. The export includes a data notice in the CSV header
     * and auto-deletes exports older than 24 hours.
     *
     * @param context Android context for file access
     * @param screenings List of screening entities from Room
     * @return Pair of (File, Intent) — the file for reference and a share intent
     */
    fun exportToCsv(context: Context, screenings: List<ScreeningEntity>): Pair<File, Intent> {
        // Finding 9: Clean up old exports (>24h) to limit data retention
        cleanupOldExports(context)

        val dateFormat = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US)
        val filename = "nku_screenings_${dateFormat.format(Date())}.csv"

        val exportDir = File(context.getExternalFilesDir(null), "exports")
        exportDir.mkdirs()
        val csvFile = File(exportDir, filename)

        csvFile.bufferedWriter().use { writer ->
            // Finding 9: Add data notice header
            writer.appendLine("# Nku Sentinel Screening Export — Contains health screening data")
            writer.appendLine("# Exported: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.US).format(Date())}")
            writer.appendLine("# NOTICE: This file may contain sensitive health information.")
            writer.appendLine("#")

            // Header row (F-8 fix: include recommendations and edema_risk_factors)
            writer.appendLine(
                "id,timestamp,heart_rate_bpm,hr_confidence,pallor_severity," +
                "edema_severity,triage_level,symptoms," +
                "recommendations,edema_risk_factors," +
                "language,is_pregnant,gestational_weeks"
            )

            // Data rows
            screenings.forEach { s ->
                val ts = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date(s.timestamp))
                writer.appendLine(
                    "${s.id},$ts,${s.heartRateBpm ?: ""},${s.heartRateConfidence ?: ""}," +
                    "${escapeCsv(s.pallorSeverity ?: "")}," +
                    "${escapeCsv(s.edemaSeverity ?: "")}," +
                    "${escapeCsv(s.triageLevel ?: "")},${escapeCsv(s.symptoms ?: "")}," +
                    "${escapeCsv(s.recommendations ?: "")},${escapeCsv(s.edemaRiskFactors ?: "")}," +
                    "${s.language},${s.isPregnant},${s.gestationalWeeks ?: ""}"
                )
            }
        }

        // Create share intent
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            csvFile
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Nku Sentinel — Screening Export")
            putExtra(Intent.EXTRA_TEXT, 
                "This export contains health screening data from Nku Sentinel. " +
                "Handle according to your organization's data protection policy.")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        return Pair(csvFile, Intent.createChooser(shareIntent, "Export Screenings"))
    }

    /**
     * Finding 9: Clean up exports older than 24 hours to limit data retention.
     */
    private fun cleanupOldExports(context: Context) {
        val exportDir = File(context.getExternalFilesDir(null), "exports")
        if (!exportDir.exists()) return

        val cutoffMs = System.currentTimeMillis() - (24 * 60 * 60 * 1000)
        exportDir.listFiles()?.forEach { file ->
            if (file.isFile && file.lastModified() < cutoffMs) {
                file.delete()
            }
        }
    }

    private fun escapeCsv(value: String): String {
        return if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }
}
