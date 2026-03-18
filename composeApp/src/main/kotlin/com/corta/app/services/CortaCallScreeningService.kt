package com.corta.app.services

import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log
import com.corta.domain.CallValidator
import com.corta.domain.FilterAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class CortaCallScreeningService : CallScreeningService() {

    private val validator: CallValidator by inject()
    private val repository: com.corta.domain.CortaRepository by inject()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onScreenCall(callDetails: Call.Details) {
        val phoneNumber = callDetails.handle?.schemeSpecificPart ?: ""
        
        scope.launch {
            val startTime = System.currentTimeMillis()
            val action = validator.validateIncomingCall(phoneNumber)
            val elapsed = System.currentTimeMillis() - startTime
            Log.d("CortaCallScreening", "Evaluation took ${elapsed}ms for $phoneNumber: $action")

            val response = CallResponse.Builder()
            when (action) {
                FilterAction.BLOCK -> {
                    response.setDisallowCall(true)
                    response.setRejectCall(true)
                    response.setSkipCallLog(false)
                    response.setSkipNotification(true)
                    repository.logCall(phoneNumber, System.currentTimeMillis(), action)
                }
                FilterAction.MUTE -> {
                    response.setSilenceCall(true)
                    response.setSkipNotification(false)
                    repository.logCall(phoneNumber, System.currentTimeMillis(), action)
                }
                FilterAction.WARN, FilterAction.ALLOW -> {
                    // For WARN, we let it ring but track state
                    response.setSilenceCall(false)
                    repository.logCall(phoneNumber, System.currentTimeMillis(), action)
                }
            }
            respondToCall(callDetails, response.build())
        }
    }
}
