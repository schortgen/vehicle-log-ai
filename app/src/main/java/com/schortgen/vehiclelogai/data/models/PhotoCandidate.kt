package com.schortgen.vehiclelogai.data.models

/**
 * Lightweight representation of a photo discovered in the MediaStore.
 * Not a Room entity — used to pass scan results before creating ReviewItems.
 */
data class PhotoCandidate(
    val mediaStoreId: Long,
    val uri: String,
    val displayName: String,
    val dateTaken: Long,
    val width: Int,
    val height: Int,
    val fileSize: Long,
    val mimeType: String,
    // optional bucket/folder name from MediaStore (may be null)
    val bucket: String? = null
)
