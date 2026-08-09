package com.schortgen.vehiclelogai.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import com.schortgen.vehiclelogai.data.repository.SettingsRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoDestinationPromptDialog(
    settingsRepository: SettingsRepository,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

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
            settingsRepository.setCompletedPhotosFolder(uri.toString(), folderName)
            settingsRepository.setMovePhotosOnComplete(true)
        } else {
            // Default if user canceled picker
            settingsRepository.setCompletedPhotosFolder(null, "Pictures/ProcessedVehiclePhotos")
            settingsRepository.setMovePhotosOnComplete(true)
        }
        settingsRepository.setHasPromptedPhotoDestination(true)
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = {
            settingsRepository.setHasPromptedPhotoDestination(true)
            onDismiss()
        },
        icon = {
            Icon(
                imageVector = Icons.Default.DriveFileMove,
                contentDescription = "Move Photos Icon",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = "Processed Photos Destination",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Where would you like photos moved after data has been extracted and a vehicle event is created?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Moving processed photos keeps your primary Camera/DCIM folder clean and organized. You can change this setting anytime in the Settings tab.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        folderPickerLauncher.launch(null)
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = "Choose Folder",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Choose Custom Folder")
                }

                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        settingsRepository.setCompletedPhotosFolder(null, "Pictures/ProcessedVehiclePhotos")
                        settingsRepository.setMovePhotosOnComplete(true)
                        settingsRepository.setHasPromptedPhotoDestination(true)
                        onDismiss()
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = "Default Folder",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Use Default (Pictures/ProcessedVehiclePhotos)")
                }

                TextButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        settingsRepository.setMovePhotosOnComplete(false)
                        settingsRepository.setHasPromptedPhotoDestination(true)
                        onDismiss()
                    }
                ) {
                    Text("Don't Move Photos")
                }
            }
        },
        dismissButton = null
    )
}
