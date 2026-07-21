package com.androkall.recorder.data

import android.content.Context
import android.media.MediaMetadataRetriever
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RecordingsRepository(context: Context) {
    private val rootDir: File = File(
        context.getExternalFilesDir(null) ?: context.filesDir,
        "recordings"
    ).also { dir ->
        if (!dir.exists()) {
            check(dir.mkdirs() || dir.isDirectory) {
                "Cannot create recordings dir: ${dir.absolutePath}"
            }
        }
    }

    fun createOutputFile(phoneNumber: String?): File {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val safeNumber = (phoneNumber ?: "unknown")
            .replace(Regex("[^0-9+]"), "")
            .ifBlank { "unknown" }
        return File(rootDir, "call_${stamp}_$safeNumber.m4a")
    }

    fun listRecordings(): List<RecordingItem> {
        return rootDir.listFiles()
            ?.filter { it.isFile && (it.extension.equals("m4a", true) || it.extension.equals("3gp", true)) }
            ?.filter { it.length() > 0L }
            ?.sortedByDescending { it.lastModified() }
            ?.map { file ->
                val parts = file.nameWithoutExtension.split("_")
                val phone = parts.getOrNull(3)
                RecordingItem(
                    file = file,
                    displayName = file.name,
                    phoneNumber = phone,
                    startedAtMillis = file.lastModified(),
                    durationHintMillis = readDurationMillis(file),
                    sizeBytes = file.length()
                )
            }
            .orEmpty()
    }

    fun delete(file: File): Boolean = file.exists() && file.delete()

    fun recordingsDir(): File = rootDir

    private fun readDurationMillis(file: File): Long? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }
}
