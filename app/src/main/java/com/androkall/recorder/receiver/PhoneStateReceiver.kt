package com.androkall.recorder.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.telephony.TelephonyManager
import android.util.Log
import com.androkall.recorder.CallRecorderApp
import com.androkall.recorder.call.CallPhase
import com.androkall.recorder.data.AppSettings
import com.androkall.recorder.service.CallControlNotifier
import com.androkall.recorder.service.CallOverlayService
import com.androkall.recorder.service.CallRecordingService
import com.androkall.recorder.util.PermissionHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class PhoneStateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val pending = goAsync()
        val appContext = context.applicationContext
        scope.launch {
            try {
                when (action) {
                    Intent.ACTION_NEW_OUTGOING_CALL -> {
                        lastOutgoingNumber = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER)
                    }
                    TelephonyManager.ACTION_PHONE_STATE_CHANGED,
                    "android.intent.action.PHONE_STATE" -> {
                        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
                        val incoming = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
                        when (state) {
                            TelephonyManager.EXTRA_STATE_RINGING ->
                                handlePhase(appContext, CallPhase.RINGING, incoming)
                            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                                val number = incoming ?: lastOutgoingNumber
                                handlePhase(appContext, CallPhase.OFFHOOK, number)
                            }
                            TelephonyManager.EXTRA_STATE_IDLE ->
                                handlePhase(appContext, CallPhase.IDLE, null)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "PhoneStateReceiver failed", e)
            } finally {
                runCatching { pending.finish() }
            }
        }
    }

    private suspend fun handlePhase(
        context: Context,
        phase: CallPhase,
        number: String?
    ) {
        if (phase == lastPhase && phase != CallPhase.IDLE) {
            return
        }

        val app = context as? CallRecorderApp
            ?: (context.applicationContext as? CallRecorderApp)
        if (app == null) {
            Log.e(TAG, "Application is not CallRecorderApp")
            return
        }

        val settings = withTimeoutOrNull(2_000) {
            app.settingsRepository.settings.first()
        } ?: AppSettings()

        val canOverlay = PermissionHelper.canDrawOverlays(context)

        when (phase) {
            CallPhase.RINGING -> {
                lastPhase = phase
                if (settings.showCallNotification) {
                    CallControlNotifier.showRinging(context, number)
                }
                if (canOverlay && (settings.showOverlayOnRinging || settings.armedForNextCall)) {
                    runCatching { CallOverlayService.show(context, number) }
                }
            }
            CallPhase.OFFHOOK -> {
                lastPhase = phase
                lastOffhookElapsed = SystemClock.elapsedRealtime()
                val shouldAutoStart = settings.autoRecordOnAnswer || settings.armedForNextCall
                if (settings.showCallNotification && !shouldAutoStart) {
                    CallControlNotifier.showInCall(context, number, recording = false)
                }
                if (canOverlay && (settings.showOverlayOnRinging || settings.armedForNextCall)) {
                    runCatching { CallOverlayService.show(context, number) }
                }
                if (shouldAutoStart) {
                    Log.i(TAG, "OFFHOOK — start recording")
                    CallRecordingService.start(context, number)
                }
            }
            CallPhase.IDLE -> {
                val sinceOffhook = SystemClock.elapsedRealtime() - lastOffhookElapsed
                // Many OEMs emit a fake IDLE right when the call connects — ignore it.
                if (sinceOffhook in 0 until CallRecordingService.IDLE_GRACE_MS) {
                    Log.w(TAG, "Ignore spurious IDLE ${sinceOffhook}ms after OFFHOOK")
                    return
                }
                lastPhase = phase
                Log.i(TAG, "Call IDLE — stop recording")
                CallRecordingService.stop(context)
                runCatching { CallOverlayService.hide(context) }
                CallControlNotifier.cancel(context)
                lastOutgoingNumber = null
            }
        }
    }

    companion object {
        private const val TAG = "PhoneStateReceiver"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        @Volatile
        private var lastOutgoingNumber: String? = null

        @Volatile
        private var lastPhase: CallPhase? = null

        @Volatile
        private var lastOffhookElapsed: Long = 0L
    }
}
