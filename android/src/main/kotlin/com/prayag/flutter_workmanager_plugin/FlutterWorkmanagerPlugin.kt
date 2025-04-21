package com.prayag.flutter_workmanager_plugin

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class FlutterWorkmanagerPlugin : FlutterPlugin, MethodChannel.MethodCallHandler {

  private lateinit var context: Context
  private lateinit var channel: MethodChannel

  private var databasePath: String? = null
  private var databaseName: String? = null

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
        databasePath = call.argument<String>("dbPath")
        databaseName = call.argument<String>("dbName")

        Log.d("TAG", "Received DB Path: $databasePath")
        Log.d("TAG", "Received DB Name: $databaseName")

        startMonitoringService()
        result.success("Monitoring started")
      }
      else -> result.notImplemented()
    }
  }

  private fun startMonitoringService() {
    val serviceIntent = Intent(context, com.prayag.flutter_workmanager_plugin.service.TaskMonitorService::class.java)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      context.startForegroundService(serviceIntent)
    } else {
      context.startService(serviceIntent)
    }
  }

  private fun logAndExtractData() {
    val dbFullPath = if (!databasePath.isNullOrEmpty() && !databaseName.isNullOrEmpty()) {
      File(databasePath, databaseName).absolutePath
    } else {
      Log.e("TAG", "Database path or name not set")
      return
    }

    Log.d("TAG", "Using full DB path: $dbFullPath")

    val dbFile = File(dbFullPath)
    if (!dbFile.exists()) {
      Log.e("TAG", "Database file does not exist at: $dbFullPath")
      return
    }

    val db = android.database.sqlite.SQLiteDatabase.openDatabase(
      dbFullPath, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY
    )

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

    Log.d("TAG", "Extracted JSON Data: ${dataArray.toString()}")
  }

  override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
    val application = context.applicationContext as Application
    application.unregisterActivityLifecycleCallbacks(lifecycleCallbacks)
    channel.setMethodCallHandler(null)
  }
}
