package com.schortgen.vehiclelogai.ui.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.schortgen.vehiclelogai.BuildConfig
import com.schortgen.vehiclelogai.VehicleLogAIApplication
import com.schortgen.vehiclelogai.debug.BuildInfo
import com.schortgen.vehiclelogai.debug.DiagnosticsViewModel
import com.schortgen.vehiclelogai.debug.DiagnosticsViewModelFactory
import com.schortgen.vehiclelogai.ui.reviewqueue.ReviewQueueViewModel
import com.schortgen.vehiclelogai.ui.reviewqueue.ReviewQueueViewModelFactory
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugScreen(navController: NavController) {
    val context = LocalContext.current
    val app = context.applicationContext as VehicleLogAIApplication
    val reviewQueueViewModel: ReviewQueueViewModel = viewModel(
        factory = ReviewQueueViewModelFactory(
            repository = app.reviewItemRepository,
            vehicleRepository = app.vehicleRepository,
            eventRepository = app.eventRepository,
            mlKitOcrService = app.mlKitOcrService,
            receiptParserService = app.receiptParserService
        )
    )
    val viewModel: DiagnosticsViewModel = viewModel(
        factory = DiagnosticsViewModelFactory(
            vehicleRepository = app.vehicleRepository,
            eventRepository = app.eventRepository,
            reviewItemRepository = app.reviewItemRepository,
            photoScannerRepository = app.photoScannerRepository,
            reviewQueueViewModel = reviewQueueViewModel,
            dbSchemaVersion = 6
        )
    )

    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.updateBuildInfo(
            BuildInfo(
                appVersion = BuildConfig.VERSION_NAME,
                versionCode = BuildConfig.VERSION_CODE,
                dbSchemaVersion = 6,
                debugBuild = BuildConfig.DEBUG
            )
        )
        viewModel.refresh()
    }

    LaunchedEffect(state.statusMessage) {
        state.statusMessage?.let {
            snackbar.showSnackbar(it)
            viewModel.clearStatus()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Debug") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!BuildConfig.DEBUG) {
                Text(
                    text = "Debug screen is unavailable in release builds.",
                    color = MaterialTheme.colorScheme.error
                )
                return@Column
            }

            BuildInfoCard(state.build)
            DatabaseStatsCard(state.database)
            MediaStoreStatsCard(state.stats)
            OcrStatsCard(state.stats)
            ParserStatsCard(state.stats)
            ActionButtons(
                isBusy = state.isBusy,
                onSeed = viewModel::seedSampleData,
                onClear = viewModel::clearTestData,
                onExport = { viewModel.exportLogs() },
                onRefresh = viewModel::refresh
            )
            RecentLogsCard(state.recentLogLines)
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun BuildInfoCard(build: BuildInfo) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Build", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            InfoRow("App version", "${build.appVersion} (${build.versionCode})")
            InfoRow("DB schema", build.dbSchemaVersion.toString())
            InfoRow("Build type", if (build.debugBuild) "Debug" else "Release")
        }
    }
}

@Composable
private fun DatabaseStatsCard(db: com.schortgen.vehiclelogai.debug.DatabaseStats) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Database", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            InfoRow("Vehicles", db.vehicleCount.toString())
            InfoRow("Events", db.eventCount.toString())
            InfoRow("Review items", db.reviewItemCount.toString())
            InfoRow("Pending review", db.pendingReviewCount.toString())
            InfoRow("Scanned photos", db.scannedPhotoCount.toString())
        }
    }
}

@Composable
private fun MediaStoreStatsCard(stats: com.schortgen.vehiclelogai.debug.DiagnosticStats) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("MediaStore scan", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            InfoRow("Scan runs", stats.scanRuns.toString())
            InfoRow("Total candidates", stats.scanCandidates.toString())
            InfoRow("Total imported", stats.scanImported.toString())
        }
    }
}

@Composable
private fun OcrStatsCard(stats: com.schortgen.vehiclelogai.debug.DiagnosticStats) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("OCR processing", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            InfoRow("Total runs", stats.ocrTotal.toString())
            InfoRow("Successes", stats.ocrSuccessCount.toString())
            InfoRow("Failures", stats.ocrFailureCount.toString())
            InfoRow("Success rate", "${(stats.ocrSuccessRate * 100).format(1)}%")
            InfoRow("Average time", "${"%.0f".format(stats.averageOcrMs)} ms")
        }
    }
}

@Composable
private fun ParserStatsCard(stats: com.schortgen.vehiclelogai.debug.DiagnosticStats) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Receipt parser", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            InfoRow("Total runs", stats.parserTotal.toString())
            InfoRow("Successes", stats.parserSuccessCount.toString())
            InfoRow("Failures", stats.parserFailureCount.toString())
            InfoRow("Success rate", "${(stats.parserSuccessRate * 100).format(1)}%")
        }
    }
}

@Composable
private fun ActionButtons(
    isBusy: Boolean,
    onSeed: () -> Unit,
    onClear: () -> Unit,
    onExport: () -> Unit,
    onRefresh: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Test actions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (isBusy) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.height(20.dp))
                    Spacer(modifier = Modifier.height(0.dp))
                    Text(" Working…", style = MaterialTheme.typography.bodyMedium)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = onSeed, enabled = !isBusy, modifier = Modifier.weight(1f)) {
                    Text("Seed sample data")
                }
                OutlinedButton(onClick = onClear, enabled = !isBusy, modifier = Modifier.weight(1f)) {
                    Text("Clear test data")
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = onExport, enabled = !isBusy, modifier = Modifier.weight(1f)) {
                    Text("Export logs")
                }
                OutlinedButton(onClick = onRefresh, enabled = !isBusy, modifier = Modifier.weight(1f)) {
                    Text("Refresh")
                }
            }
        }
    }
}

@Composable
private fun RecentLogsCard(lines: List<String>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Recent log lines", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            HorizontalDivider()
            if (lines.isEmpty()) {
                Text(
                    "No log lines yet. Trigger a scan or OCR run to populate.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                // Show the last 40 lines in a monospaced, compact block.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    lines.forEach { line ->
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

private fun Double.format(decimals: Int): String {
    return "%.${decimals}f".format(this)
}
