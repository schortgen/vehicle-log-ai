package com.schortgen.vehiclelogai.ui.reviewqueue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.schortgen.vehiclelogai.data.models.Event
import com.schortgen.vehiclelogai.data.models.FuelPurchaseCandidate
import com.schortgen.vehiclelogai.data.models.ProcessingStatus
import com.schortgen.vehiclelogai.data.models.ReviewItem
import com.schortgen.vehiclelogai.data.models.Vehicle
import com.schortgen.vehiclelogai.data.repository.EventRepository
import com.schortgen.vehiclelogai.data.repository.ReviewItemRepository
import com.schortgen.vehiclelogai.data.repository.VehicleRepository
import com.schortgen.vehiclelogai.service.CandidateMapper
import com.schortgen.vehiclelogai.service.EventGroupingService
import com.schortgen.vehiclelogai.service.MlKitOcrService
import com.schortgen.vehiclelogai.service.ReceiptParserService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONObject

sealed class ReviewQueueItem {
    data class Single(val item: ReviewItem) : ReviewQueueItem()
    data class Grouped(val event: Event, val items: List<ReviewItem>) : ReviewQueueItem()
}

class ReviewQueueViewModel(
    private val reviewItemRepository: ReviewItemRepository,
    private val vehicleRepository: VehicleRepository? = null,
    private val eventRepository: EventRepository? = null,
    private val mlKitOcrService: MlKitOcrService? = null,
    private val receiptParserService: ReceiptParserService? = null,
    private val eventGroupingService: EventGroupingService? = null
) : ViewModel() {

    private val _ocrProcessingIds = MutableStateFlow<Set<Long>>(emptySet())
    val ocrProcessingIds: StateFlow<Set<Long>> = _ocrProcessingIds.asStateFlow()

    private val _saveErrors = MutableStateFlow<String?>(null)
    val saveErrors: StateFlow<String?> = _saveErrors.asStateFlow()

    val reviewItems: StateFlow<List<ReviewItem>> = reviewItemRepository.observeAllReviewItems()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val vehicles: StateFlow<List<Vehicle>>? = vehicleRepository?.observeAllVehicles()
        ?.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val events: StateFlow<List<Event>> = (eventRepository?.observeAllEvents()
        ?: kotlinx.coroutines.flow.flowOf(emptyList()))
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val reviewQueueItems: StateFlow<List<ReviewQueueItem>> = combine(
        reviewItems,
        events
    ) { items, allEvents ->
        // Find all Events that are NOT verified (temporary groupings)
        val unverifiedEvents = allEvents.filter { !it.verified }

        // Create Grouped representation
        val groupedList = unverifiedEvents.mapNotNull { event ->
            val eventItems = items.filter { it.eventId == event.id }
            if (eventItems.isEmpty()) null else ReviewQueueItem.Grouped(event, eventItems)
        }

        // Find standalone items (not complete and not grouped)
        val singleList = items.filter { it.status != ProcessingStatus.COMPLETE && it.eventId == null }
            .map { ReviewQueueItem.Single(it) }

        (groupedList + singleList).sortedByDescending {
            when (it) {
                is ReviewQueueItem.Single -> it.item.captureDate
                is ReviewQueueItem.Grouped -> it.event.eventDate
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    init {
        runEventGrouping()
    }

    fun runEventGrouping(defaultVehicleId: Long? = null) {
        viewModelScope.launch {
            eventGroupingService?.processAndGroupEvents(defaultVehicleId)
        }
    }

    fun observeAll(): Flow<List<ReviewItem>> {
        return reviewItemRepository.observeAllReviewItems()
    }

    fun getReviewItemById(id: Long): ReviewItem? {
        return reviewItems.value.find { it.id == id }
    }

    suspend fun getReviewItemByIdSuspend(id: Long): ReviewItem? {
        return reviewItemRepository.getReviewItemById(id)
    }

    suspend fun getVehicleById(id: Long): Vehicle? {
        return vehicleRepository?.getVehicleById(id)
    }

    fun updateStatus(item: ReviewItem, newStatus: ProcessingStatus) {
        viewModelScope.launch {
            reviewItemRepository.updateReviewItem(item.copy(status = newStatus))
        }
    }

    fun updateItem(item: ReviewItem) {
        viewModelScope.launch {
            reviewItemRepository.updateReviewItem(item)
        }
    }

    fun deleteItem(item: ReviewItem) {
        viewModelScope.launch {
            reviewItemRepository.deleteReviewItem(item)
        }
    }

    fun deleteGroup(event: Event, items: List<ReviewItem>) {
        viewModelScope.launch {
            eventRepository?.deleteEvent(event)
            items.forEach { reviewItemRepository.deleteReviewItem(it) }
        }
    }

    fun insertItem(item: ReviewItem) {
        viewModelScope.launch {
            reviewItemRepository.insertReviewItem(item)
            runEventGrouping()
        }
    }

    fun removeFromGroup(item: ReviewItem) {
        viewModelScope.launch {
            val eventId = item.eventId ?: return@launch
            reviewItemRepository.updateReviewItem(item.copy(eventId = null))

            val remaining = reviewItemRepository.getAllReviewItems().filter { it.eventId == eventId && it.id != item.id }
            if (remaining.isEmpty()) {
                val event = eventRepository?.getEventById(eventId)
                if (event != null && !event.verified) {
                    eventRepository?.deleteEvent(event)
                }
            }
        }
    }

    fun addPhotoToGroup(eventId: Long, item: ReviewItem) {
        viewModelScope.launch {
            reviewItemRepository.updateReviewItem(item.copy(eventId = eventId))
        }
    }

    fun groupExistingItemWithActiveItem(activeItem: ReviewItem, itemToGroup: ReviewItem) {
        viewModelScope.launch {
            var eventId = activeItem.eventId
            if (eventId == null || eventId <= 0L) {
                val newEvent = Event(
                    vehicleId = activeItem.vehicleId ?: 1L,
                    eventType = com.schortgen.vehiclelogai.data.models.EventType.FUEL,
                    eventDate = activeItem.captureDate,
                    photoPath = activeItem.photoPath,
                    verified = false
                )
                val insertedId = eventRepository?.insertEvent(newEvent)
                if (insertedId != null && insertedId > 0L) {
                    eventId = insertedId
                    reviewItemRepository.updateReviewItem(activeItem.copy(eventId = eventId))
                }
            }

            if (eventId != null && eventId > 0L) {
                reviewItemRepository.updateReviewItem(itemToGroup.copy(eventId = eventId))
            }
        }
    }

    fun addPhotoUriToGroup(targetEventId: Long?, targetItem: ReviewItem?, photoPathUri: String) {
        viewModelScope.launch {
            var eventId = targetEventId
            if (eventId == null || eventId <= 0L) {
                if (targetItem != null) {
                    val targetItemEventId = targetItem.eventId
                    if (targetItemEventId != null && targetItemEventId > 0L) {
                        eventId = targetItemEventId
                    } else {
                        val newEvent = Event(
                            vehicleId = targetItem.vehicleId ?: 1L,
                            eventType = com.schortgen.vehiclelogai.data.models.EventType.FUEL,
                            eventDate = targetItem.captureDate,
                            photoPath = targetItem.photoPath,
                            verified = false
                        )
                        val insertedId = eventRepository?.insertEvent(newEvent)
                        if (insertedId != null && insertedId > 0L) {
                            eventId = insertedId
                            reviewItemRepository.updateReviewItem(targetItem.copy(eventId = eventId))
                        }
                    }
                }
            }

            val newItem = ReviewItem(
                photoPath = photoPathUri,
                captureDate = System.currentTimeMillis(),
                eventId = eventId,
                status = ProcessingStatus.PENDING,
                reason = "Added photo"
            )
            reviewItemRepository.insertReviewItem(newItem)
        }
    }

    fun clearSaveError() {
        _saveErrors.value = null
    }

    fun parseCandidateFromItem(item: ReviewItem): FuelPurchaseCandidate? {
        val json = item.parsedData ?: return null
        return try {
            val obj = JSONObject(json)
            FuelPurchaseCandidate(
                stationName = obj.optString("stationName", "").ifEmpty { null },
                stationNameConfidence = obj.optDouble("stationNameConfidence", 0.0).toFloat(),
                purchaseDate = obj.optString("purchaseDate", "").ifEmpty { null },
                purchaseDateConfidence = obj.optDouble("purchaseDateConfidence", 0.0).toFloat(),
                gallons = if (obj.has("gallons") && !obj.isNull("gallons")) obj.getDouble("gallons") else null,
                gallonsConfidence = obj.optDouble("gallonsConfidence", 0.0).toFloat(),
                pricePerGallon = if (obj.has("pricePerGallon") && !obj.isNull("pricePerGallon")) obj.getDouble("pricePerGallon") else null,
                pricePerGallonConfidence = obj.optDouble("pricePerGallonConfidence", 0.0).toFloat(),
                totalCost = if (obj.has("totalCost") && !obj.isNull("totalCost")) obj.getDouble("totalCost") else null,
                totalCostConfidence = obj.optDouble("totalCostConfidence", 0.0).toFloat(),
                odometer = if (obj.has("odometer") && !obj.isNull("odometer")) obj.getInt("odometer") else null,
                odometerConfidence = obj.optDouble("odometerConfidence", 0.0).toFloat()
            )
        } catch (e: Exception) {
            null
        }
    }

    fun saveAsFuelEvent(
        reviewItemId: Long,
        vehicleId: Long,
        editedCandidate: FuelPurchaseCandidate
    ) {
        _saveErrors.value = null
        viewModelScope.launch {
            try {
                val item = reviewItemRepository.getReviewItemById(reviewItemId) ?: run {
                    _saveErrors.value = "Review item not found"
                    return@launch
                }

                val vehicle = vehicleRepository?.getVehicleById(vehicleId) ?: run {
                    _saveErrors.value = "Please select a vehicle"
                    return@launch
                }

                val missingFields = mutableListOf<String>()
                if (editedCandidate.gallons == null) missingFields.add("Gallons")
                if (editedCandidate.totalCost == null) missingFields.add("Total Cost")
                if (editedCandidate.odometer == null) missingFields.add("Odometer")

                if (missingFields.isNotEmpty()) {
                    _saveErrors.value = "Required fields missing: ${missingFields.joinToString(", ")}"
                    return@launch
                }

                if (editedCandidate.gallons != null && editedCandidate.gallons <= 0) {
                    _saveErrors.value = "Gallons must be greater than 0"
                    return@launch
                }
                if (editedCandidate.totalCost != null && editedCandidate.totalCost <= 0) {
                    _saveErrors.value = "Total Cost must be greater than 0"
                    return@launch
                }
                if (editedCandidate.odometer != null && editedCandidate.odometer < 0) {
                    _saveErrors.value = "Odometer cannot be negative"
                    return@launch
                }

                val event = CandidateMapper.toEvent(editedCandidate, vehicleId, item)

                eventRepository?.insertEvent(event) ?: run {
                    _saveErrors.value = "Database error: Event repository not available"
                    return@launch
                }

                reviewItemRepository.updateReviewItem(
                    item.copy(status = ProcessingStatus.COMPLETE, vehicleId = vehicleId)
                )
            } catch (e: Exception) {
                _saveErrors.value = "Error saving fuel event: ${e.message}"
            }
        }
    }

    fun saveGroupedAsFuelEvent(
        eventId: Long,
        vehicleId: Long,
        editedCandidate: FuelPurchaseCandidate
    ) {
        _saveErrors.value = null
        viewModelScope.launch {
            try {
                val vehicle = vehicleRepository?.getVehicleById(vehicleId) ?: run {
                    _saveErrors.value = "Please select a vehicle"
                    return@launch
                }

                val existingEvent = eventRepository?.getEventById(eventId) ?: run {
                    _saveErrors.value = "Event not found"
                    return@launch
                }

                val missingFields = mutableListOf<String>()
                if (editedCandidate.gallons == null) missingFields.add("Gallons")
                if (editedCandidate.totalCost == null) missingFields.add("Total Cost")
                if (editedCandidate.odometer == null) missingFields.add("Odometer")

                if (missingFields.isNotEmpty()) {
                    _saveErrors.value = "Required fields missing: ${missingFields.joinToString(", ")}"
                    return@launch
                }

                // Update the event fields and mark as verified
                val updatedEvent = existingEvent.copy(
                    vehicleId = vehicleId,
                    verified = true,
                    gallons = editedCandidate.gallons,
                    totalCost = editedCandidate.totalCost,
                    odometer = editedCandidate.odometer,
                    pricePerGallon = editedCandidate.pricePerGallon,
                    location = editedCandidate.stationName
                )

                eventRepository?.updateEvent(updatedEvent)

                // Update all associated review items status to COMPLETE
                val associatedItems = reviewItems.value.filter { it.eventId == eventId }
                val updatedItems = associatedItems.map {
                    it.copy(status = ProcessingStatus.COMPLETE, vehicleId = vehicleId)
                }
                reviewItemRepository.updateAllReviewItems(updatedItems)

            } catch (e: Exception) {
                _saveErrors.value = "Error saving grouped event: ${e.message}"
            }
        }
    }

    fun processOcr(reviewItemId: Long) {
        val ocrService = mlKitOcrService ?: return
        val parser = receiptParserService ?: return
        viewModelScope.launch {
            val item = reviewItemRepository.getReviewItemById(reviewItemId) ?: return@launch
            val photoPath = item.photoPath ?: return@launch

            _ocrProcessingIds.value = _ocrProcessingIds.value + reviewItemId

            try {
                val ocrResult = ocrService.recognizeImage(photoPath)
                if (ocrResult != null) {
                    var updatedItem = item.copy(
                        ocrText = ocrResult.rawText,
                        ocrProcessingTimeMs = ocrResult.processingTimeMs,
                        status = ProcessingStatus.NEEDS_REVIEW
                    )

                    if (ocrResult.rawText.isNotBlank()) {
                        val candidate = parser.parse(ocrResult.rawText)
                        updatedItem = updatedItem.copy(
                            parsedData = serializeCandidate(candidate)
                        )
                    }

                    reviewItemRepository.updateReviewItem(updatedItem)
                }
            } finally {
                _ocrProcessingIds.value = _ocrProcessingIds.value - reviewItemId
            }
        }
    }

    private fun serializeCandidate(candidate: FuelPurchaseCandidate): String {
        val obj = JSONObject()
        candidate.stationName?.let { obj.put("stationName", it) }
        obj.put("stationNameConfidence", candidate.stationNameConfidence.toDouble())
        candidate.purchaseDate?.let { obj.put("purchaseDate", it) }
        obj.put("purchaseDateConfidence", candidate.purchaseDateConfidence.toDouble())
        candidate.gallons?.let { obj.put("gallons", it) }
        obj.put("gallonsConfidence", candidate.gallonsConfidence.toDouble())
        candidate.pricePerGallon?.let { obj.put("pricePerGallon", it) }
        obj.put("pricePerGallonConfidence", candidate.pricePerGallonConfidence.toDouble())
        candidate.totalCost?.let { obj.put("totalCost", it) }
        obj.put("totalCostConfidence", candidate.totalCostConfidence.toDouble())
        candidate.odometer?.let { obj.put("odometer", it) }
        obj.put("odometerConfidence", candidate.odometerConfidence.toDouble())
        obj.put("overallConfidence", candidate.overallConfidence.toDouble())
        return obj.toString()
    }

    fun seedMockData() {
        viewModelScope.launch {
            val existing = reviewItemRepository.getAllReviewItems()
            if (existing.isEmpty()) {
                val now = System.currentTimeMillis()
                reviewItemRepository.insertReviewItem(
                    ReviewItem(
                        photoPath = null,
                        captureDate = now - 86400000L * 3,
                        vehicleId = null,
                        status = ProcessingStatus.PENDING,
                        reason = "Fuel receipt from Shell"
                    )
                )
                reviewItemRepository.insertReviewItem(
                    ReviewItem(
                        photoPath = null,
                        captureDate = now - 86400000L * 2,
                        vehicleId = null,
                        status = ProcessingStatus.PROCESSING,
                        reason = "Maintenance invoice"
                    )
                )
                reviewItemRepository.insertReviewItem(
                    ReviewItem(
                        photoPath = null,
                        captureDate = now - 86400000L,
                        vehicleId = null,
                        status = ProcessingStatus.NEEDS_REVIEW,
                        reason = "Odometer reading - unclear digits",
                        confidence = 0.65f
                    )
                )
                reviewItemRepository.insertReviewItem(
                    ReviewItem(
                        photoPath = null,
                        captureDate = now,
                        vehicleId = null,
                        status = ProcessingStatus.COMPLETE,
                        reason = "Tire rotation receipt",
                        confidence = 0.92f
                    )
                )
            }
        }
    }
}

class ReviewQueueViewModelFactory(
    private val repository: ReviewItemRepository,
    private val vehicleRepository: VehicleRepository? = null,
    private val eventRepository: EventRepository? = null,
    private val mlKitOcrService: MlKitOcrService? = null,
    private val receiptParserService: ReceiptParserService? = null,
    private val eventGroupingService: EventGroupingService? = null
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ReviewQueueViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ReviewQueueViewModel(
                repository,
                vehicleRepository,
                eventRepository,
                mlKitOcrService,
                receiptParserService,
                eventGroupingService
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}