package com.corta.app.ui

import android.app.role.RoleManager
import android.os.Build
import android.provider.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import com.corta.app.ui.auth.LoginScreen
import com.corta.app.ui.i18n.CortaTheme
import com.corta.app.ui.i18n.Strings
import com.corta.app.ui.theme.*
import org.koin.compose.KoinContext
import org.koin.androidx.compose.koinViewModel

import android.content.Context
import android.content.SharedPreferences
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext
import com.corta.app.ui.theme.AccentColor
import com.corta.app.ui.theme.Handedness
import com.corta.app.ui.theme.ThemeMode
import com.corta.app.ui.theme.AppTheme

enum class LaunchState { LOGIN, ONBOARDING, DASHBOARD }

@Composable
fun CortaApp(roleManager: RoleManager, language: String = "en") {
    val viewModel: CortaAppViewModel = koinViewModel()

    LaunchedEffect(roleManager) {
        viewModel.refresh(roleManager)
    }

    val launchState by viewModel.launchState.collectAsState()
    val permissionsState by viewModel.permissionsState.collectAsState()
    val rolesState by viewModel.rolesState.collectAsState()
    val currentLanguage by viewModel.language.collectAsState()

    KoinContext {
        CortaTheme(language = currentLanguage) {
            val strings = Strings.common
            val currentThemeMode by viewModel.themeMode.collectAsState()
            val currentAccentColor by viewModel.accentColor.collectAsState()
            val currentUiScale by viewModel.uiScale.collectAsState()
            val currentHandedness by viewModel.handedness.collectAsState()
            val currentDetailedMode by viewModel.detailedMode.collectAsState()

            AppTheme(
                themeMode = currentThemeMode,
                accentColor = currentAccentColor,
                uiScale = currentUiScale,
                handedness = currentHandedness,
                detailedMode = currentDetailedMode
            ) {
                when (launchState) {
                    LaunchState.LOGIN -> {
                        LoginScreen(onLoginSuccess = {
                            viewModel.setFirstLaunchDone()
                        })
                    }
                    LaunchState.ONBOARDING -> {
                        OnboardingScreen(
                            roleManager = roleManager,
                            permissionsState = permissionsState,
                            rolesState = rolesState,
                            onPermissionsGranted = {
                                viewModel.setFirstLaunchDone()
                            },
                            onRefresh = { viewModel.refresh(roleManager) }
                        )
                    }
                    LaunchState.DASHBOARD -> {
                        DashboardScreen()
                    }
                }
            }
        }
    }
}

private fun hasAllRequiredPermissions(context: Context): Boolean {
    // Basic runtime permissions (Dangerous permissions)
    val standardPermissions = mutableListOf(
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.WRITE_CALL_LOG,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.WRITE_CONTACTS,
        Manifest.permission.CALL_PHONE,
        Manifest.permission.ANSWER_PHONE_CALLS,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.READ_SMS,
        Manifest.permission.SEND_SMS,
        Manifest.permission.RECEIVE_SMS,
        Manifest.permission.RECEIVE_MMS,
        Manifest.permission.RECEIVE_WAP_PUSH
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
        // Storage permissions (Legacy or scoped)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    val standardGranted = standardPermissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    // Special permissions check (Settings based)
    val canDrawOverlays = Settings.canDrawOverlays(context)

    return standardGranted && canDrawOverlays
}
