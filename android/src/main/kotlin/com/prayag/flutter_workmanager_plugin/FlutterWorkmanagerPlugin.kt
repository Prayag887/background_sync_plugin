package com.prayag.flutter_workmanager_plugin

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.prayag.flutter_workmanager_plugin.database.DatabaseHelper
import com.prayag.flutter_workmanager_plugin.service.TaskMonitorService
import com.prayag.flutter_workmanager_plugin.workmanager.CopyUserDataWorker
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result

/** FlutterWorkmanagerPlugin */
class FlutterWorkmanagerPlugin : FlutterPlugin, MethodCallHandler {

  private lateinit var context: Context
  private lateinit var channel: MethodChannel

  private val lifecycleCallbacks = object : Application.ActivityLifecycleCallbacks {
    override fun onActivityResumed(activity: android.app.Activity) {
      // When the app comes to the foreground
    }

    override fun onActivityPaused(activity: android.app.Activity) {
      // When the app goes to the background
//      logAndSyncData()
      Log.d("TAG", "onActivityPaused: is paused triggered")
    }

    override fun onActivityStarted(activity: android.app.Activity) {}

    override fun onActivityDestroyed(activity: android.app.Activity) {
      logAndSyncData()
    }

    override fun onActivitySaveInstanceState(activity: android.app.Activity, outState: android.os.Bundle) {}

    override fun onActivityStopped(activity: android.app.Activity) {}

    override fun onActivityCreated(activity: android.app.Activity, savedInstanceState: android.os.Bundle?) {}
  }

  override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
    context = binding.applicationContext
    channel = MethodChannel(binding.binaryMessenger, "background_database_sync")
    channel.setMethodCallHandler(this)

    // Register the lifecycle callbacks to monitor app state
    val application = context.applicationContext as Application
    application.registerActivityLifecycleCallbacks(lifecycleCallbacks)
  }

  override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
    when (call.method) {
      "startMonitoring" -> {
        Log.d("TAG", "startMonitoring: triggered")
        startMonitoringService()
        result.success("Monitoring started")
      }
      else -> result.notImplemented()
    }
  }

  // Start monitoring service
  private fun startMonitoringService() {
    Log.d("TAG", "startMonitoring function: triggered")
    val serviceIntent = Intent(context, TaskMonitorService::class.java)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      context.startForegroundService(serviceIntent)
    } else {
      context.startService(serviceIntent)
    }
  }

  // Log when the app is closed or in the background, and sync data
  private fun logAndSyncData() {
    Log.d("TAG", "App is closing or moving to background - syncing data")
    val dbHelper = DatabaseHelper(context)
    dbHelper.copyUserDataToUsersCopy() // Sync the database when app is closed
  }

  override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
    // Remove the lifecycle callback when the plugin is detached
    val application = context.applicationContext as Application
    application.unregisterActivityLifecycleCallbacks(lifecycleCallbacks)
    channel.setMethodCallHandler(null)
  }
}
