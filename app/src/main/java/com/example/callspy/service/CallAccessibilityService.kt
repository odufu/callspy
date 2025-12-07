package com.example.callspy.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class CallAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "CallAccessibilityService"
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Accessibility service connected")
        
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                        AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED or
                        AccessibilityEvent.TYPE_VIEW_CLICKED or
                        AccessibilityEvent.TYPE_VIEW_FOCUSED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 100
            packageNames = arrayOf("com.whatsapp", "com.whatsapp.w4b")
            flags = AccessibilityServiceInfo.DEFAULT
        }
        
        serviceInfo = info
        Log.d(TAG, "Accessibility service configured for WhatsApp detection")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val className = event.className?.toString() ?: ""
                val packageName = event.packageName?.toString() ?: ""
                Log.d(TAG, "Window state changed: $packageName/$className")
                
                // Check if it's WhatsApp call screen
                if (packageName.contains("whatsapp") && className.contains("call")) {
                    Log.d(TAG, "WhatsApp call detected, starting recording")
                    startCallRecording()
                }
            }
            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED -> {
                Log.d(TAG, "Notification state changed")
                // Could detect WhatsApp call notifications here
            }
            else -> {
                // Other events
            }
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility service interrupted")
    }

    private fun startCallRecording() {
        val serviceIntent = Intent(this, CallRecordingService::class.java)
        startForegroundService(serviceIntent)
        Log.d(TAG, "Started CallRecordingService via accessibility service")
    }
}
