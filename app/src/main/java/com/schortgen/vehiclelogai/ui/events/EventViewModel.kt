package com.schortgen.vehiclelogai.ui.events

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.schortgen.vehiclelogai.data.models.Event
import com.schortgen.vehiclelogai.data.repository.EventRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

import com.schortgen.vehiclelogai.data.repository.VehicleRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EventViewModel(
    private val repository: EventRepository,
    private val vehicleRepository: VehicleRepository? = null
) : ViewModel() {

    fun observeAllEvents(): Flow<List<Event>> {
        return repository.observeAllEvents()
    }

    fun observeEventsForVehicle(vehicleId: Long): Flow<List<Event>> {
        return repository.observeEventsForVehicle(vehicleId)
    }

    fun addEvent(event: Event) {
        viewModelScope.launch {
            repository.insertEvent(event)
            val odo = event.odometer
            val vehId = event.vehicleId
            if (odo != null && vehId != null && vehicleRepository != null) {
                val vehicle = vehicleRepository.getVehicleById(vehId)
                if (vehicle != null && odo > (vehicle.currentMileage ?: 0)) {
                    vehicleRepository.updateVehicle(vehicle.copy(currentMileage = odo))
                }
            }
        }
    }

    fun updateEvent(event: Event) {
        viewModelScope.launch {
            repository.updateEvent(event)
            val odo = event.odometer
            val vehId = event.vehicleId
            if (odo != null && vehId != null && vehicleRepository != null) {
                val vehicle = vehicleRepository.getVehicleById(vehId)
                if (vehicle != null && odo > (vehicle.currentMileage ?: 0)) {
                    vehicleRepository.updateVehicle(vehicle.copy(currentMileage = odo))
                }
            }
        }
    }

    fun deleteEvent(event: Event) {
        viewModelScope.launch {
            repository.deleteEvent(event)
        }
    }

    suspend fun getEventById(id: Long): Event? {
        return repository.getEventById(id)
    }

    suspend fun getLastFuelEvent(vehicleId: Long): Event? {
        return repository.getLastFuelEvent(vehicleId)
    }

    suspend fun checkPreviousEventWarnings(event: Event): String? {
        val vehicleId = event.vehicleId ?: return null
        val existingEvents = repository.getEventsForVehicle(vehicleId)
        val otherEvents = existingEvents.filter { it.id != event.id }
        if (otherEvents.isEmpty()) return null

        val warnings = mutableListOf<String>()
        val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

        val latestEvent = otherEvents.maxByOrNull { it.eventDate }
        if (latestEvent != null && latestEvent.eventDate > event.eventDate) {
            val latestDateStr = dateFormat.format(Date(latestEvent.eventDate))
            val currDateStr = dateFormat.format(Date(event.eventDate))
            warnings.add("The previous event date ($latestDateStr) is newer than the current date ($currDateStr).")
        }

        val priorEvent = otherEvents.filter { it.eventDate <= event.eventDate }.maxByOrNull { it.eventDate }
        if (priorEvent != null && priorEvent.odometer != null && event.odometer != null) {
            if (priorEvent.odometer!! > event.odometer!!) {
                val priorDateStr = dateFormat.format(Date(priorEvent.eventDate))
                warnings.add("The previous event odometer (${priorEvent.odometer} mi on $priorDateStr) is higher than the current odometer (${event.odometer} mi).")
            }
        } else if (latestEvent != null && latestEvent.odometer != null && event.odometer != null && latestEvent.odometer!! > event.odometer!!) {
            val latestDateStr = dateFormat.format(Date(latestEvent.eventDate))
            warnings.add("An existing event odometer (${latestEvent.odometer} mi on $latestDateStr) is higher than the current odometer (${event.odometer} mi).")
        }

        return if (warnings.isNotEmpty()) warnings.joinToString("\n\n") else null
    }
}

class EventViewModelFactory(
    private val repository: EventRepository,
    private val vehicleRepository: VehicleRepository? = null
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(EventViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return EventViewModel(repository, vehicleRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}