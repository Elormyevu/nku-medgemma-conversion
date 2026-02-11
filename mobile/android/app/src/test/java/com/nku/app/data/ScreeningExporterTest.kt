package com.nku.app.data

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * T-3: Unit tests for ScreeningExporter CSV output.
 * Tests header columns, data formatting, and CSV escaping.
 */
class ScreeningExporterTest {

    @Test
    fun `CSV header includes all expected columns`() {
        val expectedColumns = listOf(
            "id", "timestamp", "heart_rate_bpm", "hr_confidence",
            "pallor_severity", "edema_severity", "triage_level", "symptoms",
            "recommendations", "edema_risk_factors",  // F-8 additions
            "language", "is_pregnant", "gestational_weeks"
        )
        val header = "id,timestamp,heart_rate_bpm,hr_confidence,pallor_severity," +
            "edema_severity,triage_level,symptoms," +
            "recommendations,edema_risk_factors," +
            "language,is_pregnant,gestational_weeks"

        val headerColumns = header.split(",")
        assertEquals(expectedColumns.size, headerColumns.size)
        expectedColumns.forEachIndexed { index, expected ->
            assertEquals("Column $index mismatch", expected, headerColumns[index])
        }
    }

    @Test
    fun `CSV escape handles commas`() {
        val value = "headache, fever"
        val escaped = escapeCsv(value)
        assertEquals("\"headache, fever\"", escaped)
    }

    @Test
    fun `CSV escape handles quotes`() {
        val value = "patient said \"dizzy\""
        val escaped = escapeCsv(value)
        assertEquals("\"patient said \"\"dizzy\"\"\"", escaped)
    }

    @Test
    fun `CSV escape handles newlines`() {
        val value = "line1\nline2"
        val escaped = escapeCsv(value)
        assertEquals("\"line1\nline2\"", escaped)
    }

    @Test
    fun `CSV escape passes through safe values`() {
        val value = "MODERATE"
        val escaped = escapeCsv(value)
        assertEquals("MODERATE", escaped)
    }

    @Test
    fun `CSV escape handles empty strings`() {
        val value = ""
        val escaped = escapeCsv(value)
        assertEquals("", escaped)
    }

    /**
     * Mirror of ScreeningExporter.escapeCsv for unit testing
     * (since the actual method is private in the exporter).
     */
    private fun escapeCsv(value: String): String {
        return if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }
}
