package com.schortgen.vehiclelogai.data.models

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.documentfile.provider.DocumentFile
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.schortgen.vehiclelogai.data.models.Vehicle
import java.io.File

@Entity(
    tableName = "events",
    foreignKeys = [
        ForeignKey(
            entity = Vehicle::class,
            parentColumns = ["id"],
            childColumns = ["vehicleId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["vehicleId"]),
        Index(value = ["eventDate"]),
        Index(value = ["eventType"])
    ]
)
data class Event(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vehicleId: Long,
    val eventType: EventType,
    val eventDate: Long,
    val createdDate: Long = System.currentTimeMillis(),
    val confidence: Float? = null,
    val verified: Boolean = false,
    val notes: String? = null,
    // Fuel-specific fields
    val odometer: Int? = null,
    val tripDistance: Double? = null,
    val gallons: Double? = null,
    val pricePerGallon: Double? = null,
    val totalCost: Double? = null,
    val location: String? = null,
    val photoPath: String? = null
)

fun Event.calculateMpg(allEvents: List<Event> = emptyList()): Double? {
    if (eventType != EventType.FUEL) return null
    val gal = gallons ?: return null
    if (gal <= 0) return null

    if (tripDistance != null && tripDistance > 0) {
        return tripDistance / gal
    }

    if (odometer != null && vehicleId != null && allEvents.isNotEmpty()) {
        val prevFuelEvent = allEvents
            .filter { it.vehicleId == vehicleId && it.eventType == EventType.FUEL && it.odometer != null }
            .filter { it.eventDate < eventDate || (it.eventDate == eventDate && it.id < id) }
            .maxByOrNull { it.eventDate }
        if (prevFuelEvent?.odometer != null && odometer > prevFuelEvent.odometer) {
            val trip = (odometer - prevFuelEvent.odometer).toDouble()
            return trip / gal
        }
    }
    return null
}

fun Event.getPhotoPaths(): List<String> {
    if (photoPath.isNullOrBlank()) return emptyList()
    val clean = photoPath.trim().removePrefix("[").removeSuffix("]").replace("\"", "").replace("'", "")
    return clean.split(',', '|', '\n', ';')
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
}

fun String.getPhotoStatusInfo(context: Context? = null): Triple<String, String, Boolean> {
    val trimmed = this.trim()
    if (trimmed.isEmpty()) return Triple("", "", false)
    val fileName = extractPhotoFileName(context)
    
    // Check if it's a web URL
    if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
        return Triple(fileName, trimmed, true)
    }

    // Direct File check without disk heavy tree walking
    val cleanPath = if (trimmed.startsWith("file://")) trimmed.removePrefix("file://") else trimmed
    val directFile = File(cleanPath)
    if (directFile.exists() && directFile.canRead()) {
        return Triple(fileName, directFile.absolutePath, true)
    }

    // Content URI quick check
    if (trimmed.startsWith("content://")) {
        var canRead = false
        if (context != null) {
            try {
                val uri = Uri.parse(trimmed)
                canRead = context.contentResolver.openInputStream(uri)?.use { true } ?: false
            } catch (_: Exception) {
                canRead = false
            }
        }
        return Triple(fileName, trimmed, canRead)
    }

    // Fallback path resolution
    val imageModel = try { toImageModel(context) } catch (_: Exception) { cleanPath }
    var resolvedLocation = cleanPath
    var isResolved = false

    when (imageModel) {
        is File -> {
            resolvedLocation = imageModel.absolutePath
            isResolved = imageModel.exists() && imageModel.canRead()
        }
        is Uri -> {
            resolvedLocation = imageModel.toString()
            isResolved = true
        }
        is String -> {
            resolvedLocation = imageModel
            if (imageModel.startsWith("http://") || imageModel.startsWith("https://")) {
                isResolved = true
            } else {
                val f = File(imageModel)
                isResolved = f.exists() && f.canRead()
            }
        }
    }

    return Triple(fileName, resolvedLocation, isResolved)
}

fun String.extractPhotoFileName(context: Context? = null): String {
    val trimmed = this.trim()
    if (trimmed.isEmpty()) return ""
    val rawClean = trimmed.removePrefix("file://")
    val fileName = try {
        val decoded = Uri.decode(rawClean)
        decoded.substringAfterLast('/').substringAfterLast('\\').substringBefore('?').trim()
    } catch (_: Exception) {
        rawClean.substringAfterLast('/').substringAfterLast('\\').substringBefore('?').trim()
    }
    if (trimmed.startsWith("content://") && context != null && (fileName.all { it.isDigit() } || fileName.isEmpty())) {
        try {
            val uri = Uri.parse(trimmed)
            context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        val name = cursor.getString(nameIndex)
                        if (!name.isNullOrBlank()) return name
                    }
                }
            }
        } catch (_: Exception) {}
    }
    return if (fileName.isNotBlank()) fileName else trimmed
}

fun String.toImageModel(context: Context? = null): Any {
    val trimmed = this.trim()
    if (trimmed.isEmpty()) return ""

    // 1. Web URLs
    if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
        return trimmed
    }

    // 2. Direct Content URI
    if (trimmed.startsWith("content://")) {
        val uri = try { Uri.parse(trimmed) } catch (_: Exception) { null }
        if (uri != null) {
            if (context != null) {
                val canRead = try {
                    context.contentResolver.openInputStream(uri)?.use { true } ?: false
                } catch (_: Exception) {
                    false
                }
                if (canRead) {
                    return uri
                }
            } else {
                return uri
            }
        }
    }

    // 3. Direct File / file:// URI
    val cleanPath = if (trimmed.startsWith("file://")) trimmed.removePrefix("file://") else trimmed
    val directFile = File(cleanPath)
    if (directFile.exists() && directFile.canRead()) {
        return directFile
    }

    // 4. Smart fallback for restored backups / migrated photos / SAF tree references:
    // Extract filename (handling URL decoding, query params, etc.) and check photo storage directories & MediaStore
    val rawClean = trimmed.removePrefix("file://")
    val fileNames = mutableListOf<String>()
    try {
        val decoded = Uri.decode(rawClean)
        val name1 = decoded.substringAfterLast('/').substringAfterLast('\\').substringBefore('?').trim()
        if (name1.isNotEmpty()) fileNames.add(name1)
    } catch (_: Exception) {}
    val name2 = rawClean.substringAfterLast('/').substringAfterLast('\\').substringBefore('?').trim()
    if (name2.isNotEmpty() && !fileNames.contains(name2)) fileNames.add(name2)

    if (fileNames.isNotEmpty() && context != null) {
        val basePicturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        val baseDcimDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
        val candidateDirs = listOfNotNull(
            File(basePicturesDir, "ProcessedVehiclePhotos"),
            File(baseDcimDir, "ProcessedVehiclePhotos"),
            context.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
            context.getExternalFilesDir("ProcessedVehiclePhotos"),
            context.getExternalFilesDir(null),
            File(context.filesDir, "photos"),
            File(context.filesDir, "receipts"),
            File(context.filesDir, "images"),
            File(context.filesDir, "ProcessedVehiclePhotos"),
            basePicturesDir,
            baseDcimDir,
            File(baseDcimDir, "Camera"),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            context.filesDir,
            context.cacheDir,
            context.externalCacheDir
        )

        for (fileName in fileNames) {
            for (dir in candidateDirs) {
                if (dir.exists()) {
                    val candidateFile = File(dir, fileName)
                    if (candidateFile.exists() && candidateFile.canRead()) {
                        return candidateFile
                    }
                }
            }

            // Check user-configured SAF completed photos folder if any
            try {
                val app = context.applicationContext as? com.schortgen.vehiclelogai.VehicleLogAIApplication
                val folderUriStr = app?.settingsRepository?.getCompletedPhotosFolderUri()
                if (!folderUriStr.isNullOrBlank() && folderUriStr.startsWith("content://")) {
                    val treeUri = Uri.parse(folderUriStr)
                    val targetDir = DocumentFile.fromTreeUri(context, treeUri)
                    val found = targetDir?.findFile(fileName)
                    if (found != null && found.exists()) {
                        return found.uri
                    }
                }
            } catch (_: Exception) {}

            // Query MediaStore by DISPLAY_NAME or TITLE as a fallback in case the photo is in media storage under a content URI
            try {
                val projection = arrayOf(android.provider.MediaStore.Images.Media._ID)
                val selection = "${android.provider.MediaStore.Images.Media.DISPLAY_NAME} = ? OR ${android.provider.MediaStore.Images.Media.TITLE} = ?"
                val selectionArgs = arrayOf(fileName, fileName.substringBeforeLast('.'))
                context.contentResolver.query(
                    android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idColumn = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Images.Media._ID)
                        val id = cursor.getLong(idColumn)
                        val contentUri = android.content.ContentUris.withAppendedId(
                            android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            id
                        )
                        val canRead = try {
                            context.contentResolver.openInputStream(contentUri)?.use { true } ?: false
                        } catch (_: Exception) {
                            false
                        }
                        if (canRead) {
                            return contentUri
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    // Fallback parsing
    return when {
        trimmed.startsWith("content://") -> Uri.parse(trimmed)
        trimmed.startsWith("file://") -> Uri.parse(trimmed)
        trimmed.startsWith("/") -> File(trimmed)
        else -> {
            val file = File(cleanPath)
            if (file.exists() || cleanPath.contains("/")) file else Uri.parse(trimmed)
        }
    }
}


