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
        runOnMain {
            try {
                previousMode = audioManager.mode
                previousSpeakerphone = audioManager.isSpeakerphoneOn
                try {
                    audioManager.mode = AudioManager.MODE_IN_CALL
                } catch (_: Exception) {
                    audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                }
                @Suppress("DEPRECATION")
                audioManager.isSpeakerphoneOn = true
                mainHandler.postDelayed({
                    @Suppress("DEPRECATION")
                    audioManager.isSpeakerphoneOn = true
                }, 200)
                Log.i(TAG, "Speakerphone ON mode=${audioManager.mode}")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to enable speakerphone: ${e.message}")
            }
        }
        try {
            Thread.sleep(350)
        } catch (_: InterruptedException) {
        }
    }

    fun restore() {
        runOnMain {
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
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }

    companion object {
        private const val TAG = "CallAudioRoute"
    }
}
