package com.example.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.LeaseDeal
import kotlinx.coroutines.flow.Flow

@Dao
interface LeaseDealDao {
    @Query("SELECT * FROM lease_deals ORDER BY scrapedAt DESC")
    fun getAllDeals(): Flow<List<LeaseDeal>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDeal(deal: LeaseDeal): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDeals(deals: List<LeaseDeal>): List<Long>

    @Delete
    suspend fun deleteDeal(deal: LeaseDeal)

    @Query("DELETE FROM lease_deals WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM lease_deals WHERE sourceUrl = :sourceUrl")
    suspend fun deleteBySourceUrl(sourceUrl: String)

    @Query("DELETE FROM lease_deals")
    suspend fun clearAll()
}
