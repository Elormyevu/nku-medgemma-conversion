package com.nku.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * NkuDatabase — Room database singleton for screening persistence.
 *
 * F-9 fix: Version 2 adds recommendations and edema_risk_factors columns.
 * Migration path provided; fallbackToDestructiveMigration as safety net.
 */
@Database(entities = [ScreeningEntity::class], version = 2, exportSchema = false)
abstract class NkuDatabase : RoomDatabase() {

    abstract fun screeningDao(): ScreeningDao

    companion object {
        @Volatile
        private var INSTANCE: NkuDatabase? = null

        // F-9: Migration from v1 → v2 (add recommendations + edema_risk_factors)
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE screenings ADD COLUMN recommendations TEXT")
                db.execSQL("ALTER TABLE screenings ADD COLUMN edemaRiskFactors TEXT")
            }
        }

        fun getInstance(context: Context): NkuDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    NkuDatabase::class.java,
                    "nku_screenings.db"
                )
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigration()  // F-9: safety net for future schema mismatches
                .build().also { INSTANCE = it }
            }
        }
    }
}
