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
 * MIC is preferred for reliability; VOICE_CALL is tried but often blocked on modern OEMs.
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
        preferredSource: AudioSourceOption = AudioSourceOption.MIC,
        preferBothSides: Boolean = true
    ): File {
        if (isRecording) {
            stop()
        }

        val file = recordingsRepository.createOutputFile(phoneNumber)
        val sources = buildSourceOrder(preferredSource, preferBothSides)
        var lastError: Exception? = null

        for (source in sources) {
            // Fresh empty target for each attempt
            if (file.exists()) {
                runCatching { file.delete() }
            }
            try {
                val recorder = createRecorder()
                recorder.setAudioSource(source)
                recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                recorder.setAudioEncodingBitRate(96_000)
                recorder.setAudioSamplingRate(16_000)
                recorder.setOutputFile(file.absolutePath)
                recorder.prepare()
                recorder.start()
                mediaRecorder = recorder
                outputFile = file
                Log.i(TAG, "Recording started source=$source path=${file.absolutePath}")
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
                    // Common if call ended abruptly — keep file if it has audio bytes.
                    Log.w(TAG, "MediaRecorder.stop() threw: ${e.message}")
                    if (file == null || !file.exists() || file.length() == 0L) {
                        file?.delete()
                    }
                }
                try {
                    reset()
                } catch (_: Exception) {
                }
                try {
                    release()
                } catch (_: Exception) {
                }
            }
        } finally {
            mediaRecorder = null
            outputFile = null
        }
        val kept = file?.takeIf { it.exists() && it.length() > 0L }
        Log.i(TAG, "Recording stop kept=${kept?.absolutePath} size=${kept?.length()}")
        return kept
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
        // MIC first — most reliable while speakerphone is on.
        val reliable = listOf(
            MediaRecorder.AudioSource.MIC,
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.CAMCORDER,
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            MediaRecorder.AudioSource.DEFAULT,
            MediaRecorder.AudioSource.VOICE_CALL
        )
        val preferredInt = preferred.toMediaSource()
        return if (preferBothSides) {
            (listOf(preferredInt) + reliable).distinct()
        } else {
            (listOf(preferredInt) + reliable).distinct()
        }
    }

    private fun AudioSourceOption.toMediaSource(): Int = when (this) {
        AudioSourceOption.BOTH_SIDES -> MediaRecorder.AudioSource.MIC
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
