package com.corta.app.data

import android.content.Context
import com.corta.domain.CommunityAccessPolicy

class PrefsCommunityAccessPolicy(private val context: Context) : CommunityAccessPolicy {
    override fun canUseCommunityRules(): Boolean {
        val prefs = context.getSharedPreferences("corta_prefs", Context.MODE_PRIVATE)
        val isGuest = prefs.getBoolean("is_guest", true)
        val isDonor = prefs.getBoolean("is_donor", false)
        return !isGuest && isDonor
    }
}

