package com.schortgen.vehiclelogai.ui.eventdetail

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.schortgen.vehiclelogai.VehicleLogAIApplication
import com.schortgen.vehiclelogai.data.models.Event
import com.schortgen.vehiclelogai.data.models.EventType
import com.schortgen.vehiclelogai.data.models.calculateMpg
import com.schortgen.vehiclelogai.ui.events.EventViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDetailScreen(
    navController: NavController,
    eventViewModel: EventViewModel,
    eventId: Long
) {
    var event by remember { mutableStateOf<Event?>(null) }
    var isEditing by remember { mutableStateOf(false) }
    var showImageDialog by remember { mutableStateOf(false) }
    var selectedPhotoIndex by remember { mutableIntStateOf(0) }
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()) }

    val context = LocalContext.current
    val app = context.applicationContext as? VehicleLogAIApplication
    val reviewItems by remember(eventId, app) {
        app?.reviewItemRepository?.observeByEvent(eventId) ?: kotlinx.coroutines.flow.flowOf(emptyList())
    }.collectAsState(initial = emptyList())

    val photoPaths = remember(event, reviewItems) {
        val list = mutableListOf<String>()
        reviewItems.forEach { item ->
            val path = item.photoPath
            if (!path.isNullOrBlank() && !list.contains(path)) {
                list.add(path)
            }
        }
        event?.photoPath?.let { path ->
            if (path.isNotBlank()) {
                val splitPaths = path.split(',', '|', '\n').map { it.trim() }.filter { it.isNotBlank() }
                splitPaths.forEach { sp ->
                    if (!list.contains(sp)) {
                        list.add(sp)
                    }
                }
            }
        }
        list
    }

    // Editable fields
    var editNotes by remember { mutableStateOf("") }
    var editOdometer by remember { mutableStateOf("") }
    var editTripDistance by remember { mutableStateOf("") }
    var editGallons by remember { mutableStateOf("") }
    var editPricePerGallon by remember { mutableStateOf("") }
    var editTotalCost by remember { mutableStateOf("") }
    var editLocation by remember { mutableStateOf("") }
    var selectedDateMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val context = LocalContext.current

    val showDatePickerDialog = {
        val cal = Calendar.getInstance().apply {
            timeInMillis = if (selectedDateMillis > 0) selectedDateMillis else System.currentTimeMillis()
        }
        android.app.DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val newCal = Calendar.getInstance().apply {
                    timeInMillis = selectedDateMillis
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, dayOfMonth)
                }
                selectedDateMillis = newCal.timeInMillis
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    var pendingEventToUpdate by remember { mutableStateOf<Event?>(null) }
    var warningMessageToDisplay by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun performUpdate(updated: Event) {
        eventViewModel.updateEvent(updated)
        event = updated
        isEditing = false
    }

    fun saveUpdatedEvent(updated: Event) {
        scope.launch {
            val warning = eventViewModel.checkPreviousEventWarnings(updated)
            if (warning != null) {
                pendingEventToUpdate = updated
                warningMessageToDisplay = warning
            } else {
                performUpdate(updated)
            }
        }
    }

    if (warningMessageToDisplay != null && pendingEventToUpdate != null) {
        AlertDialog(
            onDismissRequest = {
                warningMessageToDisplay = null
                pendingEventToUpdate = null
            },
            title = { Text("Warning") },
            text = { Text(warningMessageToDisplay!!) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val evt = pendingEventToUpdate
                        warningMessageToDisplay = null
                        pendingEventToUpdate = null
                        if (evt != null) {
                            performUpdate(evt)
                        }
                    }
                ) {
                    Text("Save Anyway")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        warningMessageToDisplay = null
                        pendingEventToUpdate = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // Load event
    LaunchedEffect(eventId) {
        event = eventViewModel.getEventById(eventId)
        event?.let { e ->
            editNotes = e.notes ?: ""
            editOdometer = e.odometer?.toString() ?: ""
            editTripDistance = e.tripDistance?.toString() ?: ""
            editGallons = e.gallons?.toString() ?: ""
            editPricePerGallon = e.pricePerGallon?.toString() ?: ""
            editTotalCost = e.totalCost?.toString() ?: ""
            editLocation = e.location ?: ""
            selectedDateMillis = e.eventDate
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
            val ev = event ?: return@Scaffold
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
                            // Date selector
                            OutlinedCard(
                                onClick = { showDatePickerDialog() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            "Event / Purchase Date",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            dateFormat.format(Date(selectedDateMillis)),
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                    }
                                    Text(
                                        "Change Date",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))

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
                                    value = editTripDistance,
                                    onValueChange = { editTripDistance = it },
                                    label = { Text("Trip Distance") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
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
                                ev.tripDistance?.let { EventDetailRow("Trip Distance", "%.1f mi".format(it)) }
                                ev.gallons?.let { EventDetailRow("Gallons", "%.2f".format(it)) }
                                ev.calculateMpg()?.let { EventDetailRow("Calculated MPG", "%.2f".format(it)) }
                                ev.pricePerGallon?.let { EventDetailRow("Price/Gal", "$${"%.3f".format(it)}") }
                                ev.totalCost?.let { EventDetailRow("Total Cost", "$${"%.2f".format(it)}") }
                                ev.location?.let { EventDetailRow("Station", it) }
                            } else {
                                ev.odometer?.let { EventDetailRow("Odometer", "$it mi") }
                            }
                            EventDetailRow("Created", dateFormat.format(Date(ev.createdDate)))
                            EventDetailRow("Confidence", ev.confidence?.let { "${(it * 100).toInt()}%" } ?: "N/A")
                            if (photoPaths.isNotEmpty()) {
                                EventDetailRow("Photos", "${photoPaths.size} Attached")
                            }
                        }
                    }
                }

                // Traceability card
                if (photoPaths.isNotEmpty() || !ev.notes.isNullOrBlank()) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Traceability",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            ev.notes?.let { notes ->
                                if (notes.contains("OCR:") || notes.contains("Source:") || notes.contains("Confidence:")) {
                                    Text(
                                        text = "📄 Imported from receipt",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                            }
                            if (photoPaths.isNotEmpty()) {
                                val labelText = if (photoPaths.size == 1) "📷 Attached Photo (tap to view full image):" else "📷 Attached Photos (${photoPaths.size}) (tap to view full image):"
                                Text(
                                    text = labelText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                if (photoPaths.size == 1) {
                                    val path = photoPaths.first()
                                    val data = if (path.startsWith("content://") || path.startsWith("file://")) Uri.parse(path) else path
                                    val model = ImageRequest.Builder(context)
                                        .data(data)
                                        .crossfade(true)
                                        .build()

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(180.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                selectedPhotoIndex = 0
                                                showImageDialog = true
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        AsyncImage(
                                            model = model,
                                            contentDescription = "Event photo",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                } else {
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        itemsIndexed(photoPaths) { index, path ->
                                            val data = if (path.startsWith("content://") || path.startsWith("file://")) Uri.parse(path) else path
                                            val model = ImageRequest.Builder(context)
                                                .data(data)
                                                .crossfade(true)
                                                .build()

                                            Box(
                                                modifier = Modifier
                                                    .size(140.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .clickable {
                                                        selectedPhotoIndex = index
                                                        showImageDialog = true
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                AsyncImage(
                                                    model = model,
                                                    contentDescription = "Event photo ${index + 1}",
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            }
                                        }
                                    }
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
                                        eventDate = selectedDateMillis,
                                        notes = editNotes.ifBlank { null },
                                        odometer = if (ev.eventType == EventType.FUEL || editOdometer.isNotBlank()) editOdometer.toIntOrNull() ?: ev.odometer else ev.odometer,
                                        tripDistance = editTripDistance.toDoubleOrNull() ?: ev.tripDistance,
                                        gallons = editGallons.toDoubleOrNull() ?: ev.gallons,
                                        pricePerGallon = editPricePerGallon.toDoubleOrNull() ?: ev.pricePerGallon,
                                        totalCost = editTotalCost.toDoubleOrNull() ?: ev.totalCost,
                                        location = editLocation.ifBlank { ev.location }
                                    )
                                    saveUpdatedEvent(updated)
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
                                        eventDate = selectedDateMillis,
                                        notes = editNotes.ifBlank { null },
                                        odometer = editOdometer.toIntOrNull() ?: ev.odometer,
                                        gallons = if (ev.eventType == EventType.FUEL) editGallons.toDoubleOrNull() ?: ev.gallons else ev.gallons,
                                        pricePerGallon = editPricePerGallon.toDoubleOrNull() ?: ev.pricePerGallon,
                                        totalCost = editTotalCost.toDoubleOrNull() ?: ev.totalCost,
                                        location = editLocation.ifBlank { null }
                                    )
                                    saveUpdatedEvent(updated)
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

        if (showImageDialog && photoPaths.isNotEmpty()) {
            val safeIndex = selectedPhotoIndex.coerceIn(0, photoPaths.lastIndex)
            val photoPath = photoPaths[safeIndex]
            val data = if (photoPath.startsWith("content://") || photoPath.startsWith("file://")) Uri.parse(photoPath) else photoPath
            val model = ImageRequest.Builder(context)
                .data(data)
                .build()

            Dialog(
                onDismissRequest = { showImageDialog = false },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable { showImageDialog = false },
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = model,
                            contentDescription = "Full Size Event Photo ${safeIndex + 1}",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                        )

                        Row(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (photoPaths.size > 1) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Text(
                                        text = "Photo ${safeIndex + 1} of ${photoPaths.size}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            } else {
                                Spacer(modifier = Modifier.weight(1f))
                            }

                            IconButton(
                                onClick = { showImageDialog = false }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close"
                                )
                            }
                        }

                        if (photoPaths.size > 1) {
                            IconButton(
                                onClick = {
                                    selectedPhotoIndex = if (safeIndex > 0) safeIndex - 1 else photoPaths.lastIndex
                                },
                                modifier = Modifier
                                    .align(Alignment.CenterStart)
                                    .padding(start = 16.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(24.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Previous photo",
                                        modifier = Modifier.padding(8.dp)
                                    )
                                }
                            }

                            IconButton(
                                onClick = {
                                    selectedPhotoIndex = if (safeIndex < photoPaths.lastIndex) safeIndex + 1 else 0
                                },
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .padding(end = 16.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(24.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = "Next photo",
                                        modifier = Modifier.padding(8.dp)
                                    )
                                }
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