package com.androkall.recorder.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.androkall.recorder.CallRecorderApp
import com.androkall.recorder.R
import com.androkall.recorder.data.AudioSourceOption
import com.androkall.recorder.data.RecordingExporter
import com.androkall.recorder.recording.CallAudioRecorder
import com.androkall.recorder.recording.CallAudioRouteController
import com.androkall.recorder.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class CallRecordingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var recorder: CallAudioRecorder
    private var audioRoute: CallAudioRouteController? = null
    private var autoSaveToDownloads: Boolean = false

    override fun onCreate() {
        super.onCreate()
        recorder = CallAudioRecorder(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val number = intent.getStringExtra(EXTRA_NUMBER)
                startAsForeground(number)
                scope.launch { startRecording(number) }
            }
            ACTION_STOP -> {
                stopRecordingAndSelf()
            }
            else -> {
                if (!recorder.isRecording) {
                    stopSelf()
                }
            }
        }
        return START_STICKY
    }

    private suspend fun startRecording(number: String?) {
        if (recorder.isRecording) return
        val app = application as CallRecorderApp
        val settings = app.settingsRepository.settings.first()
        val source = runCatching {
            AudioSourceOption.valueOf(settings.preferredAudioSource)
        }.getOrDefault(AudioSourceOption.BOTH_SIDES)
        autoSaveToDownloads = settings.autoSaveToDownloads

        val bothSides = settings.captureBothSides ||
            source == AudioSourceOption.BOTH_SIDES ||
            source == AudioSourceOption.VOICE_CALL

        try {
            if (bothSides) {
                audioRoute = CallAudioRouteController(this).also { it.enableSpeakerForBothSides() }
            }
            recorder.start(number, source, preferBothSides = bothSides)
            CallControlNotifier.showInCall(this, number, recording = true)
            if (settings.armedForNextCall) {
                app.settingsRepository.setArmedForNextCall(false)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
            audioRoute?.restore()
            audioRoute = null
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun stopRecordingAndSelf() {
        val file = recorder.stop()
        audioRoute?.restore()
        audioRoute = null
        if (file != null && autoSaveToDownloads) {
            runCatching {
                RecordingExporter.copyToDownloads(this, file)
            }.onFailure {
                Log.w(TAG, "Auto-save to Downloads failed: ${it.message}")
            }
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startAsForeground(number: String?) {
        val notification = buildNotification(number)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(number: String?): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, CallRecordingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val subtitle = number?.takeIf { it.isNotBlank() } ?: getString(R.string.notification_recording_text)

        return NotificationCompat.Builder(this, CallRecorderApp.CHANNEL_RECORDING)
            .setContentTitle(getString(R.string.notification_recording_title))
            .setContentText(subtitle)
            .setSmallIcon(R.drawable.ic_mic)
            .setContentIntent(openApp)
            .setOngoing(true)
            .addAction(0, getString(R.string.overlay_stop_record), stopIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        if (recorder.isRecording) {
            recorder.stop()
        }
        audioRoute?.restore()
        audioRoute = null
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "CallRecordingService"
        private const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.androkall.recorder.action.START_RECORDING"
        const val ACTION_STOP = "com.androkall.recorder.action.STOP_RECORDING"
        const val EXTRA_NUMBER = "extra_number"

        fun start(context: Context, phoneNumber: String?) {
            val intent = Intent(context, CallRecordingService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_NUMBER, phoneNumber)
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, CallRecordingService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
