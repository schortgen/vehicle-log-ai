package com.schortgen.vehiclelogai.data.models

/**
 * Represents the output of an OCR processing operation.
 * This is a pure data class, not a Room entity — it's used to pass
 * OCR results between the service layer and the UI/ViewModel.
 *
 * Keeping this independent of event creation allows receipt parsing
 * and AI assistance to be layered on top without changing the OCR service.
 */
data class OcrResult(
    val rawText: String,
    val processingTimeMs: Long,
    val confidence: Float? = null,
    val blockCount: Int = 0,
    val lineCount: Int = 0
)