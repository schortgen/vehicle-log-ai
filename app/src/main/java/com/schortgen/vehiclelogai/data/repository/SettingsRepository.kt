package com.schortgen.vehiclelogai.data.repository

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class PreferredTripMeter(val displayName: String, val description: String) {
    TRIP_A("Trip A", "Standard meter for tracking fuel stops / fill-ups"),
    TRIP_B("Trip B", "Meter for tracking oil changes or secondary logs"),
    ANY("Calculate", "calculate based on vehicle odometer")
}

class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("vehicle_log_settings", Context.MODE_PRIVATE)

    private val _preferredTripMeter = MutableStateFlow(loadPreferredTripMeter())
    val preferredTripMeter: StateFlow<PreferredTripMeter> = _preferredTripMeter.asStateFlow()

    private val _movePhotosOnComplete = MutableStateFlow(prefs.getBoolean(KEY_MOVE_PHOTOS, true))
    val movePhotosOnComplete: StateFlow<Boolean> = _movePhotosOnComplete.asStateFlow()

    private val _completedPhotosFolderUri = MutableStateFlow(prefs.getString(KEY_COMPLETED_FOLDER_URI, null))
    val completedPhotosFolderUri: StateFlow<String?> = _completedPhotosFolderUri.asStateFlow()

    private val _completedPhotosFolderName = MutableStateFlow(prefs.getString(KEY_COMPLETED_FOLDER_NAME, "Pictures/ProcessedVehiclePhotos"))
    val completedPhotosFolderName: StateFlow<String?> = _completedPhotosFolderName.asStateFlow()

    private val _hasPromptedPhotoDestination = MutableStateFlow(prefs.getBoolean(KEY_HAS_PROMPTED_PHOTO_DEST, false))
    val hasPromptedPhotoDestination: StateFlow<Boolean> = _hasPromptedPhotoDestination.asStateFlow()

    fun getPreferredTripMeter(): PreferredTripMeter = _preferredTripMeter.value

    fun setPreferredTripMeter(meter: PreferredTripMeter) {
        prefs.edit().putString(KEY_TRIP_METER, meter.name).apply()
        _preferredTripMeter.value = meter
    }

    fun getMovePhotosOnComplete(): Boolean = _movePhotosOnComplete.value

    fun setMovePhotosOnComplete(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_MOVE_PHOTOS, enabled).apply()
        _movePhotosOnComplete.value = enabled
    }

    fun getCompletedPhotosFolderUri(): String? = _completedPhotosFolderUri.value

    fun getCompletedPhotosFolderName(): String = _completedPhotosFolderName.value ?: "Pictures/ProcessedVehiclePhotos"

    fun setCompletedPhotosFolder(uri: String?, displayName: String?) {
        prefs.edit()
            .putString(KEY_COMPLETED_FOLDER_URI, uri)
            .putString(KEY_COMPLETED_FOLDER_NAME, displayName ?: "Pictures/ProcessedVehiclePhotos")
            .apply()
        _completedPhotosFolderUri.value = uri
        _completedPhotosFolderName.value = displayName ?: "Pictures/ProcessedVehiclePhotos"
        try {
            com.schortgen.vehiclelogai.data.models.clearImageModelCache()
        } catch (_: Exception) {}
    }

    fun hasPromptedPhotoDestination(): Boolean = _hasPromptedPhotoDestination.value

    fun setHasPromptedPhotoDestination(prompted: Boolean) {
        prefs.edit().putBoolean(KEY_HAS_PROMPTED_PHOTO_DEST, prompted).apply()
        _hasPromptedPhotoDestination.value = prompted
    }

    private fun loadPreferredTripMeter(): PreferredTripMeter {
        val savedName = prefs.getString(KEY_TRIP_METER, PreferredTripMeter.ANY.name)
        return try {
            PreferredTripMeter.valueOf(savedName ?: PreferredTripMeter.ANY.name)
        } catch (e: Exception) {
            PreferredTripMeter.ANY
        }
    }

    companion object {
        private const val KEY_TRIP_METER = "pref_trip_meter"
        private const val KEY_MOVE_PHOTOS = "pref_move_photos_on_complete"
        private const val KEY_COMPLETED_FOLDER_URI = "pref_completed_photos_folder_uri"
        private const val KEY_COMPLETED_FOLDER_NAME = "pref_completed_photos_folder_name"
        private const val KEY_HAS_PROMPTED_PHOTO_DEST = "pref_has_prompted_photo_destination"
    }
}
