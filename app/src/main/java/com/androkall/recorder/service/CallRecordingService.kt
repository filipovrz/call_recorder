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
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
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
import java.io.File

class CallRecordingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var recorder: CallAudioRecorder
    private var audioRoute: CallAudioRouteController? = null
    private var startedForeground = false
    private var phoneNumber: String? = null
    private var telephonyManager: TelephonyManager? = null
    private var telephonyCallback: TelephonyCallback? = null
    private var legacyListener: PhoneStateListener? = null
    private var recordingStartedAt = 0L
    private var finishing = false

    override fun onCreate() {
        super.onCreate()
        recorder = CallAudioRecorder(this)
        telephonyManager = getSystemService(TELEPHONY_SERVICE) as TelephonyManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                phoneNumber = intent.getStringExtra(EXTRA_NUMBER)
                finishing = false
                if (!ensureForeground(phoneNumber)) {
                    isRunning = false
                    stopSelf()
                    return START_NOT_STICKY
                }
                isRunning = true
                stopRequested = false
                listenForCallEnd()
                // Start ASAP — do not wait; delayed start was racing with spurious IDLE.
                scope.launch { beginRecording() }
            }
            ACTION_STOP -> {
                stopRequested = true
                finishRecording(reason = "stop")
            }
            else -> {
                if (!recorder.isRecording) stopSelf()
            }
        }
        return START_STICKY
    }

    private suspend fun beginRecording() {
        if (stopRequested) return
        if (recorder.isRecording) return

        val app = application as CallRecorderApp
        val settings = runCatching { app.settingsRepository.settings.first() }.getOrElse {
            com.androkall.recorder.data.AppSettings()
        }
        val source = runCatching {
            AudioSourceOption.valueOf(settings.preferredAudioSource)
        }.getOrDefault(AudioSourceOption.MIC)

        val bothSides = settings.captureBothSides ||
            source == AudioSourceOption.BOTH_SIDES ||
            source == AudioSourceOption.VOICE_CALL

        try {
            if (bothSides) {
                audioRoute = CallAudioRouteController(this).also { it.enableSpeakerForBothSides() }
            }
            val file = recorder.start(phoneNumber, source, preferBothSides = bothSides)
            recordingStartedAt = System.currentTimeMillis()
            updateRecordingNotification(phoneNumber, file.name)
            CallControlNotifier.showInCall(this, phoneNumber, recording = true)
            if (settings.armedForNextCall) {
                runCatching { app.settingsRepository.setArmedForNextCall(false) }
            }
            Log.i(TAG, "Recording OK: ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Recording failed to start", e)
            notifySavedOrFailed("Записът не стартира: ${e.message ?: "грешка"}")
            teardown(keepService = false)
        }
    }

    private fun finishRecording(reason: String) {
        if (finishing) {
            Log.d(TAG, "finishRecording already in progress ($reason)")
            return
        }
        finishing = true
        mainHandler.removeCallbacksAndMessages(null)
        unlistenCallEnd()
        val file = runCatching { recorder.stop() }.getOrNull()
        audioRoute?.restore()
        audioRoute = null
        isRunning = false
        stopRequested = false

        if (file != null) {
            // Always copy to public Downloads so the user can find it.
            val publicUri = runCatching { RecordingExporter.copyToDownloads(this, file) }.getOrNull()
            val msg = if (publicUri != null) {
                "Запазен в Изтегляния/EvtinkoCallRecorder\n${file.name} (${file.length() / 1024} KB)"
            } else {
                "Запазен в приложението\n${file.name} (${file.length() / 1024} KB)"
            }
            Log.i(TAG, "Saved ($reason): ${file.absolutePath} public=$publicUri")
            notifySavedOrFailed(msg)
        } else {
            Log.w(TAG, "No file kept after stop ($reason)")
            notifySavedOrFailed("Няма запазен аудио файл (празен или неуспешен запис)")
        }

        runCatching {
            if (startedForeground) stopForeground(STOP_FOREGROUND_REMOVE)
        }
        CallControlNotifier.cancel(this)
        stopSelf()
    }

    private fun teardown(keepService: Boolean) {
        unlistenCallEnd()
        runCatching { if (recorder.isRecording) recorder.stop() }
        audioRoute?.restore()
        audioRoute = null
        isRunning = false
        if (!keepService) {
            runCatching {
                if (startedForeground) stopForeground(STOP_FOREGROUND_REMOVE)
            }
            stopSelf()
        }
    }

    private fun listenForCallEnd() {
        val tm = telephonyManager ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val cb = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                    override fun onCallStateChanged(state: Int) {
                        if (state == TelephonyManager.CALL_STATE_IDLE) {
                            Log.i(TAG, "TelephonyCallback IDLE — stop recording")
                            mainHandler.post { finishRecording(reason = "telephony-idle") }
                        }
                    }
                }
                telephonyCallback = cb
                tm.registerTelephonyCallback(ContextCompat.getMainExecutor(this), cb)
            } else {
                @Suppress("DEPRECATION")
                val listener = object : PhoneStateListener() {
                    @Deprecated("Deprecated in Java")
                    override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                        if (state == TelephonyManager.CALL_STATE_IDLE) {
                            Log.i(TAG, "PhoneStateListener IDLE — stop recording")
                            mainHandler.post { finishRecording(reason = "listener-idle") }
                        }
                    }
                }
                legacyListener = listener
                @Suppress("DEPRECATION")
                tm.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Cannot listen call state: ${e.message}")
        }
    }

    private fun unlistenCallEnd() {
        val tm = telephonyManager ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                telephonyCallback?.let { tm.unregisterTelephonyCallback(it) }
            } else {
                @Suppress("DEPRECATION")
                legacyListener?.let { tm.listen(it, PhoneStateListener.LISTEN_NONE) }
            }
        } catch (_: Exception) {
        }
        telephonyCallback = null
        legacyListener = null
    }

    private fun ensureForeground(number: String?): Boolean {
        return try {
            val notification = buildRecordingNotification(number, null)
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

    private fun updateRecordingNotification(number: String?, fileName: String) {
        if (!startedForeground) return
        val notification = buildRecordingNotification(number, fileName)
        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
    }

    private fun notifySavedOrFailed(message: String) {
        runCatching {
            val openApp = PendingIntent.getActivity(
                this,
                2,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val n = NotificationCompat.Builder(this, CallRecorderApp.CHANNEL_CALL)
                .setSmallIcon(R.drawable.ic_mic)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setContentIntent(openApp)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()
            NotificationManagerCompat.from(this).notify(NOTIFICATION_SAVED_ID, n)
        }
    }

    private fun buildRecordingNotification(number: String?, fileName: String?): Notification {
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
        val text = when {
            fileName != null -> "Запис: $fileName"
            !number.isNullOrBlank() -> number
            else -> getString(R.string.notification_recording_text)
        }

        return NotificationCompat.Builder(this, CallRecorderApp.CHANNEL_RECORDING)
            .setContentTitle(getString(R.string.notification_recording_title))
            .setContentText(text)
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
        unlistenCallEnd()
        mainHandler.removeCallbacksAndMessages(null)
        if (recorder.isRecording) {
            runCatching { recorder.stop() }
        }
        audioRoute?.restore()
        audioRoute = null
        isRunning = false
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "CallRecordingService"
        private const val NOTIFICATION_ID = 1001
        private const val NOTIFICATION_SAVED_ID = 1003
        const val ACTION_START = "com.androkall.recorder.action.START_RECORDING"
        const val ACTION_STOP = "com.androkall.recorder.action.STOP_RECORDING"
        const val EXTRA_NUMBER = "extra_number"

        @Volatile
        var isRunning: Boolean = false
            private set

        @Volatile
        private var stopRequested: Boolean = false

        /** Ignore hang-up broadcasts this many ms after OFFHOOK (OEM glitches). */
        const val IDLE_GRACE_MS = 2500L

        fun start(context: Context, phoneNumber: String?) {
            if (isRunning) {
                Log.i(TAG, "Already running — keep current recording")
                return
            }
            stopRequested = false
            val intent = Intent(context, CallRecordingService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_NUMBER, phoneNumber)
            }
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                Log.e(TAG, "startForegroundService failed", e)
                isRunning = false
            }
        }

        fun stop(context: Context) {
            stopRequested = true
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
