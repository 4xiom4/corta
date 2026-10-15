package com.corta.app.services

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.provider.Telephony
import android.telecom.TelecomManager
import androidx.core.content.ContextCompat
import timber.log.Timber

object RuntimeStatusLogger {
    private const val TAG = "CortaRouting"

    fun logSnapshot(context: Context, source: String) {
        val prefs = context.getSharedPreferences("corta_prefs", Context.MODE_PRIVATE)
        val debugMode = prefs.getBoolean("debug_mode", false)
        if (!debugMode) return

        val canOverlay = Settings.canDrawOverlays(context)
        val notificationsReady = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        val isDefaultDialer = runCatching {
            val telecom = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            telecom.defaultDialerPackage == context.packageName
        }.getOrDefault(false)

        val isDefaultSms = runCatching {
            Telephony.Sms.getDefaultSmsPackage(context) == context.packageName
        }.getOrDefault(false)

        val roleManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.getSystemService(android.app.role.RoleManager::class.java)
        } else {
            null
        }

        val hasScreeningRole = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            roleManager?.isRoleHeld(android.app.role.RoleManager.ROLE_CALL_SCREENING) == true
        } else {
            false
        }

        Timber.tag(TAG).d(
            "[%s] defaultDialer=%s defaultSms=%s screeningRole=%s overlay=%s notifications=%s",
            source, isDefaultDialer, isDefaultSms, hasScreeningRole, canOverlay, notificationsReady,
        )
    }
}


