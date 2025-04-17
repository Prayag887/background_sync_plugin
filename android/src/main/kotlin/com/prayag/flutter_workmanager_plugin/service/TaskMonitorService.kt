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
import com.prayag.flutter_workmanager_plugin.workmanager.CopyUserDataWorker

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
        Log.d("TAG", "App is closed (Task removed), syncing data...")

        // Enqueue the worker to copy data from users to users_copy
        val copyWorkRequest = OneTimeWorkRequestBuilder<CopyUserDataWorker>().build()
        WorkManager.getInstance(applicationContext).enqueue(copyWorkRequest)
    }

    companion object {
        private const val NOTIFICATION_ID = 1337
    }
}
