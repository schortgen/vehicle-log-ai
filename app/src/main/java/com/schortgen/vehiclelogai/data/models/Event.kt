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

fun Event.calculateMpg(allEvents: List<Event> = emptyList()): Double? {
    if (eventType != EventType.FUEL) return null
    val gal = gallons ?: return null
    if (gal <= 0) return null

    if (tripDistance != null && tripDistance > 0) {
        return tripDistance / gal
    }

    if (odometer != null && vehicleId != null && allEvents.isNotEmpty()) {
        val prevFuelEvent = allEvents
            .filter { it.vehicleId == vehicleId && it.eventType == EventType.FUEL && it.odometer != null }
            .filter { it.eventDate < eventDate || (it.eventDate == eventDate && it.id < id) }
            .maxByOrNull { it.eventDate }
        if (prevFuelEvent?.odometer != null && odometer > prevFuelEvent.odometer) {
            val trip = (odometer - prevFuelEvent.odometer).toDouble()
            return trip / gal
        }
    }
    return null
}

fun Event.getPhotoPaths(): List<String> {
    if (photoPath.isNullOrBlank()) return emptyList()
    return photoPath.split(',', '|', '\n', ';')
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
}

fun String.toImageModel(): Any {
    val trimmed = this.trim()
    return when {
        trimmed.startsWith("content://") -> android.net.Uri.parse(trimmed)
        trimmed.startsWith("file://") -> android.net.Uri.parse(trimmed)
        trimmed.startsWith("/") -> java.io.File(trimmed)
        else -> {
            val file = java.io.File(trimmed)
            if (file.exists() || trimmed.contains("/")) file else android.net.Uri.parse(trimmed)
        }
    }
}

