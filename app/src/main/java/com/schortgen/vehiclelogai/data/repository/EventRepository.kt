package com.schortgen.vehiclelogai.data.repository

import com.schortgen.vehiclelogai.data.local.dao.EventDao
import com.schortgen.vehiclelogai.data.models.Event
import com.schortgen.vehiclelogai.data.models.ReviewItem
import com.schortgen.vehiclelogai.data.repository.ReviewItemRepository
import com.schortgen.vehiclelogai.service.EventGroupingService
import kotlinx.coroutines.flow.Flow

class EventRepository(
    private val eventDao: EventDao,
    private val reviewItemRepository: ReviewItemRepository? = null
) {
    suspend fun insertEvent(event: Event): Long {
        return eventDao.insert(event)
    }

    suspend fun updateEvent(event: Event) {
        eventDao.update(event)
    }

    suspend fun deleteEvent(event: Event) {
        eventDao.delete(event)
    }

    suspend fun deleteUnverifiedEvents() {
        eventDao.deleteUnverifiedEvents()
    }

    suspend fun getEventById(id: Long): Event? {
        return eventDao.getById(id)
    }

    fun observeAllEvents(): Flow<List<Event>> {
        return eventDao.observeAll()
    }

    fun observeAllIncludingUnverified(): Flow<List<Event>> {
        return eventDao.observeAllIncludingUnverified()
    }

    fun observeEventsForVehicle(vehicleId: Long): Flow<List<Event>> {
        return eventDao.observeEventsForVehicle(vehicleId)
    }

    suspend fun getEventsForVehicle(vehicleId: Long): List<Event> {
        return eventDao.getEventsForVehicle(vehicleId)
    }

    // Dashboard aggregate methods
    suspend fun countFuelPurchasesThisMonth(startOfMonth: Long, startOfNextMonth: Long): Int {
        return eventDao.countFuelPurchasesThisMonth(startOfMonth, startOfNextMonth)
    }

    suspend fun sumFuelCostThisMonth(startOfMonth: Long, startOfNextMonth: Long): Double {
        return eventDao.sumFuelCostThisMonth(startOfMonth, startOfNextMonth)
    }

    suspend fun sumFuelCostThisYear(startOfYear: Long, startOfNextYear: Long): Double {
        return eventDao.sumFuelCostThisYear(startOfYear, startOfNextYear)
    }

    suspend fun averageFuelCost(): Double {
        return eventDao.averageFuelCost()
    }

    suspend fun getRecentEvents(): List<Event> {
        return eventDao.getRecentEvents()
    }

    suspend fun getLastFuelEvent(vehicleId: Long): Event? {
        return eventDao.getLastFuelEvent(vehicleId)
    }

    suspend fun countFuelEventsForVehicle(vehicleId: Long): Int {
        return eventDao.countFuelEventsForVehicle(vehicleId)
    }

    suspend fun countEvents(): Int = eventDao.count()

    /**
     * Group ungrouped ReviewItems into Events using the EventGroupingService.
     * Returns the list of created Events.
     */
    suspend fun groupUngroupedItemsIntoEvents(
        groupingService: EventGroupingService,
        defaultVehicleId: Long? = null
    ): List<Event> {
        val reviewItemRepo = reviewItemRepository
            ?: throw IllegalStateException("ReviewItemRepository not available for grouping")

        val ungroupedItems = reviewItemRepo.getUngroupedItems()
        if (ungroupedItems.isEmpty()) {
            return emptyList()
        }

        val clusters = groupingService.groupItemsIntoClusters(ungroupedItems)
        val createdEvents = mutableListOf<Event>()

        for (cluster in clusters) {
            try {
                val event = groupingService.createEventFromCluster(cluster, defaultVehicleId)
                val eventId = eventDao.insert(event)
                val eventWithId = event.copy(id = eventId)

                // Update all ReviewItems in this cluster with the eventId
                val updatedItems = cluster.items.map { it.copy(eventId = eventId) }
                reviewItemRepo.updateAllReviewItems(updatedItems)

                createdEvents.add(eventWithId)
            } catch (e: Exception) {
                // Log error but continue with other clusters
                // Could happen if no vehicleId available for a cluster
                continue
            }
        }

        return createdEvents
    }
}