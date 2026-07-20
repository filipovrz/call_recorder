package com.androkall.recorder.recording

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import com.androkall.recorder.data.AudioSourceOption
import com.androkall.recorder.data.RecordingsRepository
import java.io.File

/**
 * Records call audio locally without injecting any beep/tone into the call path.
 * Note: capturing both call parties depends on OEM/Android version restrictions.
 */
class CallAudioRecorder(private val context: Context) {
    private val recordingsRepository = RecordingsRepository(context)
    private var mediaRecorder: MediaRecorder? = null
    private var outputFile: File? = null

    val isRecording: Boolean
        get() = mediaRecorder != null

    val currentFile: File?
        get() = outputFile

    fun start(
        phoneNumber: String?,
        preferredSource: AudioSourceOption = AudioSourceOption.VOICE_COMMUNICATION
    ): File {
        if (isRecording) {
            stop()
        }

        val file = recordingsRepository.createOutputFile(phoneNumber)
        val sources = buildSourceOrder(preferredSource)
        var lastError: Exception? = null

        for (source in sources) {
            try {
                val recorder = createRecorder()
                recorder.setAudioSource(source)
                recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                recorder.setAudioEncodingBitRate(128_000)
                recorder.setAudioSamplingRate(44_100)
                recorder.setOutputFile(file.absolutePath)
                recorder.prepare()
                recorder.start()
                mediaRecorder = recorder
                outputFile = file
                Log.i(TAG, "Recording started with source=$source file=${file.name}")
                return file
            } catch (e: Exception) {
                lastError = e
                Log.w(TAG, "Failed audio source=$source: ${e.message}")
                releaseQuietly()
                if (file.exists() && file.length() == 0L) {
                    file.delete()
                }
            }
        }

        throw lastError ?: IllegalStateException("Unable to start recording")
    }

    fun stop(): File? {
        val file = outputFile
        try {
            mediaRecorder?.apply {
                try {
                    stop()
                } catch (e: RuntimeException) {
                    Log.w(TAG, "stop() failed: ${e.message}")
                    file?.delete()
                }
                reset()
                release()
            }
        } finally {
            mediaRecorder = null
            outputFile = null
        }
        return file?.takeIf { it.exists() && it.length() > 0L }
    }

    private fun createRecorder(): MediaRecorder {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
    }

    private fun buildSourceOrder(preferred: AudioSourceOption): List<Int> {
        val preferredInt = preferred.toMediaSource()
        val fallbacks = listOf(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            MediaRecorder.AudioSource.MIC,
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.CAMCORDER
        )
        return (listOf(preferredInt) + fallbacks).distinct()
    }

    private fun AudioSourceOption.toMediaSource(): Int = when (this) {
        AudioSourceOption.VOICE_COMMUNICATION -> MediaRecorder.AudioSource.VOICE_COMMUNICATION
        AudioSourceOption.MIC -> MediaRecorder.AudioSource.MIC
        AudioSourceOption.VOICE_RECOGNITION -> MediaRecorder.AudioSource.VOICE_RECOGNITION
        AudioSourceOption.CAMCORDER -> MediaRecorder.AudioSource.CAMCORDER
    }

    private fun releaseQuietly() {
        try {
            mediaRecorder?.release()
        } catch (_: Exception) {
        }
        mediaRecorder = null
    }

    companion object {
        private const val TAG = "CallAudioRecorder"
    }
}
