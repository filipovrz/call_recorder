package com.androkall.recorder.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            autoRecordOnAnswer = prefs[Keys.AUTO_RECORD] ?: false,
            showOverlayOnRinging = prefs[Keys.SHOW_OVERLAY] ?: true,
            armedForNextCall = prefs[Keys.ARMED] ?: false,
            preferredAudioSource = prefs[Keys.AUDIO_SOURCE] ?: AudioSourceOption.VOICE_COMMUNICATION.name
        )
    }

    suspend fun setAutoRecordOnAnswer(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_RECORD] = enabled }
    }

    suspend fun setShowOverlayOnRinging(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SHOW_OVERLAY] = enabled }
    }

    suspend fun setArmedForNextCall(armed: Boolean) {
        context.dataStore.edit { it[Keys.ARMED] = armed }
    }

    suspend fun setPreferredAudioSource(source: AudioSourceOption) {
        context.dataStore.edit { it[Keys.AUDIO_SOURCE] = source.name }
    }

    private object Keys {
        val AUTO_RECORD = booleanPreferencesKey("auto_record_on_answer")
        val SHOW_OVERLAY = booleanPreferencesKey("show_overlay_on_ringing")
        val ARMED = booleanPreferencesKey("armed_for_next_call")
        val AUDIO_SOURCE = stringPreferencesKey("preferred_audio_source")
    }
}

data class AppSettings(
    val autoRecordOnAnswer: Boolean = false,
    val showOverlayOnRinging: Boolean = true,
    val armedForNextCall: Boolean = false,
    val preferredAudioSource: String = AudioSourceOption.VOICE_COMMUNICATION.name
)

enum class AudioSourceOption {
    VOICE_COMMUNICATION,
    MIC,
    VOICE_RECOGNITION,
    CAMCORDER
}
