package com.prayag.flutter_workmanager_plugin

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.prayag.flutter_workmanager_plugin.database.DatabaseHelper
import com.prayag.flutter_workmanager_plugin.service.TaskMonitorService
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import org.json.JSONArray
import org.json.JSONObject

class FlutterWorkmanagerPlugin : FlutterPlugin, MethodChannel.MethodCallHandler {

  private lateinit var context: Context
  private lateinit var channel: MethodChannel

  private val lifecycleCallbacks = object : Application.ActivityLifecycleCallbacks {
    override fun onActivityResumed(activity: android.app.Activity) {}
    override fun onActivityPaused(activity: android.app.Activity) {
      Log.d("TAG", "onActivityPaused: is paused triggered")
    }

    override fun onActivityStarted(activity: android.app.Activity) {}
    override fun onActivityDestroyed(activity: android.app.Activity) {
      logAndExtractData()
    }

    override fun onActivitySaveInstanceState(activity: android.app.Activity, outState: Bundle) {}
    override fun onActivityStopped(activity: android.app.Activity) {}
    override fun onActivityCreated(activity: android.app.Activity, savedInstanceState: Bundle?) {}
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
      "startMonitoring" -> {
        Log.d("TAG", "startMonitoring: triggered")
        startMonitoringService()
        result.success("Monitoring started")
      }
      else -> result.notImplemented()
    }
  }

  private fun startMonitoringService() {
    val serviceIntent = Intent(context, TaskMonitorService::class.java)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      context.startForegroundService(serviceIntent)
    } else {
      context.startService(serviceIntent)
    }
  }

  private fun logAndExtractData() {
    Log.d("TAG", "App is closing - extracting data from Users table")

    val dbHelper = DatabaseHelper(context)
    val db = dbHelper.readableDatabase

    val cursor = db.rawQuery("SELECT * FROM Users", null)
    val dataArray = JSONArray()

    if (cursor.moveToFirst()) {
      do {
        val row = JSONObject()
        for (i in 0 until cursor.columnCount) {
          val columnName = cursor.getColumnName(i)
          val value = cursor.getString(i)
          row.put(columnName, value)
        }
        dataArray.put(row)
      } while (cursor.moveToNext())
    }

    cursor.close()
    db.close()

    // Log the JSON data
    Log.d("TAG", "Extracted JSON Data: ${dataArray.toString()}")

    // Optional: Send this JSON to Flutter via MethodChannel if needed
    // channel.invokeMethod("onDataExtracted", dataArray.toString())
  }

  override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
    val application = context.applicationContext as Application
    application.unregisterActivityLifecycleCallbacks(lifecycleCallbacks)
    channel.setMethodCallHandler(null)
  }
}
