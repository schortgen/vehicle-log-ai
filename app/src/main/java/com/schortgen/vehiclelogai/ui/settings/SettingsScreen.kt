package com.schortgen.vehiclelogai.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import androidx.navigation.NavHostController
import com.schortgen.vehiclelogai.data.repository.PreferredTripMeter
import com.schortgen.vehiclelogai.navigation.Screen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavHostController,
    viewModel: SettingsViewModel
) {
    val context = LocalContext.current
    val preferredTripMeter by viewModel.preferredTripMeter.collectAsState()
    val movePhotosOnComplete by viewModel.movePhotosOnComplete.collectAsState()
    val completedPhotosFolderUri by viewModel.completedPhotosFolderUri.collectAsState()
    val completedPhotosFolderName by viewModel.completedPhotosFolderName.collectAsState()
    val isBackupInProgress by viewModel.isBackupInProgress.collectAsState()
    val backupStatusMessage by viewModel.backupStatusMessage.collectAsState()
    val discoveredBackupFiles by viewModel.discoveredBackupFiles.collectAsState()
    val isScanningBackups by viewModel.isScanningBackups.collectAsState()

    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }
    var showRestorePickerDialog by remember { mutableStateOf(false) }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            try {
                context.contentResolver.takePersistableUriPermission(uri, flags)
            } catch (_: Exception) {}
            val docFile = DocumentFile.fromTreeUri(context, uri)
            val folderName = docFile?.name ?: uri.lastPathSegment ?: "Custom Folder"
            viewModel.setCompletedPhotosFolder(uri.toString(), folderName)
            viewModel.updateMovePhotosOnComplete(true)
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            viewModel.exportBackup(context, uri)
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        contract = OpenBackupDocumentContract()
    ) { uri ->
        if (uri != null) {
            pendingRestoreUri = uri
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // Preferred Trip Meter Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DirectionsCar,
                            contentDescription = "Trip Meter Icon",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Preferred Trip Meter",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Select which trip meter reading to use when creating odometer events from photos that show both Trip A and Trip B.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Option: Trip A
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.updatePreferredTripMeter(PreferredTripMeter.TRIP_A) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = preferredTripMeter == PreferredTripMeter.TRIP_A,
                            onClick = { viewModel.updatePreferredTripMeter(PreferredTripMeter.TRIP_A) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Trip A",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Use Trip A reading by default",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Option: Trip B
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.updatePreferredTripMeter(PreferredTripMeter.TRIP_B) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = preferredTripMeter == PreferredTripMeter.TRIP_B,
                            onClick = { viewModel.updatePreferredTripMeter(PreferredTripMeter.TRIP_B) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Trip B",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Use Trip B reading by default",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Option: Calculate / Odometer
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.updatePreferredTripMeter(PreferredTripMeter.ANY) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = preferredTripMeter == PreferredTripMeter.ANY,
                            onClick = { viewModel.updatePreferredTripMeter(PreferredTripMeter.ANY) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Calculate / Total Odometer",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Calculate based on vehicle odometer reading",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Photo Management Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = "Photo Management Icon",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Photo Management",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Automatically move processed photos out of your main camera roll to a dedicated folder after review is complete.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Toggle Switch
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val newState = !movePhotosOnComplete
                                if (newState && completedPhotosFolderUri == null) {
                                    folderPickerLauncher.launch(null)
                                } else {
                                    viewModel.updateMovePhotosOnComplete(newState)
                                }
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Move completed photos",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = if (movePhotosOnComplete) "Enabled" else "Disabled",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = movePhotosOnComplete,
                            onCheckedChange = { isChecked ->
                                if (isChecked && completedPhotosFolderUri == null) {
                                    folderPickerLauncher.launch(null)
                                } else {
                                    viewModel.updateMovePhotosOnComplete(isChecked)
                                }
                            }
                        )
                    }

                    // Folder Selection (visible when enabled)
                    if (movePhotosOnComplete) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Divider()
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { folderPickerLauncher.launch(null) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Destination Folder",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = completedPhotosFolderName ?: "VehicleLogAI/Completed (Default)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            OutlinedButton(
                                onClick = { folderPickerLauncher.launch(null) },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Text("Change")
                            }
                        }
                    }
                }
            }

            // Backup & Restore Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Backup,
                            contentDescription = "Backup & Restore Icon",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Backup & Restore",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Export your vehicles, log events, review queue, and scanned photo records to a single JSON backup file, or restore from a previous backup.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    if (isBackupInProgress) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Processing backup...",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = {
                                    val dateStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                                    exportLauncher.launch("VehicleLogAI_Backup_$dateStr.json")
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Export Backup")
                            }

                            OutlinedButton(
                                onClick = {
                                    viewModel.scanForBackupFiles(context)
                                    showRestorePickerDialog = true
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Upload,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Restore Data")
                            }
                        }
                    }
                }
            }

            // Diagnostics & Debug Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.BugReport,
                            contentDescription = "Diagnostics Icon",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Diagnostics & System Logs",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "View app metrics, database statistics, OCR success rates, uncaught crash reports, and diagnostic logs.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = { navController.navigate(Screen.Debug.route) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.BugReport,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Open Diagnostics & Debug Screen")
                    }
                }
            }

            // About & Usage Tips Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Info Icon",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "About Trip Meters",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "• Trip A is commonly used for individual trips or fuel-fill intervals.\n" +
                                "• Trip B is commonly used for oil-change or maintenance intervals.\n" +
                                "• When both are detected in a dashboard photo, your preferred meter is selected automatically, but you can always switch it on the review screen.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // Confirmation dialog before executing restore
    pendingRestoreUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingRestoreUri = null },
            title = { Text("Restore Backup?") },
            text = {
                Text("Restoring will replace all existing vehicles, timeline events, and review queue items with the contents of this backup.\n\nAre you sure you want to proceed?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        val toRestore = uri
                        pendingRestoreUri = null
                        viewModel.importBackup(context, toRestore)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Restore")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestoreUri = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Result dialog showing backup/restore outcome
    backupStatusMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { viewModel.clearStatusMessage() },
            title = { Text("Backup Status") },
            text = { Text(message) },
            confirmButton = {
                Button(onClick = { viewModel.clearStatusMessage() }) {
                    Text("OK")
                }
            }
        )
    }

    // In-App Backup File Picker Dialog
    if (showRestorePickerDialog) {
        RestoreBackupPickerDialog(
            backupFiles = discoveredBackupFiles,
            isScanning = isScanningBackups,
            onSelectFile = { uri ->
                showRestorePickerDialog = false
                pendingRestoreUri = uri
            },
            onSelectFolderUri = { folderUri ->
                viewModel.scanFolderTreeUri(context, folderUri)
            },
            onBrowseSystemPicker = {
                showRestorePickerDialog = false
                restoreLauncher.launch(
                    arrayOf(
                        "*/*",
                        "application/json",
                        "text/json",
                        "text/plain",
                        "application/octet-stream",
                        "text/x-json"
                    )
                )
            },
            onDismiss = { showRestorePickerDialog = false }
        )
    }
}

class OpenBackupDocumentContract : ActivityResultContract<Array<String>, Uri?>() {
    override fun createIntent(context: Context, input: Array<String>): Intent {
        return Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            // Use */* with EXTRA_MIME_TYPES so all document providers, Download managers,
            // DCIM folders, and OEM file explorers show and enable files without graying them out
            type = "*/*"
            val mimeTypes = if (input.isNotEmpty()) input else arrayOf(
                "*/*",
                "application/json",
                "text/json",
                "text/plain",
                "application/octet-stream",
                "text/x-json"
            )
            putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)
        }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
        return if (resultCode == android.app.Activity.RESULT_OK) intent?.data else null
    }
}
