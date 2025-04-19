package com.prayag.flutter_workmanager_plugin

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.prayag.flutter_workmanager_plugin.service.TaskMonitorService
import com.prayag.flutter_workmanager_plugin.workmanager.DartCallbackWorker
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler

class FlutterWorkmanagerPlugin : FlutterPlugin, MethodCallHandler {

  private lateinit var context: Context
  private lateinit var channel: MethodChannel

  private val lifecycleCallbacks = object : Application.ActivityLifecycleCallbacks {
    override fun onActivityResumed(activity: android.app.Activity) {}
    override fun onActivityPaused(activity: android.app.Activity) {
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

    val application = context.applicationContext as Application
    application.registerActivityLifecycleCallbacks(lifecycleCallbacks)
  }

  override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
    when (call.method) {
      "registerCallbackHandle" -> {
        val callbackHandle = call.argument<Long?>("callbackHandle")
        if (callbackHandle != null) {
          Log.d("TAG", "Callback handle received: $callbackHandle")
          val prefs = context.getSharedPreferences("flutter_workmanager_plugin", Context.MODE_PRIVATE)
          prefs.edit().putLong("callback_handle", callbackHandle).apply()
          result.success(true)
        } else {
          result.error("INVALID_ARGUMENT", "callbackHandle is null", null)
          Log.d("TAG", "onMethodCall: ----- call back handle is null")
        }
      }
      "storeCallbackHandle" -> {
        val callbackHandle = call.arguments<Long?>()  // nullable Long
        if (callbackHandle != null) {
          val prefs = context.getSharedPreferences("flutter_workmanager_plugin", Context.MODE_PRIVATE)
          prefs.edit().putLong("callback_handle", callbackHandle).apply()
          result.success(true)
        } else {
          result.error("NULL_HANDLE", "Callback handle is null", null)
        }
      }
      "startMonitoring" -> {
        Log.d("TAG", "startMonitoring: triggered")
        startMonitoringService()
        result.success("Monitoring started")
      }
      else -> result.notImplemented()
    }
  }

  private fun startMonitoringService() {
    Log.d("TAG", "startMonitoring function: triggered")
    val serviceIntent = Intent(context, TaskMonitorService::class.java)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      context.startForegroundService(serviceIntent)
    } else {
      context.startService(serviceIntent)
    }
  }

  private fun logAndSyncData() {
    Log.d("TAG", "App is closing - scheduling Dart sync")
    val prefs = context.getSharedPreferences("flutter_workmanager_plugin", Context.MODE_PRIVATE)
    val callbackHandle = prefs.getLong("callback_handle", 0L)

    if (callbackHandle == 0L) {
      Log.e("TAG", "No valid callback handle found")
      return
    }

    val request = OneTimeWorkRequestBuilder<DartCallbackWorker>()
      .setInputData(workDataOf("callback_handle" to callbackHandle))
      .build()

    WorkManager.getInstance(context).enqueue(request)
  }

  override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
    val application = context.applicationContext as Application
    application.unregisterActivityLifecycleCallbacks(lifecycleCallbacks)
    channel.setMethodCallHandler(null)
  }
}