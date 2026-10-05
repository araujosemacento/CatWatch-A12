package com.catwatch.detector.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [CatEventEntity::class], version = 2, exportSchema = false)
abstract class CatWatchDatabase : RoomDatabase() {
    abstract fun catEventDao(): CatEventDao

    companion object {
        @Volatile
        private var INSTANCE: CatWatchDatabase? = null

        fun getDatabase(context: Context): CatWatchDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    CatWatchDatabase::class.java,
                    "catwatch_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}