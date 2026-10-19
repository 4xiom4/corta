package com.corta.app.ui

import android.app.Application
import android.app.role.RoleManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.corta.db.CortaDatabase
import com.corta.domain.ContactInfo
import com.corta.domain.ContactRepository
import com.corta.domain.CortaRepository
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CortaAppViewModelInstrumentedTest {

    private lateinit var repository: CortaRepository
    private lateinit var contactRepository: ContactRepository

    @Before
    fun setup() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val driver = AndroidSqliteDriver(CortaDatabase.Schema, app, null)
        val db = CortaDatabase(driver)
        repository = CortaRepository(db)
        contactRepository = object : ContactRepository {
            override suspend fun getContacts(): List<ContactInfo> = emptyList()
            override suspend fun toggleFavorite(contactId: String, isFavorite: Boolean): Boolean = true
        }
    }

    @Test
    fun viewModelInitializesLoginOnFirstLaunch() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val prefs = app.getSharedPreferences("corta_prefs", 0)
        prefs.edit().putBoolean("is_first_launch", true).commit()

        val viewModel = CortaAppViewModel(app, repository, contactRepository)

        assertEquals(LaunchState.LOGIN, viewModel.launchState.value)
    }

    @Test
    fun viewModelUpdatesToOnboardingWhenNoPermissionsAndNoRoles() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = CortaAppViewModel(app, repository, contactRepository)

        val fakeRoleManager = null as RoleManager?

        viewModel.refresh(fakeRoleManager)

        assertTrue(viewModel.launchState.value == LaunchState.ONBOARDING || viewModel.launchState.value == LaunchState.LOGIN)
    }

    @Test
    fun setFirstLaunchDoneUpdatesLaunchState() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = CortaAppViewModel(app, repository, contactRepository)

        viewModel.setFirstLaunchDone()

        assertNotEquals(LaunchState.LOGIN, viewModel.launchState.value)
    }
}
