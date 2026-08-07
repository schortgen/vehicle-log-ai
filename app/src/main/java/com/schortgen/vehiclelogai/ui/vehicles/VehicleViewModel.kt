package com.schortgen.vehiclelogai.ui.vehicles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.schortgen.vehiclelogai.data.models.Vehicle
import com.schortgen.vehiclelogai.data.repository.VehicleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class VehicleViewModel(private val repository: VehicleRepository) : ViewModel() {

    // 1. observeVehicles(): Exposing the repository Flow as a StateFlow.
    // This caches the latest list of vehicles for the UI to observe immediately.
    val vehicles: StateFlow<List<Vehicle>> = repository.observeAllVehicles()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Alternative observe method returning the raw Flow if needed
    fun observeVehicles(): Flow<List<Vehicle>> {
        return repository.observeAllVehicles()
    }

    // 2. getVehicles(): Returns the current snapshot of vehicles synchronously
    fun getVehicles(): List<Vehicle> {
        return vehicles.value
    }

    // 3. addVehicle(): Inserts a vehicle into the database via the repository
    fun addVehicle(vehicle: Vehicle) {
        viewModelScope.launch {
            repository.insertVehicle(vehicle)
        }
    }

    // 4. getVehicleById(): Fetches a single vehicle for detail screens
    suspend fun getVehicleById(id: Long): Vehicle? {
        return repository.getVehicleById(id)
    }

    // 5. updateVehicle(): Updates an existing vehicle
    fun updateVehicle(vehicle: Vehicle) {
        viewModelScope.launch {
            repository.updateVehicle(vehicle)
        }
    }

    // 6. deleteVehicle(): Deletes a vehicle
    fun deleteVehicle(vehicle: Vehicle) {
        viewModelScope.launch {
            repository.deleteVehicle(vehicle)
        }
    }
}

// Factory to instantiate the ViewModel with the repository dependency
class VehicleViewModelFactory(private val repository: VehicleRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(VehicleViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return VehicleViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
