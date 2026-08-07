package com.schortgen.vehiclelogai.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.schortgen.vehiclelogai.data.models.ScannedPhoto
import kotlinx.coroutines.flow.Flow

@Dao
interface ScannedPhotoDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(scannedPhoto: ScannedPhoto)

    @Query("SELECT mediaStoreId FROM scanned_photos")
    suspend fun getAllMediaStoreIds(): List<Long>

    @Query("SELECT EXISTS(SELECT 1 FROM scanned_photos WHERE mediaStoreId = :mediaStoreId)")
    suspend fun exists(mediaStoreId: Long): Boolean

    @Query("SELECT COUNT(*) FROM scanned_photos")
    suspend fun count(): Int

    @Query("SELECT * FROM scanned_photos WHERE eventId = :eventId ORDER BY importedDate ASC")
    suspend fun getByEventId(eventId: Long): List<ScannedPhoto>

    @Query("SELECT * FROM scanned_photos WHERE eventId IS NULL ORDER BY importedDate ASC")
    suspend fun getUngroupedScannedPhotos(): List<ScannedPhoto>

    @Query("SELECT * FROM scanned_photos WHERE eventId = :eventId ORDER BY importedDate ASC")
    fun observeByEvent(eventId: Long): Flow<List<ScannedPhoto>>
}