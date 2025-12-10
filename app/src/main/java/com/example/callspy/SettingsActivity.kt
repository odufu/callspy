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
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityManager
import android.content.ComponentName
import java.util.Locale

class SettingsActivity : AppCompatActivity() {
    
    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.PROCESS_OUTGOING_CALLS,
            Manifest.permission.POST_NOTIFICATIONS
        )
        
        // Accessibility service component name (must match AndroidManifest)
        private const val ACCESSIBILITY_SERVICE_CLASS_NAME = "com.example.callspy.service.CallAccessibilityService"
        private const val ACCESSIBILITY_SERVICE_PACKAGE_NAME = "com.example.callspy"
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
    
    override fun onResume() {
        super.onResume()
        // Update status when returning from settings
        updatePermissionStatus(findViewById(R.id.permissionStatusText))
    }
    
    private fun updatePermissionStatus(textView: TextView) {
        val status = StringBuilder()
        
        // Check each permission
        REQUIRED_PERMISSIONS.forEach { permission ->
            val granted = ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
            val permissionName = getPermissionDisplayName(permission)
            status.append("$permissionName: ${if (granted) "✅" else "❌"}\n")
        }
        
        // Check accessibility service
        val accessibilityEnabled = isAccessibilityServiceEnabled()
        status.append("Accessibility Service: ${if (accessibilityEnabled) "✅" else "❌"}")
        if (!accessibilityEnabled) {
            status.append("\n(WhatsApp/VoIP detection disabled)")
        }
        
        // Check if recording service can run in foreground
        val hasForegroundServicePermission = ContextCompat.checkSelfPermission(this, Manifest.permission.FOREGROUND_SERVICE) == PackageManager.PERMISSION_GRANTED
        status.append("\nForeground Service: ${if (hasForegroundServicePermission) "✅" else "❌"}")
        
        textView.text = status.toString()
    }
    
    private fun getPermissionDisplayName(permission: String): String {
        return when (permission) {
            Manifest.permission.RECORD_AUDIO -> "Record Audio"
            Manifest.permission.READ_PHONE_STATE -> "Read Phone State"
            Manifest.permission.PROCESS_OUTGOING_CALLS -> "Process Outgoing Calls"
            Manifest.permission.POST_NOTIFICATIONS -> "Post Notifications"
            Manifest.permission.FOREGROUND_SERVICE -> "Foreground Service"
            else -> permission.substringAfterLast('.')
        }
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
        Toast.makeText(this, "Please enable 'CallSpy Accessibility Service' for WhatsApp detection", Toast.LENGTH_LONG).show()
    }
    
    private fun openNotificationSettings() {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        }
        startActivity(intent)
    }
    
    private fun isAccessibilityServiceEnabled(): Boolean {
        val accessibilityManager = getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = accessibilityManager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        
        // Build the expected component name string
        val expectedComponentName = ComponentName(
            ACCESSIBILITY_SERVICE_PACKAGE_NAME,
            ACCESSIBILITY_SERVICE_CLASS_NAME
        ).flattenToString()
        
        return enabledServices.any { service ->
            // Check if the service ID matches our component name
            service.id.equals(expectedComponentName, ignoreCase = true) ||
            service.id.contains(ACCESSIBILITY_SERVICE_CLASS_NAME)
        }
    }
}
