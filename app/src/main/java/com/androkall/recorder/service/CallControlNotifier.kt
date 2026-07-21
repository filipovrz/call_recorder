package com.androkall.recorder.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.androkall.recorder.CallRecorderApp
import com.androkall.recorder.R
import com.androkall.recorder.receiver.CallActionReceiver
import com.androkall.recorder.ui.CallPromptActivity
import com.androkall.recorder.ui.MainActivity

/**
 * Heads-up / full-screen call controls that work without overlay permission
 * (important for sideloaded APKs blocked by Android "Restricted settings").
 */
object CallControlNotifier {
    const val NOTIFICATION_ID = 1002

    fun showRinging(context: Context, number: String?) {
        show(context, number, recording = false, ringing = true)
    }

    fun showInCall(context: Context, number: String?, recording: Boolean) {
        show(context, number, recording = recording, ringing = false)
    }

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    private fun show(
        context: Context,
        number: String?,
        recording: Boolean,
        ringing: Boolean
    ) {
        val appIntent = PendingIntent.getActivity(
            context,
            10,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val promptIntent = PendingIntent.getActivity(
            context,
            11,
            Intent(context, CallPromptActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(CallPromptActivity.EXTRA_NUMBER, number)
                putExtra(CallPromptActivity.EXTRA_RINGING, ringing)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val startIntent = PendingIntent.getBroadcast(
            context,
            12,
            Intent(context, CallActionReceiver::class.java).apply {
                action = CallActionReceiver.ACTION_START
                putExtra(CallActionReceiver.EXTRA_NUMBER, number)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getBroadcast(
            context,
            13,
            Intent(context, CallActionReceiver::class.java).apply {
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
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(promptIntent, ringing)

        if (recording) {
            builder.addAction(0, context.getString(R.string.overlay_stop_record), stopIntent)
        } else {
            builder.addAction(0, context.getString(R.string.overlay_start_record), startIntent)
            builder.addAction(
                0,
                context.getString(R.string.call_notify_open),
                promptIntent
            )
        }

        val nm = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm?.getNotificationChannel(CallRecorderApp.CHANNEL_CALL)?.let { ch ->
                if (ch.importance < NotificationManager.IMPORTANCE_DEFAULT) {
                    ch.importance = NotificationManager.IMPORTANCE_HIGH
                }
            }
        }

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
    }
}
