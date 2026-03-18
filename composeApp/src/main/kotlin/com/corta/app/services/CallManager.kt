package com.corta.app.services

import android.content.Context
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.telecom.TelecomManager
import android.telecom.VideoProfile
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object CallManager {
    private const val TAG = "CallManager"
    private var inCallService: InCallService? = null
    
    var currentCall: Call? = null
        private set

    private val _callState = MutableStateFlow(Call.STATE_DISCONNECTED)
    val callState: StateFlow<Int> = _callState.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _isSpeakerphoneOn = MutableStateFlow(false)
    val isSpeakerphoneOn: StateFlow<Boolean> = _isSpeakerphoneOn.asStateFlow()
    
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _callDetailsUpdated = MutableStateFlow(0L)
    val callDetailsUpdated: StateFlow<Long> = _callDetailsUpdated.asStateFlow()

    private val callCallback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            super.onStateChanged(call, state)
            Log.d(TAG, "Call state changed: $state")
            _callState.value = state
            
            if (state == Call.STATE_SELECT_PHONE_ACCOUNT) {
                autoSelectPhoneAccount(call)
            }
            
            if (state == Call.STATE_DISCONNECTED) {
                clearCall()
            }
        }
        
        override fun onDetailsChanged(call: Call, details: Call.Details) {
            super.onDetailsChanged(call, details)
            _callDetailsUpdated.value = System.currentTimeMillis()
        }
    }

    private fun autoSelectPhoneAccount(call: Call) {
        try {
            val telecomManager = inCallService?.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
            val accounts = telecomManager?.callCapablePhoneAccounts
            if (!accounts.isNullOrEmpty()) {
                call.phoneAccountSelected(accounts[0], false)
                Log.d(TAG, "Auto-selected phone account: ${accounts[0].id}")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Failed to auto-select phone account", e)
        }
    }

    fun setService(service: InCallService?) {
        inCallService = service
    }

    fun updateCall(call: Call) {
        if (currentCall == call) return
        
        currentCall?.unregisterCallback(callCallback)
        currentCall = call
        if (call.state != Call.STATE_DISCONNECTED) {
            call.registerCallback(callCallback)
            _callState.value = call.state
            
            if (call.state == Call.STATE_SELECT_PHONE_ACCOUNT) {
                autoSelectPhoneAccount(call)
            }
        } else {
            clearCall()
        }
    }

    fun clearCall() {
        currentCall?.unregisterCallback(callCallback)
        currentCall = null
        _callState.value = Call.STATE_DISCONNECTED
        _isRecording.value = false
    }
    
    fun onAudioStateChanged(audioState: CallAudioState?) {
        if (audioState != null) {
            _isMuted.value = audioState.isMuted
            _isSpeakerphoneOn.value = audioState.route == CallAudioState.ROUTE_SPEAKER
        }
    }

    fun answer() {
        Log.d(TAG, "Answering call")
        currentCall?.answer(VideoProfile.STATE_AUDIO_ONLY)
    }

    fun reject() {
        Log.d(TAG, "Rejecting call")
        currentCall?.reject(false, null)
    }

    fun disconnect() {
        Log.d(TAG, "Disconnecting call")
        currentCall?.disconnect()
    }

    fun hold() {
        val state = currentCall?.state
        if (state == Call.STATE_ACTIVE) {
            Log.d(TAG, "Holding call")
            currentCall?.hold()
        } else if (state == Call.STATE_HOLDING) {
            Log.d(TAG, "Unholding call")
            currentCall?.unhold()
        }
    }

    fun setMute(muted: Boolean) {
        Log.d(TAG, "Setting mute: $muted")
        inCallService?.setMuted(muted)
        _isMuted.value = muted
    }

    fun setSpeakerphoneOn(on: Boolean) {
        Log.d(TAG, "Setting speakerphone: $on")
        val route = if (on) CallAudioState.ROUTE_SPEAKER else CallAudioState.ROUTE_EARPIECE
        inCallService?.setAudioRoute(route)
        _isSpeakerphoneOn.value = on
    }

    fun setIsRecording(recording: Boolean) {
        _isRecording.value = recording
    }

    fun playDtmfTone(digit: Char) {
        Log.d(TAG, "Playing DTMF tone: $digit")
        currentCall?.playDtmfTone(digit)
    }

    fun stopDtmfTone() {
        Log.d(TAG, "Stopping DTMF tone")
        currentCall?.stopDtmfTone()
    }
}
