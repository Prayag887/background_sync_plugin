package com.prayag.flutter_workmanager_plugin.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.prayag.flutter_workmanager_plugin.workmanager.DartCallbackWorker

class TaskMonitorService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground()
    }

    private fun startForeground() {
        val channelId = "task_monitor_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Task Monitor",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Ambition Guru")
            .setContentText("")
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        triggerCleanup()
    }

    private fun triggerCleanup() {
        Log.d("TAG", "App is closed (Task removed), scheduling Flutter task...")

        val prefs = getSharedPreferences("flutter_workmanager_plugin", MODE_PRIVATE)
        val callbackHandle = prefs.getLong("callback_handle", 0L)

        if (callbackHandle == 0L) {
            Log.e("TAG", "No valid callback handle found for Dart worker")
            return
        }

        val request = OneTimeWorkRequestBuilder<DartCallbackWorker>()
            .setInputData(workDataOf("callback_handle" to callbackHandle))
            .build()

        WorkManager.getInstance(applicationContext).enqueue(request)
    }

    companion object {
        private const val NOTIFICATION_ID = 1337
    }
}