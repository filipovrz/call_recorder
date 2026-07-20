package com.androkall.recorder.ui

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.androkall.recorder.R
import com.androkall.recorder.data.AudioSourceOption
import com.androkall.recorder.data.RecordingItem
import com.androkall.recorder.util.PermissionHelper
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallRecorderAppScreen(
    viewModel: MainViewModel = viewModel()
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val recordings by viewModel.recordings.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    var pendingSaveItem by remember { mutableStateOf<RecordingItem?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("audio/mp4")
    ) { uri ->
        val item = pendingSaveItem
        pendingSaveItem = null
        if (uri != null && item != null) {
            viewModel.saveToUri(item, uri)
        }
    }

    LaunchedEffect(Unit) {
        val missing = PermissionHelper.missingPermissions(context)
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearStatusMessage()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshRecordings()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name), fontWeight = FontWeight.SemiBold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                actions = {
                    IconButton(onClick = { viewModel.refreshRecordings() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Обнови")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = stringResource(R.string.legal_notice),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f)
                )
            }

            item {
                ControlsCard(
                    armed = settings.armedForNextCall,
                    autoRecord = settings.autoRecordOnAnswer,
                    showOverlay = settings.showOverlayOnRinging,
                    bothSides = settings.captureBothSides,
                    autoSaveDownloads = settings.autoSaveToDownloads,
                    hasOverlayPermission = PermissionHelper.canDrawOverlays(context),
                    onArmChange = viewModel::setArmed,
                    onAutoRecordChange = viewModel::setAutoRecord,
                    onShowOverlayChange = viewModel::setShowOverlay,
                    onBothSidesChange = viewModel::setCaptureBothSides,
                    onAutoSaveChange = viewModel::setAutoSaveToDownloads,
                    onRequestOverlay = { PermissionHelper.openOverlaySettings(context) },
                    onStartNow = {
                        if (!PermissionHelper.hasAllRuntimePermissions(context)) {
                            permissionLauncher.launch(PermissionHelper.requiredPermissions())
                            Toast.makeText(context, "Нужни са права за микрофон и телефон", Toast.LENGTH_SHORT).show()
                        } else {
                            viewModel.startManualRecording()
                        }
                    },
                    onStopNow = {
                        viewModel.stopManualRecording()
                        viewModel.refreshRecordings()
                    }
                )
            }

            item {
                AudioSourceCard(
                    selected = runCatching {
                        AudioSourceOption.valueOf(settings.preferredAudioSource)
                    }.getOrDefault(AudioSourceOption.BOTH_SIDES),
                    onSelect = viewModel::setAudioSource
                )
            }

            item {
                Text(
                    text = "Записи",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Вътрешни файлове + бутони за копие в Изтегляния или „Запази като…“",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )
            }

            if (recordings.isEmpty()) {
                item {
                    Text(
                        text = "Все още няма записи.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                items(recordings, key = { it.file.absolutePath }) { item ->
                    RecordingRow(
                        item = item,
                        onPlay = {
                            val uri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                item.file
                            )
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(uri, "audio/*")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            runCatching { context.startActivity(intent) }
                                .onFailure {
                                    Toast.makeText(context, "Няма плеър за аудио", Toast.LENGTH_SHORT).show()
                                }
                        },
                        onSaveDownloads = { viewModel.saveToDownloads(item) },
                        onSaveAs = {
                            pendingSaveItem = item
                            createDocumentLauncher.launch(item.displayName)
                        },
                        onShare = {
                            val uri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                item.file
                            )
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "audio/*"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            runCatching {
                                context.startActivity(Intent.createChooser(intent, "Сподели запис"))
                            }.onFailure {
                                Toast.makeText(context, "Не може да се сподели", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onDelete = { viewModel.deleteRecording(item) }
                    )
                }
            }

            item {
                Text(
                    text = stringResource(R.string.about_footer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun ControlsCard(
    armed: Boolean,
    autoRecord: Boolean,
    showOverlay: Boolean,
    bothSides: Boolean,
    autoSaveDownloads: Boolean,
    hasOverlayPermission: Boolean,
    onArmChange: (Boolean) -> Unit,
    onAutoRecordChange: (Boolean) -> Unit,
    onShowOverlayChange: (Boolean) -> Unit,
    onBothSidesChange: (Boolean) -> Unit,
    onAutoSaveChange: (Boolean) -> Unit,
    onRequestOverlay: () -> Unit,
    onStartNow: () -> Unit,
    onStopNow: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Бързи действия", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

            SettingRow(
                title = "И двете страни на разговора",
                subtitle = "Високоговорител + микрофон (и опит за VOICE_CALL). Разговорът минава през високоговорителя по време на запис.",
                checked = bothSides,
                onCheckedChange = onBothSidesChange
            )
            SettingRow(
                title = "Автокопие в Изтегляния",
                subtitle = "След край на записа копира файла в Изтегляния/EvtinkoCallRecorder",
                checked = autoSaveDownloads,
                onCheckedChange = onAutoSaveChange
            )
            SettingRow(
                title = "Подготви запис за следващото обаждане",
                subtitle = "Включи преди позвъняване — стартира при вдигане",
                checked = armed,
                onCheckedChange = onArmChange
            )
            SettingRow(
                title = "Автозапис при отговор",
                subtitle = "Започва веднага щом разговорът влезе в OFFHOOK",
                checked = autoRecord,
                onCheckedChange = onAutoRecordChange
            )
            SettingRow(
                title = "Бутон върху екрана при звънене",
                subtitle = "Лесно включване преди/по време на обаждане (може да се влачи)",
                checked = showOverlay,
                onCheckedChange = onShowOverlayChange
            )

            if (!hasOverlayPermission) {
                OutlinedButton(onClick = onRequestOverlay, modifier = Modifier.fillMaxWidth()) {
                    Text("Разреши overlay (бутон върху другите приложения)")
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onStartNow, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Mic, contentDescription = null)
                    Spacer(modifier = Modifier.padding(4.dp))
                    Text("Старт сега")
                }
                OutlinedButton(onClick = onStopNow, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Stop, contentDescription = null)
                    Spacer(modifier = Modifier.padding(4.dp))
                    Text("Стоп")
                }
            }
        }
    }
}

@Composable
private fun AudioSourceCard(
    selected: AudioSourceOption,
    onSelect: (AudioSourceOption) -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Аудио източник", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                text = "BOTH_SIDES / VOICE_CALL дават най-добър шанс за двете страни. Ако системата блокира, приложението ползва микрофон + високоговорител.",
                style = MaterialTheme.typography.bodySmall
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                AudioSourceOption.entries.forEach { option ->
                    FilterChip(
                        selected = selected == option,
                        onClick = { onSelect(option) },
                        label = { Text(option.chipLabel()) }
                    )
                }
            }
        }
    }
}

private fun AudioSourceOption.chipLabel(): String = when (this) {
    AudioSourceOption.BOTH_SIDES -> "BOTH"
    AudioSourceOption.VOICE_CALL -> "CALL"
    AudioSourceOption.VOICE_COMMUNICATION -> "COM"
    AudioSourceOption.MIC -> "MIC"
    AudioSourceOption.VOICE_RECOGNITION -> "REC"
    AudioSourceOption.CAMCORDER -> "CAM"
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun RecordingRow(
    item: RecordingItem,
    onPlay: () -> Unit,
    onSaveDownloads: () -> Unit,
    onSaveAs: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    val durationLabel = item.durationHintMillis?.let { formatDuration(it) }
    val meta = buildString {
        append(DateFormat.getDateTimeInstance().format(Date(item.startedAtMillis)))
        if (durationLabel != null) {
            append(" · ")
            append(durationLabel)
        }
        append(" · ")
        append("${item.sizeBytes / 1024} KB")
        item.phoneNumber?.takeIf { it.isNotBlank() }?.let {
            append(" · ")
            append(it)
        }
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(item.displayName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(text = meta, style = MaterialTheme.typography.bodySmall)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onPlay) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Пусни")
                }
                IconButton(onClick = onSaveDownloads) {
                    Icon(Icons.Default.Save, contentDescription = "Копирай в Изтегляния")
                }
                IconButton(onClick = onSaveAs) {
                    Icon(Icons.Default.Folder, contentDescription = "Запази като…")
                }
                IconButton(onClick = onShare) {
                    Icon(Icons.Default.Share, contentDescription = "Сподели")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Изтрий")
                }
            }
        }
    }
}

private fun formatDuration(millis: Long): String {
    val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(millis).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%d:%02d", minutes, seconds)
}
