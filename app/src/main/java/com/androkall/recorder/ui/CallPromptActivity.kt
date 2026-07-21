package com.androkall.recorder.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.androkall.recorder.R
import com.androkall.recorder.service.CallControlNotifier
import com.androkall.recorder.service.CallRecordingService
import com.androkall.recorder.ui.theme.AndroCallRecorderTheme

/**
 * Full-screen / heads-up prompt during a call — works without overlay permission.
 */
class CallPromptActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val number = intent.getStringExtra(EXTRA_NUMBER)
        val ringing = intent.getBooleanExtra(EXTRA_RINGING, false)

        setContent {
            AndroCallRecorderTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    CallPromptContent(
                        number = number,
                        ringing = ringing,
                        onStart = {
                            CallRecordingService.start(this, number)
                            CallControlNotifier.showInCall(this, number, recording = true)
                            finish()
                        },
                        onDismiss = { finish() }
                    )
                }
            }
        }
    }

    companion object {
        const val EXTRA_NUMBER = "extra_number"
        const val EXTRA_RINGING = "extra_ringing"
    }
}

@Composable
private fun CallPromptContent(
    number: String?,
    ringing: Boolean,
    onStart: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = if (ringing) {
                stringResource(R.string.call_prompt_ringing)
            } else {
                stringResource(R.string.call_prompt_incall)
            },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        if (!number.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(number, style = MaterialTheme.typography.bodyLarge)
        }
        Spacer(modifier = Modifier.height(28.dp))
        Button(
            onClick = onStart,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.overlay_start_record))
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.call_prompt_dismiss))
        }
    }
}
