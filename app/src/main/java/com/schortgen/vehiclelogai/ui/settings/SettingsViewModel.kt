package com.schortgen.vehiclelogai.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.schortgen.vehiclelogai.data.local.VehicleLogDatabase
import com.schortgen.vehiclelogai.data.repository.BackupRepository
import com.schortgen.vehiclelogai.data.repository.PreferredTripMeter
import com.schortgen.vehiclelogai.data.repository.SettingsRepository
import com.schortgen.vehiclelogai.service.PhotoMoverService
import com.schortgen.vehiclelogai.util.BackupFileItem
import com.schortgen.vehiclelogai.util.BackupFileScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val backupRepository: BackupRepository,
    private val photoMoverService: PhotoMoverService? = null,
    private val database: VehicleLogDatabase? = null
) : ViewModel() {

    val preferredTripMeter: StateFlow<PreferredTripMeter> = settingsRepository.preferredTripMeter
    val movePhotosOnComplete: StateFlow<Boolean> = settingsRepository.movePhotosOnComplete
    val completedPhotosFolderUri: StateFlow<String?> = settingsRepository.completedPhotosFolderUri
    val completedPhotosFolderName: StateFlow<String?> = settingsRepository.completedPhotosFolderName
    val hasPromptedPhotoDestination: StateFlow<Boolean> = settingsRepository.hasPromptedPhotoDestination

    private val _isBackupInProgress = MutableStateFlow(false)
    val isBackupInProgress: StateFlow<Boolean> = _isBackupInProgress.asStateFlow()

    private val _backupStatusMessage = MutableStateFlow<String?>(null)
    val backupStatusMessage: StateFlow<String?> = _backupStatusMessage.asStateFlow()

    private val _discoveredBackupFiles = MutableStateFlow<List<BackupFileItem>>(emptyList())
    val discoveredBackupFiles: StateFlow<List<BackupFileItem>> = _discoveredBackupFiles.asStateFlow()

    private val _isScanningBackups = MutableStateFlow(false)
    val isScanningBackups: StateFlow<Boolean> = _isScanningBackups.asStateFlow()

    private val _isMigratingPhotos = MutableStateFlow(false)
    val isMigratingPhotos: StateFlow<Boolean> = _isMigratingPhotos.asStateFlow()

    private val _migrationStatusMessage = MutableStateFlow<String?>(null)
    val migrationStatusMessage: StateFlow<String?> = _migrationStatusMessage.asStateFlow()

    fun moveAllPhotosToNewFolder(newFolderTreeUri: Uri, newFolderName: String) {
        val mover = photoMoverService
        val db = database
        if (mover == null || db == null) {
            _migrationStatusMessage.value = "Photo migration service unavailable."
            return
        }

        viewModelScope.launch {
            _isMigratingPhotos.value = true
            _migrationStatusMessage.value = null
            val result = mover.migrateAllPhotosToNewFolder(newFolderTreeUri, newFolderName, db)
            _isMigratingPhotos.value = false
            if (result.success) {
                _migrationStatusMessage.value = "Successfully moved ${result.movedCount} photo(s) to \"$newFolderName\". All timeline photo paths have been updated."
            } else {
                _migrationStatusMessage.value = "Failed to move photos: ${result.errorMessage ?: "Unknown error"}"
            }
        }
    }

    fun clearMigrationStatusMessage() {
        _migrationStatusMessage.value = null
    }

    fun scanForBackupFiles(context: Context) {
        viewModelScope.launch {
            _isScanningBackups.value = true
            val results = withContext(Dispatchers.IO) {
                BackupFileScanner.scanDeviceForBackups(context)
            }
            _discoveredBackupFiles.value = results
            _isScanningBackups.value = false
        }
    }

    fun scanFolderTreeUri(context: Context, treeUri: Uri) {
        viewModelScope.launch {
            _isScanningBackups.value = true
            val results = withContext(Dispatchers.IO) {
                BackupFileScanner.scanDocumentTree(context, treeUri)
            }
            _discoveredBackupFiles.value = results
            _isScanningBackups.value = false
        }
    }

    fun updatePreferredTripMeter(meter: PreferredTripMeter) {
        settingsRepository.setPreferredTripMeter(meter)
    }

    fun updateMovePhotosOnComplete(enabled: Boolean) {
        settingsRepository.setMovePhotosOnComplete(enabled)
    }

    fun setCompletedPhotosFolder(uri: String?, displayName: String?) {
        settingsRepository.setCompletedPhotosFolder(uri, displayName)
    }

    fun markPromptedPhotoDestination() {
        settingsRepository.setHasPromptedPhotoDestination(true)
    }

    fun exportBackup(context: Context, uri: Uri) {
        viewModelScope.launch {
            _isBackupInProgress.value = true
            _backupStatusMessage.value = null
            val result = backupRepository.exportBackup(context, uri)
            _isBackupInProgress.value = false
            result.fold(
                onSuccess = { data ->
                    _backupStatusMessage.value = "Backup created successfully!\n\nVehicles: ${data.vehicles.size}\nEvents: ${data.events.size}\nReview Queue Items: ${data.reviewItems.size}\nScanned Photos: ${data.scannedPhotos.size}"
                },
                onFailure = { error ->
                    _backupStatusMessage.value = "Backup export failed: ${error.localizedMessage ?: "Unknown error"}"
                }
            )
        }
    }

    fun importBackup(context: Context, uri: Uri) {
        viewModelScope.launch {
            _isBackupInProgress.value = true
            _backupStatusMessage.value = null
            val result = backupRepository.importBackup(context, uri)
            _isBackupInProgress.value = false
            result.fold(
                onSuccess = { data ->
                    _backupStatusMessage.value = "Backup restored successfully!\n\nRestored Records:\n• Vehicles: ${data.vehicles.size}\n• Events: ${data.events.size}\n• Review Queue Items: ${data.reviewItems.size}\n• Scanned Photos: ${data.scannedPhotos.size}"
                },
                onFailure = { error ->
                    _backupStatusMessage.value = "Backup restore failed: ${error.localizedMessage ?: "Invalid or corrupted backup file"}"
                }
            )
        }
    }

    fun clearStatusMessage() {
        _backupStatusMessage.value = null
    }
}

class SettingsViewModelFactory(
    private val settingsRepository: SettingsRepository,
    private val backupRepository: BackupRepository,
    private val photoMoverService: PhotoMoverService? = null,
    private val database: VehicleLogDatabase? = null
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            return SettingsViewModel(settingsRepository, backupRepository, photoMoverService, database) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
