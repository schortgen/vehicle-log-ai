package com.schortgen.vehiclelogai.data.models

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "review_items",
    foreignKeys = [
        ForeignKey(
            entity = Event::class,
            parentColumns = ["id"],
            childColumns = ["eventId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["eventId"]),
        Index(value = ["vehicleId"]),
        Index(value = ["captureDate"])
    ]
)
data class ReviewItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val photoPath: String? = null,
    val captureDate: Long = System.currentTimeMillis(),
    val vehicleId: Long? = null,
    val eventId: Long? = null, // Changed back to nullable for ungrouped items
    val reason: String? = null,
    val confidence: Float? = null,
    val status: ProcessingStatus = ProcessingStatus.PENDING,
    val createdDate: Long = System.currentTimeMillis(),
    // OCR result fields
    val ocrText: String? = null,
    val ocrProcessingTimeMs: Long? = null,
    // Parsed receipt data stored as JSON string (FuelPurchaseCandidate serialized)
    val parsedData: String? = null
)