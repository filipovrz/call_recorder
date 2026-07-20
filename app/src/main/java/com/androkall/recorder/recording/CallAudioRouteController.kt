package com.androkall.recorder.recording

import android.content.Context
import android.media.AudioManager
import android.util.Log

/**
 * Routes call audio through the loudspeaker so the microphone can pick up
 * the remote party as well as the local voice. Restores prior state on stop.
 */
class CallAudioRouteController(context: Context) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var previousSpeakerphone: Boolean? = null
    private var previousMode: Int? = null

    fun enableSpeakerForBothSides() {
        try {
            previousMode = audioManager.mode
            previousSpeakerphone = audioManager.isSpeakerphoneOn
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = true
            Log.i(TAG, "Speakerphone enabled for both-sides capture")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to enable speakerphone: ${e.message}")
        }
    }

    fun restore() {
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

    companion object {
        private const val TAG = "CallAudioRoute"
    }
}
