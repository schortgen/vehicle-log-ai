package com.schortgen.vehiclelogai.ui.eventdetail

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.schortgen.vehiclelogai.data.models.Event
import com.schortgen.vehiclelogai.data.models.EventType
import com.schortgen.vehiclelogai.ui.events.EventViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDetailScreen(
    navController: NavController,
    eventViewModel: EventViewModel,
    eventId: Long
) {
    var event by remember { mutableStateOf<Event?>(null) }
    var isEditing by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()) }

    // Editable fields
    var editNotes by remember { mutableStateOf("") }
    var editOdometer by remember { mutableStateOf("") }
    var editGallons by remember { mutableStateOf("") }
    var editPricePerGallon by remember { mutableStateOf("") }
    var editTotalCost by remember { mutableStateOf("") }
    var editLocation by remember { mutableStateOf("") }
    var editEventDate by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }

    // Load event
    LaunchedEffect(eventId) {
        event = eventViewModel.getEventById(eventId)
        event?.let { e ->
            editNotes = e.notes ?: ""
            editOdometer = e.odometer?.toString() ?: ""
            editGallons = e.gallons?.toString() ?: ""
            editPricePerGallon = e.pricePerGallon?.toString() ?: ""
            editTotalCost = e.totalCost?.toString() ?: ""
            editLocation = e.location ?: ""
            editEventDate = dateFormat.format(Date(e.eventDate))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEditing) "Edit Event" else "Event Details") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (event != null) {
                        if (isEditing) {
                            TextButton(onClick = { isEditing = false }) {
                                Text("Cancel")
                            }
                        } else {
                            TextButton(onClick = { isEditing = true }) {
                                Text("Edit")
                            }
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        if (event == null) {
            Box(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("Event not found", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            val ev = event!!
            val eventTypeLabel = when (ev.eventType) {
                EventType.FUEL -> "⛽ Fuel Purchase"
                EventType.MAINTENANCE -> "🔧 Maintenance"
                EventType.MILEAGE -> "📏 Mileage Reading"
                EventType.INSPECTION -> "🔍 Inspection"
                EventType.REGISTRATION -> "📄 Registration"
                EventType.TIRE_ROTATION -> "🔄 Tire Rotation"
            }

            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .padding(16.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Event type header
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = eventTypeLabel,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = dateFormat.format(Date(ev.eventDate)),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        if (ev.verified) {
                            Text(
                                text = "Verified",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // Details card
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Details",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        if (isEditing) {
                            // Editable fields
                            OutlinedTextField(
                                value = editNotes,
                                onValueChange = { editNotes = it },
                                label = { Text("Notes") },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 2,
                                maxLines = 4
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            if (ev.eventType == EventType.FUEL) {
                                OutlinedTextField(
                                    value = editOdometer,
                                    onValueChange = { editOdometer = it },
                                    label = { Text("Odometer") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = editGallons,
                                    onValueChange = { editGallons = it },
                                    label = { Text("Gallons") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = editPricePerGallon,
                                    onValueChange = { editPricePerGallon = it },
                                    label = { Text("Price Per Gallon") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = editTotalCost,
                                    onValueChange = { editTotalCost = it },
                                    label = { Text("Total Cost") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = editLocation,
                                    onValueChange = { editLocation = it },
                                    label = { Text("Gas Station") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else {
                                OutlinedTextField(
                                    value = editOdometer,
                                    onValueChange = { editOdometer = it },
                                    label = { Text("Odometer (optional)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        } else {
                            // Read-only display
                            EventDetailRow("Notes", ev.notes ?: "None")
                            EventDetailRow("Vehicle ID", ev.vehicleId.toString())
                            if (ev.eventType == EventType.FUEL) {
                                ev.odometer?.let { EventDetailRow("Odometer", "$it mi") }
                                ev.gallons?.let { EventDetailRow("Gallons", "%.2f".format(it)) }
                                ev.pricePerGallon?.let { EventDetailRow("Price/Gal", "$${"%.3f".format(it)}") }
                                ev.totalCost?.let { EventDetailRow("Total Cost", "$${"%.2f".format(it)}") }
                                ev.location?.let { EventDetailRow("Station", it) }
                            } else {
                                ev.odometer?.let { EventDetailRow("Odometer", "$it mi") }
                            }
                            EventDetailRow("Created", dateFormat.format(Date(ev.createdDate)))
                            EventDetailRow("Confidence", ev.confidence?.let { "${(it * 100).toInt()}%" } ?: "N/A")
                            ev.photoPath?.let { EventDetailRow("Photo", "Attached") }
                        }
                    }
                }

                // Traceability card
                if (!ev.photoPath.isNullOrBlank() || !ev.notes.isNullOrBlank()) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Traceability",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            ev.photoPath?.let {
                                Text(
                                    text = "📷 Photo attached",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            ev.notes?.let { notes ->
                                if (notes.contains("OCR:") || notes.contains("Source:") || notes.contains("Confidence:")) {
                                    Text(
                                        text = "📄 Imported from receipt",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                // Action buttons (only show when not editing)
                if (!isEditing) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Actions",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    val updated = ev.copy(
                                        notes = editNotes.ifBlank { null },
                                        odometer = if (ev.eventType == EventType.FUEL || editOdometer.isNotBlank()) editOdometer.toIntOrNull() ?: ev.odometer else ev.odometer,
                                        gallons = editGallons.toDoubleOrNull() ?: ev.gallons,
                                        pricePerGallon = editPricePerGallon.toDoubleOrNull() ?: ev.pricePerGallon,
                                        totalCost = editTotalCost.toDoubleOrNull() ?: ev.totalCost,
                                        location = editLocation.ifBlank { ev.location }
                                    )
                                    eventViewModel.updateEvent(updated)
                                    event = updated
                                    isEditing = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Save Changes")
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    eventViewModel.deleteEvent(ev)
                                    navController.popBackStack()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Text("Delete Event")
                            }
                        }
                    }
                } else {
                    // Save/Cancel when editing
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Button(
                                onClick = {
                                    val updated = ev.copy(
                                        notes = editNotes.ifBlank { null },
                                        odometer = editOdometer.toIntOrNull() ?: ev.odometer,
                                        gallons = if (ev.eventType == EventType.FUEL) editGallons.toDoubleOrNull() ?: ev.gallons else ev.gallons,
                                        pricePerGallon = editPricePerGallon.toDoubleOrNull() ?: ev.pricePerGallon,
                                        totalCost = editTotalCost.toDoubleOrNull() ?: ev.totalCost,
                                        location = editLocation.ifBlank { null }
                                    )
                                    eventViewModel.updateEvent(updated)
                                    event = updated
                                    isEditing = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Save Changes")
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    eventViewModel.deleteEvent(ev)
                                    navController.popBackStack()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Text("Delete Event")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EventDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(0.6f)
        )
    }
}