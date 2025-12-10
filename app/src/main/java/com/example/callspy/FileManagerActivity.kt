package com.example.callspy

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.media.MediaPlayer
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

/**
 * Activity to manage recorded call files.
 * Lists encrypted audio recordings, allows playback, deletion, sharing, and refreshing.
 */
class FileManagerActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: RecordingAdapter
    private lateinit var refreshButton: FloatingActionButton
    private lateinit var emptyStateTextView: TextView

    private val recordings = mutableListOf<Recording>()
    private var mediaPlayer: MediaPlayer? = null
    private var playingPosition: Int = -1

    private val activityJob = Job()
    private val uiScope = CoroutineScope(Dispatchers.Main + activityJob)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_file_manager)

        recyclerView = findViewById(R.id.recyclerViewRecordings)
        refreshButton = findViewById(R.id.buttonRefresh)
        emptyStateTextView = findViewById(R.id.textViewEmptyState)

        adapter = RecordingAdapter(recordings,
            onPlayClick = { position -> playOrStopRecording(position) },
            onDeleteClick = { position -> deleteRecording(position) },
            onShareClick = { position -> shareRecording(position) }
        )

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        refreshButton.setOnClickListener {
            loadRecordings()
        }

        loadRecordings()
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        activityJob.cancel()
    }

    /**
     * Load recordings from app private storage asynchronously
     */
    private fun loadRecordings() {
        uiScope.launch {
            val files = withContext(Dispatchers.IO) {
                listFiles()
            }
            recordings.clear()
            recordings.addAll(files)
            adapter.notifyDataSetChanged()
            updateEmptyState()
        }
    }

    /**
     * List all encrypted audio recordings from app's private storage
     * @return List of Recording objects
     */
    private fun listFiles(): List<Recording> {
        val recordingsList = mutableListOf<Recording>()
        try {
            val dir = filesDir
            val files = dir.listFiles { file -> file.extension == "enc" } ?: arrayOf()
            for (file in files) {
                // Extract metadata from filename or file attributes
                val filename = file.name
                val timestamp = file.lastModified()
                val callType = extractCallTypeFromFilename(filename)
                val duration = extractDurationFromFilename(filename)
                recordingsList.add(Recording(file, filename, timestamp, callType, duration))
            }
        } catch (e: Exception) {
            runOnUiThread {
                Toast.makeText(this, "Error listing files: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
        return recordingsList
    }

    /**
     * Extract call type from filename (example logic)
     */
    private fun extractCallTypeFromFilename(filename: String): String {
        return when {
            filename.contains("incoming", ignoreCase = true) -> "Incoming"
            filename.contains("outgoing", ignoreCase = true) -> "Outgoing"
            else -> "Unknown"
        }
    }

    /**
     * Extract duration from filename (example logic)
     */
    private fun extractDurationFromFilename(filename: String): String {
        // Example: filename might contain duration like _dur1234_
        val regex = "_dur(\\d+)_".toRegex()
        val match = regex.find(filename)
        return if (match != null) {
            val durationMs = match.groupValues[1].toLongOrNull() ?: 0L
            formatDuration(durationMs)
        } else {
            "Unknown"
        }
    }

    /**
     * Format duration milliseconds to mm:ss
     */
    private fun formatDuration(durationMs: Long): String {
        val seconds = durationMs / 1000
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, remainingSeconds)
    }

    /**
     * Play or stop recording playback
     */
    private fun playOrStopRecording(position: Int) {
        if (position == playingPosition) {
            stopPlayback()
        } else {
            playRecording(position)
        }
    }

    /**
     * Play recording (decrypt and play)
     */
    private fun playRecording(position: Int) {
        stopPlayback()
        val recording = recordings[position]
        uiScope.launch {
            try {
                val decryptedFile = withContext(Dispatchers.IO) {
                    decryptFile(recording.file)
                }
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(decryptedFile.absolutePath)
                    prepare()
                    start()
                    setOnCompletionListener {
                        stopPlayback()
                    }
                }
                playingPosition = position
                adapter.notifyDataSetChanged()
            } catch (e: Exception) {
                Toast.makeText(this@FileManagerActivity, "Error playing file: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Stop playback and release media player
     */
    private fun stopPlayback() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        val oldPosition = playingPosition
        playingPosition = -1
        if (oldPosition != -1) {
            adapter.notifyItemChanged(oldPosition)
        }
    }

    /**
     * Decrypt file to a temporary file for playback
     * @param encryptedFile Encrypted input file
     * @return Decrypted temporary file
     */
    private fun decryptFile(encryptedFile: File): File {
        // Placeholder for decryption logic
        // For demo, just copy the file to temp with .mp3 extension
        val tempFile = File.createTempFile("decrypted_", ".mp3", cacheDir)
        FileInputStream(encryptedFile).use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output)
            }
        }
        return tempFile
    }

    /**
     * Delete individual recording
     */
    private fun deleteRecording(position: Int) {
        val recording = recordings[position]
        uiScope.launch {
            val deleted = withContext(Dispatchers.IO) {
                recording.file.delete()
            }
            if (deleted) {
                if (position == playingPosition) {
                    stopPlayback()
                }
                recordings.removeAt(position)
                adapter.notifyItemRemoved(position)
                updateEmptyState()
                Toast.makeText(this@FileManagerActivity, "Recording deleted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@FileManagerActivity, "Failed to delete recording", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Share recording via other apps
     */
    private fun shareRecording(position: Int) {
        val recording = recordings[position]
        uiScope.launch {
            try {
                val decryptedFile = withContext(Dispatchers.IO) {
                    decryptFile(recording.file)
                }
                val uri: Uri = FileProvider.getUriForFile(
                    this@FileManagerActivity,
                    "com.example.callspy.fileprovider",
                    decryptedFile
                )
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "audio/*"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(shareIntent, "Share recording"))
            } catch (e: Exception) {
                Toast.makeText(this@FileManagerActivity, "Error sharing file: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Update empty state visibility
     */
    private fun updateEmptyState() {
        if (recordings.isEmpty()) {
            emptyStateTextView.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            emptyStateTextView.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
        }
    }

    /**
     * Data class representing a recording
     */
    data class Recording(
        val file: File,
        val filename: String,
        val timestamp: Long,
        val callType: String,
        val duration: String
    )

    /**
     * RecyclerView Adapter for recordings
     */
    inner class RecordingAdapter(
        private val recordings: List<Recording>,
        private val onPlayClick: (Int) -> Unit,
        private val onDeleteClick: (Int) -> Unit,
        private val onShareClick: (Int) -> Unit
    ) : RecyclerView.Adapter<RecordingAdapter.RecordingViewHolder>() {

        inner class RecordingViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val textFilename: TextView = itemView.findViewById(R.id.textFilename)
            val textTimestamp: TextView = itemView.findViewById(R.id.textTimestamp)
            val textCallType: TextView = itemView.findViewById(R.id.textCallType)
            val textDuration: TextView = itemView.findViewById(R.id.textDuration)
            val buttonPlay: ImageButton = itemView.findViewById(R.id.buttonPlay)
            val buttonDelete: ImageButton = itemView.findViewById(R.id.buttonDelete)
            val buttonShare: ImageButton = itemView.findViewById(R.id.buttonShare)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecordingViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_recording, parent, false)
            return RecordingViewHolder(view)
        }

        override fun onBindViewHolder(holder: RecordingViewHolder, position: Int) {
            val recording = recordings[position]
            holder.textFilename.text = recording.filename
            holder.textTimestamp.text = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(recording.timestamp))
            holder.textCallType.text = recording.callType
            holder.textDuration.text = recording.duration

            if (position == playingPosition) {
                holder.buttonPlay.setImageResource(android.R.drawable.ic_media_pause)
            } else {
                holder.buttonPlay.setImageResource(android.R.drawable.ic_media_play)
            }

            holder.buttonPlay.setOnClickListener {
                onPlayClick(position)
            }
            holder.buttonDelete.setOnClickListener {
                onDeleteClick(position)
            }
            holder.buttonShare.setOnClickListener {
                onShareClick(position)
            }
        }

        override fun getItemCount(): Int = recordings.size
    }
}
