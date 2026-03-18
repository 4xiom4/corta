package com.corta.app.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaRecorder
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.corta.app.services.CallManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.*

class RecordingService : Service() {
    private var mediaRecorder: MediaRecorder? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val EXTRA_PHONE_NUMBER = "EXTRA_PHONE_NUMBER"
        const val EXTRA_LANGUAGE = "EXTRA_LANGUAGE"
        private const val CHANNEL_ID = "recording_service_channel"
        private const val NOTIFICATION_ID = 200
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val phoneNumber = intent.getStringExtra(EXTRA_PHONE_NUMBER) ?: "Unknown"
                val language = intent.getStringExtra(EXTRA_LANGUAGE) ?: "es"
                startRecording(phoneNumber, language)
            }
            ACTION_STOP -> stopRecording()
        }
        return START_NOT_STICKY
    }

    private fun startRecording(phoneNumber: String, language: String) {
        serviceScope.launch {
            try {
                val folder = File(getExternalFilesDir(Environment.DIRECTORY_MUSIC), "Recordings")
                if (!folder.exists()) folder.mkdirs()

                val timeStamp = SimpleDateFormat("dd_MM_yy_HH_mm", Locale.getDefault()).format(Date())
                val prefix = if (language == "es") "llamada" else "call"
                val fileName = "${prefix}_${phoneNumber}_${timeStamp}.mp3"
                val outputFile = File(folder, fileName)

                mediaRecorder = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    MediaRecorder(this@RecordingService)
                } else {
                    @Suppress("DEPRECATION") MediaRecorder()
                }).apply {
                    setAudioSource(MediaRecorder.AudioSource.MIC)
                    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    setOutputFile(outputFile.absolutePath)
                    prepare()
                    start()
                }

                withContext(Dispatchers.Main) {
                    CallManager.setIsRecording(true)
                    showNotification(phoneNumber)
                }
            } catch (e: Exception) {
                Log.e("RecordingService", "Failed to start recording", e)
                withContext(Dispatchers.Main) {
                    CallManager.setIsRecording(false)
                }
                stopSelf()
            }
        }
    }

    private fun stopRecording() {
        serviceScope.launch {
            try {
                mediaRecorder?.apply {
                    stop()
                    reset()
                    release()
                }
            } catch (e: Exception) {
                Log.e("RecordingService", "Error stopping recorder", e)
            } finally {
                mediaRecorder = null
                withContext(Dispatchers.Main) {
                    CallManager.setIsRecording(false)
                    stopForeground(true)
                    stopSelf()
                }
            }
        }
    }

    private fun showNotification(phoneNumber: String) {
        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Grabando llamada")
            .setContentText("Grabando conversación con $phoneNumber")
            .setSmallIcon(android.R.drawable.ic_menu_save)
            .setOngoing(true)
            .build()
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Grabación de Llamadas",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
