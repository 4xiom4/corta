package com.corta.app

import android.app.role.RoleManager
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.corta.app.ui.CortaApp

import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        val roleManager = getSystemService(Context.ROLE_SERVICE) as RoleManager
        val language = Locale.getDefault().language

        setContent {
            CortaApp(roleManager = roleManager, language = language)
        }
    }
}
