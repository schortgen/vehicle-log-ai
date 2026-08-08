package com.schortgen.vehiclelogai.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.schortgen.vehiclelogai.data.models.ReviewItem
import kotlinx.coroutines.flow.Flow

@Dao
interface ReviewItemDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(reviewItem: ReviewItem): Long

    @Update
    suspend fun update(reviewItem: ReviewItem)

    @Delete
    suspend fun delete(reviewItem: ReviewItem)

    @Query("SELECT * FROM review_items WHERE id = :id")
    suspend fun getById(id: Long): ReviewItem?

    @Query("SELECT * FROM review_items")
    suspend fun getAll(): List<ReviewItem>

    @Query("SELECT * FROM review_items ORDER BY captureDate DESC")
    fun observeAll(): Flow<List<ReviewItem>>

    @Query("SELECT * FROM review_items WHERE status = :status ORDER BY captureDate DESC")
    fun observeByStatus(status: String): Flow<List<ReviewItem>>

    @Query("SELECT * FROM review_items WHERE vehicleId = :vehicleId ORDER BY captureDate DESC")
    fun observeByVehicle(vehicleId: Long): Flow<List<ReviewItem>>

    @Query("SELECT COUNT(*) FROM review_items WHERE status = 'PENDING'")
    suspend fun countPending(): Int



    @Update
    suspend fun updateAll(reviewItems: List<ReviewItem>)

    @Query("SELECT * FROM review_items WHERE eventId = :eventId ORDER BY captureDate ASC")
    fun observeByEvent(eventId: Long): Flow<List<ReviewItem>>

    @Query("SELECT * FROM review_items WHERE eventId IS NULL ORDER BY captureDate ASC")
    suspend fun getUngroupedItems(): List<ReviewItem>
}