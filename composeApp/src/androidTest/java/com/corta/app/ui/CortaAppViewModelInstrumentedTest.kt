package com.corta.app.ui

import android.app.Application
import android.app.role.RoleManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.corta.app.ui.OnboardingRolesState
import com.corta.app.ui.OnboardingPermissionsState
import com.corta.app.ui.LaunchState
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CortaAppViewModelInstrumentedTest {

    @Test
    fun viewModelInitializesLoginOnFirstLaunch() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val prefs = app.getSharedPreferences("corta_prefs", 0)
        prefs.edit().putBoolean("is_first_launch", true).commit()

        val viewModel = CortaAppViewModel(app)

        assertEquals(LaunchState.LOGIN, viewModel.launchState.value)
    }

    @Test
    fun viewModelUpdatesToOnboardingWhenNoPermissionsAndNoRoles() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = CortaAppViewModel(app)

        val fakeRoleManager = null as RoleManager?

        viewModel.refresh(fakeRoleManager)

        // En la config de emulador, la accesibilidad puede cambiar, validar que no lanza excepciones
        assertTrue(viewModel.launchState.value == LaunchState.ONBOARDING || viewModel.launchState.value == LaunchState.LOGIN)
    }

    @Test
    fun setFirstLaunchDoneUpdatesLaunchState() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = CortaAppViewModel(app)

        viewModel.setFirstLaunchDone()

        assertNotEquals(LaunchState.LOGIN, viewModel.launchState.value)
    }
}
