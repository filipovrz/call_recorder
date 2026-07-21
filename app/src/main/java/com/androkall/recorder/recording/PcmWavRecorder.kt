package com.androkall.recorder.recording

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.androkall.recorder.data.RecordingsRepository
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Reliable PCM recorder → WAV file.
 * Uses the microphone (with speakerphone enabled by the service for call capture).
 */
class PcmWavRecorder(private val context: Context) {
    private val recordingsRepository = RecordingsRepository(context)
    private var audioRecord: AudioRecord? = null
    private var outputFile: File? = null
    private var writerThread: Thread? = null
    private val running = AtomicBoolean(false)
    private var totalAudioBytes = 0L

    val isRecording: Boolean
        get() = running.get()

    val currentFile: File?
        get() = outputFile

    fun start(phoneNumber: String?): File {
        stop()

        val stampFile = recordingsRepository.createOutputFile(phoneNumber)
        // Use .wav for PCM capture
        val file = File(stampFile.parentFile, stampFile.nameWithoutExtension + ".wav")

        val sampleRate = 44100
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val minBuf = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        if (minBuf <= 0) {
            throw IllegalStateException("AudioRecord buffer size invalid: $minBuf")
        }
        val bufferSize = minBuf * 2

        val sources = intArrayOf(
            MediaRecorder.AudioSource.MIC,
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            MediaRecorder.AudioSource.CAMCORDER,
            MediaRecorder.AudioSource.DEFAULT
        )

        var lastError: Exception? = null
        var record: AudioRecord? = null
        for (source in sources) {
            try {
                val candidate = AudioRecord(source, sampleRate, channelConfig, audioFormat, bufferSize)
                if (candidate.state != AudioRecord.STATE_INITIALIZED) {
                    candidate.release()
                    continue
                }
                record = candidate
                Log.i(TAG, "AudioRecord ready source=$source")
                break
            } catch (e: Exception) {
                lastError = e
                Log.w(TAG, "AudioRecord source=$source failed: ${e.message}")
            }
        }
        if (record == null) {
            throw lastError ?: IllegalStateException("AudioRecord could not initialize")
        }

        // Write placeholder WAV header; finalize sizes on stop.
        FileOutputStream(file).use { out ->
            out.write(ByteArray(44))
        }

        audioRecord = record
        outputFile = file
        totalAudioBytes = 0L
        running.set(true)

        record.startRecording()
        if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            running.set(false)
            record.release()
            audioRecord = null
            throw IllegalStateException("AudioRecord failed to enter RECORDING state")
        }

        writerThread = thread(name = "pcm-wav-writer", isDaemon = true) {
            val buf = ByteArray(bufferSize)
            try {
                FileOutputStream(file, true).use { out ->
                    while (running.get()) {
                        val read = record.read(buf, 0, buf.size)
                        if (read > 0) {
                            out.write(buf, 0, read)
                            totalAudioBytes += read
                        } else if (read < 0) {
                            Log.w(TAG, "AudioRecord.read error=$read")
                            break
                        }
                    }
                    out.flush()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Writer failed", e)
            }
        }

        Log.i(TAG, "PCM recording started → ${file.absolutePath}")
        return file
    }

    fun stop(): File? {
        if (!running.getAndSet(false) && audioRecord == null) {
            return outputFile?.takeIf { it.exists() && it.length() > 44L }
        }
        running.set(false)
        try {
            audioRecord?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "AudioRecord.stop: ${e.message}")
        }
        try {
            audioRecord?.release()
        } catch (_: Exception) {
        }
        audioRecord = null
        try {
            writerThread?.join(2000)
        } catch (_: Exception) {
        }
        writerThread = null

        val file = outputFile
        outputFile = null
        if (file == null || !file.exists()) return null

        return try {
            writeWavHeader(file, totalAudioBytes, sampleRate = 44100, channels = 1, bitsPerSample = 16)
            val kept = file.takeIf { it.length() > 44L && totalAudioBytes > 0L }
            Log.i(TAG, "PCM stop bytes=$totalAudioBytes file=${kept?.absolutePath} size=${kept?.length()}")
            kept
        } catch (e: Exception) {
            Log.e(TAG, "WAV header failed", e)
            null
        }
    }

    private fun writeWavHeader(
        file: File,
        audioBytes: Long,
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int
    ) {
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val raf = RandomAccessFile(file, "rw")
        raf.seek(0)
        raf.writeBytes("RIFF")
        writeIntLE(raf, (36 + audioBytes).toInt())
        raf.writeBytes("WAVE")
        raf.writeBytes("fmt ")
        writeIntLE(raf, 16)
        writeShortLE(raf, 1) // PCM
        writeShortLE(raf, channels.toShort())
        writeIntLE(raf, sampleRate)
        writeIntLE(raf, byteRate)
        writeShortLE(raf, (channels * bitsPerSample / 8).toShort())
        writeShortLE(raf, bitsPerSample.toShort())
        raf.writeBytes("data")
        writeIntLE(raf, audioBytes.toInt())
        raf.close()
    }

    private fun writeIntLE(raf: RandomAccessFile, value: Int) {
        raf.write(value and 0xff)
        raf.write(value shr 8 and 0xff)
        raf.write(value shr 16 and 0xff)
        raf.write(value shr 24 and 0xff)
    }

    private fun writeShortLE(raf: RandomAccessFile, value: Short) {
        val v = value.toInt()
        raf.write(v and 0xff)
        raf.write(v shr 8 and 0xff)
    }

    companion object {
        private const val TAG = "PcmWavRecorder"
    }
}
