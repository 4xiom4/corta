package com.corta.app.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.*
import android.telecom.Call
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.corta.app.services.CallManager
import com.corta.app.ui.i18n.CortaTheme
import com.corta.app.ui.i18n.Strings
import com.corta.app.ui.i18n.EnglishStrings
import com.corta.app.ui.i18n.SpanishStrings
import com.corta.app.ui.i18n.CortaStrings
import com.corta.app.ui.theme.AccentColor
import com.corta.app.ui.theme.AppTheme
import com.corta.app.ui.theme.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class InCallActivity : ComponentActivity() {

    private var mediaRecorder: MediaRecorder? = null
    private var currentRecordingFile: File? = null

    private val requestRecordAudioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val phoneNumber = intent.getStringExtra("EXTRA_PHONE_NUMBER") ?: "Unknown"
            val rawPhoneNumber = intent.getStringExtra("EXTRA_RAW_PHONE_NUMBER") ?: phoneNumber
            lifecycleScope.launch { handleRecordToggle(rawPhoneNumber) }
        } else {
            Toast.makeText(this, "Microphone permission required", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Window setup for high availability on lockscreen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        val phoneNumber = intent.getStringExtra("EXTRA_PHONE_NUMBER") ?: "Unknown"
        val rawPhoneNumber = intent.getStringExtra("EXTRA_RAW_PHONE_NUMBER") ?: phoneNumber
        val spamAction = intent.getStringExtra("EXTRA_SPAM_ACTION")

        setContent {
            val context = LocalContext.current
            val haptic = LocalHapticFeedback.current
            val prefs = remember { context.getSharedPreferences("corta_prefs", Context.MODE_PRIVATE) }
            
            val currentThemeMode by remember { mutableStateOf(ThemeMode.valueOf(prefs.getString("theme_mode", ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name)) }
            val currentAccentColor by remember { mutableStateOf(AccentColor.valueOf(prefs.getString("accent_color", AccentColor.DYNAMIC.name) ?: AccentColor.DYNAMIC.name)) }
            val currentLanguage = remember { prefs.getString("language", "es") ?: "es" }
            val recordingEnabled = prefs.getBoolean("recording_enabled", true)

            AppTheme(themeMode = currentThemeMode, accentColor = currentAccentColor) {
                CortaTheme(language = currentLanguage) {
                    val callState by CallManager.callState.collectAsState()
                    val isMuted by CallManager.isMuted.collectAsState()
                    val isSpeakerphoneOn by CallManager.isSpeakerphoneOn.collectAsState()
                    val isRecording by CallManager.isRecording.collectAsState()
                    
                    var callDuration by remember { mutableLongStateOf(0L) }
                    val updateTrigger by CallManager.callDetailsUpdated.collectAsState()

                    LaunchedEffect(callState, updateTrigger) {
                        while (callState == Call.STATE_ACTIVE) {
                            val connectTime = CallManager.currentCall?.details?.connectTimeMillis ?: 0L
                            if (connectTime > 0) {
                                callDuration = (System.currentTimeMillis() - connectTime) / 1000
                            }
                            delay(1000)
                        }
                    }

                    if (callState == Call.STATE_DISCONNECTED) {
                        LaunchedEffect(Unit) {
                            if (isRecording) stopRecording()
                            delay(1500)
                            finish()
                        }
                    }

                    InCallScreen(
                        phoneNumber = phoneNumber,
                        spamAction = spamAction,
                        callState = callState,
                        callDuration = callDuration,
                        isMuted = isMuted,
                        isSpeakerphoneOn = isSpeakerphoneOn,
                        isRecording = isRecording,
                        recordingEnabled = recordingEnabled,
                        onMuteToggle = { 
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            CallManager.setMute(!isMuted) 
                        },
                        onSpeakerToggle = { 
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            CallManager.setSpeakerphoneOn(!isSpeakerphoneOn) 
                        },
                        onHoldToggle = { 
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            CallManager.hold() 
                        },
                        onRecordToggle = { 
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            lifecycleScope.launch { handleRecordToggle(rawPhoneNumber) }
                        },
                        onEndCall = { 
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            CallManager.disconnect() 
                        },
                        onAnswer = { 
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            CallManager.answer() 
                        },
                        onReject = { 
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            CallManager.reject() 
                        },
                        onDtmfTone = { digit -> CallManager.playDtmfTone(digit) },
                        onStopDtmfTone = { CallManager.stopDtmfTone() }
                    )
                }
            }
        }
    }

    private suspend fun handleRecordToggle(phoneNumber: String) {
        val prefs = getSharedPreferences("corta_prefs", Context.MODE_PRIVATE)
        val recordingEnabled = prefs.getBoolean("recording_enabled", true)
        val lang = prefs.getString("language", "es") ?: "es"
        val strings = if (lang == "es") SpanishStrings else EnglishStrings

        if (!recordingEnabled) {
            withContext(Dispatchers.Main) {
                Toast.makeText(this@InCallActivity, strings.recordingDisabled, Toast.LENGTH_SHORT).show()
            }
            return
        }

        if (CallManager.isRecording.value) {
            stopRecording()
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                requestRecordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            } else {
                startRecording(phoneNumber)
            }
        }
    }

    private suspend fun startRecording(phoneNumber: String) = withContext(Dispatchers.IO) {
        val prefs = getSharedPreferences("corta_prefs", Context.MODE_PRIVATE)
        val lang = prefs.getString("language", "es") ?: "es"
        val strings = if (lang == "es") SpanishStrings else EnglishStrings

        try {
            val folder = File(getExternalFilesDir(Environment.DIRECTORY_MUSIC), "Recordings")
            if (!folder.exists()) folder.mkdirs()

            val timeStamp = SimpleDateFormat("dd_MM_yy_HH_mm", Locale.getDefault()).format(Date())
            val prefix = if (lang == "es") "llamada" else "call"
            val fileName = "${prefix}_${phoneNumber}_${timeStamp}.mp3"
            
            currentRecordingFile = File(folder, fileName)

            mediaRecorder = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this@InCallActivity)
            } else {
                @Suppress("DEPRECATION") MediaRecorder()
            }).apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(currentRecordingFile?.absolutePath)
                prepare()
                start()
            }
            
            CallManager.setIsRecording(true)
            withContext(Dispatchers.Main) {
                Toast.makeText(this@InCallActivity, strings.recordingStarted, Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e("InCallActivity", "Failed to start recording", e)
            withContext(Dispatchers.Main) {
                Toast.makeText(this@InCallActivity, strings.recordingError, Toast.LENGTH_LONG).show()
            }
            CallManager.setIsRecording(false)
        }
    }

    private suspend fun stopRecording() = withContext(Dispatchers.IO) {
        val prefs = getSharedPreferences("corta_prefs", Context.MODE_PRIVATE)
        val lang = prefs.getString("language", "es") ?: "es"
        val strings = if (lang == "es") SpanishStrings else EnglishStrings

        try {
            mediaRecorder?.apply {
                stop()
                reset()
                release()
            }
        } catch (e: Exception) {
            Log.e("InCallActivity", "Error stopping MediaRecorder", e)
        } finally {
            mediaRecorder = null
            CallManager.setIsRecording(false)
            withContext(Dispatchers.Main) {
                Toast.makeText(this@InCallActivity, strings.recordingSaved, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleScope.launch(Dispatchers.IO) {
            if (CallManager.isRecording.value) stopRecording()
        }
    }
}

@Composable
fun InCallScreen(
    phoneNumber: String,
    spamAction: String?,
    callState: Int,
    callDuration: Long,
    isMuted: Boolean,
    isSpeakerphoneOn: Boolean,
    isRecording: Boolean,
    recordingEnabled: Boolean,
    onMuteToggle: () -> Unit,
    onSpeakerToggle: () -> Unit,
    onHoldToggle: () -> Unit,
    onRecordToggle: () -> Unit,
    onEndCall: () -> Unit,
    onAnswer: () -> Unit,
    onReject: () -> Unit,
    onDtmfTone: (Char) -> Unit,
    onStopDtmfTone: () -> Unit
) {
    var showKeypad by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(64.dp))
            
            // UX Premium: Animated transition between Avatar and Keypad
            AnimatedContent(
                targetState = showKeypad,
                transitionSpec = {
                    fadeIn() + scaleIn() togetherWith fadeOut() + scaleOut()
                },
                label = "AvatarKeypadTransition"
            ) { isKeypadVisible ->
                if (isKeypadVisible) {
                    Box(modifier = Modifier.height(200.dp), contentAlignment = Alignment.Center) {
                        Dialpad(onDigitPress = onDtmfTone, onDigitRelease = onStopDtmfTone)
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(120.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Rounded.Person,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(32.dp))
                        Text(
                            text = phoneNumber,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = getCallStateString(callState, callDuration, Strings),
                            style = MaterialTheme.typography.titleMedium,
                            color = if (callState == Call.STATE_DISCONNECTED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (!showKeypad && (spamAction == "WARN" || spamAction == "BLOCK" || spamAction == "MUTE")) {
                Spacer(modifier = Modifier.height(16.dp))
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.medium
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Report, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = Strings.spamWarning,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            if (showKeypad) {
                TextButton(onClick = { showKeypad = false }) {
                    Text(Strings.hideKeypad, style = MaterialTheme.typography.labelLarge)
                }
            } else if (callState != Call.STATE_DISCONNECTED && callState != Call.STATE_RINGING) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    CallActionButton(
                        icon = if (isMuted) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                        label = Strings.mute,
                        isActive = isMuted,
                        onClick = onMuteToggle
                    )
                    CallActionButton(
                        icon = Icons.Rounded.Dialpad,
                        label = Strings.keypad,
                        isActive = false,
                        onClick = { showKeypad = true }
                    )
                    CallActionButton(
                        icon = if (isSpeakerphoneOn) Icons.Rounded.VolumeUp else Icons.Rounded.VolumeOff,
                        label = Strings.speaker,
                        isActive = isSpeakerphoneOn,
                        onClick = onSpeakerToggle
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    CallActionButton(
                        icon = if (callState == Call.STATE_HOLDING) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                        label = Strings.hold,
                        isActive = callState == Call.STATE_HOLDING,
                        onClick = onHoldToggle
                    )
                    CallActionButton(
                        icon = Icons.Rounded.FiberManualRecord,
                        label = Strings.record,
                        isActive = isRecording,
                        isEnabled = recordingEnabled,
                        onClick = onRecordToggle
                    )
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))

            // Bottom Section: Answer/Reject/End
            if (callState == Call.STATE_RINGING) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    FloatingActionButton(
                        onClick = onReject,
                        containerColor = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(80.dp),
                        shape = CircleShape
                    ) {
                        Icon(Icons.Rounded.CallEnd, contentDescription = Strings.reject, tint = MaterialTheme.colorScheme.onError, modifier = Modifier.size(32.dp))
                    }
                    FloatingActionButton(
                        onClick = onAnswer,
                        containerColor = Color(0xFF4CAF50),
                        modifier = Modifier.size(80.dp),
                        shape = CircleShape
                    ) {
                        Icon(Icons.Rounded.Call, contentDescription = Strings.answer, tint = Color.White, modifier = Modifier.size(32.dp))
                    }
                }
            } else {
                FloatingActionButton(
                    onClick = onEndCall,
                    containerColor = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(80.dp),
                    shape = CircleShape
                ) {
                    Icon(Icons.Rounded.CallEnd, contentDescription = Strings.endCall, tint = MaterialTheme.colorScheme.onError, modifier = Modifier.size(32.dp))
                }
            }
            
            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}

@Composable
fun CallActionButton(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    isEnabled: Boolean = true,
    onClick: () -> Unit
) {
    val alpha = if (isEnabled) 1f else 0.4f
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.graphicsLayer(alpha = alpha)) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(if (isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                .clickable(enabled = isEnabled, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun Dialpad(onDigitPress: (Char) -> Unit, onDigitRelease: () -> Unit) {
    val rows = listOf(
        listOf('1', '2', '3'),
        listOf('4', '5', '6'),
        listOf('7', '8', '9'),
        listOf('*', '0', '#')
    )

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        for (row in rows) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                for (digit in row) {
                    DialpadButton(digit = digit, onPress = { onDigitPress(digit) }, onRelease = onDigitRelease)
                }
            }
        }
    }
}

@Composable
fun DialpadButton(digit: Char, onPress: () -> Unit, onRelease: () -> Unit) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .pointerInput(digit) {
                detectTapGestures(
                    onPress = {
                        onPress()
                        tryAwaitRelease()
                        onRelease()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = digit.toString(),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

fun getCallStateString(state: Int, durationSeconds: Long, strings: CortaStrings): String {
    return when (state) {
        Call.STATE_RINGING -> strings.incomingCall
        Call.STATE_DIALING -> strings.dialing
        Call.STATE_ACTIVE -> {
            val min = durationSeconds / 60
            val sec = durationSeconds % 60
            String.format(java.util.Locale.getDefault(), "%02d:%02d", min, sec)
        }
        Call.STATE_HOLDING -> strings.onHold
        Call.STATE_DISCONNECTED -> strings.callEnded
        else -> strings.connecting
    }
}
