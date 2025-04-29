package com.prayag.flutter_workmanager_plugin.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.prayag.flutter_workmanager_plugin.domain.model.TableConfig
import com.prayag.flutter_workmanager_plugin.utils.ServiceLocator
import com.prayag.flutter_workmanager_plugin.data.datasource.remote.ApiCredentials
import kotlinx.coroutines.*
import java.io.File

class TaskMonitorService : Service() {

    private val TAG = "TaskMonitorService"
    private var retryCount = 0
    private val maxRetries = 5

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundService()

        val dbPath = intent?.getStringExtra("dbPath") ?: return START_NOT_STICKY
        val dbName = intent.getStringExtra("dbName") ?: return START_NOT_STICKY

        val dbQueryProgress = intent.getStringExtra("dbQueryProgress") ?: return START_NOT_STICKY
        val dbQueryPractice = intent.getStringExtra("dbQueryPractice") ?: return START_NOT_STICKY
        val dbQueryAttempts = intent.getStringExtra("dbQueryAttempts") ?: return START_NOT_STICKY
        val dbQuerySuperSync = intent.getStringExtra("dbQuerySuperSync") ?: return START_NOT_STICKY

        val dbInsertQueryProgress = intent.getStringExtra("dbInsertQueryProgress") ?: return START_NOT_STICKY
        val dbInsertQueryPractice = intent.getStringExtra("dbInsertQueryPractice") ?: return START_NOT_STICKY
        val dbInsertQueryAttempts = intent.getStringExtra("dbInsertQueryAttempts") ?: return START_NOT_STICKY
        val dbInsertQuerySuperSync = intent.getStringExtra("dbInsertQuerySuperSync") ?: return START_NOT_STICKY

        val apiRouteProgress = intent.getStringExtra("apiRouteProgress") ?: return START_NOT_STICKY
        val apiRoutePractice = intent.getStringExtra("apiRoutePractice") ?: return START_NOT_STICKY
        val apiRouteAttempts = intent.getStringExtra("apiRouteAttempts") ?: return START_NOT_STICKY
        val apiRouteSuperSync = intent.getStringExtra("apiRouteSuperSync") ?: return START_NOT_STICKY

        val fingerprint = intent.getStringExtra("fingerprint") ?: return START_NOT_STICKY
        val authorization = intent.getStringExtra("authorization") ?: return START_NOT_STICKY
        val x_package_id = intent.getStringExtra("x_package_id") ?: return START_NOT_STICKY
        val deviceType = intent.getStringExtra("deviceType") ?: return START_NOT_STICKY
        val version = intent.getStringExtra("version") ?: return START_NOT_STICKY

        val filePath = File(dbPath, dbName).absolutePath
        val dbFile = File(filePath)
        if (!dbFile.exists()) {
            Log.e(TAG, "Database not found: $filePath")
            stopSelf()
            return START_NOT_STICKY
        }

        val db = android.database.sqlite.SQLiteDatabase.openDatabase(filePath, null, android.database.sqlite.SQLiteDatabase.OPEN_READWRITE)

        val tableConfigs = listOf(
            TableConfig("progress", "lang_progress", dbQueryProgress, dbInsertQueryProgress, apiRouteProgress),
            TableConfig("practice", "lang_practice", dbQueryPractice, dbInsertQueryPractice, apiRoutePractice),
            TableConfig("attempts", "lang_attempts", dbQueryAttempts, dbInsertQueryAttempts, apiRouteAttempts),
            TableConfig("supersync", "lang_supersync", dbQuerySuperSync, dbInsertQuerySuperSync, apiRouteSuperSync)
        )

        val credentials = ApiCredentials(fingerprint, authorization, x_package_id, deviceType, version)

        serviceScope.launch {
            android.util.Log.d(TAG, "------------- fingerprint::: $fingerprint ")
            var success = false
            while (!success && retryCount < maxRetries) {
                try {
                    success = ServiceLocator.syncDataUseCase.execute(db, tableConfigs, credentials)
                    Log.d(TAG, "Attempt ${retryCount + 1}: success = $success")
                    retryCount++
                    if (!success && retryCount < maxRetries) delay(5000)
                } catch (e: Exception) {
                    Log.e(TAG, "Sync failed on attempt ${retryCount + 1}: ${e.message}", e)
                    retryCount++
                    delay(5000)
                }
            }
            db.close()
            stopSelf()
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundService() {
        val notification = NotificationCompat.Builder(this, "sync_channel")
            .setContentTitle("Syncing Users")
            .setContentText("Running initial sync...")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                "sync_channel",
                "User Sync",
                android.app.NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            manager.createNotificationChannel(channel)
        }

        startForeground(1, notification)
    }
}