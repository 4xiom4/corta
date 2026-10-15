package com.corta.app.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.*
import android.telecom.Call
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import timber.log.Timber
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.corta.app.R
import com.corta.app.services.CallManager
import com.corta.app.ui.i18n.CortaTheme
import com.corta.app.ui.i18n.Strings
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

    private companion object {
        const val TAG = "Corta.InCallActivity"
    }

    private val requestRecordAudioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val phoneNumber = intent.getStringExtra("EXTRA_PHONE_NUMBER") ?: getString(R.string.unknown_number)
            val rawPhoneNumber = intent.getStringExtra("EXTRA_RAW_PHONE_NUMBER") ?: phoneNumber
            lifecycleScope.launch { handleRecordToggle(rawPhoneNumber) }
        } else {
            Toast.makeText(this, getString(R.string.microphone_permission_required), Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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

        val phoneNumber = intent.getStringExtra("EXTRA_PHONE_NUMBER") ?: getString(R.string.unknown_number)
        val rawPhoneNumber = intent.getStringExtra("EXTRA_RAW_PHONE_NUMBER") ?: phoneNumber
        val spamAction = intent.getStringExtra("EXTRA_SPAM_ACTION")

        setContent {
            val context = LocalContext.current
            val haptic = LocalHapticFeedback.current
            val prefs = remember { context.getSharedPreferences("corta_prefs", Context.MODE_PRIVATE) }

            val currentThemeMode by remember { mutableStateOf(ThemeMode.valueOf(prefs.getString("theme_mode", ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name)) }
            val currentAccentColor by remember { mutableStateOf(AccentColor.fromPreference(prefs.getString("accent_color", AccentColor.DYNAMIC.name))) }
            val currentLanguage = remember { prefs.getString("language", "es") ?: "es" }
            val recordingEnabled = prefs.getBoolean("recording_enabled", true)

            AppTheme(themeMode = currentThemeMode, accentColor = currentAccentColor) {
                CortaTheme(language = currentLanguage) {
                    val strings = Strings
                    val liveCallState by CallManager.callState.collectAsState()
                    val testCallState = remember { intent.getIntExtra("EXTRA_TEST_CALL_STATE", -1) }
                    val callState = if (testCallState != -1) testCallState else liveCallState
                    val isMuted by CallManager.isMuted.collectAsState()
                    val isSpeakerphoneOn by CallManager.isSpeakerphoneOn.collectAsState()
                    val isRecording by CallManager.isRecording.collectAsState()

                    var callDuration by remember { mutableLongStateOf(if (testCallState == Call.STATE_ACTIVE) 42L else 0L) }
                    val updateTrigger by CallManager.callDetailsUpdated.collectAsState()

                    LaunchedEffect(callState, updateTrigger) {
                        while (callState == Call.STATE_ACTIVE) {
                            val connectTime = CallManager.currentCall?.details?.connectTimeMillis ?: 0L
                            if (connectTime > 0) {
                                callDuration = (System.currentTimeMillis() - connectTime) / 1000
                            } else if (testCallState == Call.STATE_ACTIVE) {
                                callDuration += 1
                            }
                            delay(1000)
                        }
                    }

                    if (callState == Call.STATE_DISCONNECTED && testCallState == -1) {
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
                            if (testCallState != -1) finish() else CallManager.disconnect()
                        },
                        onAnswer = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            CallManager.answer()
                        },
                        onReject = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (testCallState != -1) finish() else CallManager.reject()
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

        if (!recordingEnabled) {
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    this@InCallActivity,
                    getString(R.string.recording_disabled),
                    Toast.LENGTH_SHORT
                ).show()
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

        try {
            val folder = File(getExternalFilesDir(Environment.DIRECTORY_MUSIC), getString(R.string.recording_folder))
            if (!folder.exists()) folder.mkdirs()

            val timeStamp = SimpleDateFormat("dd_MM_yy_HH_mm", Locale.getDefault()).format(Date())
            val prefix = if (lang == "es") getString(R.string.recording_filename_prefix_es) else getString(R.string.recording_filename_prefix_en)
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
                Toast.makeText(
                    this@InCallActivity,
                    getString(R.string.recording_started),
                    Toast.LENGTH_SHORT
                ).show()
            }
        } catch (e: Throwable) {
            Timber.tag(TAG).e(e, "Failed to start recording")
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    this@InCallActivity,
                    getString(R.string.recording_error),
                    Toast.LENGTH_LONG
                ).show()
            }
            CallManager.setIsRecording(false)
        }
    }

    private suspend fun stopRecording() = withContext(Dispatchers.IO) {
        try {
            mediaRecorder?.apply {
                stop()
                reset()
                release()
            }
        } catch (e: Throwable) {
            Timber.tag(TAG).e(e, "Error stopping MediaRecorder")
        } finally {
            mediaRecorder = null
            CallManager.setIsRecording(false)
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    this@InCallActivity,
                    getString(R.string.recording_saved),
                    Toast.LENGTH_SHORT
                ).show()
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

@Suppress("DEPRECATION")
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
    val strings = Strings
    var showKeypad by remember { mutableStateOf(false) }

    val rawNum = phoneNumber
    val telecomMeta = remember(rawNum) { com.corta.domain.ChileanTelecomClassifier.classify(rawNum) }
    val isSpam = spamAction == "BLOCK" || spamAction == "WARN" || telecomMeta.type == com.corta.domain.ChileanNumberType.SUBTEL_TELEMARKETING_809
    val isEmergency = telecomMeta.type == com.corta.domain.ChileanNumberType.EMERGENCY_NATIONAL ||
            telecomMeta.type == com.corta.domain.ChileanNumberType.MUNICIPAL_SECURITY_14XX ||
            telecomMeta.isVerifiedOfficialService

    // Breathing pulse animation for incoming ringing call
    val infiniteTransition = rememberInfiniteTransition(label = "InCallPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.28f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseAlpha"
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 20.dp)
                .padding(WindowInsets.safeDrawing.asPaddingValues()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(28.dp))

            AnimatedContent(
                targetState = showKeypad,
                transitionSpec = {
                    fadeIn() + scaleIn() togetherWith fadeOut() + scaleOut()
                },
                label = "AvatarKeypadTransition"
            ) { isKeypadVisible ->
                if (isKeypadVisible) {
                    Box(modifier = Modifier.fillMaxWidth().height(280.dp), contentAlignment = Alignment.Center) {
                        Dialpad(onDigitPress = onDtmfTone, onDigitRelease = onStopDtmfTone)
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        // Avatar container with optional breathing ripple during ringing
                        Box(contentAlignment = Alignment.Center) {
                            if (callState == Call.STATE_RINGING) {
                                Box(
                                    modifier = Modifier
                                        .size(112.dp)
                                        .graphicsLayer {
                                            scaleX = pulseScale
                                            scaleY = pulseScale
                                            alpha = pulseAlpha
                                        }
                                        .background(
                                            color = when {
                                                isSpam -> MaterialTheme.colorScheme.error
                                                isEmergency -> MaterialTheme.colorScheme.primary
                                                else -> MaterialTheme.colorScheme.secondary
                                            },
                                            shape = CircleShape
                                        )
                                )
                            }

                            val avatarSize = if (callState == Call.STATE_RINGING) 104.dp else 84.dp
                            val iconSize = if (callState == Call.STATE_RINGING) 52.dp else 42.dp

                            Surface(
                                shape = CircleShape,
                                color = when {
                                    isSpam -> MaterialTheme.colorScheme.errorContainer
                                    isEmergency -> MaterialTheme.colorScheme.primaryContainer
                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                },
                                modifier = Modifier.size(avatarSize),
                                border = BorderStroke(
                                    2.dp,
                                    when {
                                        isSpam -> MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                                        isEmergency -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                                    }
                                )
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = when {
                                            isSpam -> Icons.Rounded.Shield
                                            telecomMeta.isVerifiedOfficialService -> Icons.Rounded.Verified
                                            telecomMeta.type == com.corta.domain.ChileanNumberType.EMERGENCY_NATIONAL -> Icons.Rounded.LocalHospital
                                            telecomMeta.type == com.corta.domain.ChileanNumberType.MUNICIPAL_SECURITY_14XX -> Icons.Rounded.HealthAndSafety
                                            else -> Icons.Rounded.Person
                                        },
                                        contentDescription = null,
                                        modifier = Modifier.size(iconSize),
                                        tint = when {
                                            isSpam -> MaterialTheme.colorScheme.error
                                            isEmergency -> MaterialTheme.colorScheme.primary
                                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        // Phone Number
                        Text(
                            text = com.corta.domain.PhoneNumberUtils.formatDisplayNumber(phoneNumber),
                            style = if (callState == Call.STATE_RINGING) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onBackground
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Badges Row
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (telecomMeta.badgeLabel.isNotBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = when {
                                        isSpam -> MaterialTheme.colorScheme.errorContainer
                                        isEmergency -> MaterialTheme.colorScheme.primaryContainer
                                        else -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                                    }
                                ) {
                                    Text(
                                        text = telecomMeta.badgeLabel,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = when {
                                            isSpam -> MaterialTheme.colorScheme.onErrorContainer
                                            isEmergency -> MaterialTheme.colorScheme.onPrimaryContainer
                                            else -> MaterialTheme.colorScheme.onSecondaryContainer
                                        },
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            if (!telecomMeta.cityOrRegion.isNullOrBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Text(
                                        text = telecomMeta.cityOrRegion!!,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Call Duration / State Chip
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (callState == Call.STATE_ACTIVE) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                            else MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (callState == Call.STATE_ACTIVE) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Text(
                                    text = getCallStateString(callState, callDuration, Strings),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = when (callState) {
                                        Call.STATE_DISCONNECTED -> MaterialTheme.colorScheme.error
                                        Call.STATE_ACTIVE -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                                if (isRecording) {
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.errorContainer
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.error)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "REC",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Black,
                                                color = MaterialTheme.colorScheme.onErrorContainer
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Spam / Emergency Information Card
            if (!showKeypad && (isSpam || isEmergency)) {
                Spacer(modifier = Modifier.height(20.dp))
                Surface(
                    color = if (isSpam) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(
                        1.dp,
                        if (isSpam) MaterialTheme.colorScheme.error.copy(alpha = 0.35f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isSpam) Icons.Rounded.Report else Icons.Rounded.VerifiedUser,
                            contentDescription = null,
                            tint = if (isSpam) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = if (isSpam) "Alerta de Spam SUBTEL 809" else "Servicio Protegido por Ley",
                                color = if (isSpam) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isSpam)
                                    "Corta identificó este número como campaña comercial masiva no solicitada."
                                else
                                    "Línea de emergencia o utilidad pública nacional. Nunca se bloquea en Corta.",
                                color = if (isSpam) MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f)
                                else MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            if (showKeypad) {
                TextButton(
                    onClick = { showKeypad = false },
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    Icon(Icons.Rounded.KeyboardHide, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(strings.calls.hideKeypad, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }
            } else if (callState != Call.STATE_DISCONNECTED && callState != Call.STATE_RINGING) {
                // Active Call Controls (2 rows x 3 columns)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        CallActionButton(
                            icon = if (isMuted) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                            label = strings.calls.mute,
                            isActive = isMuted,
                            onClick = onMuteToggle
                        )
                        CallActionButton(
                            icon = Icons.Rounded.Dialpad,
                            label = strings.calls.keypad,
                            isActive = false,
                            onClick = { showKeypad = true }
                        )
                        CallActionButton(
                            icon = if (isSpeakerphoneOn) Icons.Rounded.VolumeUp else Icons.Rounded.VolumeOff,
                            label = strings.calls.speaker,
                            isActive = isSpeakerphoneOn,
                            onClick = onSpeakerToggle
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        CallActionButton(
                            icon = if (callState == Call.STATE_HOLDING) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                            label = strings.calls.hold,
                            isActive = callState == Call.STATE_HOLDING,
                            onClick = onHoldToggle
                        )
                        CallActionButton(
                            icon = Icons.Rounded.FiberManualRecord,
                            label = strings.calls.record,
                            isActive = isRecording,
                            isEnabled = recordingEnabled,
                            onClick = onRecordToggle
                        )
                        CallActionButton(
                            icon = Icons.Rounded.BluetoothAudio,
                            label = "Audio",
                            isActive = false,
                            isEnabled = true,
                            onClick = onSpeakerToggle
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Bottom Call Action Buttons
            if (callState == Call.STATE_RINGING) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            onClick = onReject,
                            shape = RoundedCornerShape(26.dp),
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(76.dp),
                            shadowElevation = 4.dp
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Rounded.CallEnd,
                                    contentDescription = strings.calls.reject,
                                    tint = MaterialTheme.colorScheme.onError,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = strings.calls.reject,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            onClick = onAnswer,
                            shape = RoundedCornerShape(26.dp),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(76.dp),
                            shadowElevation = 4.dp
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Rounded.Call,
                                    contentDescription = strings.calls.answer,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = strings.calls.answer,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        onClick = onEndCall,
                        shape = RoundedCornerShape(26.dp),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(76.dp),
                        shadowElevation = 4.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Rounded.CallEnd,
                                contentDescription = strings.calls.endCall,
                                tint = MaterialTheme.colorScheme.onError,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = strings.calls.endCall,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
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
    val alpha = if (isEnabled) 1f else 0.38f
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.graphicsLayer(alpha = alpha)
    ) {
        Surface(
            onClick = onClick,
            enabled = isEnabled,
            shape = RoundedCornerShape(20.dp),
            color = if (isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
            border = if (isActive) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
            modifier = Modifier.size(68.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
            color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
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
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        for (row in rows) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
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
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .size(64.dp)
            .pointerInput(digit) {
                detectTapGestures(
                    onPress = {
                        onPress()
                        tryAwaitRelease()
                        onRelease()
                    }
                )
            }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = digit.toString(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

fun getCallStateString(state: Int, durationSeconds: Long, strings: CortaStrings): String {
    return when (state) {
        Call.STATE_RINGING -> strings.calls.incomingCall
        Call.STATE_DIALING -> strings.calls.dialing
        Call.STATE_ACTIVE -> {
            val min = durationSeconds / 60
            val sec = durationSeconds % 60
            String.format(java.util.Locale.getDefault(), "%02d:%02d", min, sec)
        }
        Call.STATE_HOLDING -> strings.calls.onHold
        Call.STATE_DISCONNECTED -> strings.calls.ended
        else -> strings.calls.connecting
    }
}

