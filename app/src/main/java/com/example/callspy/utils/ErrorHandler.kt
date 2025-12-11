package com.example.callspy.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat

/**
 * ErrorHandler utility class for consistent error handling across the app.
 * Provides structured error types, logging, and user feedback mechanisms.
 */
object ErrorHandler {

    // Error type categories
    sealed class ErrorType {
        object RecordingError : ErrorType()
        object EncryptionError : ErrorType()
        object FileError : ErrorType()
        object PermissionError : ErrorType()
        object NetworkError : ErrorType()
        object UnknownError : ErrorType()
        
        override fun toString(): String = when (this) {
            is RecordingError -> "RecordingError"
            is EncryptionError -> "EncryptionError"
            is FileError -> "FileError"
            is PermissionError -> "PermissionError"
            is NetworkError -> "NetworkError"
            is UnknownError -> "UnknownError"
        }
    }

    private const val DEFAULT_ERROR_CHANNEL_ID = "callspy_error_channel"
    private const val DEFAULT_ERROR_CHANNEL_NAME = "CallSpy Errors"
    private const val DEFAULT_NOTIFICATION_ID = 9999

    /**
     * Log an error with consistent formatting
     */
    fun logError(tag: String, errorType: ErrorType, message: String, exception: Throwable? = null) {
        val fullMessage = "[${errorType}] $message"
        if (exception != null) {
            Log.e(tag, fullMessage, exception)
            // TODO: Add Firebase Crashlytics integration here
            // FirebaseCrashlytics.getInstance().recordException(exception)
            // FirebaseCrashlytics.getInstance().log("$tag: $fullMessage")
        } else {
            Log.e(tag, fullMessage)
            // TODO: Add Firebase Crashlytics log here
            // FirebaseCrashlytics.getInstance().log("$tag: $fullMessage")
        }
    }

    /**
     * Show an error toast to the user
     */
    fun showErrorToast(context: Context, message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    /**
     * Show an error notification to the user (for background services)
     */
    fun showErrorNotification(
        context: Context,
        title: String,
        message: String,
        channelId: String = DEFAULT_ERROR_CHANNEL_ID
    ) {
        createNotificationChannel(context, channelId)

        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(DEFAULT_NOTIFICATION_ID, notification)
    }

    /**
     * Comprehensive error handling method
     */
    fun handleError(
        context: Context?,
        tag: String,
        errorType: ErrorType,
        message: String,
        exception: Throwable? = null,
        showUserFeedback: Boolean = true
    ) {
        // Log the error
        logError(tag, errorType, message, exception)

        // Show user feedback if requested and context is available
        if (showUserFeedback && context != null) {
            when (errorType) {
                is ErrorType.RecordingError, 
                is ErrorType.EncryptionError,
                is ErrorType.FileError -> {
                    // For file/recording errors, show notification (service context) or toast (activity context)
                    if (context is android.app.Service) {
                        showErrorNotification(context, "CallSpy Error", message)
                    } else {
                        showErrorToast(context, message)
                    }
                }
                is ErrorType.PermissionError -> {
                    // Permission errors need clear user guidance
                    showErrorToast(context, "$message. Please check app permissions.")
                }
                else -> {
                    // Default to toast for other errors
                    showErrorToast(context, message)
                }
            }
        }
    }

    /**
     * Helper for recording errors
     */
    fun handleRecordingError(context: Context?, tag: String, message: String, exception: Throwable? = null) {
        handleError(context, tag, ErrorType.RecordingError, "Recording failed: $message", exception)
    }

    /**
     * Helper for encryption errors
     */
    fun handleEncryptionError(context: Context?, tag: String, message: String, exception: Throwable? = null) {
        handleError(context, tag, ErrorType.EncryptionError, "Encryption failed: $message", exception)
    }

    /**
     * Helper for file errors
     */
    fun handleFileError(context: Context?, tag: String, message: String, exception: Throwable? = null) {
        handleError(context, tag, ErrorType.FileError, "File operation failed: $message", exception)
    }

    /**
     * Helper for permission errors
     */
    fun handlePermissionError(context: Context?, tag: String, message: String, exception: Throwable? = null) {
        handleError(context, tag, ErrorType.PermissionError, "Permission error: $message", exception)
    }

    /**
     * Create notification channel for error notifications (required for Android O+)
     */
    private fun createNotificationChannel(context: Context, channelId: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                DEFAULT_ERROR_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Error notifications for CallSpy app"
            }

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}
