package com.schortgen.vehiclelogai.data.repository

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.schortgen.vehiclelogai.data.local.VehicleLogDatabase
import com.schortgen.vehiclelogai.data.models.BackupData
import com.schortgen.vehiclelogai.data.models.BackupSettings
import com.schortgen.vehiclelogai.debug.DiagnosticLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStreamReader
import java.io.OutputStreamWriter

class BackupRepository(
    private val database: VehicleLogDatabase,
    private val settingsRepository: SettingsRepository
) {
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    suspend fun exportBackup(context: Context, uri: Uri): Result<BackupData> = withContext(Dispatchers.IO) {
        try {
            val vehicles = database.vehicleDao().getAllVehicles()
            val events = database.eventDao().getAllEvents()
            val reviewItems = database.reviewItemDao().getAll()
            val scannedPhotos = database.scannedPhotoDao().getAllScannedPhotos()
            val tripMeter = settingsRepository.preferredTripMeter.value.name

            val backupData = BackupData(
                version = 1,
                appName = "VehicleLogAI",
                timestamp = System.currentTimeMillis(),
                settings = BackupSettings(preferredTripMeter = tripMeter),
                vehicles = vehicles,
                events = events,
                reviewItems = reviewItems,
                scannedPhotos = scannedPhotos
            )

            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                OutputStreamWriter(outputStream, Charsets.UTF_8).use { writer ->
                    gson.toJson(backupData, writer)
                }
            } ?: return@withContext Result.failure(Exception("Could not open file output stream for writing."))

            DiagnosticLogger.i("BackupRepository", "Exported backup successfully: ${vehicles.size} vehicles, ${events.size} events, ${reviewItems.size} review items, ${scannedPhotos.size} photos")
            Result.success(backupData)
        } catch (e: Exception) {
            DiagnosticLogger.e("BackupRepository", "Failed to export backup", e)
            Result.failure(e)
        }
    }

    suspend fun importBackup(context: Context, uri: Uri, clearExisting: Boolean = true): Result<BackupData> = withContext(Dispatchers.IO) {
        try {
            val jsonString: String = runCatching {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    inputStream.bufferedReader(Charsets.UTF_8).readText()
                }
            }.getOrNull() ?: runCatching {
                val path = uri.path
                if (path != null) {
                    val file = java.io.File(path)
                    if (file.exists()) file.readText(Charsets.UTF_8) else null
                } else null
            }.getOrNull() ?: return@withContext Result.failure(Exception("Could not open the selected backup file for reading."))

            val backupData = try {
                gson.fromJson(jsonString, BackupData::class.java)
            } catch (e: Exception) {
                val mimeType = runCatching { context.contentResolver.getType(uri) }.getOrNull()
                if (mimeType?.startsWith("image/") == true) {
                    return@withContext Result.failure(Exception("Selected file is an image. Please select a valid JSON backup file."))
                }
                return@withContext Result.failure(Exception("Invalid backup JSON format: ${e.message}"))
            }

            if (backupData == null || (backupData.vehicles.isEmpty() && backupData.events.isEmpty() && backupData.reviewItems.isEmpty())) {
                return@withContext Result.failure(Exception("The selected file does not contain valid VehicleLogAI backup data."))
            }

            database.withTransaction {
                if (clearExisting) {
                    database.scannedPhotoDao().deleteAllScannedPhotos()
                    database.reviewItemDao().deleteAllReviewItems()
                    database.eventDao().deleteAllEvents()
                    database.vehicleDao().deleteAllVehicles()
                }

                if (backupData.vehicles.isNotEmpty()) {
                    database.vehicleDao().insertAll(backupData.vehicles)
                }
                if (backupData.events.isNotEmpty()) {
                    database.eventDao().insertAll(backupData.events)
                }
                if (backupData.reviewItems.isNotEmpty()) {
                    database.reviewItemDao().insertAll(backupData.reviewItems)
                }
                if (backupData.scannedPhotos.isNotEmpty()) {
                    database.scannedPhotoDao().insertAll(backupData.scannedPhotos)
                }
            }

            runCatching {
                val meter = PreferredTripMeter.valueOf(backupData.settings.preferredTripMeter)
                settingsRepository.setPreferredTripMeter(meter)
            }

            DiagnosticLogger.i("BackupRepository", "Imported backup successfully: ${backupData.vehicles.size} vehicles, ${backupData.events.size} events, ${backupData.reviewItems.size} review items, ${backupData.scannedPhotos.size} photos")
            Result.success(backupData)
        } catch (e: Exception) {
            DiagnosticLogger.e("BackupRepository", "Failed to import backup", e)
            Result.failure(e)
        }
    }
}
