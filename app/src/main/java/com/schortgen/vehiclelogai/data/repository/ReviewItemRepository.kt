package com.schortgen.vehiclelogai.data.repository

import com.schortgen.vehiclelogai.data.local.dao.ReviewItemDao
import com.schortgen.vehiclelogai.data.models.ReviewItem
import kotlinx.coroutines.flow.Flow

class ReviewItemRepository(private val reviewItemDao: ReviewItemDao) {
    suspend fun insertReviewItem(reviewItem: ReviewItem): Long {
        val path = reviewItem.photoPath
        if (!path.isNullOrBlank()) {
            val existing = getByPhotoPath(path)
            if (existing != null) {
                return existing.id
            }
        }
        return reviewItemDao.insert(reviewItem)
    }

    suspend fun getByPhotoPath(photoPath: String?): ReviewItem? {
        if (photoPath.isNullOrBlank()) return null
        val directMatch = reviewItemDao.getByPhotoPath(photoPath)
        if (directMatch != null) return directMatch

        val mediaId = photoPath.substringAfterLast('/')
        if (mediaId.toLongOrNull() != null) {
            val allItems = reviewItemDao.getAll()
            return allItems.find { item ->
                item.photoPath?.substringAfterLast('/') == mediaId
            }
        }
        return null
    }

    /** Clean up any duplicate ReviewItems that share the same photo path or MediaStore ID */
    suspend fun cleanupDuplicates() {
        val allItems = reviewItemDao.getAll()
        val seenMediaIds = mutableSetOf<String>()
        val seenPaths = mutableSetOf<String>()
        val toDelete = mutableListOf<ReviewItem>()

        for (item in allItems) {
            val path = item.photoPath
            if (path.isNullOrBlank()) continue
            val mediaId = path.substringAfterLast('/')
            val isNumericId = mediaId.toLongOrNull() != null

            val isDuplicate = (path in seenPaths) || (isNumericId && mediaId in seenMediaIds)
            if (isDuplicate) {
                toDelete.add(item)
            } else {
                seenPaths.add(path)
                if (isNumericId) seenMediaIds.add(mediaId)
            }
        }

        for (dup in toDelete) {
            reviewItemDao.delete(dup)
        }
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

    suspend fun deleteAllReviewItems() {
        reviewItemDao.deleteAllReviewItems()
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