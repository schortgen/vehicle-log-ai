package com.schortgen.vehiclelogai.data.models

import androidx.room.TypeConverter

enum class ProcessingStatus(val displayName: String) {
    PENDING("Pending"),
    PROCESSING("Processing"),
    NEEDS_REVIEW("Needs Review"),
    COMPLETE("Complete");

    companion object {
        fun fromValue(value: String): ProcessingStatus {
            return entries.find { it.name == value } ?: PENDING
        }
    }
}

class ProcessingStatusConverter {
    @TypeConverter
    fun fromProcessingStatus(status: ProcessingStatus): String = status.name

    @TypeConverter
    fun toProcessingStatus(value: String): ProcessingStatus = ProcessingStatus.fromValue(value)
}