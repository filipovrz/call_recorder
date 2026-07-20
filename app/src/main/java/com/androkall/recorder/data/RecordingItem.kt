package com.androkall.recorder.data

import java.io.File

data class RecordingItem(
    val file: File,
    val displayName: String,
    val phoneNumber: String?,
    val startedAtMillis: Long,
    val durationHintMillis: Long?,
    val sizeBytes: Long
)
