package com.mrboora.plate.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [Profile::class, FoodEntry::class, SavedFood::class, WeightEntry::class, Water::class, Workout::class],
    version = 4,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3), AutoMigration(from = 3, to = 4)],
)
abstract class PlateDatabase : RoomDatabase() {
    abstract fun dao(): PlateDao

    companion object {
        @Volatile private var instance: PlateDatabase? = null

        fun get(context: Context): PlateDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, PlateDatabase::class.java, "plate.db")
                .build().also { instance = it }
        }
    }
}
