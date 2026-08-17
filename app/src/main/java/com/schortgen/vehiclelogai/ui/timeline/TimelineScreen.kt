package com.schortgen.vehiclelogai.ui.timeline

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.schortgen.vehiclelogai.VehicleLogAIApplication
import com.schortgen.vehiclelogai.data.models.Event
import com.schortgen.vehiclelogai.data.models.EventType
import com.schortgen.vehiclelogai.data.models.ReviewItem
import com.schortgen.vehiclelogai.data.models.ScannedPhoto
import com.schortgen.vehiclelogai.data.models.calculateMpg
import com.schortgen.vehiclelogai.data.models.getPhotoPaths
import com.schortgen.vehiclelogai.data.models.toImageModel
import com.schortgen.vehiclelogai.navigation.Screen
import com.schortgen.vehiclelogai.ui.events.EventViewModel
import com.schortgen.vehiclelogai.ui.vehicles.VehicleViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen(
    navController: NavController,
    eventViewModel: EventViewModel,
    vehicleViewModel: VehicleViewModel
) {
    val events by eventViewModel.observeAllEvents().collectAsState(initial = emptyList())
    val vehicles by vehicleViewModel.vehicles.collectAsState()

    val context = LocalContext.current
    val app = context.applicationContext as? VehicleLogAIApplication
    val reviewItems by remember(app) {
        app?.reviewItemRepository?.observeAllReviewItems() ?: kotlinx.coroutines.flow.flowOf(emptyList())
    }.collectAsState(initial = emptyList())

    val scannedPhotos by remember(app) {
        app?.database?.scannedPhotoDao()?.observeAll() ?: kotlinx.coroutines.flow.flowOf(emptyList())
    }.collectAsState(initial = emptyList())

    var searchQuery by remember { mutableStateOf("") }
    var selectedVehicleId by remember { mutableStateOf<Long?>(null) } // null = All
    var selectedEventType by remember { mutableStateOf<EventType?>(null) } // null = All

    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()) }

    val vehicleMap = remember(vehicles) { vehicles.associateBy { it.id } }

    val filteredEvents = remember(events, searchQuery, selectedVehicleId, selectedEventType, vehicleMap) {
        events.filter { event ->
            val matchesVehicle = selectedVehicleId == null || event.vehicleId == selectedVehicleId
            val matchesType = selectedEventType == null || event.eventType == selectedEventType
            val matchesSearch = searchQuery.isBlank() || run {
                val q = searchQuery.trim().lowercase()
                val vehName = event.vehicleId?.let { vehicleMap[it]?.nickname }?.lowercase() ?: ""
                val loc = event.location?.lowercase() ?: ""
                val notes = event.notes?.lowercase() ?: ""
                val typeStr = event.eventType.name.lowercase()
                vehName.contains(q) || loc.contains(q) || notes.contains(q) || typeStr.contains(q)
            }
            matchesVehicle && matchesType && matchesSearch
        }.sortedWith(compareByDescending<Event> { it.eventDate }.thenByDescending { it.id })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Vehicle Timeline", fontWeight = FontWeight.Bold) }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    val defaultVehId = selectedVehicleId ?: vehicles.firstOrNull()?.id ?: 1L
                    navController.navigate(Screen.FuelEntry.createRoute(defaultVehId))
                }
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Fuel")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search location, notes, vehicle...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            // Vehicle Filter Chips
            if (vehicles.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = selectedVehicleId == null,
                            onClick = { selectedVehicleId = null },
                            label = { Text("All Vehicles") }
                        )
                    }
                    items(vehicles) { veh ->
                        val name = veh.nickname ?: "${veh.year ?: ""} ${veh.make ?: ""} ${veh.model ?: ""}".trim().ifEmpty { "Vehicle #${veh.id}" }
                        FilterChip(
                            selected = selectedVehicleId == veh.id,
                            onClick = {
                                selectedVehicleId = if (selectedVehicleId == veh.id) null else veh.id
                            },
                            label = { Text(name) }
                        )
                    }
                }
            }

            // Event Type Filter Chips
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                item {
                    FilterChip(
                        selected = selectedEventType == null,
                        onClick = { selectedEventType = null },
                        label = { Text("All Types") }
                    )
                }
                items(EventType.values()) { type ->
                    FilterChip(
                        selected = selectedEventType == type,
                        onClick = {
                            selectedEventType = if (selectedEventType == type) null else type
                        },
                        label = { Text(type.name.lowercase().replaceFirstChar { it.uppercase() }) }
                    )
                }
            }

            // Events List
            if (filteredEvents.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .padding(24.dp)
                                .fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                "No Timeline Events Found",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                if (events.isEmpty())
                                    "When you save fuel receipts, maintenance logs, or manual fuel entries, they will appear here chronologically."
                                else
                                    "No events match your current filter or search criteria.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (events.isEmpty()) {
                                Button(
                                    onClick = {
                                        val firstVehId = vehicles.firstOrNull()?.id ?: 1L
                                        navController.navigate(Screen.FuelEntry.createRoute(firstVehId))
                                    }
                                ) {
                                    Text("Add Fuel Event")
                                }
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 80.dp)
                ) {
                    items(filteredEvents) { event ->
                        val vehicleName = event.vehicleId?.let { id ->
                            vehicleMap[id]?.let { v ->
                                v.nickname ?: "${v.year ?: ""} ${v.make ?: ""} ${v.model ?: ""}".trim().ifEmpty { "Vehicle #${v.id}" }
                            }
                        }
                        TimelineEventCard(
                            event = event,
                            allEvents = events,
                            reviewItems = reviewItems,
                            scannedPhotos = scannedPhotos,
                            vehicleName = vehicleName,
                            dateFormat = dateFormat,
                            onClick = {
                                navController.navigate(Screen.EventDetail.createRoute(event.id))
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineEventCard(
    event: Event,
    allEvents: List<Event> = emptyList(),
    reviewItems: List<ReviewItem> = emptyList(),
    scannedPhotos: List<ScannedPhoto> = emptyList(),
    vehicleName: String?,
    dateFormat: SimpleDateFormat,
    onClick: () -> Unit
) {
    val (icon, label) = when (event.eventType) {
        EventType.FUEL -> "⛽" to "Fuel"
        EventType.MAINTENANCE -> "🔧" to "Maintenance"
        EventType.MILEAGE -> "📏" to "Mileage"
        EventType.INSPECTION -> "🔍" to "Inspection"
        EventType.REGISTRATION -> "📄" to "Registration"
        EventType.TIRE_ROTATION -> "🔄" to "Tire Rotation"
    }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Icon box
            Surface(
                modifier = Modifier.size(44.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(text = icon, style = MaterialTheme.typography.titleLarge)
                }
            }

            // Details
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        if (!vehicleName.isNullOrBlank()) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = vehicleName,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Text(
                        text = dateFormat.format(Date(event.eventDate)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                when (event.eventType) {
                    EventType.FUEL -> {
                        val mpg = event.calculateMpg(allEvents)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                event.gallons?.let {
                                    Text("${"%.2f".format(it)} gal", style = MaterialTheme.typography.bodySmall)
                                }
                                event.tripDistance?.let {
                                    Text("${"%.1f".format(it)} trip mi", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                mpg?.let {
                                    Text("${"%.2f".format(it)} MPG", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                                }
                                event.odometer?.let {
                                    Text("${it} mi", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                event.totalCost?.let {
                                    Text(
                                        "$${"%.2f".format(it)}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                event.location?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    else -> {
                        if (!event.notes.isNullOrBlank()) {
                            Text(
                                event.notes,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2
                            )
                        } else {
                            Text(
                                "Tap to view details",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                val photoPaths = remember(event, reviewItems, scannedPhotos) {
                    val list = mutableListOf<String>()
                    reviewItems.filter { it.eventId == event.id }.forEach { item ->
                        val path = item.photoPath
                        if (!path.isNullOrBlank()) {
                            val clean = path.trim().removePrefix("[").removeSuffix("]").replace("\"", "").replace("'", "")
                            clean.split(',', '|', '\n', ';').map { it.trim() }.filter { it.isNotBlank() }.forEach { p ->
                                if (!list.contains(p)) list.add(p)
                            }
                        }
                    }
                    scannedPhotos.filter { it.eventId == event.id }.forEach { sp ->
                        val uri = sp.uri
                        if (uri.isNotBlank()) {
                            val clean = uri.trim().removePrefix("[").removeSuffix("]").replace("\"", "").replace("'", "")
                            clean.split(',', '|', '\n', ';').map { it.trim() }.filter { it.isNotBlank() }.forEach { u ->
                                if (!list.contains(u)) list.add(u)
                            }
                        }
                    }
                    event.getPhotoPaths().forEach { sp ->
                        val trimmed = sp.trim()
                        if (trimmed.isNotBlank() && !list.contains(trimmed)) {
                            list.add(trimmed)
                        }
                    }
                    list
                }
                if (photoPaths.isNotEmpty()) {
                    val currentCtx = LocalContext.current
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        photoPaths.take(4).forEach { path ->
                            val model = ImageRequest.Builder(currentCtx)
                                .data(path.toImageModel(currentCtx))
                                .crossfade(true)
                                .build()
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(6.dp))
                            ) {
                                AsyncImage(
                                    model = model,
                                    contentDescription = "Event photo",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                        if (photoPaths.size > 4) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = "+${photoPaths.size - 4}",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
