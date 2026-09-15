package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.model.LeaseDeal
import com.example.data.model.SavedUrl

@Database(entities = [LeaseDeal::class, SavedUrl::class], version = 4, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun leaseDealDao(): LeaseDealDao
    abstract fun savedUrlDao(): SavedUrlDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "lease_deals.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
