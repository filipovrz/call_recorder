package com.androkall.recorder.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
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
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var recorder: CallAudioRecorder
    private var audioRoute: CallAudioRouteController? = null
    private var autoSaveToDownloads: Boolean = false
    private var startedForeground = false

    override fun onCreate() {
        super.onCreate()
        recorder = CallAudioRecorder(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val number = intent.getStringExtra(EXTRA_NUMBER)
                // Must enter foreground immediately or the system kills the service.
                if (!ensureForeground(number)) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                isActive = true
                // Short delay helps MIC attach after call audio path is up (OEM quirk).
                mainHandler.postDelayed({
                    scope.launch { startRecording(number) }
                }, 700L)
            }
            ACTION_STOP -> {
                mainHandler.removeCallbacksAndMessages(null)
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
        if (recorder.isRecording) {
            CallControlNotifier.showInCall(this, number, recording = true)
            return
        }
        val app = application as CallRecorderApp
        val settings = runCatching { app.settingsRepository.settings.first() }.getOrNull()
            ?: return failAndStop("settings unavailable")

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
                runCatching { app.settingsRepository.setArmedForNextCall(false) }
            }
            Log.i(TAG, "Recording started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
            failAndStop(e.message ?: "recorder failed")
        }
    }

    private fun failAndStop(reason: String) {
        Log.w(TAG, "Stopping after failure: $reason")
        audioRoute?.restore()
        audioRoute = null
        isActive = false
        runCatching {
            if (startedForeground) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            }
        }
        stopSelf()
    }

    private fun stopRecordingAndSelf() {
        val file = runCatching { recorder.stop() }.getOrNull()
        audioRoute?.restore()
        audioRoute = null
        isActive = false
        if (file != null && autoSaveToDownloads) {
            runCatching { RecordingExporter.copyToDownloads(this, file) }
                .onFailure { Log.w(TAG, "Auto-save to Downloads failed: ${it.message}") }
        }
        CallControlNotifier.cancel(this)
        runCatching {
            if (startedForeground) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            }
        }
        stopSelf()
    }

    /**
     * Only MICROPHONE type — PHONE_CALL FGS type crashes on many devices unless the app
     * is the default dialer / ConnectionService owner.
     */
    private fun ensureForeground(number: String?): Boolean {
        return try {
            val notification = buildNotification(number)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            startedForeground = true
            true
        } catch (e: Exception) {
            Log.e(TAG, "startForeground failed", e)
            false
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
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        if (recorder.isRecording) {
            runCatching { recorder.stop() }
        }
        audioRoute?.restore()
        audioRoute = null
        isActive = false
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "CallRecordingService"
        private const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.androkall.recorder.action.START_RECORDING"
        const val ACTION_STOP = "com.androkall.recorder.action.STOP_RECORDING"
        const val EXTRA_NUMBER = "extra_number"

        @Volatile
        var isActive: Boolean = false
            private set

        fun start(context: Context, phoneNumber: String?) {
            if (isActive) {
                Log.i(TAG, "Already active — ignore duplicate start")
                return
            }
            val intent = Intent(context, CallRecordingService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_NUMBER, phoneNumber)
            }
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                Log.e(TAG, "startForegroundService failed", e)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, CallRecordingService::class.java).apply {
                action = ACTION_STOP
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "stop service failed", e)
            }
        }
    }
}
