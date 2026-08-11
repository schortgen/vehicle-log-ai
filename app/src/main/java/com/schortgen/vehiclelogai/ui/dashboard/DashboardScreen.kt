package com.schortgen.vehiclelogai.ui.dashboard

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.schortgen.vehiclelogai.BuildConfig
import com.schortgen.vehiclelogai.VehicleLogAIApplication
import com.schortgen.vehiclelogai.data.models.EventType
import com.schortgen.vehiclelogai.debug.DiagnosticLogger
import com.schortgen.vehiclelogai.navigation.Screen
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DashboardScreen(
    navController: NavController,
    dashboardViewModel: DashboardViewModel
) {
    val context = LocalContext.current
    val app = context.applicationContext as VehicleLogAIApplication
    val scope = rememberCoroutineScope()
    var scanResult by remember { mutableIntStateOf(-1) }
    val data by dashboardViewModel.dashboardData.collectAsState()

    // Automatically refresh dashboard data when DashboardScreen is displayed
    LaunchedEffect(Unit) {
        dashboardViewModel.refreshDashboard()
    }

    // Refresh dashboard data when screen resumes
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                dashboardViewModel.refreshDashboard()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            scope.launch {
                scanResult = app.photoScannerService.scanAndImport()
                dashboardViewModel.refreshDashboard()
            }
        }
    }

    fun scanPhotos() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (ContextCompat.checkSelfPermission(context, permission)
            == PackageManager.PERMISSION_GRANTED
        ) {
            scope.launch {
                scanResult = app.photoScannerService.scanAndImport()
                dashboardViewModel.refreshDashboard()
            }
        } else {
            permissionLauncher.launch(permission)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dashboard") }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            // Welcome card. Long-press opens the hidden Debug screen in debug builds only.
            item {
                val longClickHandler: (() -> Unit)? = if (BuildConfig.DEBUG) {
                    {
                        DiagnosticLogger.d("Debug", "Long-press: opening debug screen")
                        navController.navigate(Screen.Debug.route)
                    }
                } else null
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = { /* no-op */ },
                            onLongClick = longClickHandler
                        ),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Vehicle Log AI",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${data.totalVehicles} vehicle(s) tracked",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            // Scan result feedback
            if (scanResult >= 0) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Text(
                            text = "Imported $scanResult new photo(s)",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            // Summary Cards
            item {
                Text(
                    text = "Summary",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SummaryCard(
                        label = "Vehicles",
                        value = data.totalVehicles.toString(),
                        modifier = Modifier.weight(1f)
                    )
                    SummaryCard(
                        label = "Pending",
                        value = data.pendingReviewCount.toString(),
                        modifier = Modifier.weight(1f)
                    )
                    SummaryCard(
                        label = "Fuel (mo)",
                        value = data.fuelPurchasesThisMonth.toString(),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SummaryCard(
                        label = "Spent (mo)",
                        value = "$" + "%.2f".format(data.fuelCostThisMonth),
                        modifier = Modifier.weight(1f)
                    )
                    SummaryCard(
                        label = "Spent (yr)",
                        value = "$" + "%.2f".format(data.fuelCostThisYear),
                        modifier = Modifier.weight(1f)
                    )
                    SummaryCard(
                        label = "Avg",
                        value = "$" + "%.2f".format(data.averageFuelCost),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Per-vehicle summaries
            if (data.vehicleSummaries.isNotEmpty()) {
                item {
                    Text(
                        text = "Vehicles",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                items(data.vehicleSummaries) { summary ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = summary.vehicle.nickname
                                    ?: listOfNotNull(summary.vehicle.year?.toString(), summary.vehicle.make, summary.vehicle.model)
                                        .joinToString(" ")
                                        .ifBlank { "Unnamed Vehicle" },
                                style = MaterialTheme.typography.titleSmall
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${summary.totalFuelEvents} fuel event(s) on record",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Last fill-up: ",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = summary.lastFuelEvent?.let {
                                        dateFormat.format(Date(it.eventDate))
                                    } ?: "N/A",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            if (summary.pendingReceipts > 0) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Text(
                                        text = "${summary.pendingReceipts} pending receipt(s)",
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Quick Actions
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Quick Actions",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = { scanPhotos() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Scan Photos", maxLines = 1)
                    }
                    Button(
                        onClick = { navController.navigate(Screen.AddVehicle.route) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Add Vehicle", maxLines = 1)
                    }
                }
            }

            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = {
                            val firstVehicle = dashboardViewModel.vehicles.value.firstOrNull()
                            if (firstVehicle != null) {
                                navController.navigate(Screen.FuelEntry.createRoute(firstVehicle.id))
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Add Fuel", maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = { navController.navigate(Screen.ReviewQueue.route) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Review Queue", maxLines = 1)
                    }
                }
            }

            // Refresh button
            item {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { dashboardViewModel.refreshDashboard() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Refresh Dashboard")
                }
            }

            // Bottom spacer
            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun SummaryCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
