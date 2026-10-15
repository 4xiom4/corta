package com.corta.app.services

import android.content.Context
import android.telecom.Call
import android.telecom.CallScreeningService
import com.corta.domain.CallValidator
import com.corta.domain.FilterAction
import kotlinx.coroutines.CoroutineScope
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.android.ext.android.inject
import timber.log.Timber

class CortaCallScreeningService : CallScreeningService() {

    private val validator: CallValidator by inject()
    private val repository: com.corta.domain.CortaRepository by inject()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val prefs by lazy { applicationContext.getSharedPreferences("corta_prefs", Context.MODE_PRIVATE) }

    private fun isDebugMode(): Boolean {
        return prefs.getBoolean("debug_mode", false)
    }

    override fun onScreenCall(callDetails: Call.Details) {
        RuntimeStatusLogger.logSnapshot(applicationContext, "CallScreening")
        val rawNumber = callDetails.handle?.schemeSpecificPart?.trim() ?: ""
        val isPrivateNumber = rawNumber.isBlank() ||
            rawNumber.equals("desconocido", ignoreCase = true) ||
            rawNumber.equals("privado", ignoreCase = true) ||
            rawNumber.equals("unknown", ignoreCase = true) ||
            rawNumber.equals("private", ignoreCase = true) ||
            rawNumber.equals("restricted", ignoreCase = true) ||
            rawNumber == "-1" || rawNumber == "-2"
        val blockPrivateNumbers = prefs.getBoolean("block_private_numbers", false)
        val silenceWarnCalls = prefs.getBoolean("silence_warn_calls", false)
        val onlyContacts = prefs.getBoolean("only_contacts_calls", false)
        val blockInternational = prefs.getBoolean("block_international_calls", false)
        val chao809Aggressive = prefs.getBoolean("chao_809_aggressive", true)

        val logNumber = if (isPrivateNumber) "Número Oculto" else rawNumber

        scope.launch {
            val startTime = System.currentTimeMillis()
            val action = if (isPrivateNumber && blockPrivateNumbers) {
                FilterAction.BLOCK
            } else {
                withTimeoutOrNull(2000L) {
                    validator.validateIncomingCall(rawNumber, onlyContacts, blockInternational, chao809Aggressive)
                } ?: run {
                    Timber.tag(TAG).w("Call evaluation timed out for %s; falling back to ALLOW", logNumber)
                    FilterAction.ALLOW
                }
            }
            val elapsed = System.currentTimeMillis() - startTime
            Timber.tag(TAG).d("Evaluation for %s: %s in %dms", logNumber, action, elapsed)

            val response = CallResponse.Builder()
            when (action) {
                FilterAction.BLOCK -> {
                    response.setDisallowCall(true)
                    response.setRejectCall(true)
                    response.setSkipCallLog(false)
                    response.setSkipNotification(true)
                    repository.logCall(logNumber, System.currentTimeMillis(), action)
                    if (!isPrivateNumber) {
                        repository.queueSpamReport(logNumber, "CALL", "AUTO_BLOCK")
                    }
                    CallAlertNotifier.notifyAction(applicationContext, logNumber, CallAction.BLOCKED)
                }
                FilterAction.MUTE -> {
                    response.setSilenceCall(true)
                    response.setSkipNotification(false)
                    repository.logCall(logNumber, System.currentTimeMillis(), action)
                    if (!isPrivateNumber) {
                        repository.queueSpamReport(logNumber, "CALL", "AUTO_MUTE")
                    }
                    CallAlertNotifier.notifyAction(applicationContext, logNumber, CallAction.MUTED)
                }
                FilterAction.WARN -> {
                    response.setSilenceCall(silenceWarnCalls)
                    repository.logCall(logNumber, System.currentTimeMillis(), action)
                    if (!isPrivateNumber) {
                        repository.queueSpamReport(logNumber, "CALL", "AUTO_WARN")
                    }
                    CallAlertNotifier.notifyAction(applicationContext, logNumber, CallAction.WARNED)
                }
                FilterAction.ALLOW -> {
                    response.setSilenceCall(false)
                    repository.logCall(logNumber, System.currentTimeMillis(), action)
                }
            }
            respondToCall(callDetails, response.build())
        }
    }

    private fun formatTimestamp(timeMs: Long): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        return formatter.format(Date(timeMs))
    }

    private companion object {
        const val TAG = "Corta.CallScreening"
    }
}

