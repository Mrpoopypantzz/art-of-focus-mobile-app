package com.artoffocus.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [PaintingEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun paintingDao(): PaintingDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "art_of_focus.db",
            )
                // Never fallbackToDestructiveMigration: it drops the museum (I12).
                .addMigrations(MIGRATION_1_2)
                .build().also { instance = it }
        }
    }
}
