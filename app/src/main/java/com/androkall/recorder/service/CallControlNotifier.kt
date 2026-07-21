package com.androkall.recorder.service

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.androkall.recorder.CallRecorderApp
import com.androkall.recorder.R
import com.androkall.recorder.receiver.CallActionReceiver
import com.androkall.recorder.ui.MainActivity

/**
 * Heads-up call controls that work without overlay permission.
 * Never throws — notification failures must not crash the app during a call.
 */
object CallControlNotifier {
    private const val TAG = "CallControlNotifier"
    const val NOTIFICATION_ID = 1002

    fun showRinging(context: Context, number: String?) {
        show(context, number, recording = false, ringing = true)
    }

    fun showInCall(context: Context, number: String?, recording: Boolean) {
        show(context, number, recording = recording, ringing = false)
    }

    fun cancel(context: Context) {
        runCatching { NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID) }
    }

    private fun show(
        context: Context,
        number: String?,
        recording: Boolean,
        ringing: Boolean
    ) {
        runCatching {
            if (!canPostNotifications(context)) {
                Log.w(TAG, "Notifications disabled — skip call control notify")
                return
            }

            val app = context.applicationContext
            val appIntent = PendingIntent.getActivity(
                app,
                10,
                Intent(app, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val startIntent = PendingIntent.getBroadcast(
                app,
                12,
                Intent(app, CallActionReceiver::class.java).apply {
                    action = CallActionReceiver.ACTION_START
                    putExtra(CallActionReceiver.EXTRA_NUMBER, number)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val stopIntent = PendingIntent.getBroadcast(
                app,
                13,
                Intent(app, CallActionReceiver::class.java).apply {
                    action = CallActionReceiver.ACTION_STOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val title = when {
                recording -> context.getString(R.string.call_notify_recording_title)
                ringing -> context.getString(R.string.call_notify_ringing_title)
                else -> context.getString(R.string.call_notify_incall_title)
            }
            val text = number?.takeIf { it.isNotBlank() }
                ?: context.getString(R.string.call_notify_text)

            val builder = NotificationCompat.Builder(context, CallRecorderApp.CHANNEL_CALL)
                .setSmallIcon(R.drawable.ic_mic)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(appIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setAutoCancel(false)
                .setOnlyAlertOnce(true)

            if (recording) {
                builder.addAction(0, context.getString(R.string.overlay_stop_record), stopIntent)
            } else {
                builder.addAction(0, context.getString(R.string.overlay_start_record), startIntent)
            }

            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
        }.onFailure {
            Log.e(TAG, "Failed to show call notification", it)
        }
    }

    private fun canPostNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
}
