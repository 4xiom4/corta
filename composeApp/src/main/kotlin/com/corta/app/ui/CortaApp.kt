package com.corta.app.ui

import android.app.role.RoleManager
import android.os.Build
import android.provider.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import com.corta.app.ui.auth.LoginScreen
import com.corta.app.ui.i18n.CortaTheme
import org.koin.compose.KoinContext

import android.content.Context
import android.content.SharedPreferences
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext
import com.corta.app.ui.theme.AppTheme
import com.corta.app.ui.theme.ThemeMode
import com.corta.app.ui.theme.AccentColor

enum class LaunchState { LOGIN, ONBOARDING, DASHBOARD }

@Composable
fun CortaApp(roleManager: RoleManager, language: String = "en") {
    KoinContext {
        CortaTheme(language = language) {
            val context = LocalContext.current
            val prefs = remember { context.getSharedPreferences("corta_prefs", Context.MODE_PRIVATE) }
            
            var currentThemeMode by remember { mutableStateOf(ThemeMode.valueOf(prefs.getString("theme_mode", ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name)) }
            var currentAccentColor by remember { mutableStateOf(AccentColor.valueOf(prefs.getString("accent_color", AccentColor.DYNAMIC.name) ?: AccentColor.DYNAMIC.name)) }

            DisposableEffect(prefs) {
                val listener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
                    when(key) {
                        "theme_mode" -> currentThemeMode = ThemeMode.valueOf(sharedPreferences?.getString(key, ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name)
                        "accent_color" -> currentAccentColor = AccentColor.valueOf(sharedPreferences?.getString(key, AccentColor.DYNAMIC.name) ?: AccentColor.DYNAMIC.name)
                    }
                }
                prefs.registerOnSharedPreferenceChangeListener(listener)
                onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
            }

            AppTheme(themeMode = currentThemeMode, accentColor = currentAccentColor) {
                var isFirstLaunch by remember { mutableStateOf(prefs.getBoolean("is_first_launch", true)) }

                var corePermissionsGranted by remember { mutableStateOf(hasAllRequiredPermissions(context)) }
                var rolesGranted by remember {
                    mutableStateOf(
                        roleManager.isRoleHeld(RoleManager.ROLE_DIALER) &&
                        roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING) &&
                        (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || roleManager.isRoleHeld(RoleManager.ROLE_SMS))
                    )
                }

                var launchState by remember {
                    mutableStateOf(
                        when {
                            isFirstLaunch -> LaunchState.LOGIN
                            !corePermissionsGranted -> LaunchState.ONBOARDING
                            !rolesGranted -> LaunchState.ONBOARDING
                            else -> LaunchState.DASHBOARD
                        }
                    )
                }

                when (launchState) {
                    LaunchState.LOGIN -> {
                        LoginScreen(onLoginSuccess = { 
                            prefs.edit().putBoolean("is_first_launch", false).apply()
                            launchState = LaunchState.ONBOARDING 
                        })
                    }
                    LaunchState.ONBOARDING -> {
                        OnboardingScreen(
                            roleManager = roleManager,
                            onPermissionsGranted = { 
                                prefs.edit().putBoolean("is_first_launch", false).apply()
                                launchState = LaunchState.DASHBOARD 
                            }
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
