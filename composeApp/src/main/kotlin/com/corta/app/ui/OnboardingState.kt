package com.corta.app.ui

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

data class OnboardingPermissionsState(
    val hasCriticalPermissions: Boolean,
    val hasAllStandardPermissions: Boolean,
    val hasSpecialPermissions: Boolean
)

data class OnboardingRolesState(
    val hasDialerRole: Boolean,
    val hasCallScreeningRole: Boolean,
    val hasSmsRole: Boolean
)

fun computeOnboardingPermissionsState(context: Context): OnboardingPermissionsState {
    val criticalPermissions = listOf(
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.WRITE_CALL_LOG,
        Manifest.permission.READ_SMS,
        Manifest.permission.SEND_SMS,
        Manifest.permission.RECEIVE_SMS
    )

    val optionalPermissions = mutableListOf(
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.WRITE_CONTACTS,
        Manifest.permission.CALL_PHONE,
        Manifest.permission.ANSWER_PHONE_CALLS,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.RECEIVE_MMS,
        Manifest.permission.RECEIVE_WAP_PUSH
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    val allStandardPermissions = criticalPermissions + optionalPermissions

    val criticalGranted = criticalPermissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
    val allGranted = allStandardPermissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
    val specialGranted = Settings.canDrawOverlays(context)

    return OnboardingPermissionsState(
        hasCriticalPermissions = criticalGranted,
        hasAllStandardPermissions = allGranted,
        hasSpecialPermissions = specialGranted
    )
}

fun computeOnboardingRolesState(roleManager: RoleManager?): OnboardingRolesState {
    fun isHeld(role: String): Boolean {
        return if (roleManager == null) {
            false
        } else {
            try {
                !roleManager.isRoleAvailable(role) || roleManager.isRoleHeld(role)
            } catch (e: Exception) {
                false
            }
        }
    }

    val hasDialer = isHeld(RoleManager.ROLE_DIALER)
    val hasCallScreening = isHeld(RoleManager.ROLE_CALL_SCREENING)
    val hasSms = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        true
    } else {
        isHeld(RoleManager.ROLE_SMS)
    }

    return OnboardingRolesState(
        hasDialerRole = hasDialer,
        hasCallScreeningRole = hasCallScreening,
        hasSmsRole = hasSms
    )
}

fun computeLaunchState(
    isFirstLaunch: Boolean,
    permissionsState: OnboardingPermissionsState,
    rolesState: OnboardingRolesState
): LaunchState {
    return when {
        isFirstLaunch -> LaunchState.LOGIN
        !permissionsState.hasCriticalPermissions -> LaunchState.ONBOARDING
        else -> LaunchState.DASHBOARD
    }
}
