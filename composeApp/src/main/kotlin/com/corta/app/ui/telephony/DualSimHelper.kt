package com.corta.app.ui.telephony

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat

data class ActiveSimCard(
    val slotIndex: Int,          // 0 = SIM 1, 1 = SIM 2
    val subscriptionId: Int,    // subId (e.g. 1, 2)
    val carrierName: String,     // e.g. "WOM", "Entel"
    val displayName: String,     // e.g. "WOM", "SIM 1"
    val number: String,
    val phoneAccountHandle: PhoneAccountHandle? = null
)

object DualSimHelper {

    fun getActiveSimCards(context: Context): List<ActiveSimCard> {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) return emptyList()

        return try {
            val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
            
            val callAccounts = try {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                    telecomManager?.callCapablePhoneAccounts ?: emptyList()
                } else emptyList()
            } catch (e: Exception) {
                emptyList()
            }

            val list = subscriptionManager?.activeSubscriptionInfoList ?: emptyList()
            list.map { subInfo ->
                val handle = callAccounts.firstOrNull { 
                    it.id == subInfo.subscriptionId.toString() || 
                    it.id.contains(subInfo.subscriptionId.toString()) 
                }
                ActiveSimCard(
                    slotIndex = subInfo.simSlotIndex,
                    subscriptionId = subInfo.subscriptionId,
                    carrierName = subInfo.carrierName?.toString()?.ifBlank { null }
                        ?: subInfo.displayName?.toString()
                        ?: "SIM ${subInfo.simSlotIndex + 1}",
                    displayName = subInfo.displayName?.toString() ?: "SIM ${subInfo.simSlotIndex + 1}",
                    number = subInfo.number ?: "",
                    phoneAccountHandle = handle
                )
            }.sortedBy { it.slotIndex }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun makeCall(context: Context, phoneNumber: String, sim: ActiveSimCard? = null) {
        val uri = Uri.parse("tel:${Uri.encode(phoneNumber)}")
        val intent = Intent(Intent.ACTION_DIAL, uri).apply {
            if (sim != null) {
                putExtra("com.android.phone.force.slot", true)
                putExtra("simSlot", sim.slotIndex)
                putExtra("subscription", sim.subscriptionId)
                putExtra("subId", sim.subscriptionId)
                putExtra("android.telephony.extra.SUBSCRIPTION_INDEX", sim.subscriptionId)
                sim.phoneAccountHandle?.let {
                    putExtra("android.telecom.extra.PHONE_ACCOUNT_HANDLE", it)
                }
            }
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    fun sendSms(context: Context, phoneNumber: String, sim: ActiveSimCard? = null) {
        val uri = Uri.parse("smsto:${Uri.encode(phoneNumber)}")
        val intent = Intent(Intent.ACTION_SENDTO, uri).apply {
            if (sim != null) {
                putExtra("simSlot", sim.slotIndex)
                putExtra("subscription", sim.subscriptionId)
                putExtra("subId", sim.subscriptionId)
                putExtra("android.telephony.extra.SUBSCRIPTION_INDEX", sim.subscriptionId)
            }
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }
}
