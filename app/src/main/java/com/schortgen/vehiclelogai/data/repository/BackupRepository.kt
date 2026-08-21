package com.schortgen.vehiclelogai.data.repository

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.room.withTransaction
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.schortgen.vehiclelogai.data.local.VehicleLogDatabase
import com.schortgen.vehiclelogai.data.models.BackupData
import com.schortgen.vehiclelogai.data.models.BackupSettings
import com.schortgen.vehiclelogai.data.models.clearImageModelCache
import com.schortgen.vehiclelogai.debug.DiagnosticLogger
import com.schortgen.vehiclelogai.service.PhotoPathRelinker
import com.schortgen.vehiclelogai.service.RelinkReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class BackupRepository(
    private val database: VehicleLogDatabase,
    private val settingsRepository: SettingsRepository
) {
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    /**
     * Standard JSON Database Export (lightweight metadata backup)
     */
    suspend fun exportBackup(context: Context, uri: Uri): Result<BackupData> = withContext(Dispatchers.IO) {
        try {
            val backupData = buildBackupDataObject()

            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                OutputStreamWriter(outputStream, Charsets.UTF_8).use { writer ->
                    gson.toJson(backupData, writer)
                }
            } ?: return@withContext Result.failure(Exception("Could not open file output stream for writing."))

            DiagnosticLogger.i("BackupRepository", "Exported JSON backup: ${backupData.vehicles.size} vehicles, ${backupData.events.size} events")
            Result.success(backupData)
        } catch (e: Exception) {
            DiagnosticLogger.e("BackupRepository", "Failed to export JSON backup", e)
            Result.failure(e)
        }
    }

    /**
     * Complete Full ZIP Archive Export (Database JSON + All Physical Image Files)
     */
    suspend fun exportZipBackup(context: Context, uri: Uri): Result<BackupData> = withContext(Dispatchers.IO) {
        try {
            val backupData = buildBackupDataObject()
            val allPhotoPaths = mutableSetOf<String>()

            // Collect all referenced photo paths
            backupData.events.forEach { event ->
                val p = event.photoPath
                if (!p.isNullOrBlank()) {
                    if (p.startsWith("[") && p.endsWith("]")) {
                        try {
                            val type = object : TypeToken<List<String>>() {}.type
                            val list: List<String> = gson.fromJson(p, type)
                            allPhotoPaths.addAll(list.filter { it.isNotBlank() })
                        } catch (_: Exception) {
                            allPhotoPaths.add(p)
                        }
                    } else {
                        allPhotoPaths.add(p)
                    }
                }
            }
            backupData.reviewItems.forEach { if (it.photoPath.isNotBlank()) allPhotoPaths.add(it.photoPath) }
            backupData.scannedPhotos.forEach { if (it.filePath.isNotBlank()) allPhotoPaths.add(it.filePath) }

            val outStream = context.contentResolver.openOutputStream(uri)
                ?: return@withContext Result.failure(Exception("Could not open output stream for ZIP backup."))

            ZipOutputStream(BufferedOutputStream(outStream)).use { zipOut ->
                // 1. Write backup_data.json entry
                val jsonEntry = ZipEntry("backup_data.json")
                zipOut.putNextEntry(jsonEntry)
                val jsonBytes = gson.toJson(backupData).toByteArray(Charsets.UTF_8)
                zipOut.write(jsonBytes)
                zipOut.closeEntry()

                // 2. Write each referenced photo into photos/
                var photosPacked = 0
                val writtenEntryNames = mutableSetOf<String>()

                for (photoPath in allPhotoPaths) {
                    val inStream = openPhotoInputStream(context, photoPath) ?: continue
                    val cleanFileName = PhotoPathRelinker.extractCandidateFileNames(photoPath).firstOrNull()
                        ?: "photo_${System.currentTimeMillis()}_$photosPacked.jpg"

                    var entryName = "photos/$cleanFileName"
                    var collisionCounter = 1
                    while (writtenEntryNames.contains(entryName)) {
                        val base = cleanFileName.substringBeforeLast(".")
                        val ext = cleanFileName.substringAfterLast(".", "jpg")
                        entryName = "photos/${base}_$collisionCounter.$ext"
                        collisionCounter++
                    }
                    writtenEntryNames.add(entryName)

                    try {
                        val photoEntry = ZipEntry(entryName)
                        zipOut.putNextEntry(photoEntry)
                        inStream.use { input ->
                            input.copyTo(zipOut, bufferSize = 32768)
                        }
                        zipOut.closeEntry()
                        photosPacked++
                    } catch (e: Exception) {
                        DiagnosticLogger.w("BackupRepository", "Failed to pack photo into ZIP: $photoPath", e)
                    }
                }

                DiagnosticLogger.i("BackupRepository", "ZIP Backup complete: packed $photosPacked photos + database JSON.")
            }

            Result.success(backupData)
        } catch (e: Exception) {
            DiagnosticLogger.e("BackupRepository", "Failed to export ZIP backup", e)
            Result.failure(e)
        }
    }

    /**
     * Universal Import: Handles both .JSON and .ZIP backups seamlessly,
     * restoring the database, extracting photos (if ZIP), and auto-relinking photos.
     */
    suspend fun importBackup(context: Context, uri: Uri, clearExisting: Boolean = true): Result<BackupData> = withContext(Dispatchers.IO) {
        try {
            // Check if file is a ZIP archive
            val isZip = isZipFile(context, uri)

            val backupData = if (isZip) {
                importZipBackupInternal(context, uri, clearExisting)
            } else {
                importJsonBackupInternal(context, uri, clearExisting)
            }

            // Apply Settings
            runCatching {
                val meter = PreferredTripMeter.valueOf(backupData.settings.preferredTripMeter)
                settingsRepository.setPreferredTripMeter(meter)
            }

            // Automatic Safety Pass: Relink any missing photo paths to live files
            runCatching {
                PhotoPathRelinker.relinkAllPhotos(
                    context = context,
                    database = database,
                    settingsRepository = settingsRepository
                )
            }

            DiagnosticLogger.i("BackupRepository", "Imported backup successfully: ${backupData.vehicles.size} vehicles, ${backupData.events.size} events")
            Result.success(backupData)
        } catch (e: Exception) {
            DiagnosticLogger.e("BackupRepository", "Failed to import backup", e)
            Result.failure(e)
        }
    }

    /**
     * Relink missing photo paths manually or pointing to a user-selected directory
     */
    suspend fun relinkPhotos(
        context: Context,
        customFolderTreeUri: Uri? = null,
        customFolderPath: String? = null
    ): RelinkReport = withContext(Dispatchers.IO) {
        PhotoPathRelinker.relinkAllPhotos(
            context = context,
            database = database,
            settingsRepository = settingsRepository,
            customFolderTreeUri = customFolderTreeUri,
            customFolderPath = customFolderPath
        )
    }

    private suspend fun buildBackupDataObject(): BackupData {
        val vehicles = database.vehicleDao().getAllVehicles()
        val events = database.eventDao().getAllEvents()
        val reviewItems = database.reviewItemDao().getAll()
        val scannedPhotos = database.scannedPhotoDao().getAllScannedPhotos()
        val tripMeter = settingsRepository.preferredTripMeter.value.name

        return BackupData(
            version = 1,
            appName = "VehicleLogAI",
            timestamp = System.currentTimeMillis(),
            settings = BackupSettings(preferredTripMeter = tripMeter),
            vehicles = vehicles,
            events = events,
            reviewItems = reviewItems,
            scannedPhotos = scannedPhotos
        )
    }

    private suspend fun importJsonBackupInternal(context: Context, uri: Uri, clearExisting: Boolean): BackupData {
        val jsonString = runCatching {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                inputStream.bufferedReader(Charsets.UTF_8).readText()
            }
        }.getOrNull() ?: runCatching {
            val path = uri.path
            if (path != null) {
                val file = File(path)
                if (file.exists()) file.readText(Charsets.UTF_8) else null
            } else null
        }.getOrNull() ?: throw Exception("Could not open the selected backup file for reading.")

        val backupData = try {
            gson.fromJson(jsonString, BackupData::class.java)
        } catch (e: Exception) {
            val mimeType = runCatching { context.contentResolver.getType(uri) }.getOrNull()
            if (mimeType?.startsWith("image/") == true) {
                throw Exception("Selected file is an image. Please select a valid JSON or ZIP backup file.")
            }
            throw Exception("Invalid backup format: ${e.message}")
        }

        if (backupData == null || (backupData.vehicles.isEmpty() && backupData.events.isEmpty() && backupData.reviewItems.isEmpty())) {
            throw Exception("The selected file does not contain valid VehicleLogAI backup data.")
        }

        restoreDatabaseRows(backupData, clearExisting)
        return backupData
    }

    private suspend fun importZipBackupInternal(context: Context, uri: Uri, clearExisting: Boolean): BackupData {
        val inStream = context.contentResolver.openInputStream(uri)
            ?: throw Exception("Could not open ZIP backup file.")

        // Destination folder for extracted photos
        val targetDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            "ProcessedVehiclePhotos"
        ).apply { if (!exists()) mkdirs() }

        var backupJsonString: String? = null
        val extractedPhotosMap = mutableMapOf<String, String>() // filename.lowercase() -> newAbsolutePath

        ZipInputStream(BufferedInputStream(inStream)).use { zipIn ->
            var entry: ZipEntry? = zipIn.nextEntry
            val buffer = ByteArray(32768)

            while (entry != null) {
                val entryName = entry.name
                if (!entry.isDirectory) {
                    if (entryName.equals("backup_data.json", ignoreCase = true) || entryName.endsWith(".json", ignoreCase = true)) {
                        backupJsonString = zipIn.bufferedReader(Charsets.UTF_8).readText()
                    } else if (entryName.startsWith("photos/") || entryName.endsWith(".jpg", ignoreCase = true) || entryName.endsWith(".jpeg", ignoreCase = true) || entryName.endsWith(".png", ignoreCase = true)) {
                        val fileName = File(entryName).name
                        val destFile = File(targetDir, fileName)
                        FileOutputStream(destFile).use { out ->
                            var len: Int
                            while (zipIn.read(buffer).also { len = it } > 0) {
                                out.write(buffer, 0, len)
                            }
                        }
                        extractedPhotosMap[fileName.lowercase()] = destFile.absolutePath
                    }
                }
                zipIn.closeEntry()
                entry = zipIn.nextEntry
            }
        }

        if (backupJsonString.isNullOrBlank()) {
            throw Exception("The ZIP file does not contain a valid backup_data.json manifest.")
        }

        val rawBackupData = gson.fromJson(backupJsonString, BackupData::class.java)
            ?: throw Exception("Invalid backup data format inside ZIP.")

        // Update photo paths in BackupData if photos were extracted
        val updatedEvents = rawBackupData.events.map { event ->
            val p = event.photoPath
            if (!p.isNullOrBlank()) {
                val updatedPath = mapExtractedPhotoPath(p, extractedPhotosMap)
                event.copy(photoPath = updatedPath)
            } else event
        }

        val updatedReviewItems = rawBackupData.reviewItems.map { item ->
            if (item.photoPath.isNotBlank()) {
                val updatedPath = mapExtractedPhotoPath(item.photoPath, extractedPhotosMap)
                item.copy(photoPath = updatedPath)
            } else item
        }

        val updatedScannedPhotos = rawBackupData.scannedPhotos.map { sp ->
            if (sp.filePath.isNotBlank()) {
                val updatedPath = mapExtractedPhotoPath(sp.filePath, extractedPhotosMap)
                sp.copy(filePath = updatedPath)
            } else sp
        }

        val finalBackupData = rawBackupData.copy(
            events = updatedEvents,
            reviewItems = updatedReviewItems,
            scannedPhotos = updatedScannedPhotos
        )

        restoreDatabaseRows(finalBackupData, clearExisting)
        clearImageModelCache()

        return finalBackupData
    }

    private fun mapExtractedPhotoPath(originalPath: String, extractedMap: Map<String, String>): String {
        val trimmed = originalPath.trim()
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            return try {
                val type = object : TypeToken<List<String>>() {}.type
                val list: List<String> = gson.fromJson(trimmed, type)
                val mapped = list.map { single ->
                    val names = PhotoPathRelinker.extractCandidateFileNames(single)
                    var found = single
                    for (n in names) {
                        val match = extractedMap[n.lowercase()]
                        if (match != null) {
                            found = match
                            break
                        }
                    }
                    found
                }
                gson.toJson(mapped)
            } catch (_: Exception) {
                originalPath
            }
        } else {
            val names = PhotoPathRelinker.extractCandidateFileNames(trimmed)
            for (n in names) {
                val match = extractedMap[n.lowercase()]
                if (match != null) return match
            }
            return originalPath
        }
    }

    private suspend fun restoreDatabaseRows(backupData: BackupData, clearExisting: Boolean) {
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
    }

    private fun isZipFile(context: Context, uri: Uri): Boolean {
        // 1. Check filename/MIME
        val name = uri.lastPathSegment ?: ""
        if (name.endsWith(".zip", ignoreCase = true)) return true
        val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull()
        if (mime == "application/zip" || mime == "application/x-zip-compressed") return true

        // 2. Check ZIP header magic bytes (0x50, 0x4B, 0x03, 0x04)
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val header = ByteArray(4)
                val read = stream.read(header)
                read == 4 && header[0] == 0x50.toByte() && header[1] == 0x4B.toByte() &&
                        (header[2] == 0x03.toByte() || header[2] == 0x05.toByte() || header[2] == 0x07.toByte())
            } ?: false
        } catch (_: Exception) {
            false
        }
    }

    private fun openPhotoInputStream(context: Context, photoPath: String): InputStream? {
        return try {
            val clean = photoPath.trim()
            if (clean.startsWith("content://")) {
                context.contentResolver.openInputStream(Uri.parse(clean))
            } else {
                val f = if (clean.startsWith("file://")) File(clean.removePrefix("file://")) else File(clean)
                if (f.exists() && f.canRead()) f.inputStream() else null
            }
        } catch (_: Exception) {
            null
        }
    }
}
