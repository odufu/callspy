package com.example.callspy.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.callspy.service.CallRecordingService

class PhoneStateReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "PhoneStateReceiver"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) {
            Log.w(TAG, "Context or Intent is null")
            return
        }

        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
        val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)

        Log.d(TAG, "Phone state changed: $state, incoming number: $incomingNumber")

        if (!hasCallPermissions(context)) {
            Log.w(TAG, "Missing required call permissions")
            return
        }

        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                Log.d(TAG, "Incoming call ringing from: $incomingNumber")
                // Could prepare for recording here if needed
            }
            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                Log.d(TAG, "Call answered or outgoing call started")
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

    private fun hasCallPermissions(context: Context): Boolean {
        val recordAudioPermission = ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO)
        val readPhoneStatePermission = ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_PHONE_STATE)
        val processOutgoingCallsPermission = ContextCompat.checkSelfPermission(context, android.Manifest.permission.PROCESS_OUTGOING_CALLS)

        return recordAudioPermission == PackageManager.PERMISSION_GRANTED &&
               readPhoneStatePermission == PackageManager.PERMISSION_GRANTED &&
               processOutgoingCallsPermission == PackageManager.PERMISSION_GRANTED
    }

    private fun startCallRecordingService(context: Context) {
        val serviceIntent = Intent(context, CallRecordingService::class.java)
        ContextCompat.startForegroundService(context, serviceIntent)
        Log.d(TAG, "Started CallRecordingService")
    }

    private fun stopCallRecordingService(context: Context) {
        val serviceIntent = Intent(context, CallRecordingService::class.java)
        context.stopService(serviceIntent)
        Log.d(TAG, "Stopped CallRecordingService")
    }
}
