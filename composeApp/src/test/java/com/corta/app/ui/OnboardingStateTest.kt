package com.corta.app.ui

import android.app.role.RoleManager
import org.junit.Assert.assertEquals
import org.junit.Test

class OnboardingStateTest {

    @Test
    fun `computeLaunchState returns LOGIN when first launch`() {
        val permissionsState = OnboardingPermissionsState(true, true, true)
        val rolesState = OnboardingRolesState(true, true, true)

        val actual = computeLaunchState(true, permissionsState, rolesState)

        assertEquals(LaunchState.LOGIN, actual)
    }

    @Test
    fun `computeLaunchState returns ONBOARDING when permissions missing`() {
        val permissionsState = OnboardingPermissionsState(false, false, false)
        val rolesState = OnboardingRolesState(true, true, true)

        val actual = computeLaunchState(false, permissionsState, rolesState)

        assertEquals(LaunchState.ONBOARDING, actual)
    }

    @Test
    fun `computeLaunchState returns DASHBOARD even when optional roles missing`() {
        val permissionsState = OnboardingPermissionsState(true, true, true)
        val rolesState = OnboardingRolesState(false, false, false)

        val actual = computeLaunchState(false, permissionsState, rolesState)

        assertEquals(LaunchState.DASHBOARD, actual)
    }

    @Test
    fun `computeLaunchState returns DASHBOARD when all conditions good`() {
        val permissionsState = OnboardingPermissionsState(true, true, true)
        val rolesState = OnboardingRolesState(true, true, true)

        val actual = computeLaunchState(false, permissionsState, rolesState)

        assertEquals(LaunchState.DASHBOARD, actual)
    }

    @Test
    fun `computeOnboardingRolesState returns false if roleManager null`() {
        val rolesState = computeOnboardingRolesState(null)

        assertEquals(false, rolesState.hasDialerRole)
        assertEquals(false, rolesState.hasCallScreeningRole)
        assertEquals(true, rolesState.hasSmsRole) // fallback para API<Q
    }
}
