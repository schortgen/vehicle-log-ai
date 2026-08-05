package com.schortgen.vehiclelogai.data.repository

import com.schortgen.vehiclelogai.data.local.dao.ReviewItemDao
import com.schortgen.vehiclelogai.data.models.ReviewItem
import kotlinx.coroutines.flow.Flow

class ReviewItemRepository(private val reviewItemDao: ReviewItemDao) {
    suspend fun insertReviewItem(reviewItem: ReviewItem): Long {
        return reviewItemDao.insert(reviewItem)
    }

    suspend fun updateReviewItem(reviewItem: ReviewItem) {
        reviewItemDao.update(reviewItem)
    }

    suspend fun updateAllReviewItems(reviewItems: List<ReviewItem>) {
        reviewItemDao.updateAll(reviewItems)
    }

    suspend fun deleteReviewItem(reviewItem: ReviewItem) {
        reviewItemDao.delete(reviewItem)
    }

    suspend fun getReviewItemById(id: Long): ReviewItem? {
        return reviewItemDao.getById(id)
    }

    suspend fun getAllReviewItems(): List<ReviewItem> {
        return reviewItemDao.getAll()
    }

    fun observeAllReviewItems(): Flow<List<ReviewItem>> {
        return reviewItemDao.observeAll()
    }

    suspend fun getUngroupedItems(): List<ReviewItem> {
        return reviewItemDao.getUngroupedItems()
    }

    fun observeByEvent(eventId: Long): Flow<List<ReviewItem>> {
        return reviewItemDao.observeByEvent(eventId)
    }

    suspend fun countPending(): Int {
        return reviewItemDao.countPending()
    }
}