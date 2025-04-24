package com.prayag.flutter_workmanager_plugin.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.prayag.flutter_workmanager_plugin.workmanager.DartCallbackWorker

class TaskMonitorService : Service() {

    private val handler = Handler()
    private val interval: Long = 5000 // 5 seconds

    private val syncRunnable = object : Runnable {
        override fun run() {
            Thread {
                try {
                    val url = java.net.URL("https://jsonplaceholder.typicode.com/todos/1")
                    val connection = url.openConnection() as java.net.HttpURLConnection
                    connection.requestMethod = "GET"
                    connection.connectTimeout = 5000
                    connection.readTimeout = 5000

                    val responseCode = connection.responseCode
                    val responseMessage = connection.inputStream.bufferedReader().use { it.readText() }

                    Log.d("TaskMonitorService", "API Response Code: $responseCode")
                    Log.d("TaskMonitorService", "API Response Body: $responseMessage")

                    if (responseCode == 200) {
                        val prefs = getSharedPreferences("flutter_workmanager_plugin", Context.MODE_PRIVATE)
                        val callbackHandle = prefs.getLong("callback_handle", 0L)

                        if (callbackHandle != 0L) {
                            val request = OneTimeWorkRequestBuilder<DartCallbackWorker>()
                                .setInputData(workDataOf("callback_handle" to callbackHandle))
                                .build()

                            WorkManager.getInstance(applicationContext).enqueue(request)
                        }
                    }

                    connection.disconnect()
                } catch (e: Exception) {
                    Log.e("TaskMonitorService", "Error hitting API: ${e.message}", e)
                }
            }.start()

            handler.postDelayed(this, interval)
        }
    }


    override fun onCreate() {
        super.onCreate()
        startForegroundService()
        handler.post(syncRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(syncRunnable)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundService() {
        val notification = NotificationCompat.Builder(this, "sync_channel")
            .setContentTitle("Data Sync Active")
            .setContentText("Syncing data every 5 seconds...")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                "sync_channel",
                "Data Sync",
                android.app.NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(android.app.NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        startForeground(1, notification)
    }
}
