package com.androkall.recorder.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import com.androkall.recorder.CallRecorderApp
import com.androkall.recorder.call.CallPhase
import com.androkall.recorder.service.CallControlNotifier
import com.androkall.recorder.service.CallOverlayService
import com.androkall.recorder.service.CallRecordingService
import com.androkall.recorder.util.PermissionHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class PhoneStateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                when (action) {
                    Intent.ACTION_NEW_OUTGOING_CALL -> {
                        val number = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER)
                        lastOutgoingNumber = number
                        handlePhase(context, CallPhase.OFFHOOK, number, isIncoming = false)
                    }
                    TelephonyManager.ACTION_PHONE_STATE_CHANGED,
                    "android.intent.action.PHONE_STATE" -> {
                        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
                        val incoming = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
                        when (state) {
                            TelephonyManager.EXTRA_STATE_RINGING -> {
                                handlePhase(context, CallPhase.RINGING, incoming, isIncoming = true)
                            }
                            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                                val number = incoming ?: lastOutgoingNumber
                                handlePhase(context, CallPhase.OFFHOOK, number, isIncoming = incoming != null)
                            }
                            TelephonyManager.EXTRA_STATE_IDLE -> {
                                handlePhase(context, CallPhase.IDLE, null, isIncoming = true)
                            }
                        }
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun handlePhase(
        context: Context,
        phase: CallPhase,
        number: String?,
        isIncoming: Boolean
    ) {
        val app = context.applicationContext as CallRecorderApp
        val settings = app.settingsRepository.settings.first()
        val canOverlay = PermissionHelper.canDrawOverlays(context)

        when (phase) {
            CallPhase.RINGING -> {
                // Primary path (works for sideloaded APKs): notification + prompt activity.
                if (settings.showCallNotification) {
                    CallControlNotifier.showRinging(context, number)
                }
                // Overlay only if Android allows it (often blocked for sideload).
                if (canOverlay && (settings.showOverlayOnRinging || settings.armedForNextCall)) {
                    CallOverlayService.show(context, number)
                }
            }
            CallPhase.OFFHOOK -> {
                val shouldAutoStart = settings.autoRecordOnAnswer || settings.armedForNextCall
                if (settings.showCallNotification) {
                    CallControlNotifier.showInCall(context, number, recording = shouldAutoStart)
                }
                if (canOverlay && (settings.showOverlayOnRinging || settings.armedForNextCall)) {
                    CallOverlayService.show(context, number)
                }
                if (shouldAutoStart) {
                    CallRecordingService.start(context, number)
                }
            }
            CallPhase.IDLE -> {
                CallRecordingService.stop(context)
                CallOverlayService.hide(context)
                CallControlNotifier.cancel(context)
                lastOutgoingNumber = null
            }
        }

        lastCallWasIncoming = isIncoming
    }

    companion object {
        @Volatile
        private var lastOutgoingNumber: String? = null

        @Volatile
        var lastCallWasIncoming: Boolean = true
            private set
    }
}
