package com.schortgen.vehiclelogai.data.models

import androidx.room.TypeConverter

enum class EventType(val value: String) {
    FUEL("FUEL"),
    MAINTENANCE("MAINTENANCE"),
    MILEAGE("MILEAGE"),
    INSPECTION("INSPECTION"),
    REGISTRATION("REGISTRATION"),
    TIRE_ROTATION("TIRE_ROTATION");

    companion object {
        fun fromValue(value: String): EventType {
            return entries.find { it.value == value } ?: MAINTENANCE
        }
    }
}

class EventTypeConverter {
    @TypeConverter
    fun fromEventType(eventType: EventType): String = eventType.value

    @TypeConverter
    fun toEventType(value: String): EventType = EventType.fromValue(value)
}