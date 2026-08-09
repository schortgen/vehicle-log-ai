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

    fun getPreferredTripMeter(): PreferredTripMeter = _preferredTripMeter.value

    fun setPreferredTripMeter(meter: PreferredTripMeter) {
        prefs.edit().putString(KEY_TRIP_METER, meter.name).apply()
        _preferredTripMeter.value = meter
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
    }
}
