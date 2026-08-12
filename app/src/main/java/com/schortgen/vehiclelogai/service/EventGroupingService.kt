package com.schortgen.vehiclelogai.service

import com.schortgen.vehiclelogai.data.models.Event
import com.schortgen.vehiclelogai.data.models.EventType
import com.schortgen.vehiclelogai.data.models.FuelPurchaseCandidate
import com.schortgen.vehiclelogai.data.models.ReviewItem
import com.schortgen.vehiclelogai.data.repository.EventRepository
import com.schortgen.vehiclelogai.data.repository.ReviewItemRepository
import com.schortgen.vehiclelogai.data.repository.VehicleRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Groups ReviewItems (photos) into Events based on temporal proximity.
 * Photos taken within the same time window (default 15 minutes) are considered part of the same event.
 * 
 * This service handles the core event grouping logic:
 * 1. Clustering photos by capture time
 * 2. Inferring event types from OCR data
 * 3. Creating Event entities from clusters
 * 4. Updating ReviewItems with their assigned eventId
 */
class EventGroupingService(
    private val eventRepository: EventRepository,
    private val reviewItemRepository: ReviewItemRepository? = null,
    private val vehicleRepository: VehicleRepository? = null
) {
    companion object {
        const val DEFAULT_GROUPING_WINDOW_MS = 15 * 60 * 1000L // 15 minutes
        
        // Configurable grouping window (can be overridden)
        var groupingWindowMs: Long = DEFAULT_GROUPING_WINDOW_MS
            private set
        
        fun setGroupingWindow(windowMs: Long) {
            groupingWindowMs = windowMs
        }
    }

    /**
     * Data class representing a cluster of photos that should be grouped into one event.
     */
    data class PhotoCluster(
        val items: List<ReviewItem>,
        val eventType: EventType?,
        val vehicleId: Long?,
        val representativePhotoPath: String?
    ) {
        val earliestDate: Long
            get() = items.minOf { it.captureDate }
        
        val latestDate: Long
            get() = items.maxOf { it.captureDate }
    }

    private val nonVehicleTerms = setOf(
        "flower", "flowers", "plant", "garden", "nature",
        "pet", "dog", "cat", "food", "dish", "meal",
        "selfie", "portrait", "family", "vacation", "beach",
        "party", "concert", "sunset", "sky"
    )

    private fun isVehicleRelatedItem(item: ReviewItem): Boolean {
        val ocr = item.ocrText?.lowercase() ?: ""
        val path = item.photoPath?.lowercase() ?: ""
        val reason = item.reason?.lowercase() ?: ""

        val containsNonVehicle = nonVehicleTerms.any { ocr.contains(it) || path.contains(it) || reason.contains(it) }
        val vehicleTerms = setOf("fuel", "gallons", "gas", "pump", "station", "odometer", "receipt", "service", "oil", "tire", "total", "cost", "invoice", "price", "vehicle", "car", "truck")
        val containsVehicle = vehicleTerms.any { ocr.contains(it) || path.contains(it) || reason.contains(it) }

        if (containsNonVehicle && !containsVehicle) {
            return false
        }
        return true
    }

    private fun isSameCalendarDay(time1: Long, time2: Long): Boolean {
        val cal1 = java.util.Calendar.getInstance().apply { timeInMillis = time1 }
        val cal2 = java.util.Calendar.getInstance().apply { timeInMillis = time2 }
        return cal1.get(java.util.Calendar.YEAR) == cal2.get(java.util.Calendar.YEAR) &&
               cal1.get(java.util.Calendar.DAY_OF_YEAR) == cal2.get(java.util.Calendar.DAY_OF_YEAR)
    }

    /**
     * Group ungrouped ReviewItems into clusters based on capture time or same date.
     */
    fun groupItemsIntoClusters(items: List<ReviewItem>): List<PhotoCluster> {
        if (items.isEmpty()) return emptyList()

        // Filter out non-vehicle photos (e.g., photos of flowers, plants, pets, food)
        val vehicleItems = items.filter { isVehicleRelatedItem(it) }
        if (vehicleItems.isEmpty()) return emptyList()

        // Sort by captureDate ascending
        val sorted = vehicleItems.sortedBy { it.captureDate }

        val clusters = mutableListOf<PhotoCluster>()
        var currentCluster = mutableListOf<ReviewItem>()

        for (item in sorted) {
            if (currentCluster.isEmpty()) {
                currentCluster.add(item)
            } else {
                val lastItem = currentCluster.last()
                val timeDiff = kotlin.math.abs(item.captureDate - lastItem.captureDate)

                if (timeDiff <= groupingWindowMs) {
                    // Add to current cluster if captured within time window
                    currentCluster.add(item)
                } else {
                    // Start a new cluster
                    clusters.add(createCluster(currentCluster))
                    currentCluster = mutableListOf(item)
                }
            }
        }

        // Don't forget the last cluster
        if (currentCluster.isNotEmpty()) {
            clusters.add(createCluster(currentCluster))
        }

        return clusters
    }

    /**
     * Create a PhotoCluster from a list of ReviewItems, determining event type and vehicle.
     */
    private fun createCluster(items: List<ReviewItem>): PhotoCluster {
        val eventType = inferEventType(items)
        val vehicleId = inferVehicleId(items)
        val representativePhoto = selectRepresentativePhoto(items)

        return PhotoCluster(
            items = items,
            eventType = eventType,
            vehicleId = vehicleId,
            representativePhotoPath = representativePhoto
        )
    }

    /**
     * Infer the event type from the cluster's OCR data and parsed candidates.
     * Uses keyword matching and parsed data heuristics.
     */
    private fun inferEventType(items: List<ReviewItem>): EventType? {
        // Count occurrences of different event types based on OCR text and parsed data
        val typeScores = mutableMapOf<EventType, Int>()

        for (item in items) {
            // Check OCR text for keywords
            val ocrText = item.ocrText ?: ""
            val lowerOcr = ocrText.lowercase()

            when {
                lowerOcr.contains("fuel") || lowerOcr.contains("gallons") ||
                lowerOcr.contains("price per gallon") || lowerOcr.contains("total cost") -> {
                    typeScores[EventType.FUEL] = typeScores.getOrDefault(EventType.FUEL, 0) + 3
                }
                lowerOcr.contains("maintenance") || lowerOcr.contains("service") -> {
                    typeScores[EventType.MAINTENANCE] = typeScores.getOrDefault(EventType.MAINTENANCE, 0) + 3
                }
                lowerOcr.contains("tire") || lowerOcr.contains("rotation") -> {
                    typeScores[EventType.TIRE_ROTATION] = typeScores.getOrDefault(EventType.TIRE_ROTATION, 0) + 3
                }
                lowerOcr.contains("inspection") -> {
                    typeScores[EventType.INSPECTION] = typeScores.getOrDefault(EventType.INSPECTION, 0) + 3
                }
                lowerOcr.contains("registration") || lowerOcr.contains("renewal") -> {
                    typeScores[EventType.REGISTRATION] = typeScores.getOrDefault(EventType.REGISTRATION, 0) + 3
                }
                lowerOcr.contains("odometer") -> {
                    typeScores[EventType.MILEAGE] = typeScores.getOrDefault(EventType.MILEAGE, 0) + 2
                }
            }

            // Check parsed data if available
            if (item.parsedData != null) {
                try {
                    val json = JSONObject(item.parsedData)
                    if (json.has("gallons") || json.has("totalCost")) {
                        typeScores[EventType.FUEL] = typeScores.getOrDefault(EventType.FUEL, 0) + 5
                    }
                } catch (e: Exception) {
                    // Ignore JSON parsing errors
                }
            }
        }

        // Return the highest scoring type, or null if no clear indication
        if (typeScores.isEmpty()) return null
        return typeScores.maxByOrNull { it.value }?.key
    }

    /**
     * Infer the vehicle ID from the cluster.
     * If all items have the same non-null vehicleId, use it.
     * If there's a mix, return null (requires manual assignment).
     */
    private fun inferVehicleId(items: List<ReviewItem>): Long? {
        val vehicleIds = items.mapNotNull { it.vehicleId }.toSet()
        return if (vehicleIds.size == 1) vehicleIds.first() else null
    }

    /**
     * Select a representative photo for the event.
     * Prefer photos with parsed data (e.g., receipts) over plain photos.
     */
    private fun selectRepresentativePhoto(items: List<ReviewItem>): String? {
        // Prefer items with parsedData (successfully parsed receipts)
        val withParsedData = items.filter { it.parsedData != null }
        if (withParsedData.isNotEmpty()) {
            return withParsedData.first().photoPath
        }
        // Otherwise return the first item's photo
        return items.firstOrNull()?.photoPath
    }

    /**
     * Merge data from all ReviewItems in a cluster to create an Event.
     * For fuel events, aggregate parsed candidate data.
     */
    fun createEventFromCluster(
        cluster: PhotoCluster,
        defaultVehicleId: Long? = null
    ): Event {
        val vehicleId = cluster.vehicleId ?: defaultVehicleId
            ?: throw IllegalArgumentException("No vehicle ID available for event creation")

        val eventType = cluster.eventType ?: EventType.MAINTENANCE // Default to MAINTENANCE if unclear
        val eventDate = cluster.earliestDate

        val mergedFuelData = if (eventType == EventType.FUEL) {
            mergeFuelCandidates(cluster.items)
        } else null

        return Event(
            vehicleId = vehicleId,
            eventType = eventType,
            eventDate = eventDate,
            photoPath = cluster.representativePhotoPath,
            odometer = mergedFuelData?.odometer,
            tripDistance = mergedFuelData?.tripDistance,
            gallons = mergedFuelData?.gallons,
            pricePerGallon = mergedFuelData?.pricePerGallon,
            totalCost = mergedFuelData?.totalCost,
            location = mergedFuelData?.stationName
        )
    }

    /**
     * Merge FuelPurchaseCandidate data from multiple ReviewItems.
     * Strategy: Take the first non-null value for each field.
     * For conflicting values, prefer higher confidence scores.
     */
    private fun mergeFuelCandidates(items: List<ReviewItem>): FuelPurchaseCandidate? {
        val candidates = items.mapNotNull { item ->
            try {
                if (item.parsedData != null) {
                    val json = JSONObject(item.parsedData)
                    FuelPurchaseCandidate(
                        stationName = json.optString("stationName", "").ifEmpty { null },
                        stationNameConfidence = json.optDouble("stationNameConfidence", 0.0).toFloat(),
                        gallons = if (json.has("gallons") && !json.isNull("gallons")) json.getDouble("gallons") else null,
                        gallonsConfidence = json.optDouble("gallonsConfidence", 0.0).toFloat(),
                        pricePerGallon = if (json.has("pricePerGallon") && !json.isNull("pricePerGallon")) json.getDouble("pricePerGallon") else null,
                        pricePerGallonConfidence = json.optDouble("pricePerGallonConfidence", 0.0).toFloat(),
                        totalCost = if (json.has("totalCost") && !json.isNull("totalCost")) json.getDouble("totalCost") else null,
                        totalCostConfidence = json.optDouble("totalCostConfidence", 0.0).toFloat(),
                        odometer = if (json.has("odometer") && !json.isNull("odometer")) json.getInt("odometer") else null,
                        odometerConfidence = json.optDouble("odometerConfidence", 0.0).toFloat(),
                        tripDistance = if (json.has("tripDistance") && !json.isNull("tripDistance")) json.getDouble("tripDistance") else null,
                        tripDistanceConfidence = json.optDouble("tripDistanceConfidence", 0.0).toFloat()
                    )
                } else null
            } catch (e: Exception) {
                null
            }
        }

        if (candidates.isEmpty()) return null

        // Merge strategy: For each field, take the value with the highest confidence
        var merged = candidates.first()

        for (candidate in candidates.drop(1)) {
            merged = merged.copy(
                stationName = selectByConfidence(merged.stationName, merged.stationNameConfidence, candidate.stationName, candidate.stationNameConfidence),
                gallons = selectByConfidence(merged.gallons, merged.gallonsConfidence, candidate.gallons, candidate.gallonsConfidence),
                pricePerGallon = selectByConfidence(merged.pricePerGallon, merged.pricePerGallonConfidence, candidate.pricePerGallon, candidate.pricePerGallonConfidence),
                totalCost = selectByConfidence(merged.totalCost, merged.totalCostConfidence, candidate.totalCost, candidate.totalCostConfidence),
                odometer = selectByConfidence(merged.odometer, merged.odometerConfidence, candidate.odometer, candidate.odometerConfidence),
                tripDistance = selectByConfidence(merged.tripDistance, merged.tripDistanceConfidence, candidate.tripDistance, candidate.tripDistanceConfidence)
            )
        }

        return merged
    }

    /**
     * Helper to select between two values based on confidence scores.
     * Returns the value with higher confidence, or the first if equal.
     */
    private fun <T> selectByConfidence(
        value1: T?, confidence1: Float,
        value2: T?, confidence2: Float
    ): T? {
        if (value1 == null) return value2
        if (value2 == null) return value1
        return if (confidence1 >= confidence2) value1 else value2
    }

    /**
     * Process all ungrouped items and create events from them.
     * This is the main entry point for the grouping workflow.
     * 
     * @param defaultVehicleId Optional default vehicle ID to use when items don't have one
     * @return List of created events
     */
    suspend fun processAndGroupEvents(defaultVehicleId: Long? = null): List<Event> = withContext(Dispatchers.IO) {
        val ungroupedItems = reviewItemRepository?.getUngroupedItems()
            ?: return@withContext emptyList()
        
        if (ungroupedItems.isEmpty()) {
            return@withContext emptyList()
        }

        val clusters = groupItemsIntoClusters(ungroupedItems)
        if (clusters.isEmpty()) {
            return@withContext emptyList()
        }

        // Resolve target vehicle ID if defaultVehicleId is null
        var targetVehicleId = defaultVehicleId
        if (targetVehicleId == null || targetVehicleId <= 0L) {
            try {
                val vehicles = vehicleRepository?.getAllVehicles()
                if (!vehicles.isNullOrEmpty()) {
                    targetVehicleId = vehicles.first().id
                } else {
                    val defaultVehicle = com.schortgen.vehiclelogai.data.models.Vehicle(
                        nickname = "My Vehicle",
                        make = "",
                        model = "",
                        year = 2024
                    )
                    targetVehicleId = vehicleRepository?.insertVehicle(defaultVehicle)
                }
            } catch (e: Exception) {
                // Ignore failure if vehicle repo unavailable
            }
        }

        if (targetVehicleId == null || targetVehicleId <= 0L) {
            return@withContext emptyList()
        }

        val createdEvents = mutableListOf<Event>()
        val allUpdatedItems = mutableListOf<ReviewItem>()

        for (cluster in clusters) {
            try {
                // Skip auto-creating an event if it's a single photo with no clear event type detected from OCR
                if (cluster.items.size == 1 && cluster.eventType == null) {
                    continue
                }

                val event = createEventFromCluster(cluster, targetVehicleId)
                val eventId = eventRepository.insertEvent(event)
                val eventWithId = event.copy(id = eventId)

                val updatedItems = cluster.items.map { it.copy(eventId = eventId) }
                allUpdatedItems.addAll(updatedItems)

                createdEvents.add(eventWithId)
            } catch (e: Exception) {
                continue
            }
        }

        if (allUpdatedItems.isNotEmpty()) {
            allUpdatedItems.chunked(100).forEach { chunk ->
                reviewItemRepository?.updateAllReviewItems(chunk)
            }
        }

        return@withContext createdEvents
    }
}