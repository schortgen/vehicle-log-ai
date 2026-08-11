package com.schortgen.vehiclelogai.ui.scanphotos

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.*

class ScanPhotosViewModel(
    private val addToReviewQueue: suspend (Uri, Long) -> Boolean, // provide your queue function here
    private val clearReviewQueue: (suspend () -> Unit)? = null
) : ViewModel() {

    private val _startDateMillis = MutableStateFlow<Long?>(null)
    val startDateMillis: StateFlow<Long?> = _startDateMillis.asStateFlow()

    private val _endDateMillis = MutableStateFlow<Long?>(null)
    val endDateMillis: StateFlow<Long?> = _endDateMillis.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _scannedCount = MutableStateFlow(0)
    val scannedCount: StateFlow<Int> = _scannedCount.asStateFlow()

    private val _queuedCount = MutableStateFlow(0)
    val queuedCount: StateFlow<Int> = _queuedCount.asStateFlow()

    private val _totalToScan = MutableStateFlow<Int?>(null)
    val totalToScan: StateFlow<Int?> = _totalToScan.asStateFlow()

    private var scanJob: Job? = null

    fun setStartDate(millis: Long?) {
        _startDateMillis.value = millis
    }

    fun setEndDate(millis: Long?) {
        _endDateMillis.value = millis
    }

    fun cancelScan() {
        scanJob?.cancel()
    }

    fun scanPhotos(context: Context) {
        if (_isScanning.value) return

        scanJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                _isScanning.value = true
                _scannedCount.value = 0
                _queuedCount.value = 0
                _totalToScan.value = null

                clearReviewQueue?.invoke()

                // Build selection for MediaStore
                val projection = arrayOf(
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DISPLAY_NAME,
                    MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
                    MediaStore.Images.Media.DATE_TAKEN,
                    MediaStore.Images.Media.DATE_ADDED
                )

                val selectionBuilder = StringBuilder()
                val selectionArgs = ArrayList<String>()

                // Prefer DATE_TAKEN; fall back to DATE_ADDED
                val start = _startDateMillis.value
                val end = _endDateMillis.value
                // Note: DATE_TAKEN is stored in millis on many devices; DATE_ADDED is seconds.
                if (start != null) {
                    selectionBuilder.append("${MediaStore.Images.Media.DATE_TAKEN} >= ?")
                    selectionArgs.add(start.toString())
                }
                if (end != null) {
                    if (selectionBuilder.isNotEmpty()) selectionBuilder.append(" AND ")
                    selectionBuilder.append("${MediaStore.Images.Media.DATE_TAKEN} <= ?")
                    selectionArgs.add(end.toString())
                }

                val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI

                // first count total (so we can show determinate progress)
                context.contentResolver.query(
                    uri,
                    projection,
                    if (selectionBuilder.isNotEmpty()) selectionBuilder.toString() else null,
                    if (selectionArgs.isNotEmpty()) selectionArgs.toTypedArray() else null,
                    null
                )?.use { cursor ->
                    _totalToScan.value = cursor.count
                }

                val nonVehicleKeywords = setOf(
                    "flower", "flowers", "plant", "garden", "nature",
                    "pet", "dog", "cat", "food", "dish", "meal",
                    "selfie", "portrait", "family", "vacation", "beach",
                    "party", "concert", "sunset", "sky"
                )

                // Now iterate and process
                context.contentResolver.query(
                    uri,
                    projection,
                    if (selectionBuilder.isNotEmpty()) selectionBuilder.toString() else null,
                    if (selectionArgs.isNotEmpty()) selectionArgs.toTypedArray() else null,
                    "${MediaStore.Images.Media.DATE_TAKEN} DESC"
                )?.use { cursor ->
                    val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                    val nameIndex = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
                    val bucketIndex = cursor.getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                    val dateTakenIndex = cursor.getColumnIndex(MediaStore.Images.Media.DATE_TAKEN)

                    while (isActive && cursor.moveToNext()) {
                        _scannedCount.value = _scannedCount.value + 1

                        val id = cursor.getLong(idIndex)
                        val displayName = if (nameIndex >= 0) cursor.getString(nameIndex)?.lowercase() ?: "" else ""
                        val bucketName = if (bucketIndex >= 0) cursor.getString(bucketIndex)?.lowercase() ?: "" else ""
                        val dateTaken = if (dateTakenIndex >= 0) cursor.getLong(dateTakenIndex) else System.currentTimeMillis()

                        if (nonVehicleKeywords.any { displayName.contains(it) || bucketName.contains(it) }) {
                            // Skip non-vehicle photo
                            continue
                        }

                        val contentUri = ContentUris.withAppendedId(uri, id)
                        try {
                            // Attempt to add to review queue. The addToReviewQueue should return true if enqueued.
                            val added = try {
                                addToReviewQueue(contentUri, dateTaken)
                            } catch (t: Throwable) {
                                // log and treat as not added
                                false
                            }
                            if (added) _queuedCount.value = _queuedCount.value + 1
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            // swallow per-item exceptions to keep scanning
                        }
                    }
                }
            } finally {
                _isScanning.value = false
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        scanJob?.cancel()
    }
}