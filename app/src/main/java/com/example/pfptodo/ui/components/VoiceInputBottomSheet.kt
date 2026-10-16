package com.example.pfptodo.ui.components

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicNone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.pfptodo.R
import com.example.pfptodo.domain.model.ParsedTask
import com.example.pfptodo.domain.parser.LocalTaskParser
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceInputBottomSheet(
    onResult: (String) -> Unit,
    onParsedResult: ((ParsedTask) -> Unit)? = null,
    onOpenEditorWithTask: ((ParsedTask) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var isListening by remember { mutableStateOf(false) }
    var spokenText by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val parser = remember { LocalTaskParser() }
    var parsedTaskState by remember { mutableStateOf<ParsedTask?>(null) }

    LaunchedEffect(spokenText) {
        if (spokenText.isNotBlank()) {
            parsedTaskState = parser.parse(spokenText).parsedTask
        } else {
            parsedTaskState = null
        }
    }

    val speechRecognizer = remember { SpeechRecognizer.createSpeechRecognizer(context) }

    val recognizerIntent = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            speechRecognizer.startListening(recognizerIntent)
            isListening = true
        } else {
            errorMessage = context.getString(R.string.voice_permission_denied)
        }
    }

    DisposableEffect(Unit) {
        val listener = object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                errorMessage = null
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                isListening = false
            }
            override fun onError(error: Int) {
                isListening = false
                errorMessage = context.getString(R.string.voice_error)
            }
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    spokenText = matches[0]
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
        speechRecognizer.setRecognitionListener(listener)

        // Start listening immediately
        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)

        onDispose {
            speechRecognizer.destroy()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(
                text = stringResource(R.string.voice_create_task),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(90.dp)
            ) {
                if (isListening) {
                    val infiniteTransition = rememberInfiniteTransition(label = "Pulse")
                    val scale by infiniteTransition.animateFloat(
                        initialValue = 1f,
                        targetValue = 1.5f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1000, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "PulseScale"
                    )
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .scale(scale)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), CircleShape)
                    )
                }

                IconButton(
                    onClick = {
                        if (!isListening) {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        } else {
                            speechRecognizer.stopListening()
                            isListening = false
                        }
                    },
                    modifier = Modifier
                        .size(64.dp)
                        .background(
                            if (isListening) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = if (isListening) Icons.Default.Mic else Icons.Default.MicNone,
                        contentDescription = stringResource(R.string.cd_mic),
                        tint = if (isListening) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                    )
                }
            }

            Text(
                text = when {
                    isListening -> stringResource(R.string.voice_listening)
                    errorMessage != null -> errorMessage!!
                    spokenText.isEmpty() -> stringResource(R.string.voice_ready)
                    else -> spokenText
                },
                style = MaterialTheme.typography.bodyLarge,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                color = if (errorMessage != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )

            if (errorMessage != null && !isListening) {
                OutlinedButton(
                    onClick = {
                        errorMessage = null
                        spokenText = ""
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.voice_retry))
                }
            }

            AnimatedVisibility(visible = parsedTaskState != null) {
                parsedTaskState?.let { currentParsed ->
                    SmartTaskPreviewCard(
                        parsedTask = currentParsed,
                        onTitleChange = { newTitle ->
                            parsedTaskState = currentParsed.copy(title = newTitle)
                        },
                        onRemoveDate = {
                            parsedTaskState = currentParsed.copy(date = null, dueInstant = null)
                        },
                        onRemoveTime = {
                            val newInstant = currentParsed.date?.atTime(9, 0)?.atZone(java.time.ZoneId.systemDefault())?.toInstant()
                            parsedTaskState = currentParsed.copy(time = null, dueInstant = newInstant)
                        },
                        onRemovePriority = {
                            parsedTaskState = currentParsed.copy(priority = com.example.pfptodo.domain.model.Priority.MEDIUM)
                        },
                        onRemoveTag = { tagToRemove ->
                            parsedTaskState = currentParsed.copy(tags = currentParsed.tags.filter { it != tagToRemove })
                        },
                        onRemoveRecurrence = {
                            parsedTaskState = currentParsed.copy(recurrenceRule = com.example.pfptodo.domain.model.RecurrenceRule.None)
                        },
                        onConfirm = {
                            if (onParsedResult != null) {
                                onParsedResult(currentParsed)
                            } else {
                                onResult(currentParsed.title)
                            }
                        },
                        onOpenEditor = {
                            if (onOpenEditorWithTask != null) {
                                onOpenEditorWithTask(currentParsed)
                            } else {
                                onResult(currentParsed.title)
                            }
                        },
                        onCancel = onDismiss
                    )
                }
            }
        }
    }
}
