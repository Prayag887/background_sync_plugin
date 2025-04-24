package com.prayag.flutter_workmanager_plugin.viewmodels

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.prayag.flutter_workmanager_plugin.data.DatabaseExtractor
import com.prayag.flutter_workmanager_plugin.service.UserSyncWorker
import io.flutter.plugin.common.MethodCall
import java.util.concurrent.TimeUnit

class PluginViewModel {
    private var dbPath: String? = null
    private var dbName: String? = null
    private var dbQueryProgress: String? = null
    private var dbQueryPractice: String? = null
    private var dbQueryAttempts: String? = null
    private var dbQuerySuperSync: String? = null

    fun getLifecycleCallbacks(context: Context): Application.ActivityLifecycleCallbacks {
        return object : Application.ActivityLifecycleCallbacks {
            override fun onActivityDestroyed(activity: Activity) {
                try {
                    DatabaseExtractor.extractAndSendDatabase(context, dbPath, dbName, dbQueryProgress, dbQueryPractice, dbQueryAttempts, dbQuerySuperSync)
                } catch (e: Exception) {
                    Log.e("TAG", "Error in onDestroy: ${e.message}")
                }
            }

            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
        }
    }

    fun updateDbConfig(call: MethodCall) {
        dbPath = call.argument("dbPath") ?: ""
        dbName = call.argument("dbName") ?: ""
        dbQueryProgress = call.argument("dbQueryProgress") ?: ""
        dbQueryPractice = call.argument("dbQueryPractice") ?: ""
        dbQueryAttempts = call.argument("dbQueryAttempts") ?: ""
        dbQuerySuperSync = call.argument("dbQuerySuperSync") ?: ""
    }

    fun startMonitoringService(context: Context) {
        scheduleUserSyncWork(context)
    }

    private fun scheduleUserSyncWork(context: Context) {
        // Cancel any existing sync work first
        WorkManager.getInstance(context).cancelAllWorkByTag("user_sync_work")

        val data = workDataOf(
            "dbPath" to dbPath,
            "dbName" to dbName
        )

        val syncWork = OneTimeWorkRequestBuilder<UserSyncWorker>()
            .setInputData(data)
            .addTag("user_sync_work")
            .build()

        WorkManager.getInstance(context).enqueue(syncWork)
    }
}