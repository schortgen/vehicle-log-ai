package com.schortgen.vehiclelogai.data.models

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * Tracks which MediaStore photos have already been imported into the review queue.
 * The mediaStoreId serves as the unique deduplication key.
 */
@Entity(
    tableName = "scanned_photos",
    foreignKeys = [
        ForeignKey(
            entity = Event::class,
            parentColumns = ["id"],
            childColumns = ["eventId"],
            onDelete = ForeignKey.SET_NULL
        )
    ]
)
data class ScannedPhoto(
    @PrimaryKey val mediaStoreId: Long,
    val uri: String,
    val displayName: String,
    val importedDate: Long = System.currentTimeMillis(),
    val eventId: Long? = null
)