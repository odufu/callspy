package com.example.callspy

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.callspy.service.CallRecordingService

class MainActivity : AppCompatActivity() {
    
    private lateinit var statusTextView: TextView
    private lateinit var openSettingsButton: Button
    private lateinit var startRecordingButton: Button
    private lateinit var stopRecordingButton: Button
    
    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
        private val REQUIRED_PERMISSIONS = arrayOf(
            android.Manifest.permission.RECORD_AUDIO,
            android.Manifest.permission.READ_PHONE_STATE,
            android.Manifest.permission.PROCESS_OUTGOING_CALLS
        )
    }
    
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
            if (checkPermissions()) {
                startRecordingService()
                Toast.makeText(this, "Starting recording service...", Toast.LENGTH_SHORT).show()
                statusTextView.text = "Recording service started"
            } else {
                requestPermissions()
                Toast.makeText(this, "Please grant all permissions first", Toast.LENGTH_SHORT).show()
            }
        }
        
        stopRecordingButton.setOnClickListener {
            stopRecordingService()
            Toast.makeText(this, "Stopping recording service...", Toast.LENGTH_SHORT).show()
            statusTextView.text = "Recording service stopped"
        }
    }
    
    override fun onResume() {
        super.onResume()
        updateStatus()
    }
    
    private fun updateStatus() {
        val serviceRunning = isServiceRunning(CallRecordingService::class.java)
        val permissionsGranted = checkPermissions()
        
        val status = StringBuilder()
        status.append("CallSpy Status:\n")
        status.append("Service: ${if (serviceRunning) "Running" else "Stopped"}\n")
        status.append("Permissions: ${if (permissionsGranted) "Granted" else "Missing"}\n")
        status.append("\nTap Settings to configure")
        
        statusTextView.text = status.toString()
        
        // Update button states
        startRecordingButton.isEnabled = permissionsGranted && !serviceRunning
        stopRecordingButton.isEnabled = serviceRunning
    }
    
    private fun checkPermissions(): Boolean {
        return REQUIRED_PERMISSIONS.all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    private fun requestPermissions() {
        ActivityCompat.requestPermissions(
            this,
            REQUIRED_PERMISSIONS,
            PERMISSION_REQUEST_CODE
        )
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (allGranted) {
                Toast.makeText(this, "All permissions granted!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Some permissions were denied", Toast.LENGTH_SHORT).show()
            }
            updateStatus()
        }
    }
    
    private fun startRecordingService() {
        val serviceIntent = Intent(this, CallRecordingService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)
    }
    
    private fun stopRecordingService() {
        val serviceIntent = Intent(this, CallRecordingService::class.java)
        stopService(serviceIntent)
    }
    
    private fun isServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        @Suppress("DEPRECATION")
        return manager.getRunningServices(Integer.MAX_VALUE).any { 
            it.service.className == serviceClass.name 
        }
    }
}
