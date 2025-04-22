package com.prayag.flutter_workmanager_plugin

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
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
        // Receive the database path, name, and query from Flutter
        databasePath = call.argument<String>("dbPath")
        databaseName = call.argument<String>("dbName")
        databaseQueryProgress = call.argument<String>("dbQueryProgress") ?: ""
        databaseQueryPractice = call.argument<String>("dbQueryPractice") ?: ""
        databaseQueryAttempt = call.argument<String>("dbQueryAttempt") ?: ""
        databaseQuerySuperSync = call.argument<String>("dbQuerySuperSync") ?: ""

        Log.d("TAG", "Received DB Path: $databasePath")
        Log.d("TAG", "Received DB Name: $databaseName")
        Log.d("TAG", "Received DB Query: $databaseQueryAttempt")

        // Check if the query is empty
        if (databaseQueryProgress.isEmpty()) {
          Log.e("TAG", "Error: databaseQuery cannot be empty")
          result.error("QUERY_EMPTY", "Database query is empty", null)
          return
        }

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

    val dbFile = File(dbFullPath)
    if (!dbFile.exists()) {
      Log.e("TAG", "Database file does not exist at: $dbFullPath")
      return
    }

    val db = android.database.sqlite.SQLiteDatabase.openDatabase(
      dbFullPath, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY
    )

    try {
      // Query for all table names
//      val tableQuery = "SELECT name FROM sqlite_master WHERE type = 'table';"
      val tableQuery = databaseQueryProgress
      val cursor = db.rawQuery(tableQuery, null)
      val tablesArray = JSONArray()

      if (cursor.moveToFirst()) {
        do {
          val tableName = cursor.getString(cursor.getColumnIndex("name"))
          tablesArray.put(tableName)
        } while (cursor.moveToNext())
      }

      cursor.close()

      // Now loop through each table and fetch its data
      for (i in 0 until tablesArray.length()) {
        val tableName = tablesArray.getString(i)
        val dataQuery = "SELECT * FROM $tableName;"
        val tableCursor = db.rawQuery(dataQuery, null)

        // Prepare JSON structure for the table and its records
        val tableData = JSONObject()
        tableData.put("table_name", tableName)
        val recordsArray = JSONArray()

        if (tableCursor.moveToFirst()) {
          do {
            val row = JSONObject()

            // Loop through columns and add them to the row
            for (j in 0 until tableCursor.columnCount) {
              val columnName = tableCursor.getColumnName(j)
              val value = tableCursor.getString(j)

              // Add fields to match the desired structure
              when (columnName) {
                "progress_data" -> row.put(columnName, "{\"$value\":$value}")
                else -> row.put(columnName, value)
              }
            }

            recordsArray.put(row)

          } while (tableCursor.moveToNext())
        }

        tableCursor.close()

        // Add the records to the JSON object
        tableData.put("records", recordsArray)

        // Log the result for this table
        Log.d("TAG", "Table Data: ${tableData.toString()}")
      }

      db.close()

    } catch (e: Exception) {
      Log.e("TAG", "Error executing query: ${e.message}")
    }
  }

  override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
    val application = context.applicationContext as Application
    application.unregisterActivityLifecycleCallbacks(lifecycleCallbacks)
    channel.setMethodCallHandler(null)
  }
}
