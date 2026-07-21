package com.androkall.recorder.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileInputStream
import java.io.OutputStream

object RecordingExporter {
    /**
     * Copies a recording into the public Downloads folder (visible in Files app).
     * Returns the content Uri when successful.
     */
    fun copyToDownloads(context: Context, source: File): Uri? {
        if (!source.exists() || source.length() == 0L) return null
        val displayName = source.name

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, displayName)
                put(MediaStore.Downloads.MIME_TYPE, mimeFor(source))
                put(MediaStore.Downloads.IS_PENDING, 1)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/EvtinkoCallRecorder")
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
            try {
                resolver.openOutputStream(uri)?.use { out -> copyStream(source, out) }
                    ?: run {
                        resolver.delete(uri, null, null)
                        return null
                    }
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                uri
            } catch (e: Exception) {
                runCatching { resolver.delete(uri, null, null) }
                null
            }
        } else {
            @Suppress("DEPRECATION")
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "EvtinkoCallRecorder"
            )
            if (!dir.exists() && !dir.mkdirs()) return null
            val dest = File(dir, displayName)
            source.copyTo(dest, overwrite = true)
            @Suppress("DEPRECATION")
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DATA, dest.absolutePath)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeFor(source))
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            }
            context.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
                ?: Uri.fromFile(dest)
        }
    }

    private fun mimeFor(file: File): String = when (file.extension.lowercase()) {
        "wav" -> "audio/wav"
        "3gp" -> "audio/3gpp"
        else -> "audio/mp4"
    }

    fun copyToUri(context: Context, source: File, dest: Uri): Boolean {
        if (!source.exists()) return false
        return try {
            context.contentResolver.openOutputStream(dest)?.use { out ->
                copyStream(source, out)
            } != null
        } catch (_: Exception) {
            false
        }
    }

    private fun copyStream(source: File, out: OutputStream) {
        FileInputStream(source).use { input ->
            input.copyTo(out)
            out.flush()
        }
    }
}
