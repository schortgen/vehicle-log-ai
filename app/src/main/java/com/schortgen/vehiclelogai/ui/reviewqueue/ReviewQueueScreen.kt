package com.schortgen.vehiclelogai.ui.reviewqueue

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.schortgen.vehiclelogai.data.models.Event
import com.schortgen.vehiclelogai.data.models.EventType
import com.schortgen.vehiclelogai.data.models.ProcessingStatus
import com.schortgen.vehiclelogai.data.models.ReviewItem
import com.schortgen.vehiclelogai.data.models.getPhotoPaths
import androidx.compose.foundation.lazy.LazyRow
import com.schortgen.vehiclelogai.data.models.toImageModel
import com.schortgen.vehiclelogai.navigation.Screen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import coil.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewQueueScreen(
    navController: NavController,
    reviewQueueViewModel: ReviewQueueViewModel
) {
    val items by reviewQueueViewModel.reviewQueueItems.collectAsState()
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()) }

    // Seed mock data on first composition
    LaunchedEffect(Unit) {
        reviewQueueViewModel.seedMockData()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Review Queue") }
            )
        }
    ) { innerPadding ->
        if (items.isEmpty()) {
            Box(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "No items to review",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Imported photos will appear here",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(items, key = {
                    when (it) {
                        is ReviewQueueItem.Single -> "single_${it.item.id}"
                        is ReviewQueueItem.Grouped -> "grouped_${it.event.id}"
                    }
                }) { queueItem ->
                    when (queueItem) {
                        is ReviewQueueItem.Single -> {
                            ReviewQueueCard(
                                item = queueItem.item,
                                dateFormat = dateFormat,
                                onClick = {
                                    navController.navigate(Screen.ReviewDetail.createRoute(queueItem.item.id))
                                },
                                onStatusChange = { newStatus ->
                                    reviewQueueViewModel.updateStatus(queueItem.item, newStatus)
                                },
                                onDelete = {
                                    reviewQueueViewModel.deleteItem(queueItem.item)
                                }
                            )
                        }
                        is ReviewQueueItem.Grouped -> {
                            GroupedEventCard(
                                event = queueItem.event,
                                items = queueItem.items,
                                dateFormat = dateFormat,
                                onClick = {
                                    navController.navigate(Screen.ReviewGroupDetail.createRoute(queueItem.event.id))
                                },
                                onDelete = {
                                    reviewQueueViewModel.deleteGroup(queueItem.event, queueItem.items)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReviewQueueCard(
    item: ReviewItem,
    dateFormat: SimpleDateFormat,
    onClick: () -> Unit,
    onStatusChange: (ProcessingStatus) -> Unit,
    onDelete: () -> Unit
) {
    val statusColor = when (item.status) {
        ProcessingStatus.PENDING -> MaterialTheme.colorScheme.outline
        ProcessingStatus.PROCESSING -> MaterialTheme.colorScheme.tertiary
        ProcessingStatus.NEEDS_REVIEW -> MaterialTheme.colorScheme.error
        ProcessingStatus.COMPLETE -> MaterialTheme.colorScheme.primary
        ProcessingStatus.FAILED -> MaterialTheme.colorScheme.error
    }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail
            Surface(
                modifier = Modifier.size(96.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                if (item.photoPath != null) {
                    val currentCtx = LocalContext.current
                    AsyncImage(
                        model = item.photoPath.toImageModel(currentCtx),
                        contentDescription = "Receipt Thumbnail",
                        placeholder = painterResource(id = android.R.drawable.ic_menu_report_image),
                        error = painterResource(id = android.R.drawable.ic_dialog_alert),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "📷",
                            style = MaterialTheme.typography.headlineLarge
                        )
                    }
                }
            }

            // Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.reason ?: "Imported Photo",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = dateFormat.format(Date(item.captureDate)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = statusColor.copy(alpha = 0.15f),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = item.status.displayName,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = statusColor
                        )
                    }
                    item.confidence?.let { confidence ->
                        Text(
                            text = "${(confidence * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Overflow menu for actions
            var showMenu by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Text("⋮", style = MaterialTheme.typography.titleLarge)
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Mark Pending") },
                        onClick = { onStatusChange(ProcessingStatus.PENDING); showMenu = false }
                    )
                    DropdownMenuItem(
                        text = { Text("Mark Processing") },
                        onClick = { onStatusChange(ProcessingStatus.PROCESSING); showMenu = false }
                    )
                    DropdownMenuItem(
                        text = { Text("Mark Needs Review") },
                        onClick = { onStatusChange(ProcessingStatus.NEEDS_REVIEW); showMenu = false }
                    )
                    DropdownMenuItem(
                        text = { Text("Mark Complete") },
                        onClick = { onStatusChange(ProcessingStatus.COMPLETE); showMenu = false }
                    )
                    DropdownMenuItem(
                        text = { Text("Mark Failed") },
                        onClick = { onStatusChange(ProcessingStatus.FAILED); showMenu = false }
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        onClick = { onDelete(); showMenu = false }
                    )
                }
            }
        }
    }
}

@Composable
private fun GroupedEventCard(
    event: Event,
    items: List<ReviewItem>,
    dateFormat: SimpleDateFormat,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val allPhotos = remember(event, items) {
        val list = mutableListOf<String>()
        items.forEach { item ->
            val p = item.photoPath
            if (!p.isNullOrBlank() && !list.contains(p.trim())) {
                list.add(p.trim())
            }
        }
        event.getPhotoPaths().forEach { p ->
            val trimmed = p.trim()
            if (trimmed.isNotBlank() && !list.contains(trimmed)) {
                list.add(trimmed)
            }
        }
        list
    }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Info
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Grouped Event (${allPhotos.size.coerceAtLeast(items.size)} photos)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    val typeName = when (event.eventType) {
                        EventType.FUEL -> "⛽ Fuel Purchase"
                        else -> "🔧 Maintenance/Service"
                    }
                    Text(
                        text = typeName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = dateFormat.format(Date(event.eventDate)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Delete action
                IconButton(onClick = onDelete) {
                    Text("🗑️", style = MaterialTheme.typography.titleMedium)
                }
            }

            if (allPhotos.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(allPhotos) { photoPath ->
                        val currentCtx = LocalContext.current
                        Surface(
                            modifier = Modifier.size(80.dp),
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            AsyncImage(
                                model = photoPath.toImageModel(currentCtx),
                                contentDescription = "Grouped Photo",
                                placeholder = painterResource(id = android.R.drawable.ic_menu_report_image),
                                error = painterResource(id = android.R.drawable.ic_dialog_alert),
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }
            }
        }
    }
}