package com.example.callspy

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.Button
import android.widget.TextView
import android.widget.Toast

class MainActivity : AppCompatActivity() {
    
    private lateinit var statusTextView: TextView
    private lateinit var openSettingsButton: Button
    private lateinit var startRecordingButton: Button
    private lateinit var stopRecordingButton: Button
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        statusTextView = findViewById(R.id.statusTextView)
        openSettingsButton = findViewById(R.id.openSettingsButton)
        startRecordingButton = findViewById(R.id.startRecordingButton)
        stopRecordingButton = findViewById(R.id.stopRecordingButton)
        
        updateStatus()
        
        openSettingsButton.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }
        
        startRecordingButton.setOnClickListener {
            Toast.makeText(this, "Starting recording service...", Toast.LENGTH_SHORT).show()
            // In a real implementation, we would start the recording service here
            // For now, just update status
            statusTextView.text = "Recording service started"
        }
        
        stopRecordingButton.setOnClickListener {
            Toast.makeText(this, "Stopping recording service...", Toast.LENGTH_SHORT).show()
            // In a real implementation, we would stop the recording service here
            // For now, just update status
            statusTextView.text = "Recording service stopped"
        }
    }
    
    override fun onResume() {
        super.onResume()
        updateStatus()
    }
    
    private fun updateStatus() {
        // This would check actual service status in a real implementation
        val status = "CallSpy Ready\nTap Settings to configure permissions"
        statusTextView.text = status
    }
}