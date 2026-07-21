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
import android.os.SystemClock
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
import com.androkall.recorder.data.RecordingExporter
import com.androkall.recorder.recording.CallAudioRouteController
import com.androkall.recorder.recording.PcmWavRecorder
import com.androkall.recorder.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Foreground mic recorder using PCM→WAV (reliable).
 * Ignores fake early IDLE; stops on real hang-up or explicit STOP.
 */
class CallRecordingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var recorder: PcmWavRecorder
    private var audioRoute: CallAudioRouteController? = null
    private var startedForeground = false
    private var phoneNumber: String? = null
    private var telephonyManager: TelephonyManager? = null
    private var telephonyCallback: TelephonyCallback? = null
    private var legacyListener: PhoneStateListener? = null
    private var recordingStartedElapsed = 0L
    private var serviceStartedElapsed = 0L
    private var finishing = false
    private var captureReady = false

    override fun onCreate() {
        super.onCreate()
        recorder = PcmWavRecorder(this)
        telephonyManager = getSystemService(TELEPHONY_SERVICE) as TelephonyManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                phoneNumber = intent.getStringExtra(EXTRA_NUMBER)
                finishing = false
                captureReady = false
                serviceStartedElapsed = SystemClock.elapsedRealtime()
                if (!ensureForeground(phoneNumber)) {
                    isRunning = false
                    stopSelf()
                    return START_NOT_STICKY
                }
                isRunning = true
                stopRequested = false
                scope.launch { beginRecording() }
            }
            ACTION_STOP -> finishRecording(reason = "stop", force = true)
            else -> if (!recorder.isRecording) stopSelf()
        }
        return START_STICKY
    }

    private fun beginRecording() {
        if (stopRequested || finishing) return
        if (recorder.isRecording) return

        try {
            audioRoute = CallAudioRouteController(this).also { it.enableSpeakerForBothSides() }
            val file = recorder.start(phoneNumber)
            recordingStartedElapsed = SystemClock.elapsedRealtime()
            captureReady = true
            mainHandler.post {
                updateRecordingNotification(phoneNumber, file.name)
                CallControlNotifier.showInCall(this@CallRecordingService, phoneNumber, recording = true)
                listenForCallEnd()
            }
            Log.i(TAG, "Recording OK: ${file.absolutePath}")

            scope.launch {
                runCatching {
                    val app = application as CallRecorderApp
                    val settings = app.settingsRepository.settings.first()
                    if (settings.armedForNextCall) {
                        app.settingsRepository.setArmedForNextCall(false)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Recording failed to start", e)
            mainHandler.post {
                notifySavedOrFailed("Записът не стартира: ${e.message ?: "грешка с микрофона"}")
            }
            teardown()
        }
    }

    private fun finishRecording(reason: String, force: Boolean) {
        if (finishing) return

        val aliveMs = SystemClock.elapsedRealtime() - serviceStartedElapsed
        val recordedMs = if (recordingStartedElapsed > 0) {
            SystemClock.elapsedRealtime() - recordingStartedElapsed
        } else {
            0L
        }

        if (!force && reason.contains("idle", ignoreCase = true)) {
            if (!captureReady || recordedMs < MIN_RECORD_BEFORE_IDLE_STOP_MS || aliveMs < IDLE_GRACE_MS) {
                Log.w(
                    TAG,
                    "IGNORE early IDLE ($reason) ready=$captureReady recordedMs=$recordedMs aliveMs=$aliveMs"
                )
                return
            }
        }

        finishing = true
        stopRequested = true
        unlistenCallEnd()

        val file = runCatching { recorder.stop() }.getOrNull()
        audioRoute?.restore()
        audioRoute = null
        isRunning = false
        captureReady = false

        if (file != null && file.length() > 44L) {
            val publicUri = runCatching { RecordingExporter.copyToDownloads(this, file) }.getOrNull()
            val msg = buildString {
                append("Записът е готов: ${file.name}\n")
                append("${file.length() / 1024} KB")
                if (publicUri != null) append("\nКопие: Изтегляния/EvtinkoCallRecorder")
                else append("\nВиж списъка в приложението")
            }
            Log.i(TAG, "Saved ($reason): ${file.absolutePath} public=$publicUri")
            notifySavedOrFailed(msg)
        } else {
            Log.w(TAG, "No usable file ($reason) size=${file?.length()}")
            notifySavedOrFailed(
                "Записът е празен. Разреши МИКРОФОН и остави високоговорителя включен по време на разговора."
            )
        }

        runCatching { if (startedForeground) stopForeground(STOP_FOREGROUND_REMOVE) }
        CallControlNotifier.cancel(this)
        stopSelf()
    }

    private fun teardown() {
        unlistenCallEnd()
        runCatching { if (recorder.isRecording) recorder.stop() }
        audioRoute?.restore()
        audioRoute = null
        isRunning = false
        captureReady = false
        runCatching { if (startedForeground) stopForeground(STOP_FOREGROUND_REMOVE) }
        stopSelf()
    }

    private fun listenForCallEnd() {
        val tm = telephonyManager ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val cb = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                    override fun onCallStateChanged(state: Int) {
                        if (state == TelephonyManager.CALL_STATE_IDLE) {
                            mainHandler.post {
                                finishRecording(reason = "telephony-idle", force = false)
                            }
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
                            mainHandler.post {
                                finishRecording(reason = "listener-idle", force = false)
                            }
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
        NotificationManagerCompat.from(this)
            .notify(NOTIFICATION_ID, buildRecordingNotification(number, fileName))
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
            fileName != null -> "Записва: $fileName"
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
        if (recorder.isRecording) runCatching { recorder.stop() }
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

        const val IDLE_GRACE_MS = 4000L
        private const val MIN_RECORD_BEFORE_IDLE_STOP_MS = 3000L

        @Volatile
        var isRunning: Boolean = false
            private set

        @Volatile
        private var stopRequested: Boolean = false

        fun start(context: Context, phoneNumber: String?) {
            if (isRunning) {
                Log.i(TAG, "Already running")
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
