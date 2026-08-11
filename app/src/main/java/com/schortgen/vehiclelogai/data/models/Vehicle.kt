package com.schortgen.vehiclelogai.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vehicles")
data class Vehicle(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nickname: String? = null,
    val year: Int? = null,
    val make: String? = null,
    val model: String? = null,
    val licensePlate: String? = null,
    val vin: String? = null,
    val currentMileage: Int? = null,
    val isActive: Boolean = true,
    val createdDate: Long = System.currentTimeMillis()
)

val Vehicle.displayName: String
    get() {
        if (!nickname.isNullOrBlank()) return nickname
        val details = "${year ?: ""} ${make ?: ""} ${model ?: ""}".trim()
        return details.ifEmpty { "Vehicle #$id" }
    }
