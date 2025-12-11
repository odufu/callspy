package com.example.callspy.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaRecorder
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.callspy.utils.KeyStoreManager
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherOutputStream
import javax.crypto.spec.IvParameterSpec

class CallRecordingService : Service() {

    private var mediaRecorder: MediaRecorder? = null
    private var outputFilePath: String? = null
    private var encryptedFilePath: String? = null
    private var ivSpec: IvParameterSpec? = null
    
    companion object {
        const val CHANNEL_ID = "CallRecordingServiceChannel"
        const val NOTIFICATION_ID = 1
        private const val TAG = "CallRecordingService"

        // Audio sources to try (in order of preference)
        private val AUDIO_SOURCES = listOf(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.MIC,
            MediaRecorder.AudioSource.DEFAULT
        )

        // IV size for AES (16 bytes)
        private const val IV_SIZE = 16
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        Log.d(TAG, "CallRecordingService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "CallRecordingService starting")

        // Extract call metadata from intent if available
        val phoneNumber = intent?.getStringExtra("phone_number") ?: "Unknown"
        val isIncoming = intent?.getBooleanExtra("is_incoming", true) ?: true

        startForeground(NOTIFICATION_ID, createNotification(phoneNumber, isIncoming))

        // Try to start recording with retry logic
        if (!startRecordingWithRetry(phoneNumber, isIncoming)) {
            Log.e(TAG, "Failed to start recording after all attempts")
            stopSelf()
            return START_NOT_STICKY
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRecording()
        Log.d(TAG, "CallRecordingService destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? {
        // We don't provide binding
        return null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Call Recording Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Channel for call recording service notifications"
                setSound(null, null)
                enableVibration(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(phoneNumber: String, isIncoming: Boolean): Notification {
        val callType = if (isIncoming) "Incoming" else "Outgoing"
        val notificationIntent = Intent(this, com.example.callspy.MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Call Recording Active")
            .setContentText("Recording $callType call from $phoneNumber")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .build()
    }

    private fun startRecordingWithRetry(phoneNumber: String, isIncoming: Boolean): Boolean {
        var lastException: Exception? = null

        for (audioSource in AUDIO_SOURCES) {
            try {
                if (startRecording(audioSource, phoneNumber, isIncoming)) {
                    Log.d(TAG, "Recording started successfully with audio source: $audioSource")
                    return true
                }
            } catch (e: Exception) {
                lastException = e
                Log.w(TAG, "Failed to start recording with audio source $audioSource: ${e.message}")
                stopRecording()
            }
        }

        lastException?.let { Log.e(TAG, "All audio sources failed: ${it.message}", it) }
        return false
    }

    private fun startRecording(audioSource: Int, phoneNumber: String, isIncoming: Boolean): Boolean {
        try {
            // Generate IV for this encryption session
            ivSpec = generateInitializationVector()

            // Create output directory
            val outputDir = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            if (outputDir == null) {
                Log.e(TAG, "Failed to get external files directory")
                return false
            }

            // Create hidden directory for recordings
            val hiddenDir = File(outputDir, ".call_recordings")
            if (!hiddenDir.exists()) {
                hiddenDir.mkdirs()
                // Create .nomedia file to hide from gallery
                File(hiddenDir, ".nomedia").createNewFile()
            }

            // Generate filename with metadata
            val timestamp = System.currentTimeMillis()
            val callType = if (isIncoming) "incoming" else "outgoing"
            val sanitizedNumber = phoneNumber.replace("[^0-9]".toRegex(), "")
            val baseFilename = "call_${callType}_$sanitizedNumber_$timestamp"

            // Temporary unencrypted file
            val tempFile = File(hiddenDir, "${baseFilename}_temp.mp4")
            outputFilePath = tempFile.absolutePath

            // Encrypted file
            val encryptedFile = File(hiddenDir, "${baseFilename}_encrypted.aes")
            encryptedFilePath = encryptedFile.absolutePath

            Log.d(TAG, "Starting recording to: $outputFilePath")
            Log.d(TAG, "Will encrypt to: $encryptedFilePath")

            mediaRecorder = MediaRecorder().apply {
                setAudioSource(audioSource)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(128000)
                setOutputFile(outputFilePath)
                prepare()
                start()
            }

            Log.d(TAG, "MediaRecorder started successfully")
            return true

        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception: ${e.message}", e)
            throw e
        } catch (e: IOException) {
            Log.e(TAG, "IO exception: ${e.message}", e)
            throw e
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Illegal state: ${e.message}", e)
            throw e
        }
    }

    private fun stopRecording() {
        try {
            mediaRecorder?.apply {
                try {
                    stop()
                } catch (e: IllegalStateException) {
                    Log.w(TAG, "MediaRecorder already stopped or not started")
                } catch (e: RuntimeException) {
                    Log.e(TAG, "Error stopping MediaRecorder: ${e.message}", e)
                }
                reset()
                release()
            }
            mediaRecorder = null

            // Encrypt the recorded file
            outputFilePath?.let { tempPath ->
                encryptedFilePath?.let { encPath ->
                    encryptFile(tempPath, encPath)
                    // Delete temporary unencrypted file
                    File(tempPath).delete()
                    Log.d(TAG, "File encrypted and temporary file deleted: $encPath")
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error in stopRecording: ${e.message}", e)
        } finally {
            outputFilePath = null
            encryptedFilePath = null
            ivSpec = null
        }
    }

    private fun generateInitializationVector(): IvParameterSpec {
        val iv = ByteArray(IV_SIZE)
        SecureRandom().nextBytes(iv)
        return IvParameterSpec(iv)
    }

    private fun encryptFile(inputPath: String, outputPath: String): Boolean {
        return try {
            // Get encryption key from KeyStoreManager
            val encryptionKey = KeyStoreManager.getOrCreateEncryptionKey()
            val cipher = Cipher.getInstance(KeyStoreManager.getEncryptionAlgorithm())
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, ivSpec)

            File(inputPath).inputStream().use { inputStream ->
                FileOutputStream(outputPath).use { outputStream ->
                    // Write IV first (16 bytes)
                    ivSpec?.iv?.let { ivBytes ->
                        outputStream.write(ivBytes)
                    }
                    
                    // Then write encrypted data
                    CipherOutputStream(outputStream, cipher).use { cipherStream ->
                        inputStream.copyTo(cipherStream)
                    }
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Encryption failed: ${e.message}", e)
            false
        }
    }

    private fun updateNotification(contentText: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Call Recording")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}
