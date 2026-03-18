package com.corta.app.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import android.util.Log
import android.widget.Toast
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import kotlinx.coroutines.launch
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("corta_prefs", Context.MODE_PRIVATE) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.Shield,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(fontSize = MaterialTheme.typography.titleMedium.fontSize, fontStyle = FontStyle.Italic)) {
                        append("con ")
                    }
                    withStyle(SpanStyle(fontSize = MaterialTheme.typography.displaySmall.fontSize, fontWeight = FontWeight.Bold)) {
                        append("Corta!")
                    }
                    withStyle(SpanStyle(fontSize = MaterialTheme.typography.titleMedium.fontSize, fontStyle = FontStyle.Italic)) {
                        append(" la haces corta")
                    }
                },
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "tu privacidad y seguridad es siempre nuestra prioridad, inicia sesion para personalizar tu experiencia.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(48.dp))
            
            Button(
                onClick = {
                    coroutineScope.launch {
                        try {
                            val credentialManager = CredentialManager.create(context)
                            val googleIdOption = GetGoogleIdOption.Builder()
                                .setFilterByAuthorizedAccounts(false)
                                .setServerClientId("SU_CLIENT_ID_WEB_AQUI")
                                .setAutoSelectEnabled(true)
                                .build()

                            val request = GetCredentialRequest.Builder()
                                .addCredentialOption(googleIdOption)
                                .build()

                            val result = credentialManager.getCredential(request = request, context = context)
                            
                            prefs.edit().putBoolean("is_guest", false).apply()
                            onLoginSuccess()

                        } catch (e: GetCredentialException) {
                            Log.e("Auth", "Error en Sign In", e)
                            Toast.makeText(context, "Configura tu Client ID en Google Cloud para la versión de Producción.", Toast.LENGTH_LONG).show()
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Icon(Icons.Rounded.Person, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Iniciar sesión con Google")
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            TextButton(
                onClick = { 
                    prefs.edit().putBoolean("is_guest", true).apply()
                    onLoginSuccess() 
                }
            ) {
                Text("Continuar sin cuenta")
            }
        }
    }
}
