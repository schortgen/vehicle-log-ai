package com.schortgen.vehiclelogai.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.schortgen.vehiclelogai.data.repository.BackupRepository
import com.schortgen.vehiclelogai.data.repository.PreferredTripMeter
import com.schortgen.vehiclelogai.data.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val backupRepository: BackupRepository
) : ViewModel() {

    val preferredTripMeter: StateFlow<PreferredTripMeter> = settingsRepository.preferredTripMeter

    private val _isBackupInProgress = MutableStateFlow(false)
    val isBackupInProgress: StateFlow<Boolean> = _isBackupInProgress.asStateFlow()

    private val _backupStatusMessage = MutableStateFlow<String?>(null)
    val backupStatusMessage: StateFlow<String?> = _backupStatusMessage.asStateFlow()

    fun updatePreferredTripMeter(meter: PreferredTripMeter) {
        settingsRepository.setPreferredTripMeter(meter)
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
    private val backupRepository: BackupRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            return SettingsViewModel(settingsRepository, backupRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
