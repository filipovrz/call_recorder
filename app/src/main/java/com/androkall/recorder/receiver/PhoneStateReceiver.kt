package com.androkall.recorder.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log
import com.androkall.recorder.CallRecorderApp
import com.androkall.recorder.call.CallPhase
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
                        // Only remember the number — do NOT start recording yet.
                        lastOutgoingNumber = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER)
                    }
                    TelephonyManager.ACTION_PHONE_STATE_CHANGED,
                    "android.intent.action.PHONE_STATE" -> {
                        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
                        val incoming = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
                        when (state) {
                            TelephonyManager.EXTRA_STATE_RINGING -> {
                                handlePhase(appContext, CallPhase.RINGING, incoming)
                            }
                            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                                val number = incoming ?: lastOutgoingNumber
                                handlePhase(appContext, CallPhase.OFFHOOK, number)
                            }
                            TelephonyManager.EXTRA_STATE_IDLE -> {
                                handlePhase(appContext, CallPhase.IDLE, null)
                            }
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
        // Debounce identical rapid repeats (OEMs fire PHONE_STATE multiple times).
        // IDLE is never skipped — recording must always stop when the call ends.
        if (phase == lastPhase && phase != CallPhase.IDLE) {
            Log.d(TAG, "Ignore duplicate phase=$phase")
            return
        }
        lastPhase = phase

        val app = context as? CallRecorderApp
            ?: (context.applicationContext as? CallRecorderApp)
        if (app == null) {
            Log.e(TAG, "Application is not CallRecorderApp")
            return
        }

        val settings = withTimeoutOrNull(2_000) {
            app.settingsRepository.settings.first()
        } ?: run {
            Log.w(TAG, "Settings timeout — using safe defaults")
            com.androkall.recorder.data.AppSettings()
        }

        val canOverlay = PermissionHelper.canDrawOverlays(context)

        when (phase) {
            CallPhase.RINGING -> {
                if (settings.showCallNotification) {
                    CallControlNotifier.showRinging(context, number)
                }
                if (canOverlay && (settings.showOverlayOnRinging || settings.armedForNextCall)) {
                    runCatching { CallOverlayService.show(context, number) }
                }
            }
            CallPhase.OFFHOOK -> {
                val shouldAutoStart = settings.autoRecordOnAnswer || settings.armedForNextCall
                if (settings.showCallNotification && !shouldAutoStart) {
                    CallControlNotifier.showInCall(context, number, recording = false)
                }
                if (canOverlay && (settings.showOverlayOnRinging || settings.armedForNextCall)) {
                    runCatching { CallOverlayService.show(context, number) }
                }
                if (shouldAutoStart) {
                    CallRecordingService.start(context, number)
                }
            }
            CallPhase.IDLE -> {
                // Always stop on hang-up — never debounce away the end of a call.
                Log.i(TAG, "Call IDLE — stopping recording")
                CallRecordingService.stop(context)
                runCatching { CallOverlayService.hide(context) }
                CallControlNotifier.cancel(context)
                lastOutgoingNumber = null
                lastPhase = CallPhase.IDLE
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
    }
}
