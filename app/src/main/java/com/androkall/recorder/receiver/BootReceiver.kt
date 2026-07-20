package com.androkall.recorder.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Ensures the app process can wake after reboot so manifest-registered
 * [PhoneStateReceiver] remains available for the next call.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        // Touch Application so DataStore / channels are ready after reboot.
        context.applicationContext
        Log.i(TAG, "Boot completed — call state receiver remains registered in manifest")
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
