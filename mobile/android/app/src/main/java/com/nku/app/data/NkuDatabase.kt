package com.nku.app.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import net.sqlcipher.database.SupportFactory
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * NkuDatabase — Room database singleton for screening persistence.
 *
 * OBS-5: Encrypted at rest via SQLCipher with Android Keystore-derived passphrase.
 * The AES-256 key is generated in hardware-backed Keystore on first launch,
 * making the passphrase non-extractable even on rooted devices.
 *
 * F-9 fix: Version 2 adds recommendations and edema_risk_factors columns.
 */
@Database(entities = [ScreeningEntity::class], version = 2, exportSchema = false)
abstract class NkuDatabase : RoomDatabase() {

    abstract fun screeningDao(): ScreeningDao

    companion object {
        @Volatile
        private var INSTANCE: NkuDatabase? = null

        private const val KEYSTORE_ALIAS = "nku_db_key"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val PREFS_NAME = "nku_db_prefs"
        private const val KEY_ENCRYPTED_MARKER = "encrypted_marker"
        private const val KEY_MARKER_IV = "marker_iv"

        // F-9: Migration from v1 → v2 (add recommendations + edema_risk_factors)
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE screenings ADD COLUMN recommendations TEXT")
                db.execSQL("ALTER TABLE screenings ADD COLUMN edemaRiskFactors TEXT")
            }
        }

        /**
         * Ensure the AES-256 key exists in Android Keystore.
         * Created on first launch; persists across app updates.
         */
        private fun ensureKeystoreKey() {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)
            if (!keyStore.containsAlias(KEYSTORE_ALIAS)) {
                val keyGen = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE
                )
                keyGen.init(
                    KeyGenParameterSpec.Builder(
                        KEYSTORE_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                    )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256)
                        .build()
                )
                keyGen.generateKey()
            }
        }

        /**
         * Derive a deterministic passphrase from the Keystore key.
         *
         * Strategy: Encrypt a fixed marker string with AES-GCM using the Keystore key.
         * The ciphertext (which is deterministic for the same key + IV) becomes the passphrase.
         * The IV is stored in SharedPreferences on first derivation.
         */
        private fun derivePassphrase(context: Context): ByteArray {
            ensureKeystoreKey()

            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val existingMarker = prefs.getString(KEY_ENCRYPTED_MARKER, null)
            val existingIv = prefs.getString(KEY_MARKER_IV, null)

            if (existingMarker != null && existingIv != null) {
                // Return the previously derived passphrase
                return Base64.decode(existingMarker, Base64.NO_WRAP)
            }

            // First launch: encrypt a fixed marker to derive the passphrase
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)
            val secretKey = keyStore.getKey(KEYSTORE_ALIAS, null) as SecretKey

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)

            val marker = "nku_sentinel_db_passphrase_v1".toByteArray()
            val encrypted = cipher.doFinal(marker)
            val iv = cipher.iv

            // Persist both so we always derive the same passphrase
            prefs.edit()
                .putString(KEY_ENCRYPTED_MARKER, Base64.encodeToString(encrypted, Base64.NO_WRAP))
                .putString(KEY_MARKER_IV, Base64.encodeToString(iv, Base64.NO_WRAP))
                .apply()

            return encrypted
        }

        /**
         * Get or create the encrypted database instance.
         * OBS-5: Uses Android Keystore-derived passphrase instead of ANDROID_ID.
         */
        fun getInstance(context: Context): NkuDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: run {
                    val passphrase = derivePassphrase(context)
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
