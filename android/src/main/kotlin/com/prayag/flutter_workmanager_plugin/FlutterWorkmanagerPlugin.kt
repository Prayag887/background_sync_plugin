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
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.system.exitProcess
import java.net.HttpURLConnection
import java.net.URL
import kotlin.system.exitProcess

class FlutterWorkmanagerPlugin : FlutterPlugin, MethodCallHandler {

  private lateinit var context: Context
  private lateinit var channel: MethodChannel
  private var databasePath: String? = null
  private var databaseName: String? = null
  private var databaseQueryProgress: String = ""
  private var databaseQueryPractice: String = ""
  private var databaseQueryAttempt: String = ""
  private var databaseQuerySuperSync: String = ""

  private val lifecycleCallbacks = object : Application.ActivityLifecycleCallbacks {
    override fun onActivityResumed(activity: android.app.Activity) {}
    override fun onActivityPaused(activity: android.app.Activity) {
      Log.d("TAG", "onActivityPaused: is paused triggered")
    }
    override fun onActivityStarted(activity: android.app.Activity) {}
    override fun onActivityDestroyed(activity: android.app.Activity) {
      try {
        logAndExtractData()
      }catch (e: Exception){
        Log.d("TAG", "onActivityStarted: exception caught $e")
      }
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
        databasePath = call.argument<String>("dbPath")
        databaseName = call.argument<String>("dbName")
        databaseQueryProgress = call.argument<String>("dbQueryProgress") ?: ""
        databaseQueryPractice = call.argument<String>("dbQueryPractice") ?: ""
        databaseQueryAttempt = call.argument<String>("dbQueryAttempt") ?: ""
        databaseQuerySuperSync = call.argument<String>("dbQuerySuperSync") ?: ""

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


  private fun logAndExtractData() {
    val dbFullPath = if (!databasePath.isNullOrEmpty() && !databaseName.isNullOrEmpty()) {
      File(databasePath, databaseName).absolutePath
    } else {
      Log.e("TAG", "Database path or name not set")
      return
    }

    val dbFile = File(dbFullPath)
    if (!dbFile.exists()) {
      Log.e("TAG", "Database file does not exist at: $dbFullPath")
      return
    }

    val db = android.database.sqlite.SQLiteDatabase.openDatabase(
      dbFullPath, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY
    )

    val payload = JSONArray()

    try {
      val tableQuery = "SELECT name FROM sqlite_master WHERE type = 'table';"
      val cursor = db.rawQuery(tableQuery, null)

      if (cursor.moveToFirst()) {
        do {
          val tableName = cursor.getString(cursor.getColumnIndex("name"))
          val dataQuery = "SELECT * FROM $tableName;"
          val tableCursor = db.rawQuery(dataQuery, null)

          val tableData = JSONObject()
          tableData.put("table_name", tableName)
          val recordsArray = JSONArray()

          if (tableCursor.moveToFirst()) {
            do {
              val row = JSONObject()
              for (j in 0 until tableCursor.columnCount) {
                val columnName = tableCursor.getColumnName(j)
                val value = tableCursor.getString(j)
                if (columnName == "progress_data") {
                  row.put(columnName, "{\"$value\":$value}")
                } else {
                  row.put(columnName, value)
                }
              }
              recordsArray.put(row)
            } while (tableCursor.moveToNext())
          }

          tableCursor.close()
          tableData.put("records", recordsArray)
          payload.put(tableData)

        } while (cursor.moveToNext())
      }

      cursor.close()
      db.close()

    } catch (e: Exception) {
      Log.e("TAG", "Error executing query: ${e.message}")
      db.close()
      return
    }

    Thread {
      var attempt = 1
      var success = false

      while (attempt <= 5 && !success) {
        try {
          Log.d("TAG", "Attempt $attempt to send data to API")

          val url = URL("https://jsonplaceholder.typicode.com/posts")
          val connection = url.openConnection() as HttpURLConnection
          connection.requestMethod = "POST"
          connection.doOutput = true
          connection.setRequestProperty("Content-Type", "application/json")
          connection.connectTimeout = 5000
          connection.readTimeout = 5000

          val outStream = connection.outputStream
          outStream.write(payload.toString().toByteArray())
          outStream.flush()
          outStream.close()

          val responseCode = connection.responseCode
          val response = connection.inputStream.bufferedReader().use { it.readText() }
          Log.d("TAG", "On Destroy Response Code: $responseCode")
          Log.d("TAG", "On Destroy Response Body: $response")

          connection.disconnect()

          if (responseCode in 200..299) {
            success = true
            Log.d("TAG", "API call successful. Exiting...")
            exitProcess(0)
          } else {
            Log.w("TAG", "API call failed with code $responseCode. Retrying...")
          }

        } catch (e: Exception) {
          Log.e("TAG", "Exception during API call: ${e.message}")
        }

        attempt++
        Thread.sleep(2000) // wait before retry
      }

      if (!success) {
        Log.e("TAG", "Failed to send data after 5 attempts.")
      }
    }.start()
  }


  override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
    val application = context.applicationContext as Application
    application.unregisterActivityLifecycleCallbacks(lifecycleCallbacks)
    channel.setMethodCallHandler(null)
  }
}