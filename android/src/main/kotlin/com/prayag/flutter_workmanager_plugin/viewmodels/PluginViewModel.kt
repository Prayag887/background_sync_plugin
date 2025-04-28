package com.prayag.flutter_workmanager_plugin.viewmodels

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.prayag.flutter_workmanager_plugin.data.DatabaseExtractor
import com.prayag.flutter_workmanager_plugin.workmanager.UserSyncWorker
import com.prayag.flutter_workmanager_plugin.service.TaskMonitorService
import io.flutter.plugin.common.MethodCall

class PluginViewModel {
    private var dbPath: String? = null
    private var dbName: String? = null
    private var dbQueryProgress: String? = null
    private var dbQueryPractice: String? = null
    private var dbQueryAttempts: String? = null
    private var dbQuerySuperSync: String? = null

    private var apiRouteProgress: String? = null
    private var apiRoutePractice: String? = null
    private var apiRouteAttempts: String? = null
    private var apiRouteSuperSync: String? = null
    private var fingerprint: String? = null
    private var authorization: String? = null
    private var x_package_id: String? = null
    private var deviceType: String? = null
    private var version: String? = null

    fun getLifecycleCallbacks(context: Context): Application.ActivityLifecycleCallbacks {
        return object : Application.ActivityLifecycleCallbacks {
            override fun onActivityDestroyed(activity: Activity) {
                try {
                    DatabaseExtractor.extractAndSendDatabase(context, dbPath, dbName, dbQueryProgress, dbQueryPractice, dbQueryAttempts, dbQuerySuperSync)

                    val intent = Intent(context, TaskMonitorService::class.java)
                    // database details and queries
                    intent.putExtra("dbPath", dbPath)
                    intent.putExtra("dbName", dbName)
                    intent.putExtra("dbQueryProgress", dbQueryProgress)
                    intent.putExtra("apiRoutePractice", apiRoutePractice)
                    intent.putExtra("dbQueryAttempts", dbQueryAttempts)
                    intent.putExtra("dbQuerySuperSync", dbQuerySuperSync)

                    // api routes and post credentials
                    intent.putExtra("apiRouteProgress", apiRouteProgress)
                    intent.putExtra("apiRoutePractice", apiRoutePractice)
                    intent.putExtra("apiRouteAttempts", apiRouteAttempts)
                    intent.putExtra("apiRouteSuperSync", apiRouteSuperSync)
                    intent.putExtra("fingerprint", fingerprint)
                    intent.putExtra("authorization", authorization)
                    intent.putExtra("x_package_id", x_package_id)
                    intent.putExtra("deviceType", deviceType)
                    intent.putExtra("version", version)

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(intent)
                    } else {
                        context.startService(intent)
                    }
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

    fun updateApiConfig(call: MethodCall) {
        apiRouteProgress = call.argument("apiRouteProgress") ?: ""
        apiRoutePractice = call.argument("apiRoutePractice") ?: ""
        apiRouteAttempts= call.argument("apiRouteAttempts") ?: ""
        apiRouteSuperSync = call.argument("apiRouteSuperSync") ?: ""
        fingerprint = call.argument("fingerprint") ?: ""
        authorization = call.argument("authorization") ?: ""
        x_package_id = call.argument("x_package_id") ?: ""
        deviceType = call.argument("deviceType") ?: ""
        version = call.argument("version") ?: ""
    }

    fun startMonitoringService(context: Context) {
        scheduleUserSyncWork(context)
    }

    private fun scheduleUserSyncWork(context: Context) {
        // Cancel any existing sync work first
        WorkManager.getInstance(context).cancelAllWorkByTag("user_sync_work")

        val data = workDataOf(

            // database paths and queries
            "dbPath" to dbPath,
            "dbName" to dbName,
            "dbQueryProgress" to dbQueryProgress,
            "dbQueryPractice" to  dbQueryPractice,
            "dbQueryAttempts" to dbQueryAttempts,
            "dbQuerySuperSync" to dbQuerySuperSync,


            // api routes and credentials for api posting:
            "apiRouteProgress" to apiRouteProgress,
            "apiRoutePractice" to apiRoutePractice,
            "apiRouteAttempts" to apiRouteAttempts,
            "apiRouteSuperSync" to apiRouteSuperSync,
            "fingerprint" to fingerprint,
            "authorization" to authorization,
            "x_package_id" to x_package_id,
            "deviceType" to  deviceType,
            "version" to version
        )

        val syncWork = OneTimeWorkRequestBuilder<UserSyncWorker>()
            .setInputData(data)
            .addTag("user_sync_work")
            .build()

        WorkManager.getInstance(context).enqueue(syncWork)
    }
}