package com.schortgen.vehiclelogai.data.models

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.schortgen.vehiclelogai.data.models.Vehicle

@Entity(
    tableName = "events",
    foreignKeys = [
        ForeignKey(
            entity = Vehicle::class,
            parentColumns = ["id"],
            childColumns = ["vehicleId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["vehicleId"]),
        Index(value = ["eventDate"]),
        Index(value = ["eventType"])
    ]
)
data class Event(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vehicleId: Long,
    val eventType: EventType,
    val eventDate: Long,
    val createdDate: Long = System.currentTimeMillis(),
    val confidence: Float? = null,
    val verified: Boolean = false,
    val notes: String? = null,
    // Fuel-specific fields
    val odometer: Int? = null,
    val tripDistance: Double? = null,
    val gallons: Double? = null,
    val pricePerGallon: Double? = null,
    val totalCost: Double? = null,
    val location: String? = null,
    val photoPath: String? = null
)
