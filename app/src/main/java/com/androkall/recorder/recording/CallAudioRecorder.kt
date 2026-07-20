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
 *
 * For both parties: tries VOICE_CALL first; when [preferBothSides] is true the service
 * also routes audio to the loudspeaker so MIC can pick up the remote side.
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
        preferredSource: AudioSourceOption = AudioSourceOption.BOTH_SIDES,
        preferBothSides: Boolean = true
    ): File {
        if (isRecording) {
            stop()
        }

        val file = recordingsRepository.createOutputFile(phoneNumber)
        val sources = buildSourceOrder(preferredSource, preferBothSides)
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

    private fun buildSourceOrder(preferred: AudioSourceOption, preferBothSides: Boolean): List<Int> {
        val preferredInt = preferred.toMediaSource()
        val bothSidesFirst = listOf(
            MediaRecorder.AudioSource.VOICE_CALL,
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            MediaRecorder.AudioSource.MIC,
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.CAMCORDER,
            MediaRecorder.AudioSource.DEFAULT
        )
        val standard = listOf(
            preferredInt,
            MediaRecorder.AudioSource.VOICE_CALL,
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            MediaRecorder.AudioSource.MIC,
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.CAMCORDER,
            MediaRecorder.AudioSource.DEFAULT
        )
        val ordered = if (preferBothSides || preferred == AudioSourceOption.BOTH_SIDES || preferred == AudioSourceOption.VOICE_CALL) {
            listOf(preferredInt) + bothSidesFirst
        } else {
            standard
        }
        return ordered.distinct()
    }

    private fun AudioSourceOption.toMediaSource(): Int = when (this) {
        AudioSourceOption.BOTH_SIDES -> MediaRecorder.AudioSource.VOICE_CALL
        AudioSourceOption.VOICE_CALL -> MediaRecorder.AudioSource.VOICE_CALL
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
