package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity representing a saved vehicle leasing target URL with description.
 */
@Entity(tableName = "saved_urls")
data class SavedUrl(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val url: String,
    val description: String,
    val createdAt: Long = System.currentTimeMillis()
)
