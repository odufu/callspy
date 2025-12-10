package com.example.callspy.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.callspy.service.CallRecordingService

class PhoneStateReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "PhoneStateReceiver"
        
        // Android version constants for compatibility checks
        private const val ANDROID_9_PIE = 28  // Android 9.0
        private const val ANDROID_10_Q = 29   // Android 10
        private const val ANDROID_11_R = 30   // Android 11
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) {
            Log.w(TAG, "Context or Intent is null")
            return
        }

        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
        val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)

        Log.d(TAG, "Phone state changed: $state, incoming number: $incomingNumber")
        
        // Log Android version for debugging
        Log.d(TAG, "Android SDK: ${Build.VERSION.SDK_INT} (${Build.VERSION.RELEASE})")

        if (!hasRequiredPermissions(context)) {
            Log.w(TAG, "Missing required call permissions")
            return
        }

        // Check Android version compatibility
        if (Build.VERSION.SDK_INT >= ANDROID_10_Q) {
            Log.w(TAG, "Android 10+ detected - call recording may be limited")
            Log.w(TAG, "Note: On Android 10+, READ_PHONE_STATE is heavily restricted")
            Log.w(TAG, "App may not detect calls unless it's the default dialer app")
        }

        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                Log.d(TAG, "Incoming call ringing from: $incomingNumber")
                // Could prepare for recording here if needed
                // On Android 10+, we might not get this event without being default dialer
            }
            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                Log.d(TAG, "Call answered or outgoing call started")
                
                // Check if this is likely to work on current Android version
                if (Build.VERSION.SDK_INT >= ANDROID_10_Q) {
                    Log.w(TAG, "Warning: On Android 10+, call detection may not work")
                    Log.w(TAG, "Consider using accessibility service for WhatsApp/VoIP calls")
                }
                
                startCallRecordingService(context)
            }
            TelephonyManager.EXTRA_STATE_IDLE -> {
                Log.d(TAG, "Call ended or idle")
                stopCallRecordingService(context)
            }
            else -> {
                Log.d(TAG, "Unknown phone state: $state")
            }
        }
    }

    private fun hasRequiredPermissions(context: Context): Boolean {
        // Check RECORD_AUDIO permission
        val recordAudioPermission = ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.RECORD_AUDIO
        )
        
        // Check READ_PHONE_STATE permission (restricted on Android 10+)
        val readPhoneStatePermission = ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.READ_PHONE_STATE
        )
        
        // PROCESS_OUTGOING_CALLS was deprecated in Android 10
        // We'll check it only on older Android versions
        var processOutgoingCallsPermission = PackageManager.PERMISSION_GRANTED
        if (Build.VERSION.SDK_INT < ANDROID_10_Q) {
            processOutgoingCallsPermission = ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.PROCESS_OUTGOING_CALLS
            )
        } else {
            Log.d(TAG, "PROCESS_OUTGOING_CALLS deprecated on Android 10+, skipping check")
        }
        
        val hasRecordAudio = recordAudioPermission == PackageManager.PERMISSION_GRANTED
        val hasReadPhoneState = readPhoneStatePermission == PackageManager.PERMISSION_GRANTED
        val hasProcessOutgoingCalls = processOutgoingCallsPermission == PackageManager.PERMISSION_GRANTED
        
        Log.d(TAG, "Permission status - RECORD_AUDIO: $hasRecordAudio, " +
                   "READ_PHONE_STATE: $hasReadPhoneState, " +
                   "PROCESS_OUTGOING_CALLS: $hasProcessOutgoingCalls")
        
        // On Android 10+, PROCESS_OUTGOING_CALLS is deprecated, so we don't require it
        return if (Build.VERSION.SDK_INT >= ANDROID_10_Q) {
            hasRecordAudio && hasReadPhoneState
        } else {
            hasRecordAudio && hasReadPhoneState && hasProcessOutgoingCalls
        }
    }

    private fun startCallRecordingService(context: Context) {
        try {
            val serviceIntent = Intent(context, CallRecordingService::class.java)
            ContextCompat.startForegroundService(context, serviceIntent)
            Log.d(TAG, "Started CallRecordingService")
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException starting service: ${e.message}")
            Log.e(TAG, "May need additional permissions or foreground service type")
        } catch (e: IllegalStateException) {
            Log.e(TAG, "IllegalStateException starting service: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Exception starting service: ${e.message}")
        }
    }

    private fun stopCallRecordingService(context: Context) {
        try {
            val serviceIntent = Intent(context, CallRecordingService::class.java)
            context.stopService(serviceIntent)
            Log.d(TAG, "Stopped CallRecordingService")
        } catch (e: Exception) {
            Log.e(TAG, "Exception stopping service: ${e.message}")
        }
    }
}
