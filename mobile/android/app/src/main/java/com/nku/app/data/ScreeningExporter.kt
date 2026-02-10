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
 */
object ScreeningExporter {

    /**
     * Export all screenings to a CSV file and return a shareable Intent.
     *
     * @param context Android context for file access
     * @param screenings List of screening entities from Room
     * @return Pair of (File, Intent) — the file for reference and a share intent
     */
    fun exportToCsv(context: Context, screenings: List<ScreeningEntity>): Pair<File, Intent> {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US)
        val filename = "nku_screenings_${dateFormat.format(Date())}.csv"

        val exportDir = File(context.getExternalFilesDir(null), "exports")
        exportDir.mkdirs()
        val csvFile = File(exportDir, filename)

        csvFile.bufferedWriter().use { writer ->
            // Header row
            writer.appendLine(
                "id,timestamp,heart_rate_bpm,hr_confidence,pallor_severity," +
                "edema_severity,triage_level,symptoms," +
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
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        return Pair(csvFile, Intent.createChooser(shareIntent, "Export Screenings"))
    }

    private fun escapeCsv(value: String): String {
        return if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }
}
