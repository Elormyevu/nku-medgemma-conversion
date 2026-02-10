package com.nku.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * NkuDatabase — Room database singleton for screening persistence.
 */
@Database(entities = [ScreeningEntity::class], version = 1, exportSchema = false)
abstract class NkuDatabase : RoomDatabase() {

    abstract fun screeningDao(): ScreeningDao

    companion object {
        @Volatile
        private var INSTANCE: NkuDatabase? = null

        fun getInstance(context: Context): NkuDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    NkuDatabase::class.java,
                    "nku_screenings.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
