package com.schortgen.vehiclelogai.data.repository

import com.schortgen.vehiclelogai.data.local.dao.VehicleDao
import com.schortgen.vehiclelogai.data.models.Vehicle
import kotlinx.coroutines.flow.Flow

class VehicleRepository(private val vehicleDao: VehicleDao) {
    suspend fun insertVehicle(vehicle: Vehicle): Long {
        return vehicleDao.insert(vehicle)
    }

    suspend fun updateVehicle(vehicle: Vehicle) {
        vehicleDao.update(vehicle)
    }

    suspend fun deleteVehicle(vehicle: Vehicle) {
        vehicleDao.delete(vehicle)
    }

    suspend fun getVehicleById(id: Long): Vehicle? {
        return vehicleDao.getById(id)
    }

    fun observeAllVehicles(): Flow<List<Vehicle>> {
        return vehicleDao.observeAll()
    }

    suspend fun countVehicles(): Int = vehicleDao.count()
}
