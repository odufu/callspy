package com.example.callspy.service

import android.util.Log
import com.example.callspy.keystore.KeyStoreManager
import com.example.callspy.utils.ErrorHandler
import java.io.File
import java.io.FileOutputStream
import javax.crypto.Cipher
import javax.crypto.CipherOutputStream

class CallRecordingService {
    companion object {
        private const val TAG = "CallRecordingService"
        private var ivSpec: javax.crypto.spec.IvParameterSpec? = null

        private fun encryptFile(inputPath: String, outputPath: String): Boolean {
            return try {
                // Get encryption key from KeyStoreManager
                val encryptionKey = KeyStoreManager.getOrCreateEncryptionKey()
                val cipher = Cipher.getInstance(KeyStoreManager.getEncryptionAlgorithm())

                // Ensure ivSpec is not null
                if (ivSpec == null) {
                    ErrorHandler.handleEncryptionError(null, "IV not initialized for encryption")
                    return false
                }

                cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, ivSpec)

                File(inputPath).inputStream().use { inputStream ->
                    FileOutputStream(outputPath).use { fileOut ->
                        // Write IV bytes first (16 bytes for AES)
                        fileOut.write(ivSpec!!.iv)

                        CipherOutputStream(fileOut, cipher).use { cipherOut ->
                            val buffer = ByteArray(1024)
                            var bytesRead: Int
                            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                                cipherOut.write(buffer, 0, bytesRead)
                            }
                        }
                    }
                }
                true
            } catch (e: Exception) {
                ErrorHandler.handleEncryptionError(null, "Encryption failed: ${e.message}")
                false
            }
        }
    }
}
