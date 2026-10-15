package com.corta.data

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import timber.log.Timber
import java.security.SecureRandom

object SecurityProvider {
    private const val TAG = "Corta.Security"
    private const val PREFS_NAME = "corta_secure_prefs"
    private const val FALLBACK_PREFS_NAME = "corta_keystore_fallback_prefs"
    private const val KEY_PASSPHRASE = "database_passphrase"

    @Synchronized
    fun getDatabasePassphrase(context: Context): ByteArray {
        // 1. Try to obtain passphrase via EncryptedSharedPreferences (Hardware KeyStore backed)
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            val sharedPreferences = EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )

            val storedPassphrase = sharedPreferences.getString(KEY_PASSPHRASE, null)
            if (storedPassphrase != null) {
                return Base64.decode(storedPassphrase, Base64.DEFAULT)
            }

            // Check if we previously had a fallback passphrase to migrate
            val fallbackPrefs = context.getSharedPreferences(FALLBACK_PREFS_NAME, Context.MODE_PRIVATE)
            val fallbackPassphrase = fallbackPrefs.getString(KEY_PASSPHRASE, null)
            if (fallbackPassphrase != null) {
                sharedPreferences.edit().putString(KEY_PASSPHRASE, fallbackPassphrase).apply()
                fallbackPrefs.edit().remove(KEY_PASSPHRASE).apply()
                return Base64.decode(fallbackPassphrase, Base64.DEFAULT)
            }

            // Generate new cryptographically secure 256-bit passphrase
            val newPassphrase = ByteArray(32)
            SecureRandom().nextBytes(newPassphrase)
            val encodedPassphrase = Base64.encodeToString(newPassphrase, Base64.DEFAULT)
            sharedPreferences.edit().putString(KEY_PASSPHRASE, encodedPassphrase).apply()
            return newPassphrase
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error accessing EncryptedSharedPreferences. Preserving existing keys.")
            // CRITICAL RULE: NEVER delete MasterKey or clear preferences on error!
            // Doing so creates a new key and permanently destroys the SQLCipher database.
            return getFallbackPassphrase(context)
        }
    }

    private fun getFallbackPassphrase(context: Context): ByteArray {
        val fallbackPrefs = context.getSharedPreferences(FALLBACK_PREFS_NAME, Context.MODE_PRIVATE)
        val stored = fallbackPrefs.getString(KEY_PASSPHRASE, null)
        if (stored != null) {
            return Base64.decode(stored, Base64.DEFAULT)
        }

        // Generate persistent fallback passphrase
        val newPassphrase = ByteArray(32)
        SecureRandom().nextBytes(newPassphrase)
        val encoded = Base64.encodeToString(newPassphrase, Base64.DEFAULT)
        fallbackPrefs.edit().putString(KEY_PASSPHRASE, encoded).apply()
        return newPassphrase
    }
}
