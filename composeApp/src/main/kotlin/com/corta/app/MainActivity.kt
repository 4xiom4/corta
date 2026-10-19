package com.corta.app

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import com.corta.app.ui.CortaApp
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val targetTabState = mutableStateOf<Int?>(null)
    private val targetSmsSenderState = mutableStateOf<String?>(null)
    private val targetCallNumberState = mutableStateOf<String?>(null)
    private val donationApprovedState = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)

        val roleManager = getSystemService(Context.ROLE_SERVICE) as RoleManager
        val language = Locale.getDefault().language

        setContent {
            val targetTab by targetTabState
            val targetSmsSender by targetSmsSenderState
            val targetCallNumber by targetCallNumberState
            val donationApproved by donationApprovedState

            CortaApp(
                roleManager = roleManager,
                language = language,
                initialTargetTab = targetTab,
                initialSmsSender = targetSmsSender,
                initialCallNumber = targetCallNumber,
                donationApproved = donationApproved,
                onClearDeepLink = {
                    targetTabState.value = null
                    targetSmsSenderState.value = null
                    targetCallNumberState.value = null
                    donationApprovedState.value = false
                }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return

        if (intent.hasExtra("EXTRA_TARGET_TAB")) {
            targetTabState.value = intent.getIntExtra("EXTRA_TARGET_TAB", 0)
        }
        if (intent.hasExtra("EXTRA_SMS_SENDER")) {
            targetSmsSenderState.value = intent.getStringExtra("EXTRA_SMS_SENDER")
        }
        if (intent.hasExtra("EXTRA_CALL_NUMBER")) {
            targetCallNumberState.value = intent.getStringExtra("EXTRA_CALL_NUMBER")
        }

        // Manejo de Deep Link de retorno de donación (ej. corta://donation?status=approved)
        val data = intent.data
        if (data != null && data.scheme == "corta" && data.host == "donation") {
            val status = data.getQueryParameter("status") ?: data.getQueryParameter("collection_status")
            if (status == "approved" || status == "success") {
                donationApprovedState.value = true
                val prefs = getSharedPreferences("corta_prefs", Context.MODE_PRIVATE)
                prefs.edit().putBoolean("donor_badge_unlocked", true).apply()
            }
        }
    }
}
