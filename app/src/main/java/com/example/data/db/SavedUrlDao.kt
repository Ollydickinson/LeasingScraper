package com.example.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.SavedUrl
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedUrlDao {

    @Query("SELECT * FROM saved_urls ORDER BY createdAt DESC")
    fun getAllSavedUrls(): Flow<List<SavedUrl>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUrl(url: SavedUrl): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUrls(urls: List<SavedUrl>): List<Long>

    @Update
    suspend fun updateUrl(url: SavedUrl)

    @Delete
    suspend fun deleteUrl(url: SavedUrl)

    @Query("DELETE FROM saved_urls WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM saved_urls")
    suspend fun clearAll()
}
