package com.schortgen.vehiclelogai.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.schortgen.vehiclelogai.data.models.Event
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: Event): Long

    @Update
    suspend fun update(event: Event)

    @Delete
    suspend fun delete(event: Event)

    @Query("SELECT * FROM events WHERE id = :id")
    suspend fun getById(id: Long): Event?

    @Query("SELECT * FROM events ORDER BY eventDate DESC")
    fun observeAll(): Flow<List<Event>>

    @Query("SELECT * FROM events WHERE vehicleId = :vehicleId ORDER BY eventDate DESC")
    fun observeEventsForVehicle(vehicleId: Long): Flow<List<Event>>

    // Dashboard aggregate queries
    @Query("SELECT COUNT(*) FROM events WHERE eventType = 'FUEL' AND eventDate >= :startOfMonth AND eventDate < :startOfNextMonth")
    suspend fun countFuelPurchasesThisMonth(startOfMonth: Long, startOfNextMonth: Long): Int

    @Query("SELECT COALESCE(SUM(totalCost), 0) FROM events WHERE eventType = 'FUEL' AND eventDate >= :startOfMonth AND eventDate < :startOfNextMonth")
    suspend fun sumFuelCostThisMonth(startOfMonth: Long, startOfNextMonth: Long): Double

    @Query("SELECT COALESCE(SUM(totalCost), 0) FROM events WHERE eventType = 'FUEL' AND eventDate >= :startOfYear AND eventDate < :startOfNextYear")
    suspend fun sumFuelCostThisYear(startOfYear: Long, startOfNextYear: Long): Double

    @Query("SELECT COALESCE(AVG(totalCost), 0) FROM events WHERE eventType = 'FUEL' AND totalCost IS NOT NULL")
    suspend fun averageFuelCost(): Double

    @Query("SELECT * FROM events ORDER BY eventDate DESC LIMIT 5")
    suspend fun getRecentEvents(): List<Event>

    @Query("SELECT * FROM events WHERE vehicleId = :vehicleId AND eventType = 'FUEL' ORDER BY eventDate DESC LIMIT 1")
    suspend fun getLastFuelEvent(vehicleId: Long): Event?

    @Query("SELECT COUNT(*) FROM events WHERE vehicleId = :vehicleId AND eventType = 'FUEL'")
    suspend fun countFuelEventsForVehicle(vehicleId: Long): Int

    @Query("SELECT COUNT(*) FROM events")
    suspend fun count(): Int
}
