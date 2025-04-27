package com.prayag.flutter_workmanager_plugin.service

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

class UserSyncWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val dbPath = inputData.getString("dbPath") ?: return@withContext Result.failure()
        val dbName = inputData.getString("dbName") ?: return@withContext Result.failure()

        val filePath = File(dbPath, dbName).absolutePath
        val dbFile = File(filePath)
        if (!dbFile.exists()) {
            Log.e("UserSyncWorker", "Database not found: $filePath")
            scheduleNextRun() // Schedule next run even on failure
            return@withContext Result.failure()
        }

        val db = SQLiteDatabase.openDatabase(filePath, null, SQLiteDatabase.OPEN_READWRITE)
        try {
            val batchSize = 5000
            var offset = 0

            while (true) {
                // Check if there are any records left to process
                val countCursor = db.rawQuery("SELECT COUNT(*) FROM users", null)
                countCursor.moveToFirst()
                val totalCount = countCursor.getInt(0)
                countCursor.close()

                if (totalCount <= offset) {
                    break // No more records to process
                }

                // Query actual user data with pagination
                val cursor = db.rawQuery(
                    "SELECT * FROM users LIMIT $batchSize OFFSET $offset", null
                )

                if (cursor.count == 0) {
                    cursor.close()
                    break
                }

                val batch = JSONArray()
                while (cursor.moveToNext()) {
                    val row = JSONObject()
                    for (i in 0 until cursor.columnCount) {
                        row.put(cursor.getColumnName(i), cursor.getString(i))
                    }
                    batch.put(row)
                }
                cursor.close()

                offset += cursor.count

                val response = postBatchToApi(batch)
                if (response != "[]") {
                    storeResponseInUsersCopy(db, response)
                }
            }

            Log.d("UserSyncWorker", "Finished syncing all data.")
            scheduleNextRun() // Schedule next run
            Result.success()
        } catch (e: Exception) {
            Log.e("UserSyncWorker", "Sync failed: ${e.message}", e)
            scheduleNextRun() // Schedule next run even on failure
            Result.retry()
        } finally {
            db.close()
        }
    }

    private fun scheduleNextRun() {
        val data = workDataOf(
            "dbPath" to inputData.getString("dbPath"),
            "dbName" to inputData.getString("dbName")
        )

        val nextWork = OneTimeWorkRequestBuilder<UserSyncWorker>()
            .setInputData(data)
            .setInitialDelay(15, TimeUnit.SECONDS)
            .addTag("user_sync_work")
            .build()

        WorkManager.getInstance(applicationContext).enqueue(nextWork)
        Log.d("UserSyncWorker", "Scheduled next run in 15 seconds")
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
            Log.e("UserSyncWorker", "Post failed: ${e.message}", e)
            "[]"
        }
    }

    private fun storeResponseInUsersCopy(db: SQLiteDatabase, response: String) {
        try {
            val jsonObject = JSONObject(response)
            val jsonArray = jsonObject.getJSONArray("data")

            db.beginTransaction()
            try {
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val columns = mutableListOf<String>()
                    val values = mutableListOf<String>()

                    for (key in obj.keys()) {
                        columns.add(key)
                        values.add(obj.optString(key))
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

            Log.d("UserSyncWorker", "Stored ${jsonArray.length()} rows into users_copy")
        } catch (e: Exception) {
            Log.e("UserSyncWorker", "Store failed: ${e.message}", e)
        }
    }
}