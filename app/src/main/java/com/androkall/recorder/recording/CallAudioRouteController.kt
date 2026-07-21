package com.androkall.recorder.recording

import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Forces call audio onto the loudspeaker so the mic can capture the remote party.
 */
class CallAudioRouteController(context: Context) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var previousSpeakerphone: Boolean? = null
    private var previousMode: Int? = null

    fun enableSpeakerForBothSides() {
        val apply = {
            try {
                previousMode = audioManager.mode
                previousSpeakerphone = audioManager.isSpeakerphoneOn
                // Prefer IN_CALL during an active telephony call.
                try {
                    audioManager.mode = AudioManager.MODE_IN_CALL
                } catch (_: Exception) {
                    audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                }
                @Suppress("DEPRECATION")
                audioManager.isSpeakerphoneOn = true
                // Some OEMs need a second nudge.
                mainHandler.postDelayed({
                    @Suppress("DEPRECATION")
                    audioManager.isSpeakerphoneOn = true
                }, 200)
                Log.i(TAG, "Speakerphone ON mode=${audioManager.mode}")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to enable speakerphone: ${e.message}")
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) apply() else mainHandler.post(apply)
        // Block briefly so recording starts after route change.
        try {
            Thread.sleep(350)
        } catch (_: InterruptedException) {
        }
    }

    fun restore() {
        val apply = {
            try {
                previousSpeakerphone?.let { wanted ->
                    @Suppress("DEPRECATION")
                    audioManager.isSpeakerphoneOn = wanted
                }
                previousMode?.let { audioManager.mode = it }
                Log.i(TAG, "Audio route restored")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to restore audio route: ${e.message}")
            } finally {
                previousSpeakerphone = null
                previousMode = null
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) apply() else mainHandler.post(apply)
    }

    companion object {
        private const val TAG = "CallAudioRoute"
    }
}
