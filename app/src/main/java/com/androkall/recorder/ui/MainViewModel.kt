package com.androkall.recorder.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.androkall.recorder.CallRecorderApp
import com.androkall.recorder.data.AppSettings
import com.androkall.recorder.data.AudioSourceOption
import com.androkall.recorder.data.RecordingExporter
import com.androkall.recorder.data.RecordingItem
import com.androkall.recorder.data.RecordingsRepository
import com.androkall.recorder.service.CallRecordingService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val settingsRepository = (application as CallRecorderApp).settingsRepository
    private val recordingsRepository = RecordingsRepository(application)

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    private val _recordings = MutableStateFlow<List<RecordingItem>>(emptyList())
    val recordings: StateFlow<List<RecordingItem>> = _recordings.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    init {
        refreshRecordings()
    }

    fun refreshRecordings() {
        _recordings.value = recordingsRepository.listRecordings()
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
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

    fun setCaptureBothSides(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setCaptureBothSides(enabled)
        if (enabled) {
            settingsRepository.setPreferredAudioSource(AudioSourceOption.BOTH_SIDES)
        }
    }

    fun setAutoSaveToDownloads(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setAutoSaveToDownloads(enabled)
    }

    fun setShowCallNotification(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setShowCallNotification(enabled)
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

    fun saveToDownloads(item: RecordingItem) = viewModelScope.launch {
        val uri = withContext(Dispatchers.IO) {
            RecordingExporter.copyToDownloads(getApplication(), item.file)
        }
        _statusMessage.value = if (uri != null) {
            "Записът е копиран в Изтегляния/EvtinkoCallRecorder"
        } else {
            "Неуспешно копиране в Изтегляния"
        }
    }

    fun saveToUri(item: RecordingItem, uri: Uri) = viewModelScope.launch {
        val ok = withContext(Dispatchers.IO) {
            RecordingExporter.copyToUri(getApplication(), item.file, uri)
        }
        _statusMessage.value = if (ok) {
            "Записът е запазен на избраното място"
        } else {
            "Неуспешно запазване"
        }
    }
}
