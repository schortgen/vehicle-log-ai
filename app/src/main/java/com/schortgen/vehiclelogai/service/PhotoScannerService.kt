package com.schortgen.vehiclelogai.service

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.MediaStore.Images
import android.graphics.BitmapFactory
import com.schortgen.vehiclelogai.data.models.PhotoCandidate
import com.schortgen.vehiclelogai.data.models.ProcessingStatus
import com.schortgen.vehiclelogai.data.models.ReviewItem
import com.schortgen.vehiclelogai.data.models.ScannedPhoto
import com.schortgen.vehiclelogai.data.repository.EventRepository
import com.schortgen.vehiclelogai.data.repository.PhotoScannerRepository
import com.schortgen.vehiclelogai.data.repository.ReviewItemRepository
import com.schortgen.vehiclelogai.debug.DiagnosticLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

/**
 * Scans the Android MediaStore for new image files, filters out screenshots,
 * very small images, and unsupported types, then creates ReviewItems for
 * newly discovered photos.
 *
 * Designed so a real OCR engine can be plugged into the ReviewItemRepository
 * or a dedicated OcrService later without changing this scanner.
 */
class PhotoScannerService(
    private val context: Context,
    private val photoScannerRepository: PhotoScannerRepository,
    private val reviewItemRepository: ReviewItemRepository,
    private val eventRepository: EventRepository? = null
) {

    suspend fun clearQueue() = withContext(Dispatchers.IO) {
        reviewItemRepository.deleteAllReviewItems()
        photoScannerRepository.clearAll()
        eventRepository?.deleteUnverifiedEvents()
    }

    private val supportedMimeTypes = setOf(
        "image/jpeg",
        "image/jpg",
        "image/png",
        "image/webp",
        "image/heic",
        "image/heif"
    )

    private val minDimension = 400

    private val screenshotKeywords = setOf(
        "screenshot", "screen shot", "screen_shot",
        "screen capture", "screen_recording",
        "instagram", "facebook", "twitter", "whatsapp",
        "telegram", "snapchat", "tiktok"
    )

    private val nonVehicleKeywords = setOf(
        "flower", "flowers", "plant", "garden", "nature",
        "pet", "dog", "cat", "food", "dish", "meal",
        "selfie", "portrait", "family", "vacation", "beach",
        "party", "concert", "sunset", "sky"
    )

    /**
     * Scan the MediaStore for new photos and create ReviewItems for them.
     * Optionally filtered by start and end date range (in milliseconds).
     *
     * @return the number of new photos imported
     */
    suspend fun scanAndImport(
        startDateMillis: Long? = null,
        endDateMillis: Long? = null,
        clearQueueFirst: Boolean = true
    ): Int = withContext(Dispatchers.IO) {
        val startedAt = System.nanoTime()
        DiagnosticLogger.i("Scanner", "scanAndImport begin (range: $startDateMillis .. $endDateMillis)")
        DiagnosticLogger.logSystemMetrics("Scanner", "Start scan")
        try {
            if (clearQueueFirst) {
                clearQueue()
            }
            val candidates = queryMediaStore(startDateMillis, endDateMillis)
            var importedCount = 0
            var skippedAlreadyImported = 0
            var skippedScreenshots = 0
            var skippedSmall = 0
            var skippedType = 0
            var skippedVehicleRelated = 0

            // Pre-fetch existing state into in-memory sets for O(1) bulk lookups
            val importedIds = photoScannerRepository.getAllImportedIds().toMutableSet()
            val existingReviewItems = reviewItemRepository.getAllReviewItems()
            val existingPaths = existingReviewItems.mapNotNull { it.photoPath }.toMutableSet()
            val existingMediaIds = existingReviewItems.mapNotNull { it.photoPath?.substringAfterLast('/') }.toMutableSet()

            val newReviewItems = mutableListOf<ReviewItem>()
            val newScannedPhotos = mutableListOf<ScannedPhoto>()

            for ((index, candidate) in candidates.withIndex()) {
                if (index > 0 && index % 50 == 0) {
                    DiagnosticLogger.logSystemMetrics("Scanner", "Scanning photo $index/${candidates.size}")
                    yield()
                }
                val candidateMediaId = candidate.uri.substringAfterLast('/')
                val isAlreadyImported = candidate.mediaStoreId in importedIds ||
                    candidate.uri in existingPaths ||
                    (candidateMediaId.isNotEmpty() && candidateMediaId in existingMediaIds)

                if (isAlreadyImported) {
                    skippedAlreadyImported++
                    if (candidate.mediaStoreId !in importedIds) {
                        newScannedPhotos.add(
                            ScannedPhoto(
                                mediaStoreId = candidate.mediaStoreId,
                                uri = candidate.uri,
                                displayName = candidate.displayName,
                                dateTaken = candidate.dateTaken
                            )
                        )
                        importedIds.add(candidate.mediaStoreId)
                    }
                    continue
                }

                if (looksLikeScreenshot(candidate.displayName, candidate.bucket)) {
                    skippedScreenshots++
                    continue
                }

                if (candidate.width in 1..<minDimension || candidate.height in 1..<minDimension) {
                    skippedSmall++
                    continue
                }

                if (candidate.mimeType.isNotBlank() && candidate.mimeType !in supportedMimeTypes) {
                    skippedType++
                    continue
                }

                // Filter out images that are unlikely to be vehicle-related based on filename heuristics
                if (!isVehicleRelated(candidate, candidate.bucket)) {
                    skippedVehicleRelated++
                    continue
                }

                val reviewItem = ReviewItem(
                    photoPath = candidate.uri,
                    captureDate = candidate.dateTaken,
                    status = ProcessingStatus.PENDING,
                    reason = "Imported: ${candidate.displayName}"
                )
                newReviewItems.add(reviewItem)

                val scannedPhoto = ScannedPhoto(
                    mediaStoreId = candidate.mediaStoreId,
                    uri = candidate.uri,
                    displayName = candidate.displayName,
                    dateTaken = candidate.dateTaken
                )
                newScannedPhotos.add(scannedPhoto)

                importedIds.add(candidate.mediaStoreId)
                existingPaths.add(candidate.uri)
                if (candidateMediaId.isNotEmpty()) {
                    existingMediaIds.add(candidateMediaId)
                }
                importedCount++

                // Flush incrementally in batches of 50 to prevent memory list growth
                if (newReviewItems.size >= 50) {
                    reviewItemRepository.insertAllReviewItems(newReviewItems.toList())
                    newReviewItems.clear()
                }
                if (newScannedPhotos.size >= 50) {
                    photoScannerRepository.markAllAsImported(newScannedPhotos.toList())
                    newScannedPhotos.clear()
                }
            }

            // Flush remaining batch
            if (newReviewItems.isNotEmpty()) {
                reviewItemRepository.insertAllReviewItems(newReviewItems.toList())
                newReviewItems.clear()
            }
            if (newScannedPhotos.isNotEmpty()) {
                photoScannerRepository.markAllAsImported(newScannedPhotos.toList())
                newScannedPhotos.clear()
            }

            val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
            DiagnosticLogger.recordScan(candidates.size, importedCount)
            // Detailed scan statistics
            DiagnosticLogger.i(
                "Scanner",
                "Scanned: ${candidates.size} photos\n"
                    + "Skipped (already imported): $skippedAlreadyImported\n"
                    + "Skipped (too small): $skippedSmall\n"
                    + "Skipped (screenshots): $skippedScreenshots\n"
                    + "Skipped (not vehicle related): $skippedVehicleRelated\n"
                    + "Imported: $importedCount\n"
                    + "Elapsed: ${elapsedMs}ms"
            )
            importedCount
        } catch (t: Throwable) {
            DiagnosticLogger.e("Scanner", "scanAndImport failed", t)
            throw t
        }
    }

    private fun looksLikeScreenshot(displayName: String, bucketName: String?): Boolean {
        val lower = displayName.lowercase()
        val lowerBucket = bucketName?.lowercase() ?: ""
        // If the file is in a folder named "Screenshots" treat it as a screenshot
        if (lowerBucket.contains("screenshot") || lowerBucket.contains("screenshots")) return true
        // otherwise be conservative with generic keywords
        return screenshotKeywords.any { lower.contains(it) }
    }

    // Heuristic to determine if a photo is likely vehicle‑related based on filename keywords
    private val vehicleKeywords = setOf(
        "gallons",
        "fuel",
        "total",
        "receipt",
        "invoice",
        "oil",
        "tire",
        "maintenance",
        "brake",
        "inspection",
        "registration",
        "vin",
        "odometer",
        "gas",
        "pump",
        "station",
        "chevron",
        "shell",
        "exxon",
        "mobil",
        "valero",
        "costco",
        "car",
        "truck",
        "auto",
        "vehicle"
    )

    private fun isVehicleRelated(candidate: PhotoCandidate, bucketName: String?): Boolean {
        val lowerName = candidate.displayName.lowercase()
        val lowerBucket = bucketName?.lowercase() ?: ""

        // Explicit non-vehicle check: if filename or bucket clearly indicates non-vehicle item (e.g., flower, garden, food, selfie)
        if (nonVehicleKeywords.any { lowerName.contains(it) || lowerBucket.contains(it) }) {
            return false
        }

        // Accept user photos by default unless explicitly non-vehicle
        return true
    }

    private suspend fun queryMediaStore(
        startDateMillis: Long? = null,
        endDateMillis: Long? = null
    ): List<PhotoCandidate> = withContext(Dispatchers.IO) {
        val candidates = mutableListOf<PhotoCandidate>()
        val collectionUri: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            Images.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            Images.Media._ID,
            Images.Media.DISPLAY_NAME,
            Images.Media.DATE_TAKEN,
            Images.Media.WIDTH,
            Images.Media.HEIGHT,
            Images.Media.SIZE,
            Images.Media.MIME_TYPE,
            Images.Media.BUCKET_DISPLAY_NAME
        )

        val selectionBuilder = StringBuilder()
        val selectionArgs = ArrayList<String>()

        if (startDateMillis != null) {
            selectionBuilder.append("${Images.Media.DATE_TAKEN} >= ?")
            selectionArgs.add(startDateMillis.toString())
        }
        if (endDateMillis != null) {
            if (selectionBuilder.isNotEmpty()) selectionBuilder.append(" AND ")
            selectionBuilder.append("${Images.Media.DATE_TAKEN} <= ?")
            selectionArgs.add(endDateMillis.toString())
        }

        val sortOrder = "${Images.Media.DATE_TAKEN} DESC"

        val cursor = context.contentResolver.query(
            collectionUri,
            projection,
            if (selectionBuilder.isNotEmpty()) selectionBuilder.toString() else null,
            if (selectionArgs.isNotEmpty()) selectionArgs.toTypedArray() else null,
            sortOrder
        )

        cursor?.use { c ->
            val idCol = c.getColumnIndexOrThrow(Images.Media._ID)
            val nameCol = c.getColumnIndexOrThrow(Images.Media.DISPLAY_NAME)
            val dateCol = c.getColumnIndexOrThrow(Images.Media.DATE_TAKEN)
            val widthCol = c.getColumnIndexOrThrow(Images.Media.WIDTH)
            val heightCol = c.getColumnIndexOrThrow(Images.Media.HEIGHT)
            val sizeCol = c.getColumnIndexOrThrow(Images.Media.SIZE)
            val mimeCol = c.getColumnIndexOrThrow(Images.Media.MIME_TYPE)
            val bucketCol = c.getColumnIndexOrThrow(Images.Media.BUCKET_DISPLAY_NAME)

            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val displayName = c.getString(nameCol) ?: "image_$id"
                val dateTaken = c.getLong(dateCol)
                var width = c.getInt(widthCol)
                var height = c.getInt(heightCol)
                val size = c.getLong(sizeCol)
                var mime = c.getString(mimeCol) ?: ""
                val bucketName = c.getString(bucketCol)
                val uri = ContentUris.withAppendedId(collectionUri, id).toString()

                // If width/height are zero in MediaStore, default to 1000px so we do not open FileInputStreams during cursor read
                if (width <= 0) width = 1000
                if (height <= 0) height = 1000

                // Accept blank/unknown mime - try to query ContentResolver if blank
                if (mime.isBlank()) {
                    try {
                        mime = context.contentResolver.getType(Uri.parse(uri)) ?: ""
                    } catch (_: Exception) { /* ignore */ }
                }

                candidates.add(
                    PhotoCandidate(
                        mediaStoreId = id,
                        uri = uri,
                        displayName = displayName,
                        dateTaken = if (dateTaken > 0) dateTaken else System.currentTimeMillis(),
                        width = width,
                        height = height,
                        fileSize = size,
                        mimeType = mime,
                        bucket = bucketName
                    )
                )
            }
        }

        DiagnosticLogger.d("Scanner", "queryMediaStore returned ${candidates.size} candidate(s)")
        candidates
    }
}
