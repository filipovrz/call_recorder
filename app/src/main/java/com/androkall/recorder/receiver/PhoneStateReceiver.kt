package com.androkall.recorder.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import com.androkall.recorder.CallRecorderApp
import com.androkall.recorder.call.CallPhase
import com.androkall.recorder.service.CallOverlayService
import com.androkall.recorder.service.CallRecordingService
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

        when (phase) {
            CallPhase.RINGING -> {
                if (settings.showOverlayOnRinging) {
                    CallOverlayService.show(context, number)
                }
                if (settings.armedForNextCall) {
                    // Arming before answer: start as soon as user wants via overlay,
                    // or wait for OFFHOOK if auto-record is also on.
                }
            }
            CallPhase.OFFHOOK -> {
                if (settings.showOverlayOnRinging) {
                    CallOverlayService.show(context, number)
                }
                val shouldAutoStart = settings.autoRecordOnAnswer || settings.armedForNextCall
                if (shouldAutoStart) {
                    CallRecordingService.start(context, number)
                }
            }
            CallPhase.IDLE -> {
                CallRecordingService.stop(context)
                CallOverlayService.hide(context)
                lastOutgoingNumber = null
            }
        }
        // Keep isIncoming available for future metadata/UI; currently unused.
        @Suppress("UNUSED_VARIABLE")
        val ignored = isIncoming
    }

    companion object {
        @Volatile
        private var lastOutgoingNumber: String? = null
    }
}
