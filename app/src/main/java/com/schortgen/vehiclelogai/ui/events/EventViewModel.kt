package com.schortgen.vehiclelogai.ui.events

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.schortgen.vehiclelogai.data.models.Event
import com.schortgen.vehiclelogai.data.repository.EventRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

import com.schortgen.vehiclelogai.data.repository.VehicleRepository

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