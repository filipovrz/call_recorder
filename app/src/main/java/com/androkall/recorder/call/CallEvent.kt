package com.androkall.recorder.call

enum class CallPhase {
    IDLE,
    RINGING,
    OFFHOOK
}

data class CallEvent(
    val phase: CallPhase,
    val phoneNumber: String? = null,
    val isIncoming: Boolean = true
)
