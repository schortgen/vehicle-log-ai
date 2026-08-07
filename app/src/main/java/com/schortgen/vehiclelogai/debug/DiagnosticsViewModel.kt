package com.schortgen.vehiclelogai.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.schortgen.vehiclelogai.data.models.Event
import com.schortgen.vehiclelogai.data.models.EventType
import com.schortgen.vehiclelogai.data.models.ProcessingStatus
import com.schortgen.vehiclelogai.data.models.ReviewItem
import com.schortgen.vehiclelogai.data.models.Vehicle
import com.schortgen.vehiclelogai.data.repository.EventRepository
import com.schortgen.vehiclelogai.data.repository.PhotoScannerRepository
import com.schortgen.vehiclelogai.data.repository.ReviewItemRepository
import com.schortgen.vehiclelogai.data.repository.VehicleRepository
import com.schortgen.vehiclelogai.ui.reviewqueue.ReviewQueueViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class DatabaseStats(
    val vehicleCount: Int = 0,
    val eventCount: Int = 0,
    val reviewItemCount: Int = 0,
    val pendingReviewCount: Int = 0,
    val scannedPhotoCount: Int = 0
)

data class BuildInfo(
    val appVersion: String,
    val versionCode: Int,
    val dbSchemaVersion: Int,
    val debugBuild: Boolean
)

data class DiagnosticsUiState(
    val build: BuildInfo = BuildInfo("?", 0, 0, false),
    val database: DatabaseStats = DatabaseStats(),
    val stats: DiagnosticStats = DiagnosticStats(0, 0, 0, 0, 0.0, 0, 0, 0, 0, 0, 0, 0),
    val recentLogLines: List<String> = emptyList(),
    val statusMessage: String? = null,
    val isBusy: Boolean = false
)

/**
 * ViewModel for the hidden Debug screen. Exposes DB statistics, in-memory
 * diagnostic stats, and provides seed/clear/export actions.
 *
 * This ViewModel is only constructed behind a [com.schortgen.vehiclelogai.BuildConfig.DEBUG]
 * gate at the call site, so it is never present in release builds.
 */
class DiagnosticsViewModel(
    private val vehicleRepository: VehicleRepository,
    private val eventRepository: EventRepository,
    private val reviewItemRepository: ReviewItemRepository,
    private val photoScannerRepository: PhotoScannerRepository,
    private val reviewQueueViewModel: ReviewQueueViewModel,
    private val dbSchemaVersion: Int
) : ViewModel() {

    private val _state = MutableStateFlow(DiagnosticsUiState())
    val state: StateFlow<DiagnosticsUiState> = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            val db = withContext(Dispatchers.IO) { gatherDatabaseStats() }
            _state.value = _state.value.copy(
                database = db,
                stats = DiagnosticLogger.stats(),
                recentLogLines = DiagnosticLogger.snapshotLines().takeLast(40)
            )
        }
    }

    fun updateBuildInfo(build: BuildInfo) {
        _state.value = _state.value.copy(build = build)
    }

    fun seedSampleData() {
        if (_state.value.isBusy) return
        _state.value = _state.value.copy(isBusy = true, statusMessage = "Seeding sample data…")
        viewModelScope.launch {
            val inserted = withContext(Dispatchers.IO) {
                var count = 0
                val sampleVehicles = listOf(
                    Vehicle(
                        nickname = "Daily Driver",
                        year = 2021,
                        make = "Toyota",
                        model = "RAV4",
                        licensePlate = "ABC-1234",
                        vin = "JTMRWRFV0MD123456",
                        currentMileage = 32_500
                    ),
                    Vehicle(
                        nickname = "Weekend Truck",
                        year = 2018,
                        make = "Ford",
                        model = "F-150",
                        licensePlate = "TRK-9876",
                        vin = "1FTEW1E50JFA12345",
                        currentMileage = 71_240
                    )
                )
                sampleVehicles.forEach {
                    val id = vehicleRepository.insertVehicle(it)
                    if (id > 0) count++
                }

                val now = System.currentTimeMillis()
                val sampleEvents = listOf(
                    Event(
                        vehicleId = 1,
                        eventType = EventType.FUEL,
                        eventDate = now - 86_400_000L * 3,
                        totalCost = 48.20,
                        gallons = 11.6,
                        pricePerGallon = 4.15,
                        odometer = 32_300,
                        location = "Shell"
                    ),
                    Event(
                        vehicleId = 1,
                        eventType = EventType.MAINTENANCE,
                        eventDate = now - 86_400_000L * 14,
                        notes = "Oil change"
                    )
                )
                sampleEvents.forEach {
                    val id = eventRepository.insertEvent(it)
                    if (id > 0) count++
                }

                val sampleReviews = listOf(
                    ReviewItem(
                        photoPath = null,
                        captureDate = now - 86_400_000L,
                        status = ProcessingStatus.PENDING,
                        reason = "[seed] Fuel receipt"
                    ),
                    ReviewItem(
                        photoPath = null,
                        captureDate = now,
                        status = ProcessingStatus.NEEDS_REVIEW,
                        reason = "[seed] Tire rotation",
                        confidence = 0.7f
                    )
                )
                sampleReviews.forEach {
                    val id = reviewItemRepository.insertReviewItem(it)
                    if (id > 0) count++
                }
                count
            }
            DiagnosticLogger.recordSeed(inserted)
            DiagnosticLogger.i("Debug", "seedSampleData inserted=$inserted")
            _state.value = _state.value.copy(
                isBusy = false,
                statusMessage = "Seeded $inserted sample row(s)."
            )
            refresh()
        }
    }

    fun clearTestData() {
        if (_state.value.isBusy) return
        _state.value = _state.value.copy(isBusy = true, statusMessage = "Clearing test data…")
        viewModelScope.launch {
            val cleared = withContext(Dispatchers.IO) {
                var count = 0
                val reviews = reviewItemRepository.getAllReviewItems()
                reviews.forEach {
                    reviewItemRepository.deleteReviewItem(it)
                    count++
                }
                count
            }
            DiagnosticLogger.recordClear(cleared)
            DiagnosticLogger.w("Debug", "clearTestData removed $cleared review item(s)")
            _state.value = _state.value.copy(
                isBusy = false,
                statusMessage = "Cleared $cleared review item(s). Full DB wipe is not yet supported."
            )
            refresh()
        }
    }

    fun exportLogs(): File? {
        DiagnosticLogger.i("Debug", "exportLogs requested")
        val file = DiagnosticLogger.exportToFile()
        _state.value = _state.value.copy(
            statusMessage = if (file != null) "Exported to ${file.absolutePath}" else "Export unavailable in release builds."
        )
        return file
    }

    fun clearStatus() {
        _state.value = _state.value.copy(statusMessage = null)
    }

    private suspend fun gatherDatabaseStats(): DatabaseStats = withContext(Dispatchers.IO) {
        DatabaseStats(
            vehicleCount = vehicleRepository.countVehicles(),
            eventCount = eventRepository.countEvents(),
            reviewItemCount = reviewItemRepository.getAllReviewItems().size,
            pendingReviewCount = reviewItemRepository.countPending(),
            scannedPhotoCount = photoScannerRepository.getImportedCount()
        )
    }
}

class DiagnosticsViewModelFactory(
    private val vehicleRepository: VehicleRepository,
    private val eventRepository: EventRepository,
    private val reviewItemRepository: ReviewItemRepository,
    private val photoScannerRepository: PhotoScannerRepository,
    private val reviewQueueViewModel: ReviewQueueViewModel,
    private val dbSchemaVersion: Int
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DiagnosticsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return DiagnosticsViewModel(
                vehicleRepository = vehicleRepository,
                eventRepository = eventRepository,
                reviewItemRepository = reviewItemRepository,
                photoScannerRepository = photoScannerRepository,
                reviewQueueViewModel = reviewQueueViewModel,
                dbSchemaVersion = dbSchemaVersion
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
