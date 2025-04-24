package com.prayag.flutter_workmanager_plugin.viewmodels

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import com.prayag.flutter_workmanager_plugin.data.DatabaseExtractor
import com.prayag.flutter_workmanager_plugin.service.TaskMonitorService
import io.flutter.plugin.common.MethodCall

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
        val intent = Intent(context, TaskMonitorService::class.java)
        intent.putExtra("dbPath", dbPath)
        intent.putExtra("dbName", dbName)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }
}
