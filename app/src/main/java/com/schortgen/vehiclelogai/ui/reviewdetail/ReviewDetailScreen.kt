package com.schortgen.vehiclelogai.ui.reviewdetail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavController
import com.schortgen.vehiclelogai.data.models.FuelPurchaseCandidate
import com.schortgen.vehiclelogai.data.models.ProcessingStatus
import com.schortgen.vehiclelogai.data.models.Vehicle
import com.schortgen.vehiclelogai.ui.reviewqueue.ReviewQueueViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import coil.compose.AsyncImage
import android.net.Uri
import com.schortgen.vehiclelogai.debug.DiagnosticLogger

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewDetailScreen(
    navController: NavController,
    reviewQueueViewModel: ReviewQueueViewModel,
    reviewItemId: Long,
    eventId: Long = -1L
) {
    val isGrouped = eventId != -1L
    val reviewItems by reviewQueueViewModel.reviewItems.collectAsState()
    val events by reviewQueueViewModel.events.collectAsState()

    val item = if (isGrouped) null else reviewItems.find { it.id == reviewItemId }
    val event = if (isGrouped) events.find { it.id == eventId } else null
    val itemsInEvent = if (isGrouped) reviewItems.filter { it.eventId == eventId } else emptyList()

    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()) }
    val ocrProcessingIds by reviewQueueViewModel.ocrProcessingIds.collectAsState()
    val isOcrRunning = !isGrouped && ocrProcessingIds.contains(reviewItemId)

    // Handle nullable vehicles StateFlow
    val vehiclesFlow = reviewQueueViewModel.vehicles
    val vehicles = if (vehiclesFlow != null) {
        vehiclesFlow.collectAsState().value
    } else {
        remember { emptyList<Vehicle>() }
    }

    val saveErrors by reviewQueueViewModel.saveErrors.collectAsState()

    // Parse the stored candidate from JSON
    val candidate = remember(item, event) {
        if (isGrouped && event != null) {
            val dateStr = try {
                SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(event.eventDate))
            } catch (e: Exception) {
                ""
            }
            FuelPurchaseCandidate(
                stationName = event.location,
                stationNameConfidence = 1f,
                purchaseDate = dateStr,
                purchaseDateConfidence = 1f,
                gallons = event.gallons,
                gallonsConfidence = 1f,
                pricePerGallon = event.pricePerGallon,
                pricePerGallonConfidence = 1f,
                totalCost = event.totalCost,
                totalCostConfidence = 1f,
                odometer = event.odometer,
                odometerConfidence = 1f
            )
        } else {
            item?.let { reviewQueueViewModel.parseCandidateFromItem(it) }
        }
    }

    // Editable state fields initialized from candidate
    var editedStationName by remember { mutableStateOf(candidate?.stationName ?: "") }
    var editedDate by remember { mutableStateOf(candidate?.purchaseDate ?: "") }
    var editedGallons by remember { mutableStateOf(candidate?.gallons?.toString() ?: "") }
    var editedPricePerGallon by remember { mutableStateOf(candidate?.pricePerGallon?.toString() ?: "") }
    var editedTotalCost by remember { mutableStateOf(candidate?.totalCost?.toString() ?: "") }
    var editedOdometer by remember { mutableStateOf(candidate?.odometer?.toString() ?: "") }

    LaunchedEffect(candidate) {
        if (candidate != null) {
            editedStationName = candidate.stationName ?: ""
            editedDate = candidate.purchaseDate ?: ""
            editedGallons = candidate.gallons?.toString() ?: ""
            editedPricePerGallon = candidate.pricePerGallon?.toString() ?: ""
            editedTotalCost = candidate.totalCost?.toString() ?: ""
            editedOdometer = candidate.odometer?.toString() ?: ""
        }
    }

    // Vehicle selection
    var selectedVehicleId by remember { mutableStateOf(if (isGrouped) event?.vehicleId ?: -1L else item?.vehicleId ?: -1L) }
    var vehicleDropdownExpanded by remember { mutableStateOf(false) }
    val selectedVehicle = vehicles.find { v -> v.id == selectedVehicleId }

    // Save/validation state
    var isSaving by remember { mutableStateOf(false) }

    // Refresh from DB
    LaunchedEffect(reviewItemId, eventId) {
        if (isGrouped) {
            if (event != null && event.vehicleId != -1L) {
                selectedVehicleId = event.vehicleId
            }
        } else {
            val refreshedItem = reviewQueueViewModel.getReviewItemByIdSuspend(reviewItemId)
            if (refreshedItem != null && refreshedItem.vehicleId != null && refreshedItem.vehicleId != -1L) {
                selectedVehicleId = refreshedItem.vehicleId
            }
        }
    }

    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }
    var activePhotoForZoom by remember { mutableStateOf<String?>(null) }

    if (activePhotoForZoom != null) {
        ZoomableImageDialog(
            photoPath = activePhotoForZoom!!,
            onDismiss = { activePhotoForZoom = null }
        )
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text(if (isGrouped) "Remove this group from the Review Queue?" else "Remove this receipt from the Review Queue?") },
            text = { Text("The original photos will remain on your phone.\nYou can scan them again later if needed.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (isGrouped && event != null) {
                            reviewQueueViewModel.deleteGroup(event, itemsInEvent)
                        } else {
                            item?.let { reviewQueueViewModel.deleteItem(it) }
                        }
                        showDeleteConfirmDialog = false
                        navController.popBackStack()
                    }
                ) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Review Detail") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        val isLoading = !isGrouped && reviewItems.isEmpty()
        if (isLoading) {
            Box(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (isGrouped && event == null || !isGrouped && item == null) {
            Box(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("Item not found", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            val photoPath = if (isGrouped) event?.photoPath else item?.photoPath
            val photoUri = photoPath?.let { Uri.parse(it) }
            val reason = if (isGrouped) "Grouped Event (${itemsInEvent.size} Photos)" else item?.reason ?: "Imported photo"
            val captureDate = if (isGrouped) event?.eventDate ?: System.currentTimeMillis() else item?.captureDate ?: System.currentTimeMillis()
            val statusDisplay = if (isGrouped) "Needs Review" else item?.status?.displayName ?: ""
            val confidence = if (isGrouped) event?.confidence else item?.confidence
            val createdDate = if (isGrouped) event?.createdDate ?: System.currentTimeMillis() else item?.createdDate ?: System.currentTimeMillis()
            val candidateValue = candidate

            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .padding(16.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Photo placeholder
                Card(modifier = Modifier.fillMaxWidth()) {
                    if (isGrouped) {
                        val photos = itemsInEvent.mapNotNull { it.photoPath }
                        if (photos.isNotEmpty()) {
                            LazyRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(250.dp)
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(photos) { photo ->
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .width(180.dp)
                                            .clickable { activePhotoForZoom = photo }
                                    ) {
                                        AsyncImage(
                                            model = Uri.parse(photo),
                                            contentDescription = "Scanned photo",
                                            placeholder = painterResource(id = android.R.drawable.ic_menu_report_image),
                                            error = painterResource(id = android.R.drawable.ic_dialog_alert),
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Fit
                                        )
                                        Surface(
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                            shape = MaterialTheme.shapes.small,
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .padding(4.dp)
                                        ) {
                                            Text(
                                                text = "Zoom",
                                                style = MaterialTheme.typography.labelSmall,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(250.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No photos",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        if (photoUri != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(250.dp)
                                    .clickable { activePhotoForZoom = photoPath }
                            ) {
                                AsyncImage(
                                    model = photoUri,
                                    contentDescription = "Scanned photo",
                                    placeholder = painterResource(id = android.R.drawable.ic_menu_report_image),
                                    error = painterResource(id = android.R.drawable.ic_dialog_alert),
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit
                                )
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                    shape = MaterialTheme.shapes.small,
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(8.dp)
                                ) {
                                    Text(
                                        text = "Tap to zoom",
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(250.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No photo",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
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

                        DetailRow("Reason", reason)
                        DetailRow("Capture Date", dateFormat.format(Date(captureDate)))
                        DetailRow("Status", statusDisplay)
                        confidence?.let { conf ->
                            DetailRow("Confidence", "${(conf * 100).toInt()}%")
                        }
                        DetailRow("Created", dateFormat.format(Date(createdDate)))
                    }
                }

                // Vehicle Selection card
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Vehicle",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        if (vehicles.isEmpty()) {
                            Text(
                                text = "No vehicles available. Please add a vehicle first.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        } else {
                            ExposedDropdownMenuBox(
                                expanded = vehicleDropdownExpanded,
                                onExpandedChange = { expanded -> vehicleDropdownExpanded = expanded }
                            ) {
                                val displayText = if (selectedVehicle != null) {
                                    "${selectedVehicle.nickname ?: ""} (${selectedVehicle.year} ${selectedVehicle.make} ${selectedVehicle.model})"
                                } else {
                                    "Select a vehicle"
                                }
                                OutlinedTextField(
                                    value = displayText,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Vehicle") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = vehicleDropdownExpanded) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .menuAnchor(),
                                    singleLine = true
                                )
                                ExposedDropdownMenu(
                                    expanded = vehicleDropdownExpanded,
                                    onDismissRequest = { vehicleDropdownExpanded = false }
                                ) {
                                    vehicles.forEach { vehicle ->
                                        DropdownMenuItem(
                                            text = {
                                                Text("${vehicle.nickname ?: ""} (${vehicle.year} ${vehicle.make} ${vehicle.model})")
                                            },
                                            onClick = {
                                                selectedVehicleId = vehicle.id
                                                vehicleDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        if (selectedVehicleId != -1L && selectedVehicle != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "ID: ${selectedVehicle.id}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (candidateValue != null) {
                    val fieldsTotal = 6
                    val extractedFieldsCount = (if (candidateValue.stationName != null) 1 else 0) +
                            (if (candidateValue.purchaseDate != null) 1 else 0) +
                            (if (candidateValue.gallons != null) 1 else 0) +
                            (if (candidateValue.pricePerGallon != null) 1 else 0) +
                            (if (candidateValue.totalCost != null) 1 else 0) +
                            (if (candidateValue.odometer != null) 1 else 0)
                            
                    val isReadyToCreate = candidateValue.gallons != null && candidateValue.totalCost != null && candidateValue.odometer != null
                    
                    val statusText = if (isReadyToCreate) "Ready to Create Fuel Record\nAll required fields detected." else "Needs Review\n$extractedFieldsCount of $fieldsTotal fields extracted"
                    val containerColor = if (isReadyToCreate) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.tertiaryContainer
                    val contentColor = if (isReadyToCreate) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onTertiaryContainer

                    Card(
                        colors = CardDefaults.cardColors(containerColor = containerColor),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Receipt Status", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = contentColor)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(statusText, style = MaterialTheme.typography.bodyMedium, color = contentColor)
                        }
                    }
                    
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Receipt Analysis", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            AnalysisRow("Station", candidateValue.stationName)
                            AnalysisRow("Date", candidateValue.purchaseDate)
                            AnalysisRow("Gallons", candidateValue.gallons?.toString())
                            AnalysisRow("Price Per Gallon", candidateValue.pricePerGallon?.let { "$$it" })
                            AnalysisRow("Total", candidateValue.totalCost?.let { "$$it" })
                            AnalysisRow("Odometer", candidateValue.odometer?.toString())
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider()
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            val conf = candidateValue.overallConfidence
                            val (confEmoji, confText) = when {
                                conf >= 0.7f -> "🟢" to "High"
                                conf >= 0.4f -> "🟡" to "Medium"
                                else -> "🔴" to "Low"
                            }
                            Text("Overall Confidence: $confEmoji $confText (${(conf * 100).toInt()}%)", style = MaterialTheme.typography.bodyMedium)
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider()
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            Text("Explain This", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            if (candidateValue.missingFields.isEmpty()) {
                                Text("All fields were successfully detected.", style = MaterialTheme.typography.bodyMedium)
                            } else {
                                candidateValue.missingFields.forEach { field ->
                                    val text = when (field) {
                                        "stationName" -> "Station Name was not detected."
                                        "purchaseDate" -> "Receipt date could not be determined."
                                        "gallons" -> "Gallons were not detected."
                                        "pricePerGallon" -> "Price Per Gallon was not detected."
                                        "totalCost" -> "Multiple total amounts were found or total was missing."
                                        "odometer" -> "Odometer not found because the receipt did not contain an ODO field."
                                        else -> "$field was not detected."
                                    }
                                    Text("• $text", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }

                // Suggested Values card (editable)
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Suggested Values",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            if (candidateValue != null) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Text(
                                        text = "${(candidateValue.overallConfidence * 100).toInt()}% confidence",
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))

                        val c = candidateValue
                        EditableField(
                            label = "Station Name",
                            value = editedStationName,
                            onValueChange = { editedStationName = it },
                            confidence = c?.stationNameConfidence ?: 0f,
                            isMissing = c?.stationName == null
                        )
                        EditableField(
                            label = "Purchase Date",
                            value = editedDate,
                            onValueChange = { editedDate = it },
                            confidence = c?.purchaseDateConfidence ?: 0f,
                            isMissing = c?.purchaseDate == null
                        )
                        EditableField(
                            label = "Gallons",
                            value = editedGallons,
                            onValueChange = { editedGallons = it },
                            confidence = c?.gallonsConfidence ?: 0f,
                            isMissing = c?.gallons == null,
                            keyboardType = KeyboardType.Decimal
                        )
                        EditableField(
                            label = "Price Per Gallon",
                            value = editedPricePerGallon,
                            onValueChange = { editedPricePerGallon = it },
                            confidence = c?.pricePerGallonConfidence ?: 0f,
                            isMissing = c?.pricePerGallon == null,
                            keyboardType = KeyboardType.Decimal
                        )
                        EditableField(
                            label = "Total Cost",
                            value = editedTotalCost,
                            onValueChange = { editedTotalCost = it },
                            confidence = c?.totalCostConfidence ?: 0f,
                            isMissing = c?.totalCost == null,
                            keyboardType = KeyboardType.Decimal
                        )
                        EditableField(
                            label = "Odometer",
                            value = editedOdometer,
                            onValueChange = { editedOdometer = it },
                            confidence = c?.odometerConfidence ?: 0f,
                            isMissing = c?.odometer == null,
                            keyboardType = KeyboardType.Number
                        )
                    }
                }

                // Error display
                if (saveErrors != null || validationError != null) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = validationError ?: saveErrors ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Status actions + Save button
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Actions",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        if (!isGrouped) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        item?.let { reviewQueueViewModel.updateStatus(it, ProcessingStatus.PENDING) }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Pending", maxLines = 1)
                                }
                                OutlinedButton(
                                    onClick = {
                                        item?.let { reviewQueueViewModel.updateStatus(it, ProcessingStatus.COMPLETE) }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Complete", maxLines = 1)
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        // Run OCR button
                        if (!isGrouped && item?.photoPath != null) {
                            Button(
                                onClick = {
                                    reviewQueueViewModel.processOcr(item.id)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !isOcrRunning
                            ) {
                                if (isOcrRunning) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Running OCR...")
                                } else {
                                    Text("Extract Receipt Data")
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        } else if (isGrouped && itemsInEvent.any { it.photoPath != null }) {
                            val anyOcrRunning = itemsInEvent.any { ocrProcessingIds.contains(it.id) }
                            Button(
                                onClick = {
                                    itemsInEvent.forEach { itm ->
                                        if (itm.photoPath != null) {
                                            reviewQueueViewModel.processOcr(itm.id)
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !anyOcrRunning
                            ) {
                                if (anyOcrRunning) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Extracting Data...")
                                } else {
                                    Text("Extract Receipt Data (All Photos)")
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        // Save as Fuel Event button
                        Button(
                            onClick = {
                                validationError = null
                                reviewQueueViewModel.clearSaveError()

                                val gallons = editedGallons.toDoubleOrNull()
                                val totalCost = editedTotalCost.toDoubleOrNull()
                                val odometer = editedOdometer.toIntOrNull()

                                if (gallons == null || totalCost == null || odometer == null) {
                                    validationError = "Additional information is required before creating a Fuel Record."
                                    return@Button
                                }

                                isSaving = true

                                val editedCandidate = FuelPurchaseCandidate(
                                    stationName = editedStationName.ifBlank { null },
                                    stationNameConfidence = candidateValue?.stationNameConfidence ?: 0f,
                                    purchaseDate = editedDate.ifBlank { null },
                                    purchaseDateConfidence = candidateValue?.purchaseDateConfidence ?: 0f,
                                    gallons = gallons,
                                    gallonsConfidence = candidateValue?.gallonsConfidence ?: 0f,
                                    pricePerGallon = editedPricePerGallon.toDoubleOrNull(),
                                    pricePerGallonConfidence = candidateValue?.pricePerGallonConfidence ?: 0f,
                                    totalCost = totalCost,
                                    totalCostConfidence = candidateValue?.totalCostConfidence ?: 0f,
                                    odometer = odometer,
                                    odometerConfidence = candidateValue?.odometerConfidence ?: 0f
                                )

                                if (isGrouped) {
                                    reviewQueueViewModel.saveGroupedAsFuelEvent(
                                        eventId = eventId,
                                        vehicleId = selectedVehicleId,
                                        editedCandidate = editedCandidate
                                    )
                                } else {
                                    item?.let {
                                        reviewQueueViewModel.saveAsFuelEvent(
                                            reviewItemId = it.id,
                                            vehicleId = selectedVehicleId,
                                            editedCandidate = editedCandidate
                                        )
                                    }
                                }

                                if (reviewQueueViewModel.saveErrors.value == null) {
                                    navController.popBackStack()
                                }
                                isSaving = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isSaving && selectedVehicleId != -1L,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            if (isSaving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Saving...")
                            } else {
                                Text("Save as Fuel Event", style = MaterialTheme.typography.titleMedium)
                            }
                        }
                        if (selectedVehicleId == -1L) {
                            Text(
                                text = "Select a vehicle to enable saving",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                showDeleteConfirmDialog = true
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text(if (isGrouped) "Remove Group from Review Queue" else "Remove from Review Queue")
                        }
                    }
                }
                
                // Photo Retention Notice
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "The original photo remains on your phone.",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Vehicle Log AI only stores a reference to it until you create a fuel record.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "OCR text and parser results are only working copies used to create a Fuel Event.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Raw OCR Text card
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Raw OCR Text",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        val ocrText = item?.ocrText
                        if (ocrText.isNullOrBlank()) {
                            Text(
                                text = "No OCR data yet. Tap \"Extract Receipt Data\" to process this photo.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            item?.ocrProcessingTimeMs?.let { timeMs ->
                                Text(
                                    text = "Processed in ${timeMs}ms",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                            HorizontalDivider()
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = ocrText,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EditableField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    confidence: Float,
    isMissing: Boolean,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    val confidenceLabel = if (isMissing) {
        "Not found"
    } else {
        "${(confidence * 100).toInt()}%"
    }
    val confidenceColor = when {
        isMissing -> MaterialTheme.colorScheme.error
        confidence >= 0.7f -> MaterialTheme.colorScheme.primary
        confidence >= 0.4f -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.error
    }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        trailingIcon = {
            Surface(
                color = confidenceColor.copy(alpha = 0.15f),
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = confidenceLabel,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = confidenceColor
                )
            }
        },
        supportingText = if (isMissing) {
            { Text("Not detected", color = MaterialTheme.colorScheme.error) }
        } else null
    )
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
private fun DetailRow(label: String, value: String) {
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

@Composable
private fun AnalysisRow(label: String, value: String?) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        if (value != null) {
            Text("✓ ", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Text("$label\n$value", style = MaterialTheme.typography.bodyMedium)
        } else {
            Text("✗ ", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
            Text("$label\nNot Found", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun ZoomableImageDialog(photoPath: String, onDismiss: () -> Unit) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 5f)
                        val extraWidth = (scale - 1) * size.width
                        val extraHeight = (scale - 1) * size.height
                        val maxX = extraWidth / 2
                        val maxY = extraHeight / 2
                        
                        val newOffset = offset + pan * scale
                        
                        offset = Offset(
                            x = newOffset.x.coerceIn(-maxX, maxX),
                            y = newOffset.y.coerceIn(-maxY, maxY)
                        )
                    }
                }
        ) {
            AsyncImage(
                model = photoPath,
                contentDescription = "Zoomable receipt",
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y
                    ),
                contentScale = ContentScale.Fit
            )
            
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color.White
                )
            }
        }
    }
}
