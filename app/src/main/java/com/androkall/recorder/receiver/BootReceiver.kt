package com.androkall.recorder.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // PhoneStateReceiver is registered in the manifest; nothing else required on boot for v0.1.
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
    }
}
