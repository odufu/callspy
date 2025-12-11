package com.example.callspy

import android.content.Intent
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.callspy.utils.KeyStoreManager
import java.io.File
import java.io.FileOutputStream
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.spec.IvParameterSpec
import kotlin.concurrent.thread

class FileManagerActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var noRecordingsTextView: TextView
    private lateinit var loadingProgressBar: ProgressBar
    private lateinit var adapter: RecordingAdapter
    
    private var recordings = mutableListOf<RecordingItem>()
    private var mediaPlayer: MediaPlayer? = null
    private var currentlyPlayingPosition = -1
    
    companion object {
        private const val TAG = "FileManagerActivity"
        private const val IV_SIZE = 16  // AES IV size (16 bytes)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_file_manager)
        
        setTitle(R.string.file_manager_title)
        
        recyclerView = findViewById(R.id.recordingsRecyclerView)
        noRecordingsTextView = findViewById(R.id.noRecordingsTextView)
        loadingProgressBar = findViewById(R.id.loadingProgressBar)
        
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = RecordingAdapter()
        recyclerView.adapter = adapter
        
        loadRecordings()
    }
    
    private fun loadRecordings() {
        loadingProgressBar.visibility = View.VISIBLE
        noRecordingsTextView.visibility = View.GONE
        recyclerView.visibility = View.GONE
        
        thread {
            try {
                val outputDir = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
                if (outputDir == null) {
                    runOnUiThread {
                        Toast.makeText(this, R.string.error_loading_recordings, Toast.LENGTH_SHORT).show()
                        loadingProgressBar.visibility = View.GONE
                        noRecordingsTextView.visibility = View.VISIBLE
                    }
                    return@thread
                }
                
                val hiddenDir = File(outputDir, ".call_recordings")
                if (!hiddenDir.exists() || !hiddenDir.isDirectory) {
                    runOnUiThread {
                        recordings.clear()
                        adapter.notifyDataSetChanged()
                        loadingProgressBar.visibility = View.GONE
                        noRecordingsTextView.visibility = View.VISIBLE
                        recyclerView.visibility = View.GONE
                    }
                    return@thread
                }
                
                val files = hiddenDir.listFiles { file ->
                    file.name.endsWith("_encrypted.aes")
                } ?: emptyArray()
                
                val newRecordings = mutableListOf<RecordingItem>()
                files.sortedByDescending { it.lastModified() }.forEach { file ->
                    val filename = file.name
                    // Parse metadata from filename: call_{type}_{number}_{timestamp}_encrypted.aes
                    val parts = filename.removeSuffix("_encrypted.aes").split("_")
                    if (parts.size >= 4) {
                        val callType = parts.getOrNull(1) ?: "unknown"
                        val phoneNumber = parts.getOrNull(2) ?: "unknown"
                        val timestamp = parts.getOrNull(3)?.toLongOrNull() ?: file.lastModified()
                        val duration = getAudioDuration(file) ?: 0L
                        
                        newRecordings.add(RecordingItem(
                            file = file,
                            filename = filename,
                            callType = callType,
                            phoneNumber = phoneNumber,
                            timestamp = timestamp,
                            duration = duration
                        ))
                    } else {
                        // Fallback for malformed filenames
                        newRecordings.add(RecordingItem(
                            file = file,
                            filename = filename,
                            callType = "unknown",
                            phoneNumber = "unknown",
                            timestamp = file.lastModified(),
                            duration = getAudioDuration(file) ?: 0L
                        ))
                    }
                }
                
                runOnUiThread {
                    recordings.clear()
                    recordings.addAll(newRecordings)
                    adapter.notifyDataSetChanged()
                    loadingProgressBar.visibility = View.GONE
                    
                    if (recordings.isEmpty()) {
                        noRecordingsTextView.visibility = View.VISIBLE
                        recyclerView.visibility = View.GONE
                    } else {
                        noRecordingsTextView.visibility = View.GONE
                        recyclerView.visibility = View.VISIBLE
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading recordings: ${e.message}", e)
                runOnUiThread {
                    Toast.makeText(this, R.string.error_loading_recordings, Toast.LENGTH_SHORT).show()
                    loadingProgressBar.visibility = View.GONE
                    noRecordingsTextView.visibility = View.VISIBLE
                }
            }
        }
    }
    
    private fun getAudioDuration(file: File): Long? {
        return try {
            // Decrypt the file temporarily to get duration
            val decryptedFile = decryptFile(file)
            if (decryptedFile == null) {
                Log.w(TAG, "Failed to decrypt file for duration extraction: ${file.name}")
                return null
            }
            
            MediaMetadataRetriever().use { retriever ->
                retriever.setDataSource(decryptedFile.absolutePath)
                val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                durationStr?.toLongOrNull()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting audio duration: ${e.message}", e)
            null
        }
    }
    
    private fun decryptFile(encryptedFile: File): File? {
        return try {
            // Get encryption key from KeyStoreManager
            val encryptionKey = KeyStoreManager.getOrCreateEncryptionKey()
            
            // Read IV from the beginning of the encrypted file (first 16 bytes)
            encryptedFile.inputStream().use { inputStream ->
                // Read IV (first 16 bytes)
                val ivBytes = ByteArray(IV_SIZE)
                val bytesRead = inputStream.read(ivBytes)
                if (bytesRead != IV_SIZE) {
                    Log.e(TAG, "Failed to read IV from encrypted file: expected $IV_SIZE bytes, got $bytesRead")
                    return null
                }
                
                val ivSpec = IvParameterSpec(ivBytes)
                
                // Create cipher for decryption
                val cipher = Cipher.getInstance(KeyStoreManager.getEncryptionAlgorithm())
                cipher.init(Cipher.DECRYPT_MODE, encryptionKey, ivSpec)
                
                // Create temporary file for decrypted content
                val tempFile = File.createTempFile("decrypted_", ".mp4", cacheDir)
                tempFile.deleteOnExit()
                
                // Decrypt the rest of the file
                FileOutputStream(tempFile).use { outputStream ->
                    CipherInputStream(inputStream, cipher).use { cipherInputStream ->
                        cipherInputStream.copyTo(outputStream)
                    }
                }
                
                tempFile
            }
        } catch (e: Exception) {
            Log.e(TAG, "Decryption failed: ${e.message}", e)
            null
        }
    }
    
    private fun playRecording(position: Int) {
        if (currentlyPlayingPosition == position) {
            stopPlayback()
            return
        }
        
        stopPlayback()
        
        val recording = recordings[position]
        thread {
            try {
                val decryptedFile = decryptFile(recording.file)
                if (decryptedFile == null) {
                    runOnUiThread {
                        Toast.makeText(this, R.string.error_decrypting_file, Toast.LENGTH_SHORT).show()
                    }
                    return@thread
                }
                
                runOnUiThread {
                    mediaPlayer = MediaPlayer().apply {
                        setDataSource(decryptedFile.absolutePath)
                        prepare()
                        start()
                        
                        setOnCompletionListener {
                            stopPlayback()
                            adapter.notifyItemChanged(position)
                        }
                        
                        currentlyPlayingPosition = position
                        adapter.notifyItemChanged(position)
                        Toast.makeText(this@FileManagerActivity, "Playing recording...", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Playback error: ${e.message}", e)
                runOnUiThread {
                    Toast.makeText(this, R.string.error_playing_audio, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun stopPlayback() {
        mediaPlayer?.release()
        mediaPlayer = null
        
        if (currentlyPlayingPosition != -1) {
            val previousPosition = currentlyPlayingPosition
            currentlyPlayingPosition = -1
            adapter.notifyItemChanged(previousPosition)
        }
    }
    
    private fun deleteRecording(position: Int) {
        val recording = recordings[position]
        
        AlertDialog.Builder(this)
            .setTitle(R.string.confirm_delete)
            .setMessage(R.string.confirm_delete_message)
            .setPositiveButton(R.string.yes) { dialog, _ ->
                thread {
                    try {
                        val deleted = recording.file.delete()
                        runOnUiThread {
                            if (deleted) {
                                recordings.removeAt(position)
                                adapter.notifyItemRemoved(position)
                                
                                if (recordings.isEmpty()) {
                                    noRecordingsTextView.visibility = View.VISIBLE
                                    recyclerView.visibility = View.GONE
                                }
                                
                                Toast.makeText(this, "Recording deleted", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(this, "Failed to delete recording", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Delete error: ${e.message}", e)
                        runOnUiThread {
                            Toast.makeText(this, "Error deleting recording", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                dialog.dismiss()
            }
            .setNegativeButton(R.string.no) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
    
    private fun shareRecording(position: Int) {
        val recording = recordings[position]
        
        thread {
            try {
                val decryptedFile = decryptFile(recording.file)
                if (decryptedFile == null) {
                    runOnUiThread {
                        Toast.makeText(this, R.string.error_decrypting_file, Toast.LENGTH_SHORT).show()
                    }
                    return@thread
                }
                
                runOnUiThread {
                    val shareIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        type = "audio/*"
                        putExtra(Intent.EXTRA_STREAM, android.net.Uri.fromFile(decryptedFile))
                        putExtra(Intent.EXTRA_SUBJECT, "Call recording: ${recording.filename}")
                        putExtra(Intent.EXTRA_TEXT, "Call recording from CallSpy app")
                        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                    }
                    
                    startActivity(Intent.createChooser(shareIntent, "Share recording"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Share error: ${e.message}", e)
                runOnUiThread {
                    Toast.makeText(this, "Error sharing recording", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        stopPlayback()
    }
    
    // Data class for recording items
    data class RecordingItem(
        val file: File,
        val filename: String,
        val callType: String,
        val phoneNumber: String,
        val timestamp: Long,
        val duration: Long?
    )
    
    // RecyclerView Adapter
    inner class RecordingAdapter : RecyclerView.Adapter<RecordingAdapter.ViewHolder>() {
        
        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val dateTextView: TextView = itemView.findViewById(R.id.recordingDateTextView)
            val typeTextView: TextView = itemView.findViewById(R.id.recordingTypeTextView)
            val numberTextView: TextView = itemView.findViewById(R.id.recordingNumberTextView)
            val durationTextView: TextView = itemView.findViewById(R.id.recordingDurationTextView)
            val playButton: Button = itemView.findViewById(R.id.playButton)
            val stopButton: Button = itemView.findViewById(R.id.stopButton)
            val deleteButton: Button = itemView.findViewById(R.id.deleteButton)
            val shareButton: Button = itemView.findViewById(R.id.shareButton)
            val progressBar: ProgressBar = itemView.findViewById(R.id.playbackProgressBar)
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.recording_item, parent, false)
            return ViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val recording = recordings[position]
            
            // Format date
            val dateStr = android.text.format.DateFormat.format("yyyy-MM-dd HH:mm:ss", recording.timestamp).toString()
            holder.dateTextView.text = getString(R.string.recording_date, dateStr)
            holder.typeTextView.text = getString(R.string.recording_type, recording.callType)
            holder.numberTextView.text = getString(R.string.recording_number, recording.phoneNumber)
            
            val durationStr = recording.duration?.let { 
                val minutes = it / 1000 / 60
                val seconds = (it / 1000) % 60
                String.format("%d:%02d", minutes, seconds)
            } ?: "Unknown"
            holder.durationTextView.text = getString(R.string.recording_duration, durationStr)
            
            val isPlaying = currentlyPlayingPosition == position
            holder.playButton.visibility = if (isPlaying) View.GONE else View.VISIBLE
            holder.stopButton.visibility = if (isPlaying) View.VISIBLE else View.GONE
            holder.progressBar.visibility = View.GONE // Simplified for now
            
            holder.playButton.setOnClickListener {
                playRecording(position)
            }
            
            holder.stopButton.setOnClickListener {
                stopPlayback()
            }
            
            holder.deleteButton.setOnClickListener {
                deleteRecording(position)
            }
            
            holder.shareButton.setOnClickListener {
                shareRecording(position)
            }
        }
        
        override fun getItemCount(): Int = recordings.size
    }
}
