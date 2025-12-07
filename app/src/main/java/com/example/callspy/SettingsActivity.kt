package com.example.callspy

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast

class SettingsActivity : AppCompatActivity() {
    
    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.PROCESS_OUTGOING_CALLS,
            Manifest.permission.POST_NOTIFICATIONS
        )
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        
        val permissionStatusText = findViewById<TextView>(R.id.permissionStatusText)
        val requestPermissionsButton = findViewById<Button>(R.id.requestPermissionsButton)
        val accessibilitySettingsButton = findViewById<Button>(R.id.accessibilitySettingsButton)
        val notificationSettingsButton = findViewById<Button>(R.id.notificationSettingsButton)
        
        updatePermissionStatus(permissionStatusText)
        
        requestPermissionsButton.setOnClickListener {
            requestPermissions()
        }
        
        accessibilitySettingsButton.setOnClickListener {
            openAccessibilitySettings()
        }
        
        notificationSettingsButton.setOnClickListener {
            openNotificationSettings()
        }
    }
    
    private fun updatePermissionStatus(textView: TextView) {
        val status = StringBuilder()
        REQUIRED_PERMISSIONS.forEach { permission ->
            val granted = ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
            val permissionName = permission.substringAfterLast('.')
            status.append("$permissionName: ${if (granted) "✓" else "✗"}\n")
        }
        
        // Check accessibility service
        val accessibilityEnabled = isAccessibilityServiceEnabled()
        status.append("Accessibility Service: ${if (accessibilityEnabled) "✓" else "✗"}\n")
        
        textView.text = status.toString()
    }
    
    private fun requestPermissions() {
        val permissionsToRequest = REQUIRED_PERMISSIONS.filter { permission ->
            ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()
        
        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest, PERMISSION_REQUEST_CODE)
        } else {
            Toast.makeText(this, "All permissions already granted", Toast.LENGTH_SHORT).show()
            updatePermissionStatus(findViewById(R.id.permissionStatusText))
        }
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
            updatePermissionStatus(findViewById(R.id.permissionStatusText))
        }
    }
    
    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
    }
    
    private fun openNotificationSettings() {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        }
        startActivity(intent)
    }
    
    private fun isAccessibilityServiceEnabled(): Boolean {
        // This is a simplified check - in production you'd need to check if your service is enabled
        return false // Placeholder
    }
}