package com.nku.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import net.sqlcipher.database.SupportFactory

/**
 * NkuDatabase — Room database singleton for screening persistence.
 *
 * Finding 8 fix: Encrypted at rest via SQLCipher. Screening records may contain
 * PHI-like data (symptoms, recommendations) that should be protected on
 * rooted/compromised devices.
 *
 * F-9 fix: Version 2 adds recommendations and edema_risk_factors columns.
 * Migration path provided.
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

        /**
         * Get or create the encrypted database instance.
         *
         * Finding 8 fix: Uses SQLCipher with a device-derived passphrase.
         * The passphrase is derived from the app's package name + Android ID,
         * making it unique per device and non-extractable without both values.
         */
        fun getInstance(context: Context): NkuDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: run {
                    // Derive a device-specific passphrase for SQLCipher
                    val androidId = android.provider.Settings.Secure.getString(
                        context.contentResolver,
                        android.provider.Settings.Secure.ANDROID_ID
                    ) ?: "nku_default_salt"
                    val passphrase = "nku_${context.packageName}_$androidId".toByteArray()
                    val factory = SupportFactory(passphrase)

                    Room.databaseBuilder(
                        context.applicationContext,
                        NkuDatabase::class.java,
                        "nku_screenings.db"
                    )
                    .openHelperFactory(factory)
                    .addMigrations(MIGRATION_1_2)
                    // M-03 fix: removed fallbackToDestructiveMigration() — PHI must never be silently destroyed
                    .build().also { INSTANCE = it }
                }
            }
        }
    }
}
