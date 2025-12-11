package com.example.callspy.utils

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

object KeyStoreManager {
    private const val TAG = "KeyStoreManager"
    private const val KEY_ALIAS = "callspy_encryption_key"
    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    
    // Algorithm parameters matching CallRecordingService
    private const val KEY_ALGORITHM = KeyProperties.KEY_ALGORITHM_AES
    private const val BLOCK_MODE = KeyProperties.BLOCK_MODE_CBC
    private const val ENCRYPTION_PADDING = KeyProperties.ENCRYPTION_PADDING_PKCS7
    private const val ENCRYPTION_ALGORITHM = "$KEY_ALGORITHM/$BLOCK_MODE/$ENCRYPTION_PADDING"
    private const val KEY_SIZE = 256
    
    /**
     * Get or create the encryption key from Android KeyStore.
     * Returns the SecretKey for encryption/decryption.
     */
    fun getOrCreateEncryptionKey(): SecretKey {
        return getEncryptionKey() ?: generateEncryptionKey()
    }
    
    /**
     * Get the encryption key from Android KeyStore if it exists.
     * Returns null if the key doesn't exist.
     */
    fun getEncryptionKey(): SecretKey? {
        return try {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER)
            keyStore.load(null)
            
            val key = keyStore.getKey(KEY_ALIAS, null)
            if (key != null && key is SecretKey) {
                Log.d(TAG, "Encryption key retrieved from KeyStore")
                key
            } else {
                Log.d(TAG, "Encryption key not found in KeyStore")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting encryption key: ${e.message}", e)
            null
        }
    }
    
    /**
     * Generate a new encryption key and store it in Android KeyStore.
     */
    private fun generateEncryptionKey(): SecretKey {
        return try {
            val keyGenerator = KeyGenerator.getInstance(KEY_ALGORITHM, KEYSTORE_PROVIDER)
            
            val keyGenParameterSpec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(BLOCK_MODE)
                .setEncryptionPaddings(ENCRYPTION_PADDING)
                .setKeySize(KEY_SIZE)
                .setUserAuthenticationRequired(false)
                .build()
            
            keyGenerator.init(keyGenParameterSpec)
            val secretKey = keyGenerator.generateKey()
            Log.d(TAG, "New encryption key generated and stored in KeyStore")
            secretKey
        } catch (e: Exception) {
            Log.e(TAG, "Error generating encryption key: ${e.message}", e)
            throw e
        }
    }
    
    /**
     * Delete the encryption key from Android KeyStore.
     * Returns true if successful, false otherwise.
     */
    fun deleteEncryptionKey(): Boolean {
        return try {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER)
            keyStore.load(null)
            
            if (keyStore.containsAlias(KEY_ALIAS)) {
                keyStore.deleteEntry(KEY_ALIAS)
                Log.d(TAG, "Encryption key deleted from KeyStore")
                true
            } else {
                Log.d(TAG, "Encryption key alias not found, nothing to delete")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting encryption key: ${e.message}", e)
            false
        }
    }
    
    /**
     * Check if encryption key exists in Android KeyStore.
     */
    fun hasEncryptionKey(): Boolean {
        return try {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER)
            keyStore.load(null)
            keyStore.containsAlias(KEY_ALIAS)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for encryption key: ${e.message}", e)
            false
        }
    }
    
    /**
     * Get the encryption algorithm string (AES/CBC/PKCS7Padding).
     * Note: PKCS7Padding is the same as PKCS5Padding for AES.
     */
    fun getEncryptionAlgorithm(): String = ENCRYPTION_ALGORITHM
}
