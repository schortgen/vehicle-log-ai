package com.schortgen.vehiclelogai.data.models

data class BackupSettings(
    val preferredTripMeter: String = "TRIP_A"
)

data class BackupData(
    val version: Int = 1,
    val appName: String = "VehicleLogAI",
    val timestamp: Long = System.currentTimeMillis(),
    val settings: BackupSettings = BackupSettings(),
    val vehicles: List<Vehicle> = emptyList(),
    val events: List<Event> = emptyList(),
    val reviewItems: List<ReviewItem> = emptyList(),
    val scannedPhotos: List<ScannedPhoto> = emptyList()
)
