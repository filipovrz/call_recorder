package com.androkall.recorder.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.androkall.recorder.CallRecorderApp
import com.androkall.recorder.data.AppSettings
import com.androkall.recorder.data.AudioSourceOption
import com.androkall.recorder.data.RecordingItem
import com.androkall.recorder.data.RecordingsRepository
import com.androkall.recorder.service.CallRecordingService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val settingsRepository = (application as CallRecorderApp).settingsRepository
    private val recordingsRepository = RecordingsRepository(application)

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    private val _recordings = MutableStateFlow<List<RecordingItem>>(emptyList())
    val recordings: StateFlow<List<RecordingItem>> = _recordings.asStateFlow()

    init {
        refreshRecordings()
    }

    fun refreshRecordings() {
        _recordings.value = recordingsRepository.listRecordings()
    }

    fun setAutoRecord(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setAutoRecordOnAnswer(enabled)
    }

    fun setShowOverlay(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setShowOverlayOnRinging(enabled)
    }

    fun setArmed(armed: Boolean) = viewModelScope.launch {
        settingsRepository.setArmedForNextCall(armed)
    }

    fun setAudioSource(source: AudioSourceOption) = viewModelScope.launch {
        settingsRepository.setPreferredAudioSource(source)
    }

    fun startManualRecording() {
        CallRecordingService.start(getApplication(), null)
    }

    fun stopManualRecording() {
        CallRecordingService.stop(getApplication())
    }

    fun deleteRecording(item: RecordingItem) {
        recordingsRepository.delete(item.file)
        refreshRecordings()
    }
}
