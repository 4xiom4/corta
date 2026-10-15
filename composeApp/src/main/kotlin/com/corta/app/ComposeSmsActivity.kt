package com.corta.app

import android.net.Uri
import android.os.Bundle
import android.telephony.SmsManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.corta.app.ui.i18n.Strings

class ComposeSmsActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val initialNumber = extractNumberFromIntentData(intent?.data)

        setContent {
            val context = LocalContext.current
            val strings = Strings
            var toNumber by remember { mutableStateOf(initialNumber) }
            var message by remember { mutableStateOf("") }

            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(strings.sms.newMessageTitle) },
                        navigationIcon = {
                            IconButton(onClick = { finish() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.diagnostics.dsBackCd)
                            }
                        },
                        actions = {
                            IconButton(
                                onClick = {
                                    if (toNumber.isBlank() || message.isBlank()) {
                                        Toast.makeText(
                                            context,
                                            strings.sms.composeRequiredToast,
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        return@IconButton
                                    }

                                    try {
                                        val smsManager = context.getSystemService(SmsManager::class.java)
                                        smsManager.sendTextMessage(toNumber, null, message, null, null)
                                        Toast.makeText(context, strings.sms.composeSentToast, Toast.LENGTH_SHORT).show()
                                        finish()
                                    } catch (e: Exception) {
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.compose_send_error, e.message.orEmpty()),
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = strings.sms.sendCd)
                            }
                        }
                    )
                }
            ) { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(16.dp)
                ) {
                    OutlinedTextField(
                        value = toNumber,
                        onValueChange = { toNumber = it },
                        label = { Text(strings.sms.composeToLabel) },
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    OutlinedTextField(
                        value = message,
                        onValueChange = { message = it },
                        label = { Text(strings.sms.composeMessageLabel) },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }

    private fun extractNumberFromIntentData(data: Uri?): String {
        if (data == null) return ""
        return when (data.scheme) {
            "smsto", "sms", "mmsto", "mms" -> data.schemeSpecificPart.orEmpty()
            else -> ""
        }
    }
}
