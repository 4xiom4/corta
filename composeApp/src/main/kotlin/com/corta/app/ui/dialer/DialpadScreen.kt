package com.corta.app.ui.dialer

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.provider.ContactsContract
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corta.app.ui.i18n.Strings
import com.corta.domain.ChileanTelecomClassifier
import com.corta.domain.PhoneNumberUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Keypad button model with standard ITU-T DTMF alphanumeric mappings.
 */
data class DialKey(
    val digit: String,
    val letters: String = ""
)

@Composable
fun DialpadScreen(
    onDial: (String) -> Unit
) {
    val strings = Strings
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    var phoneNumber by remember { mutableStateOf("") }
    val toneGenerator = remember {
        try {
            ToneGenerator(AudioManager.STREAM_DTMF, 75)
        } catch (_: Exception) {
            null
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            toneGenerator?.release()
        }
    }

    // Chilean number carrier/service metadata hint
    val numberHint = remember(phoneNumber) {
        if (phoneNumber.isBlank()) ""
        else getChileanNumberHint(phoneNumber)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 1. Phone number display area
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (phoneNumber.isEmpty()) {
                Text(
                    text = strings.calls.dialpadPlaceholder,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center
                )
            } else {
                Text(
                    text = formatDialpadDisplay(phoneNumber),
                    fontSize = if (phoneNumber.length > 11) 26.sp else 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            AnimatedVisibility(
                visible = numberHint.isNotEmpty(),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Text(
                    text = numberHint,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 2.dp),
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 2. Dialpad Matrix (4 rows of 3 buttons)
        val dialRows = listOf(
            listOf(DialKey("1", ""), DialKey("2", "ABC"), DialKey("3", "DEF")),
            listOf(DialKey("4", "GHI"), DialKey("5", "JKL"), DialKey("6", "MNO")),
            listOf(DialKey("7", "PQRS"), DialKey("8", "TUV"), DialKey("9", "WXYZ")),
            listOf(DialKey("*", ""), DialKey("0", "+"), DialKey("#", ""))
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            for (row in dialRows) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    for (key in row) {
                        DialpadButton(
                            key = key,
                            onTap = {
                                phoneNumber += key.digit
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                toneGenerator?.let { playTone(it, key.digit) }
                            },
                            onLongPress = {
                                if (key.digit == "0") {
                                    phoneNumber += "+"
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    toneGenerator?.let { playTone(it, "0") }
                                }
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // 3. Action Row: Add Contact | Call (Green/Primary Squircle) | Backspace
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Action: Add Contact
            Box(
                modifier = Modifier.size(56.dp),
                contentAlignment = Alignment.Center
            ) {
                if (phoneNumber.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_INSERT).apply {
                                type = ContactsContract.RawContacts.CONTENT_TYPE
                                putExtra(ContactsContract.Intents.Insert.PHONE, phoneNumber)
                            }
                            context.startActivity(intent)
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PersonAdd,
                            contentDescription = strings.contacts.addContact,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }

            // Center Action: Call Button (Stitch Compliant Squircle 64dp)
            Surface(
                onClick = {
                    if (phoneNumber.isNotEmpty()) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onDial(phoneNumber)
                    }
                },
                modifier = Modifier.size(64.dp),
                shape = RoundedCornerShape(20.dp),
                color = if (phoneNumber.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                shadowElevation = if (phoneNumber.isNotEmpty()) 4.dp else 0.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Call,
                        contentDescription = strings.common.dial,
                        tint = if (phoneNumber.isNotEmpty()) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(30.dp)
                    )
                }
            }

            // Right Action: Backspace with single-tap delete and long-press clear
            Box(
                modifier = Modifier.size(56.dp),
                contentAlignment = Alignment.Center
            ) {
                if (phoneNumber.isNotEmpty()) {
                    var isDeleting by remember { mutableStateOf(false) }

                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .pointerInput(phoneNumber) {
                                detectTapGestures(
                                    onPress = {
                                        if (phoneNumber.isEmpty()) return@detectTapGestures
                                        isDeleting = true
                                        val job = scope.launch {
                                            delay(380)
                                            while (isDeleting && phoneNumber.isNotEmpty()) {
                                                phoneNumber = phoneNumber.dropLast(1)
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                delay(70)
                                            }
                                        }
                                        tryAwaitRelease()
                                        isDeleting = false
                                        job.cancel()
                                    },
                                    onTap = {
                                        if (phoneNumber.isNotEmpty()) {
                                            phoneNumber = phoneNumber.dropLast(1)
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                    }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Backspace,
                            contentDescription = strings.common.delete,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DialpadButton(
    key: DialKey,
    onTap: () -> Unit,
    onLongPress: () -> Unit
) {
    Surface(
        modifier = Modifier
            .size(width = 80.dp, height = 58.dp)
            .clip(RoundedCornerShape(16.dp))
            .pointerInput(key) {
                detectTapGestures(
                    onTap = { onTap() },
                    onLongPress = { onLongPress() }
                )
            },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            val fontSize = if (key.digit == "*") 28.sp else 23.sp
            val topOffset = if (key.digit == "*") 4.dp else 0.dp

            Text(
                text = key.digit,
                fontSize = fontSize,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = topOffset),
                lineHeight = 24.sp
            )

            if (key.letters.isNotEmpty()) {
                Text(
                    text = key.letters,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    letterSpacing = 1.2.sp,
                    lineHeight = 10.sp
                )
            }
        }
    }
}

private fun playTone(toneGenerator: ToneGenerator, digit: String) {
    val tone = when (digit) {
        "1" -> ToneGenerator.TONE_DTMF_1
        "2" -> ToneGenerator.TONE_DTMF_2
        "3" -> ToneGenerator.TONE_DTMF_3
        "4" -> ToneGenerator.TONE_DTMF_4
        "5" -> ToneGenerator.TONE_DTMF_5
        "6" -> ToneGenerator.TONE_DTMF_6
        "7" -> ToneGenerator.TONE_DTMF_7
        "8" -> ToneGenerator.TONE_DTMF_8
        "9" -> ToneGenerator.TONE_DTMF_9
        "0" -> ToneGenerator.TONE_DTMF_0
        "*" -> ToneGenerator.TONE_DTMF_S
        "#" -> ToneGenerator.TONE_DTMF_P
        else -> -1
    }
    if (tone != -1) {
        try {
            toneGenerator.startTone(tone, 120)
        } catch (_: Exception) {
            // Ignore if audio track unavailable
        }
    }
}

private fun formatDialpadDisplay(raw: String): String {
    val clean = raw.trim()
    return if (clean.startsWith("+56") && clean.length > 3) {
        PhoneNumberUtils.formatDisplayNumber(clean)
    } else if (clean.startsWith("9") && clean.length == 9) {
        PhoneNumberUtils.formatDisplayNumber("+56$clean")
    } else {
        clean
    }
}

private fun getChileanNumberHint(number: String): String {
    val clean = number.trim()
    val meta = ChileanTelecomClassifier.classify(clean)

    return when {
        clean == "133" -> "Emergencia · Carabineros de Chile"
        clean == "131" -> "Emergencia · SAMU Ambulancia"
        clean == "132" -> "Emergencia · Bomberos de Chile"
        clean == "134" -> "Emergencia · PDI Policía de Investigaciones"
        clean == "130" -> "Emergencia · CONAF Incendios Forestales"
        clean == "149" -> "Emergencia · Fono Familia Carabineros"
        clean == "147" -> "Emergencia · Fono Niños Carabineros"
        meta.type == com.corta.domain.ChileanNumberType.MUNICIPAL_SECURITY_14XX -> "Seguridad Municipal · Comuna de Chile"
        clean.startsWith("809") || clean.startsWith("+56809") -> "SUBTEL 809 · Telemarketing masivo"
        clean.startsWith("600") || clean.startsWith("+56600") -> "Servicio Oficial · Línea 600"
        clean.startsWith("9") && clean.length <= 9 -> "Celular Chile"
        clean.startsWith("+569") -> "Celular Chile"
        clean.startsWith("2") && clean.length <= 9 -> "Red Fija Santiago / RM"
        clean.startsWith("+562") -> "Red Fija Santiago / RM"
        clean.startsWith("+56") -> "Chile (+56)"
        else -> ""
    }
}
