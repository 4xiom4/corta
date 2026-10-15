package com.corta.app.ui

import android.app.Application
import android.app.role.RoleManager
import android.content.Context
import android.os.Build
import com.corta.app.ui.theme.AccentColor
import com.corta.app.ui.theme.Handedness
import com.corta.app.ui.theme.ThemeMode
import com.corta.app.ui.theme.UiScale
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import com.corta.domain.ContactRepository
import com.corta.domain.CortaRepository

class CortaAppViewModel(
    application: Application,
    private val repository: CortaRepository,
    private val contactRepository: ContactRepository
) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _isFirstLaunch = MutableStateFlow(prefs.getBoolean(KEY_FIRST_LAUNCH, true))
    val isFirstLaunch: StateFlow<Boolean> = _isFirstLaunch.asStateFlow()

    private val _permissionsState = MutableStateFlow(computeOnboardingPermissionsState(application.applicationContext))
    val permissionsState: StateFlow<OnboardingPermissionsState> = _permissionsState.asStateFlow()

    private val initialRoleManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        application.getSystemService(Context.ROLE_SERVICE) as? RoleManager
    } else {
        null
    }

    private val _rolesState = MutableStateFlow(computeOnboardingRolesState(initialRoleManager))
    val rolesState: StateFlow<OnboardingRolesState> = _rolesState.asStateFlow()

    private val _debugMode = MutableStateFlow(prefs.getBoolean(KEY_DEBUG_MODE, false))
    val debugMode: StateFlow<Boolean> = _debugMode.asStateFlow()

    private val _detailedMode = MutableStateFlow(prefs.getBoolean(KEY_DETAILED_MODE, false))
    val detailedMode: StateFlow<Boolean> = _detailedMode.asStateFlow()

    private val _themeMode = MutableStateFlow(ThemeMode.valueOf(prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name))
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _accentColor = MutableStateFlow(AccentColor.fromPreference(prefs.getString(KEY_ACCENT_COLOR, null)))
    val accentColor: StateFlow<AccentColor> = _accentColor.asStateFlow()

    private val _uiScale = MutableStateFlow(UiScale.fromPreference(prefs.getString(KEY_UI_SCALE, UiScale.NORMAL.name)))
    val uiScale: StateFlow<UiScale> = _uiScale.asStateFlow()

    private val _handedness = MutableStateFlow(Handedness.fromPreference(prefs.getString(KEY_HANDEDNESS, Handedness.RIGHT.name)))
    val handedness: StateFlow<Handedness> = _handedness.asStateFlow()

    private val _language = MutableStateFlow(prefs.getString(KEY_LANGUAGE, "es") ?: "es")
    val language: StateFlow<String> = _language.asStateFlow()

    private val _launchState = MutableStateFlow(computeLaunchState(_isFirstLaunch.value, _permissionsState.value, _rolesState.value))
    val launchState: StateFlow<LaunchState> = _launchState.asStateFlow()

    init {
        refresh(null)
        syncContactsInBackground()
    }

    private fun syncContactsInBackground() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val contacts = contactRepository.getContacts()
                repository.syncContacts(contacts)
            } catch (e: Throwable) {
                Timber.tag(TAG).e(e, "Failed to sync contacts to local DB")
            }
        }
    }

    fun refresh(roleManager: RoleManager?) {
        viewModelScope.launch {
            _permissionsState.value = computeOnboardingPermissionsState(getApplication())
            _rolesState.value = computeOnboardingRolesState(roleManager)
            _launchState.value = computeLaunchState(_isFirstLaunch.value, _permissionsState.value, _rolesState.value)
        }
    }

    fun setFirstLaunchDone() {
        _isFirstLaunch.value = false
        prefs.edit().putBoolean(KEY_FIRST_LAUNCH, false).apply()
        _launchState.value = computeLaunchState(_isFirstLaunch.value, _permissionsState.value, _rolesState.value)
    }

    fun setDetailedMode(enabled: Boolean) {
        _detailedMode.value = enabled
        prefs.edit().putBoolean(KEY_DETAILED_MODE, enabled).apply()
        if (enabled != _debugMode.value) {
            _debugMode.value = enabled
            prefs.edit().putBoolean(KEY_DEBUG_MODE, enabled).apply()
        }
    }

    fun setDebugMode(enabled: Boolean) {
        _debugMode.value = enabled
        prefs.edit().putBoolean(KEY_DEBUG_MODE, enabled).apply()
        _detailedMode.value = enabled
        prefs.edit().putBoolean(KEY_DETAILED_MODE, enabled).apply()
    }

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
    }

    fun setAccentColor(color: AccentColor) {
        _accentColor.value = color
        prefs.edit().putString(KEY_ACCENT_COLOR, color.name).apply()
    }

    fun setUiScale(scale: UiScale) {
        _uiScale.value = scale
        prefs.edit().putString(KEY_UI_SCALE, scale.name).apply()
    }

    fun setHandedness(handedness: Handedness) {
        _handedness.value = handedness
        prefs.edit().putString(KEY_HANDEDNESS, handedness.name).apply()
    }

    fun setLanguage(language: String) {
        _language.value = language
        prefs.edit().putString(KEY_LANGUAGE, language).apply()
    }

    fun setRolesState(roleManager: RoleManager?) {
        _rolesState.value = computeOnboardingRolesState(roleManager)
        _launchState.value = computeLaunchState(_isFirstLaunch.value, _permissionsState.value, _rolesState.value)
    }

    private companion object {
        const val TAG = "Corta.AppViewModel"

        const val PREFS_NAME = "corta_prefs"
        const val KEY_FIRST_LAUNCH = "is_first_launch"
        const val KEY_DEBUG_MODE = "debug_mode"
        const val KEY_DETAILED_MODE = "detailed_mode"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_ACCENT_COLOR = "accent_color"
        const val KEY_UI_SCALE = "ui_scale"
        const val KEY_HANDEDNESS = "handedness"
        const val KEY_LANGUAGE = "language"
    }
}
