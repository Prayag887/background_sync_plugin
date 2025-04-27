package com.prayag.flutter_workmanager_plugin.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class TaskMonitorService : Service() {

    private val handler = Handler()
    private val interval: Long = 5000
    private var filePath: String = ""

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val dbPath = intent?.getStringExtra("dbPath") ?: ""
        val dbName = intent?.getStringExtra("dbName") ?: ""
        filePath = File(dbPath, dbName).absolutePath

        handler.post(syncRunnable)
        return START_STICKY
    }

    private val syncRunnable = object : Runnable {
        override fun run() {
            serviceScope.launch {
                try {
                    val dbFile = File(filePath)
                    if (!dbFile.exists()) {
                        Log.e("TaskMonitorService", "Database file does not exist: $filePath")
                        return@launch
                    }

                    val db = android.database.sqlite.SQLiteDatabase.openDatabase(
                        filePath,
                        null,
                        android.database.sqlite.SQLiteDatabase.OPEN_READWRITE
                    )

                    val totalCursor = db.rawQuery("SELECT COUNT(*) FROM users", null)
                    val totalRecords = if (totalCursor.moveToFirst()) totalCursor.getInt(0) else 0
                    totalCursor.close()

                    val batchSize = 10000
                    var offset = 0

                    while (offset < totalRecords) {
                        val cursor = db.rawQuery(
                            "SELECT * FROM users LIMIT $batchSize OFFSET $offset", null
                        )

                        val batch = JSONArray()

                        while (cursor.moveToNext()) {
                            val row = JSONObject()
                            for (i in 0 until cursor.columnCount) {
                                row.put(cursor.getColumnName(i), cursor.getString(i))
                            }
                            batch.put(row)
                        }
                        cursor.close()

                        if (batch.length() > 0) {
                            val startTime = System.currentTimeMillis()
                            val response = postBatchToApi(batch)
                            val endTime = System.currentTimeMillis()
                            val duration = endTime - startTime

                            Log.d("TaskMonitorService", "Posted records in ${duration}ms")

                            // Store response
                            storeResponseInUsersCopy(db, response)
                        }

                        offset += batchSize
                    }

                    db.close()
                } catch (e: Exception) {
                    Log.e("TaskMonitorService", "Error syncing data: ${e.message}", e)
                }
            }

            // Post next sync only after all data is stored
            handler.postDelayed(this, interval)
        }
    }

    private fun postBatchToApi(data: JSONArray): String {
        return try {
            val url = URL("https://jsonplaceholder.typicode.com/posts")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            conn.doOutput = true

            val jsonBody = JSONObject().put("data", data).toString()
            OutputStreamWriter(conn.outputStream).use { it.write(jsonBody) }

            val response = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            response
        } catch (e: Exception) {
            Log.e("TaskMonitorService", "Failed to send batch: ${e.message}", e)
            "[]"
        }
    }

    private fun storeResponseInUsersCopy(db: android.database.sqlite.SQLiteDatabase, response: String) {
        try {
            // Parse the response into a JSONObject
            val jsonResponse = JSONObject(response)

            // Extract the 'data' field which contains the actual user data as a JSONArray
            val jsonArray = jsonResponse.getJSONArray("data")

            db.beginTransaction()
            try {
                for (i in 0 until jsonArray.length()) {
                    val jsonObject = jsonArray.getJSONObject(i)
                    val keys = jsonObject.keys()

                    val columns = mutableListOf<String>()
                    val values = mutableListOf<String>()

                    while (keys.hasNext()) {
                        val key = keys.next()
                        columns.add(key)
                        values.add(jsonObject.optString(key))
                    }

                    val insertQuery = StringBuilder()
                    insertQuery.append("INSERT OR REPLACE INTO users_copy (")
                    insertQuery.append(columns.joinToString(", "))
                    insertQuery.append(") VALUES (")
                    insertQuery.append(values.joinToString(", ") { "'${it.replace("'", "''")}'" })
                    insertQuery.append(");")

                    db.execSQL(insertQuery.toString())
                }

                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }

            Log.d("TaskMonitorService", "Stored ${jsonArray.length()} records into users_copy")

        } catch (e: Exception) {
            Log.e("TaskMonitorService", "Failed to store API response: ${e.message}", e)
        }
    }

    override fun onCreate() {
        super.onCreate()
        startForegroundService()
        handler.post(syncRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(syncRunnable)
        serviceJob.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundService() {
        val notification = NotificationCompat.Builder(this, "sync_channel")
            .setContentTitle("Syncing Users")
            .setContentText("Running background sync every 15s...")
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