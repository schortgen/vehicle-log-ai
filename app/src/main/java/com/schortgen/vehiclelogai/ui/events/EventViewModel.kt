package com.schortgen.vehiclelogai.ui.events

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.schortgen.vehiclelogai.data.models.Event
import com.schortgen.vehiclelogai.data.repository.EventRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class EventViewModel(private val repository: EventRepository) : ViewModel() {

    fun observeEventsForVehicle(vehicleId: Long): Flow<List<Event>> {
        return repository.observeEventsForVehicle(vehicleId)
    }

    fun addEvent(event: Event) {
        viewModelScope.launch {
            repository.insertEvent(event)
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

class EventViewModelFactory(private val repository: EventRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(EventViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return EventViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}