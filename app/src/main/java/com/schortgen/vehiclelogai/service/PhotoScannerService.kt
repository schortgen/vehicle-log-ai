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

            for (candidate in candidates) {
                if (photoScannerRepository.isAlreadyImported(candidate.mediaStoreId) ||
                    reviewItemRepository.getByPhotoPath(candidate.uri) != null
                ) {
                    skippedAlreadyImported++
                    photoScannerRepository.markAsImported(
                        ScannedPhoto(
                            mediaStoreId = candidate.mediaStoreId,
                            uri = candidate.uri,
                            displayName = candidate.displayName,
                            dateTaken = candidate.dateTaken
                        )
                    )
                    DiagnosticLogger.d("Scanner", "skip already imported id=${candidate.mediaStoreId} name=${candidate.displayName}")
                    continue
                }

                if (looksLikeScreenshot(candidate.displayName, candidate.bucket)) {
                    skippedScreenshots++
                    DiagnosticLogger.d(
                        "Scanner",
                        "skip screenshot mediaStoreId=${candidate.mediaStoreId} name=${candidate.displayName} bucket=${candidate.bucket}"
                    )
                    continue
                }

                if (candidate.width < minDimension || candidate.height < minDimension) {
                    skippedSmall++
                    DiagnosticLogger.d(
                        "Scanner",
                        "skip small mediaStoreId=${candidate.mediaStoreId} ${candidate.width}x${candidate.height} name=${candidate.displayName}"
                    )
                    continue
                }

                if (candidate.mimeType.isNotBlank() && candidate.mimeType !in supportedMimeTypes) {
                    skippedType++
                    DiagnosticLogger.d(
                        "Scanner",
                        "skip mime mediaStoreId=${candidate.mediaStoreId} mime=${candidate.mimeType} name=${candidate.displayName}"
                    )
                    continue
                }

                // Filter out images that are unlikely to be vehicle-related based on filename heuristics
                if (!isVehicleRelated(candidate, candidate.bucket)) {
                    skippedVehicleRelated++
                    DiagnosticLogger.d(
                        "Scanner",
                        "skip non-vehicle mediaStoreId=${candidate.mediaStoreId} name=${candidate.displayName} bucket=${candidate.bucket}"
                    )
                    continue
                }

                val reviewItem = ReviewItem(
                    photoPath = candidate.uri,
                    captureDate = candidate.dateTaken,
                    status = ProcessingStatus.PENDING,
                    reason = "Imported: ${candidate.displayName}"
                )
                reviewItemRepository.insertReviewItem(reviewItem)

                photoScannerRepository.markAsImported(
                    ScannedPhoto(
                        mediaStoreId = candidate.mediaStoreId,
                        uri = candidate.uri,
                        displayName = candidate.displayName,
                        dateTaken = candidate.dateTaken
                    )
                )
                importedCount++
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

                // If width/height are zero, probe image bounds as fallback
                if (width <= 0 || height <= 0) {
                    try {
                        context.contentResolver.openInputStream(Uri.parse(uri))?.use { stream ->
                            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                            BitmapFactory.decodeStream(stream, null, opts)
                            if (opts.outWidth > 0 && opts.outHeight > 0) {
                                width = opts.outWidth
                                height = opts.outHeight
                            }
                        }
                    } catch (t: Throwable) {
                        DiagnosticLogger.d("Scanner", "Failed to decode bounds for $uri: ${t.message}")
                    }
                }

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
