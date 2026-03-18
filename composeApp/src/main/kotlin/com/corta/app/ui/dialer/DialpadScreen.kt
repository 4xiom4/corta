package com.corta.app.ui.dialer

import android.media.ToneGenerator
import android.media.AudioManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corta.app.ui.i18n.Strings

import android.content.Intent
import android.provider.ContactsContract
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun DialpadScreen(
    onDial: (String) -> Unit
) {
    var phoneNumber by remember { mutableStateOf("") }
    val toneGenerator = remember { ToneGenerator(AudioManager.STREAM_DTMF, 80) }

    DisposableEffect(Unit) {
        onDispose {
            toneGenerator.release()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Phone Number Display
        Text(
            text = phoneNumber,
            fontSize = 44.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp, top = 16.dp)
                .height(60.dp),
            textAlign = TextAlign.Center,
            maxLines = 1
        )

        // Add Contact Option
        val context = LocalContext.current
        if (phoneNumber.isNotEmpty()) {
            TextButton(
                onClick = {
                    val intent = Intent(Intent.ACTION_INSERT).apply {
                        type = ContactsContract.RawContacts.CONTENT_TYPE
                        putExtra(ContactsContract.Intents.Insert.PHONE, phoneNumber)
                    }
                    context.startActivity(intent)
                },
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Icon(Icons.Rounded.Person, contentDescription = null, modifier = Modifier.size(28.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(Strings.addContact, fontSize = 20.sp)
            }
        } else {
            Spacer(modifier = Modifier.height(48.dp)) // Maintain spacing when empty
        }

        // Dialpad Grid
        val buttons = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("*", "0", "#")
        )

        for (row in buttons) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                for (btn in row) {
                    DialpadButton(text = btn) {
                        phoneNumber += btn
                        playTone(toneGenerator, btn)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Action Buttons Row (Call & Delete)
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Invisible placeholder to keep the Call button centered
            Box(modifier = Modifier.size(64.dp))

            // Call Button
            FloatingActionButton(
                onClick = {
                    if (phoneNumber.isNotEmpty()) {
                        onDial(phoneNumber)
                    }
                },
                modifier = Modifier.size(72.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape
            ) {
                Icon(
                    imageVector = Icons.Rounded.Phone,
                    contentDescription = Strings.dial,
                    modifier = Modifier.size(32.dp)
                )
            }

            // Delete Button
            val scope = rememberCoroutineScope()
            var isDeleting by remember { mutableStateOf(false) }

            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .clickable(enabled = phoneNumber.isNotEmpty()) {
                        if (phoneNumber.isNotEmpty()) {
                            phoneNumber = phoneNumber.dropLast(1)
                        }
                    }
                    .pointerInput(phoneNumber) {
                        detectTapGestures(
                            onPress = {
                                if (phoneNumber.isEmpty()) return@detectTapGestures
                                isDeleting = true
                                val job = scope.launch {
                                    // Wait a bit before fast deleting (long press threshold)
                                    delay(400)
                                    while (isDeleting && phoneNumber.isNotEmpty()) {
                                        phoneNumber = phoneNumber.dropLast(1)
                                        delay(80) 
                                    }
                                }
                                tryAwaitRelease()
                                isDeleting = false
                                job.cancel()
                            },
                            onTap = {
                                if (phoneNumber.isNotEmpty()) {
                                    phoneNumber = phoneNumber.dropLast(1)
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Backspace,
                    contentDescription = Strings.delete,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
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
        toneGenerator.startTone(tone, 150)
    }
}

@Composable
fun DialpadButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        val fontSize = if (text == "*") 44.sp else 28.sp
        val verticalOffset = if (text == "*") 6.dp else 0.dp // Asterisks are naturally top-aligned, offset to center relative to #
        Text(
            text = text,
            fontSize = fontSize,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = verticalOffset)
        )
    }
}
