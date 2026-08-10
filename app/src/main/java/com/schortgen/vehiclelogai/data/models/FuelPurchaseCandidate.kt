package com.schortgen.vehiclelogai.data.models

/**
 * Represents the result of parsing OCR text for fuel purchase data.
 * Each field has a confidence score (0.0–1.0) indicating how reliable
 * the extracted value is.
 *
 * This is independent of ML Kit and independent of the event model.
 * Future sprints can convert this into a VehicleEvent.
 */
data class FuelPurchaseCandidate(
    val stationName: String? = null,
    val stationNameConfidence: Float = 0f,
    val purchaseDate: String? = null,
    val purchaseDateConfidence: Float = 0f,
    val gallons: Double? = null,
    val gallonsConfidence: Float = 0f,
    val pricePerGallon: Double? = null,
    val pricePerGallonConfidence: Float = 0f,
    val totalCost: Double? = null,
    val totalCostConfidence: Float = 0f,
    val odometer: Int? = null,
    val odometerConfidence: Float = 0f,
    val tripDistance: Double? = null,
    val tripDistanceConfidence: Float = 0f,
    val missingFields: List<String> = emptyList(),
    val overallConfidence: Float = 0f,
    val warningMessage: String? = null
)