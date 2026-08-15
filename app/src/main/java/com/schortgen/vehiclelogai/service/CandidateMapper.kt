package com.schortgen.vehiclelogai.service

import com.schortgen.vehiclelogai.data.models.Event
import com.schortgen.vehiclelogai.data.models.EventType
import com.schortgen.vehiclelogai.data.models.FuelPurchaseCandidate
import com.schortgen.vehiclelogai.data.models.ReviewItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Maps a reviewed FuelPurchaseCandidate into a VehicleEvent.
 *
 * All mapping logic lives here, outside the UI.
 * Keeps the event model independent of the parsing layer.
 */
object CandidateMapper {

    private val dateFormat = SimpleDateFormat("MM/dd/yyyy", Locale.US)
    private val dateFormatAlt = SimpleDateFormat("MM-dd-yyyy", Locale.US)
    private val dateFormatYearFirst = SimpleDateFormat("yyyy/MM/dd", Locale.US)
    private val dateFormatYearFirstAlt = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    /**
     * Convert a reviewed FuelPurchaseCandidate into a VehicleEvent.
     *
     * @param candidate The reviewed (and possibly user-edited) candidate.
     * @param vehicleId The selected vehicle ID.
     * @param reviewItem The original review item (for photoPath, ocrText, etc.).
     * @return A fully populated Event ready for insertion.
     */
    fun toEvent(
        candidate: FuelPurchaseCandidate,
        vehicleId: Long,
        reviewItem: ReviewItem
    ): Event {
        val eventDate = parseDate(candidate.purchaseDate) ?: reviewItem.captureDate

        return Event(
            vehicleId = vehicleId,
            eventType = EventType.FUEL,
            eventDate = eventDate,
            confidence = candidate.overallConfidence.takeIf { it > 0f },
            verified = true,
            notes = buildNotes(candidate, reviewItem),
            odometer = candidate.odometer,
            tripDistance = candidate.tripDistance,
            gallons = candidate.gallons,
            pricePerGallon = candidate.pricePerGallon,
            totalCost = candidate.totalCost,
            location = candidate.stationName,
            photoPath = reviewItem.photoPath
        )
    }

    /**
     * Convert a reviewed FuelPurchaseCandidate into a VehicleEvent with multiple review items.
     *
     * @param candidate The reviewed (and possibly user-edited) candidate.
     * @param vehicleId The selected vehicle ID.
     * @param reviewItems The list of associated review items.
     * @return A fully populated Event ready for insertion.
     */
    fun toEvent(
        candidate: FuelPurchaseCandidate,
        vehicleId: Long,
        reviewItems: List<ReviewItem>
    ): Event {
        val primaryItem = reviewItems.firstOrNull()
        val eventDate = parseDate(candidate.purchaseDate) ?: primaryItem?.captureDate ?: System.currentTimeMillis()
        val allPhotos = reviewItems.mapNotNull { it.photoPath }.filter { it.isNotBlank() }.distinct()
        val combinedPhotoPath = if (allPhotos.isNotEmpty()) allPhotos.joinToString(",") else null

        return Event(
            vehicleId = vehicleId,
            eventType = EventType.FUEL,
            eventDate = eventDate,
            confidence = candidate.overallConfidence.takeIf { it > 0f },
            verified = true,
            notes = primaryItem?.let { buildNotes(candidate, it) },
            odometer = candidate.odometer,
            tripDistance = candidate.tripDistance,
            gallons = candidate.gallons,
            pricePerGallon = candidate.pricePerGallon,
            totalCost = candidate.totalCost,
            location = candidate.stationName,
            photoPath = combinedPhotoPath
        )
    }

    /**
     * Build notes that preserve traceability back to the original receipt.
     */
    private fun buildNotes(candidate: FuelPurchaseCandidate, reviewItem: ReviewItem): String {
        val parts = mutableListOf<String>()

        // Preserve original reason
        reviewItem.reason?.let { parts.add("Source: $it") }

        // Preserve raw OCR text reference
        if (!reviewItem.ocrText.isNullOrBlank()) {
            val preview = reviewItem.ocrText.take(100).replace("\n", " ")
            parts.add("OCR: $preview${if (reviewItem.ocrText.length > 100) "..." else ""}")
        }

        // Preserve confidence
        if (candidate.overallConfidence > 0f) {
            parts.add("Confidence: ${(candidate.overallConfidence * 100).toInt()}%")
        }

        // Preserve missing fields info
        if (candidate.missingFields.isNotEmpty()) {
            parts.add("Missing: ${candidate.missingFields.joinToString(", ")}")
        }

        return parts.joinToString("\n")
    }

    /**
     * Parse a date string from the candidate into epoch millis.
     * Supports multiple formats found on receipts.
     */
    fun parseDate(dateStr: String?): Long? {
        if (dateStr.isNullOrBlank()) return null

        val formatPatterns = listOf(
            "MM/dd/yyyy",
            "MM-dd-yyyy",
            "yyyy/MM/dd",
            "yyyy-MM-dd",
            "MMM dd, yyyy",
            "MMMM dd, yyyy",
            "yyyy-MM-dd'T'HH:mm:ss"
        )

        for (pattern in formatPatterns) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US)
                val date = sdf.parse(dateStr)
                if (date != null) return date.time
            } catch (_: Exception) {
                // Try next format
            }
        }

        return dateStr.toLongOrNull()
    }
}