package com.schortgen.vehiclelogai.ui.vehicle

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.schortgen.vehiclelogai.VehicleLogAIApplication
import com.schortgen.vehiclelogai.data.models.EventType
import com.schortgen.vehiclelogai.data.models.Vehicle
import com.schortgen.vehiclelogai.data.models.calculateMpg
import com.schortgen.vehiclelogai.data.models.displayName
import com.schortgen.vehiclelogai.data.models.getPhotoPaths
import com.schortgen.vehiclelogai.navigation.Screen
import com.schortgen.vehiclelogai.ui.events.EventViewModel
import com.schortgen.vehiclelogai.ui.vehicles.VehicleViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleDetailScreen(
    navController: NavController,
    vehicleViewModel: VehicleViewModel,
    eventViewModel: EventViewModel,
    vehicleId: Long
) {
    val allVehicles by vehicleViewModel.vehicles.collectAsState()
    var fetchedVehicle by remember { mutableStateOf<Vehicle?>(null) }
    val vehicle = remember(allVehicles, fetchedVehicle, vehicleId) {
        allVehicles.find { it.id == vehicleId } ?: fetchedVehicle
    }
    val events by eventViewModel.observeEventsForVehicle(vehicleId).collectAsState(initial = emptyList())
    val sortedEvents = remember(events) {
        events.sortedWith(compareByDescending<com.schortgen.vehiclelogai.data.models.Event> { it.eventDate }.thenByDescending { it.id })
    }

    val context = LocalContext.current
    val app = context.applicationContext as? VehicleLogAIApplication
    val reviewItems by remember(app) {
        app?.reviewItemRepository?.observeAllReviewItems() ?: kotlinx.coroutines.flow.flowOf(emptyList())
    }.collectAsState(initial = emptyList())

    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()) }

    // Fetch the vehicle from the local database when the screen loads
    LaunchedEffect(vehicleId) {
        if (fetchedVehicle == null) {
            fetchedVehicle = vehicleViewModel.getVehicleById(vehicleId)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(vehicle?.displayName ?: "Loading...") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (vehicle == null) {
            Box(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                // Vehicle Info Header
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(text = "Details", style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Year: ${vehicle?.year ?: "N/A"}")
                            Text("Make: ${vehicle?.make ?: "N/A"}")
                            Text("Model: ${vehicle?.model ?: "N/A"}")
                            Text("VIN: ${vehicle?.vin ?: "N/A"}")
                            Text("Mileage: ${vehicle?.currentMileage ?: "N/A"}")
                        }
                    }
                }

                // Action Buttons
                item {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(onClick = { navController.navigate(Screen.ScanPhotos.route) }, modifier = Modifier.weight(1f)) {
                            Text("Scan Photos")
                        }
                        Button(
                            onClick = { navController.navigate(Screen.FuelEntry.createRoute(vehicleId)) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Add Fuel")
                        }
                    }
                }

                item {
                    OutlinedButton(onClick = { navController.navigate(Screen.EditVehicle.createRoute(vehicleId)) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Edit Vehicle")
                    }
                }

                item {
                    HorizontalDivider()
                    Text(
                        text = "Timeline",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (sortedEvents.isEmpty()) {
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "No events yet. Tap \"Add Fuel\" to get started.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    items(sortedEvents) { event ->
                        TimelineCard(
                            event = event,
                            allEvents = sortedEvents,
                            dateFormat = dateFormat,
                            onClick = {
                                navController.navigate(Screen.EventDetail.createRoute(event.id))
                            }
                        )
                    }
                }

                // Bottom spacer
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun TimelineCard(
    event: com.schortgen.vehiclelogai.data.models.Event,
    allEvents: List<com.schortgen.vehiclelogai.data.models.Event> = emptyList(),
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
            // Timeline dot column
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(text = icon, style = MaterialTheme.typography.titleLarge)
                    }
                }
            }

            // Content
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = dateFormat.format(Date(event.eventDate)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Event-specific details
                when (event.eventType) {
                    EventType.FUEL -> {
                        if (event.gallons != null || event.totalCost != null) {
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
                                        Text("$${"%.2f".format(it)}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    }
                                    event.location?.let {
                                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        } else {
                            Text(
                                "Tap to view details",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    EventType.MAINTENANCE -> {
                        Text(
                            event.notes ?: "Tap to view details",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    EventType.MILEAGE -> {
                        event.odometer?.let {
                            Text("${it} mi", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        } ?: Text(
                            "Tap to view details",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    else -> {
                        if (!event.notes.isNullOrBlank()) {
                            Text(
                                event.notes,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2
                            )
                        }
                    }
                }

                val photoPaths = remember(event.photoPath, reviewItems) {
                    val list = mutableListOf<String>()
                    reviewItems.filter { it.eventId == event.id }.forEach { item ->
                        val path = item.photoPath
                        if (!path.isNullOrBlank() && !list.contains(path)) {
                            list.add(path)
                        }
                    }
                    event.getPhotoPaths().forEach { sp ->
                        if (sp.isNotBlank() && !list.contains(sp)) {
                            list.add(sp)
                        }
                    }
                    list
                }
                if (photoPaths.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        photoPaths.take(4).forEach { path ->
                            val data = if (path.startsWith("content://") || path.startsWith("file://")) Uri.parse(path) else path
                            val model = ImageRequest.Builder(LocalContext.current)
                                .data(data)
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