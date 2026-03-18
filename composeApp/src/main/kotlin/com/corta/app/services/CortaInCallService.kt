package com.corta.app.services

import android.content.Intent
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.util.Log
import com.corta.app.ui.InCallActivity
import com.corta.domain.SpamEvaluator
import org.koin.android.ext.android.inject
import android.provider.ContactsContract
import android.net.Uri

class CortaInCallService : InCallService() {
    private val spamEvaluator: SpamEvaluator by inject()
    private var ringtone: Ringtone? = null

    override fun onCreate() {
        super.onCreate()
        CallManager.setService(this)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        CallManager.setService(null)
    }
    
    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        val phoneNumber = call.details.handle?.schemeSpecificPart ?: "Unknown"
        val spamAction = spamEvaluator.evaluateCall(phoneNumber).name
        
        // Intentar obtener el nombre del contacto
        val contactName = getContactName(phoneNumber)
        val displayName = contactName ?: phoneNumber
        
        Log.d("CortaInCallService", "Call added: $phoneNumber ($displayName), State: ${call.state}")
        
        CallManager.updateCall(call)

        if (call.state == Call.STATE_RINGING) {
            startRingtone()
        }
        call.registerCallback(object : Call.Callback() {
            override fun onStateChanged(call: Call, state: Int) {
                if (state != Call.STATE_RINGING) {
                    stopRingtone()
                }
            }
        })

        // Only launch UI if the call is not already disconnected
        if (call.state != Call.STATE_DISCONNECTED) {
            val intent = Intent(this, InCallActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("EXTRA_PHONE_NUMBER", displayName)
                // También pasamos el número original por si se necesita
                putExtra("EXTRA_RAW_PHONE_NUMBER", phoneNumber)
                putExtra("EXTRA_SPAM_ACTION", spamAction)
            }
            startActivity(intent)
        }
    }

    private fun getContactName(phoneNumber: String): String? {
        var name: String? = null
        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(phoneNumber)
        )
        val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)
        
        contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                if (nameIndex != -1) {
                    name = cursor.getString(nameIndex)
                }
            }
        }
        return name
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        Log.d("CortaInCallService", "Call removed")
        stopRingtone()
        if (CallManager.currentCall == call) {
            CallManager.clearCall()
        }
    }
    
    override fun onCallAudioStateChanged(audioState: CallAudioState?) {
        super.onCallAudioStateChanged(audioState)
        CallManager.onAudioStateChanged(audioState)
    }

    private fun startRingtone() {
        try {
            stopRingtone()
            val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ringtone = RingtoneManager.getRingtone(this, ringtoneUri)?.apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    isLooping = true
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    audioAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .build()
                }
                play()
            }
        } catch (e: Exception) {
            Log.e("CortaInCallService", "Unable to start ringtone", e)
        }
    }

    private fun stopRingtone() {
        try {
            ringtone?.stop()
            ringtone = null
        } catch (e: Exception) {
            Log.e("CortaInCallService", "Unable to stop ringtone", e)
        }
    }
}
