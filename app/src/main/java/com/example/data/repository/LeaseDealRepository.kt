package com.example.data.repository

import com.example.data.db.LeaseDealDao
import com.example.data.db.SavedUrlDao
import com.example.data.model.LeaseDeal
import com.example.data.model.SavedUrl
import kotlinx.coroutines.flow.Flow

class LeaseDealRepository(
    private val leaseDealDao: LeaseDealDao,
    private val savedUrlDao: SavedUrlDao
) {

    val allDeals: Flow<List<LeaseDeal>> = leaseDealDao.getAllDeals()
    val allSavedUrls: Flow<List<SavedUrl>> = savedUrlDao.getAllSavedUrls()

    suspend fun insertDeal(deal: LeaseDeal): Long {
        return leaseDealDao.insertDeal(deal)
    }

    suspend fun insertDeals(deals: List<LeaseDeal>): List<Long> {
        return leaseDealDao.insertDeals(deals)
    }

    suspend fun deleteDeal(deal: LeaseDeal) {
        leaseDealDao.deleteDeal(deal)
    }

    suspend fun deleteById(id: Long) {
        leaseDealDao.deleteById(id)
    }

    suspend fun deleteDealsBySourceUrl(sourceUrl: String) {
        leaseDealDao.deleteBySourceUrl(sourceUrl)
    }

    suspend fun clearAll() {
        leaseDealDao.clearAll()
    }

    // Saved URLs
    suspend fun insertSavedUrl(url: SavedUrl): Long {
        return savedUrlDao.insertUrl(url)
    }

    suspend fun insertSavedUrls(urls: List<SavedUrl>): List<Long> {
        return savedUrlDao.insertUrls(urls)
    }

    suspend fun updateSavedUrl(url: SavedUrl) {
        savedUrlDao.updateUrl(url)
    }

    suspend fun deleteSavedUrl(url: SavedUrl) {
        savedUrlDao.deleteUrl(url)
    }

    suspend fun deleteSavedUrlById(id: Long) {
        savedUrlDao.deleteById(id)
    }

    suspend fun clearSavedUrls() {
        savedUrlDao.clearAll()
    }
}
