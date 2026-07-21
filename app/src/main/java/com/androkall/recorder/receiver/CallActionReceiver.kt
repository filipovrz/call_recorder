package com.androkall.recorder.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.androkall.recorder.service.CallControlNotifier
import com.androkall.recorder.service.CallRecordingService

class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            ACTION_START -> {
                val number = intent.getStringExtra(EXTRA_NUMBER)
                CallRecordingService.start(context, number)
                CallControlNotifier.showInCall(context, number, recording = true)
            }
            ACTION_STOP -> {
                CallRecordingService.stop(context)
                CallControlNotifier.cancel(context)
            }
        }
    }

    companion object {
        const val ACTION_START = "com.androkall.recorder.action.CALL_NOTIFY_START"
        const val ACTION_STOP = "com.androkall.recorder.action.CALL_NOTIFY_STOP"
        const val EXTRA_NUMBER = "extra_number"
    }
}
