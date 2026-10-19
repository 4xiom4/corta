package com.corta.app.ui.auth

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.corta.app.ui.contacts.ChileanRegion
import com.corta.app.ui.contacts.ChileanRegionalDirectory
import com.corta.app.ui.contacts.RegionSelectorBottomSheet
import com.corta.app.ui.i18n.Strings
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.launch
import timber.log.Timber

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit
) {
    val context = LocalContext.current
    val strings = Strings
    val coroutineScope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("corta_prefs", Context.MODE_PRIVATE) }
    val scrollState = rememberScrollState()

    var isGuest by remember { mutableStateOf(true) }
    var userName by remember { mutableStateOf(prefs.getString("user_name", "Usuario Corta") ?: "Usuario Corta") }
    var userGoogleEmail by remember { mutableStateOf(prefs.getString("user_google_email", "") ?: "") }
    var isGoogleLoading by remember { mutableStateOf(false) }

    var selectedRegion by remember {
        val savedCode = prefs.getString("user_selected_region", ChileanRegion.RM.code)
        mutableStateOf(ChileanRegion.fromCode(savedCode))
    }
    var selectedComuna by remember {
        val savedComuna = prefs.getString("user_selected_comuna", null)
        mutableStateOf(
            if (savedComuna != null && ChileanRegionalDirectory.isValidComunaForRegion(selectedRegion, savedComuna)) {
                savedComuna
            } else if (selectedRegion == ChileanRegion.RM && savedComuna == null) {
                "Santiago"
            } else {
                null
            }
        )
    }
    val effectiveComuna = remember(selectedRegion, selectedComuna) {
        if (selectedComuna != null && ChileanRegionalDirectory.isValidComunaForRegion(selectedRegion, selectedComuna)) {
            selectedComuna
        } else {
            null
        }
    }
    var showRegionPicker by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(WindowInsets.safeDrawing.asPaddingValues())
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Spacer(modifier = Modifier.height(12.dp))

                // Hero Shield Icon
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                    modifier = Modifier.size(76.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Shield,
                            contentDescription = "Corta Escudo",
                            modifier = Modifier.size(42.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "¡CORTA!",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = "«Con Corta la haces corta»",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(6.dp))

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                ) {
                    Text(
                        text = "🇨🇱 Telefonía Chilena Inteligente",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Section 1: Chile Location Setup (Essential for Comunas 14xx and regional directories)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                    ),
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.LocationOn,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = "Tu Comuna y Región",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Para seguridad 14xx, comisaría y directorio local",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Current Selected Location Button
                        val locationSummary = when {
                            selectedRegion == ChileanRegion.ALL -> "🇨🇱 Todo Chile (Todas las comunas)"
                            effectiveComuna != null -> "📍 $effectiveComuna (${selectedRegion.displayName})"
                            else -> "📍 ${selectedRegion.displayName}"
                        }

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showRegionPicker = true },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = locationSummary,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Toca para cambiar de comuna o región",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Rounded.ArrowDropDown,
                                    contentDescription = "Abrir selector",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Quick Chips for Fastest Selection
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilterChip(
                                selected = selectedRegion == ChileanRegion.RM && selectedComuna == "Santiago",
                                onClick = {
                                    selectedRegion = ChileanRegion.RM
                                    selectedComuna = "Santiago"
                                },
                                label = { Text("Santiago (RM)", fontSize = 11.sp) }
                            )
                            FilterChip(
                                selected = selectedRegion == ChileanRegion.RM && selectedComuna == "Providencia",
                                onClick = {
                                    selectedRegion = ChileanRegion.RM
                                    selectedComuna = "Providencia"
                                },
                                label = { Text("Providencia", fontSize = 11.sp) }
                            )
                            FilterChip(
                                selected = selectedRegion == ChileanRegion.ALL,
                                onClick = {
                                    selectedRegion = ChileanRegion.ALL
                                    selectedComuna = null
                                },
                                label = { Text("Todo Chile", fontSize = 11.sp) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Section 2: Identity Setup (Google or Guest)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Tu Perfil de Usuario",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        if (userGoogleEmail.isNotBlank()) {
                            // Connected with Google
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Rounded.AccountCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = userName,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            text = userGoogleEmail,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    TextButton(onClick = {
                                        userGoogleEmail = ""
                                        isGuest = true
                                    }) {
                                        Text("Desvincular", fontSize = 11.sp)
                                    }
                                }
                            }
                        } else {
                            // Guest or Google One-Tap
                            OutlinedTextField(
                                value = userName,
                                onValueChange = { userName = it },
                                label = { Text("Nombre o Apodo") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedButton(
                                onClick = {
                                    isGoogleLoading = true
                                    coroutineScope.launch {
                                        try {
                                            val credentialManager = CredentialManager.create(context)
                                            val googleIdOption = GetGoogleIdOption.Builder()
                                                .setFilterByAuthorizedAccounts(false)
                                                .setServerClientId("corta-app-client-id")
                                                .setAutoSelectEnabled(true)
                                                .build()
                                            val request = GetCredentialRequest.Builder()
                                                .addCredentialOption(googleIdOption)
                                                .build()
                                            val result = credentialManager.getCredential(context, request)
                                            val credential = result.credential
                                            val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data)
                                            userGoogleEmail = googleIdToken.id
                                            userName = googleIdToken.displayName ?: userName
                                            isGuest = false
                                            Toast.makeText(context, "Conectado con Google: $userName", Toast.LENGTH_SHORT).show()
                                        } catch (e: GetCredentialException) {
                                            Timber.tag(TAG).w(e, "Google Sign-In cancelado o falló: ${e.message}")
                                            // Fallback local sin bloqueo
                                            isGuest = true
                                        } catch (e: Exception) {
                                            Timber.tag(TAG).e(e, "Error al iniciar con Google")
                                            isGuest = true
                                        } finally {
                                            isGoogleLoading = false
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                if (isGoogleLoading) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.AutoMirrored.Rounded.Login, null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Vincular Cuenta de Google", fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Value Cards (SUBTEL 809 & Lista Blanca)
                FeatureValueCard(
                    icon = Icons.Rounded.Gavel,
                    title = "Normativa SUBTEL 809",
                    description = "Bloqueo nativo al primer tono de llamadas comerciales y telemarketing masivo."
                )

                Spacer(modifier = Modifier.height(8.dp))

                FeatureValueCard(
                    icon = Icons.Rounded.HealthAndSafety,
                    title = "Lista Blanca Inmutable (Chile)",
                    description = "Emergencias (131, 132, 133), Seguridad Comunal (14xx) y Salud Responde siempre permitidas por ley."
                )
            }

            // Bottom Actions: Start Protection
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Button(
                    onClick = {
                        val editor = prefs.edit()
                        editor.putString("user_selected_region", selectedRegion.code)
                        if (effectiveComuna != null && selectedRegion != ChileanRegion.ALL) {
                            editor.putString("user_selected_comuna", effectiveComuna)
                        } else {
                            editor.remove("user_selected_comuna")
                        }
                        editor.putString("user_name", userName.ifBlank { "Usuario Corta" })
                        editor.putString("user_google_email", userGoogleEmail)
                        editor.putBoolean("is_guest", isGuest)
                        editor.apply()

                        onLoginSuccess()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = MaterialTheme.shapes.large,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(
                        text = "Comenzar y Proteger mi Teléfono",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "🔒 Evaluación 100% On-Device en <2ms · Privacidad Absoluta",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (showRegionPicker) {
        RegionSelectorBottomSheet(
            selectedRegion = selectedRegion,
            selectedComuna = selectedComuna,
            onSelectLocation = { region, comuna ->
                selectedRegion = region
                selectedComuna = comuna
                showRegionPicker = false
            },
            onDismiss = { showRegionPicker = false }
        )
    }
}

@Composable
private fun FeatureValueCard(
    icon: ImageVector,
    title: String,
    description: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                modifier = Modifier.size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

private const val TAG = "Corta.Auth"
