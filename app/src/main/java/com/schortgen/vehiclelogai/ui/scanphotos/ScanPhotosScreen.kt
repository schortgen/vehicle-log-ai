package com.schortgen.vehiclelogai.ui.scanphotos

import android.Manifest
import android.app.DatePickerDialog
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.schortgen.vehiclelogai.ui.reviewqueue.ReviewQueueViewModel
import com.schortgen.vehiclelogai.data.models.ReviewItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanPhotosScreen(
    navController: NavController,
    reviewQueueViewModel: ReviewQueueViewModel,
    viewModel: ScanPhotosViewModel = viewModel(
        factory = ScanViewModelFactory(LocalContext.current) { uri, dateTaken ->
            reviewQueueViewModel.insertItemAndReturnNew(
                ReviewItem(
                    photoPath = uri.toString(),
                    captureDate = dateTaken
                )
            )
        }
    )
) {
    val context = LocalContext.current
    val fmt = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }

    // Permission handling
    val requiredPermission = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, requiredPermission) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
    }

    val startMillis by viewModel.startDateMillis.collectAsState()
    val endMillis by viewModel.endDateMillis.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val scanned by viewModel.scannedCount.collectAsState()
    val queued by viewModel.queuedCount.collectAsState()
    val total by viewModel.totalToScan.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Scan Photos") })
        }
    ) { inner ->
        Column(
            modifier = Modifier
                .padding(inner)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            DateRow(
                label = "Start date",
                millis = startMillis,
                onPick = { pickDate(context) { viewModel.setStartDate(it) } },
                fmt = fmt
            )
            DateRow(
                label = "End date",
                millis = endMillis,
                onPick = { pickDate(context) { viewModel.setEndDate(it) } },
                fmt = fmt
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        // If permission not granted, request it first. Otherwise start scan.
                        if (!hasPermission) {
                            permissionLauncher.launch(requiredPermission)
                        } else {
                            viewModel.scanPhotos(context)
                        }
                    },
                    enabled = !isScanning,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Start scan")
                }
                Button(
                    onClick = { viewModel.cancelScan() },
                    enabled = isScanning,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel")
                }
            }

            // Show permission status & hint
            if (!hasPermission) {
                Text(
                    text = "Permission required to read images. Tap Start to request permission.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (isScanning) {
                if (total == null) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                } else {
                    val progress = (scanned.coerceAtMost(total ?: 1).toFloat() / (total ?: 1).toFloat())
                    LinearProgressIndicator(progress = progress, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${scanned} / ${total ?: "?"} scanned",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // Live counters
            Text("Scanned: $scanned", style = MaterialTheme.typography.bodyLarge)
            Text("Queued: $queued", style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.weight(1f))

            // Small help / status
            Text(
                text = if (isScanning) "Scanning..." else "Idle",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun DateRow(label: String, millis: Long?, onPick: () -> Unit, fmt: SimpleDateFormat) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(text = label, modifier = Modifier.width(90.dp))
        OutlinedButton(onClick = onPick) {
            Text(text = millis?.let { fmt.format(Date(it)) } ?: "Any")
        }
    }
}

private fun pickDate(context: Context, onResult: (Long?) -> Unit) {
    val cal = Calendar.getInstance()
    val dlg = DatePickerDialog(
        context,
        { _, year, month, day ->
            val c = Calendar.getInstance()
            c.set(year, month, day, 0, 0, 0)
            onResult(c.timeInMillis)
        },
        cal.get(Calendar.YEAR),
        cal.get(Calendar.MONTH),
        cal.get(Calendar.DAY_OF_MONTH)
    )
    dlg.setOnCancelListener { onResult(null) }
    dlg.show()
}
