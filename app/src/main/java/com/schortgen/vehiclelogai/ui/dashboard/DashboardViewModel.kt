package com.schortgen.vehiclelogai.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.schortgen.vehiclelogai.data.models.Event
import com.schortgen.vehiclelogai.data.models.Vehicle
import com.schortgen.vehiclelogai.data.repository.EventRepository
import com.schortgen.vehiclelogai.data.repository.ReviewItemRepository
import com.schortgen.vehiclelogai.data.repository.VehicleRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

data class DashboardData(
    val totalVehicles: Int = 0,
    val pendingReviewCount: Int = 0,
    val fuelPurchasesThisMonth: Int = 0,
    val fuelCostThisMonth: Double = 0.0,
    val fuelCostThisYear: Double = 0.0,
    val averageFuelCost: Double = 0.0,
    val recentEvents: List<Event> = emptyList(),
    val vehicleSummaries: List<VehicleSummary> = emptyList()
)

data class VehicleSummary(
    val vehicle: Vehicle,
    val lastFuelEvent: Event? = null,
    val totalFuelEvents: Int = 0,
    val pendingReceipts: Int = 0
)

class DashboardViewModel(
    private val vehicleRepository: VehicleRepository,
    private val eventRepository: EventRepository,
    private val reviewItemRepository: ReviewItemRepository
) : ViewModel() {

    private val _dashboardData = MutableStateFlow(DashboardData())
    val dashboardData: StateFlow<DashboardData> = _dashboardData.asStateFlow()

    val vehicles: StateFlow<List<Vehicle>> = vehicleRepository.observeAllVehicles()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        refreshDashboard()
    }

    fun refreshDashboard() {
        viewModelScope.launch {
            try {
                val calendar = Calendar.getInstance()

                // Start of this month
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val startOfMonth = calendar.timeInMillis

                // Start of next month
                calendar.add(Calendar.MONTH, 1)
                val startOfNextMonth = calendar.timeInMillis

                // Start of this year
                calendar.timeInMillis = startOfMonth
                calendar.set(Calendar.MONTH, Calendar.JANUARY)
                val startOfYear = calendar.timeInMillis

                // Start of next year
                calendar.add(Calendar.YEAR, 1)
                val startOfNextYear = calendar.timeInMillis

                // Gather all data
                val allVehicles = vehicleRepository.observeAllVehicles().first()
                val totalVehicles = allVehicles.size
                val pendingCount = reviewItemRepository.countPending()
                val fuelCount = eventRepository.countFuelPurchasesThisMonth(startOfMonth, startOfNextMonth)
                val fuelCostMonth = eventRepository.sumFuelCostThisMonth(startOfMonth, startOfNextMonth)
                val fuelCostYear = eventRepository.sumFuelCostThisYear(startOfYear, startOfNextYear)
                val avgFuelCost = eventRepository.averageFuelCost()
                val recentEvents = eventRepository.getRecentEvents()

                // Build vehicle summaries
                val summaries = allVehicles.map { vehicle ->
                    val lastFuel = eventRepository.getLastFuelEvent(vehicle.id)
                    val totalFuel = eventRepository.countFuelEventsForVehicle(vehicle.id)
                    val pendingReceipts = reviewItemRepository.getAllReviewItems()
                        .count { it.vehicleId == vehicle.id && it.status.name == "PENDING" }
                    VehicleSummary(
                        vehicle = vehicle,
                        lastFuelEvent = lastFuel,
                        totalFuelEvents = totalFuel,
                        pendingReceipts = pendingReceipts
                    )
                }

                _dashboardData.value = DashboardData(
                    totalVehicles = totalVehicles,
                    pendingReviewCount = pendingCount,
                    fuelPurchasesThisMonth = fuelCount,
                    fuelCostThisMonth = fuelCostMonth,
                    fuelCostThisYear = fuelCostYear,
                    averageFuelCost = avgFuelCost,
                    recentEvents = recentEvents,
                    vehicleSummaries = summaries
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

class DashboardViewModelFactory(
    private val vehicleRepository: VehicleRepository,
    private val eventRepository: EventRepository,
    private val reviewItemRepository: ReviewItemRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DashboardViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return DashboardViewModel(vehicleRepository, eventRepository, reviewItemRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}