package com.prayag.flutter_workmanager_plugin.workmanager

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
        // Database details
        val dbPath = inputData.getString("dbPath") ?: return@withContext Result.failure()
        val dbName = inputData.getString("dbName") ?: return@withContext Result.failure()

        // Get all queries
        val dbQueryProgress = inputData.getString("dbQueryProgress") ?: return@withContext Result.failure()
        val dbQueryPractice = inputData.getString("dbQueryPractice") ?: return@withContext Result.failure()
        val dbQueryAttempts = inputData.getString("dbQueryAttempts") ?: return@withContext Result.failure()
        val dbQuerySuperSync = inputData.getString("dbQuerySuperSync") ?: return@withContext Result.failure()

        // API endpoints
        val apiRouteProgress = inputData.getString("apiRouteProgress") ?: return@withContext Result.failure()
        val apiRoutePractice = inputData.getString("apiRoutePractice") ?: return@withContext Result.failure()
        val apiRouteAttempts = inputData.getString("apiRouteAttempts") ?: return@withContext Result.failure()
        val apiRouteSuperSync = inputData.getString("apiRouteSuperSync") ?: return@withContext Result.failure()

        // API credentials
        val fingerprint = inputData.getString("fingerprint") ?: return@withContext Result.failure()
        val authorization = inputData.getString("authorization") ?: return@withContext Result.failure()
        val x_package_id = inputData.getString("x_package_id") ?: return@withContext Result.failure()
        val deviceType = inputData.getString("deviceType") ?: return@withContext Result.failure()
        val version = inputData.getString("version") ?: return@withContext Result.failure()

        val filePath = File(dbPath, dbName).absolutePath
        val dbFile = File(filePath)
        if (!dbFile.exists()) {
            Log.e("UserSyncWorker", "Database not found: $filePath")
            scheduleNextRun() // Schedule next run even on failure
            return@withContext Result.failure()
        }

        val db = SQLiteDatabase.openDatabase(filePath, null, SQLiteDatabase.OPEN_READWRITE)
        try {
            // Process all tables
            val tables = listOf(
                TableConfig("progress", "lang_progress", dbQueryProgress, apiRouteProgress),
                TableConfig("practice", "lang_practice", dbQueryPractice, apiRoutePractice),
                TableConfig("attempts", "lang_attempts", dbQueryAttempts, apiRouteAttempts),
                TableConfig("supersync", "lang_supersync", dbQuerySuperSync, apiRouteSuperSync)
            )

            for (table in tables) {
                Log.d("UserSyncWorker", "Starting sync for table: ${table.name}")
                processTable(db, table, fingerprint, authorization, x_package_id, deviceType, version)
            }

            Log.d("UserSyncWorker", "Finished syncing all tables.")
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

    private suspend fun processTable(
        db: SQLiteDatabase,
        tableConfig: TableConfig,
        fingerprint: String,
        authorization: String,
        x_package_id: String,
        deviceType: String,
        version: String
    ) {
        val batchSize = 5000
        var offset = 0

        while (true) {
            // Execute the query with pagination
            val paginatedQuery = "${tableConfig.query} LIMIT $batchSize OFFSET $offset"
            val cursor = db.rawQuery(paginatedQuery, null)

            if (cursor.count == 0) {
                cursor.close()
                break
            }

            val batch = JSONArray()
            while (cursor.moveToNext()) {
                val row = JSONObject()
                for (i in 0 until cursor.columnCount) {
                    val columnName = cursor.getColumnName(i)
                    val columnValue = cursor.getString(i)
                    row.put(columnName, columnValue ?: JSONObject.NULL)
                }
                batch.put(row)
            }

            offset += cursor.count
            cursor.close()

            if (batch.length() > 0) {
                Log.d("UserSyncWorker", "Sending ${batch.length()} records to API for ${tableConfig.name}")

                val response = postDataToApi(
                    batch,
                    tableConfig.apiTableName,
                    tableConfig.apiEndpoint,
                    fingerprint,
                    authorization,
                    x_package_id,
                    deviceType,
                    version
                )

                // Log the API response (safely handling large responses)
                if (response.isNotEmpty()) {
                    if (response.length <= 1000) {
                        Log.d("UserSyncWorker", "API Response for ${tableConfig.name}: $response")
                    } else {
                        Log.d("UserSyncWorker", "API Response for ${tableConfig.name} (truncated): ${response.substring(0, 1000)}...")
                    }

                    if (response != "[]") {
                        storeResponseInDatabase(db, response, "${tableConfig.name}_copy")
                    }
                } else {
                    Log.d("UserSyncWorker", "Empty API response for ${tableConfig.name}")
                }
            }

            if (cursor.count < batchSize) {
                break // No more data to process
            }
        }

        Log.d("UserSyncWorker", "Completed sync for table: ${tableConfig.name}")
    }

    private fun scheduleNextRun() {
        val data = workDataOf(
            "dbPath" to inputData.getString("dbPath"),
            "dbName" to inputData.getString("dbName"),
            "dbQueryProgress" to inputData.getString("dbQueryProgress"),
            "dbQueryPractice" to inputData.getString("dbQueryPractice"),
            "dbQueryAttempts" to inputData.getString("dbQueryAttempts"),
            "dbQuerySuperSync" to inputData.getString("dbQuerySuperSync"),
            "apiRouteProgress" to inputData.getString("apiRouteProgress"),
            "apiRoutePractice" to inputData.getString("apiRoutePractice"),
            "apiRouteAttempts" to inputData.getString("apiRouteAttempts"),
            "apiRouteSuperSync" to inputData.getString("apiRouteSuperSync"),
            "fingerprint" to inputData.getString("fingerprint"),
            "authorization" to inputData.getString("authorization"),
            "x_package_id" to inputData.getString("x_package_id"),
            "deviceType" to inputData.getString("deviceType"),
            "version" to inputData.getString("version")
        )

        val nextWork = OneTimeWorkRequestBuilder<UserSyncWorker>()
            .setInputData(data)
            .setInitialDelay(1, TimeUnit.MINUTES)
            .addTag("user_sync_work")
            .build()

        WorkManager.getInstance(applicationContext).enqueue(nextWork)
        Log.d("UserSyncWorker", "Scheduled next run in 1 minute")
    }

    private suspend fun postDataToApi(
        data: JSONArray,
        tableName: String,
        apiEndpoint: String,
        fingerprint: String,
        authorization: String,
        x_package_id: String,
        deviceType: String,
        version: String
    ): String {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL(apiEndpoint)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                conn.setRequestProperty("Authorization", authorization)
                conn.setRequestProperty("X-Package-ID", x_package_id)
                conn.setRequestProperty("Fingerprint", fingerprint)
                conn.setRequestProperty("Device-Type", deviceType)
                conn.setRequestProperty("Version", version)
                conn.doOutput = true
                conn.connectTimeout = 30000
                conn.readTimeout = 30000

                // Format the request body as specified: {"table_name":"xxx","records":[...]}
                val jsonBody = JSONObject()
                    .put("table_name", tableName)
                    .put("records", data)
                    .toString()

                Log.d("UserSyncWorker", "Sending request to $apiEndpoint with ${data.length()} records")
                // Log a sample of the request body (first 300 chars)
                val truncatedBody = if (jsonBody.length > 300) "${jsonBody.substring(0, 300)}..." else jsonBody
                Log.d("UserSyncWorker", "Request body sample: $truncatedBody")

                OutputStreamWriter(conn.outputStream).use { it.write(jsonBody) }

                val responseCode = conn.responseCode
                Log.d("UserSyncWorker", "API response code: $responseCode")

                val response = if (responseCode in 200..299) {
                    conn.inputStream.bufferedReader().use { it.readText() }
                } else {
                    val errorMsg = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
                    Log.e("UserSyncWorker", "API request failed: $responseCode, $errorMsg")
                    ""
                }
                conn.disconnect()
                response
            } catch (e: Exception) {
                Log.e("UserSyncWorker", "API request exception: ${e.message}", e)
                ""
            }
        }
    }

    private fun storeResponseInDatabase(db: SQLiteDatabase, response: String, tableName: String) {
        try {
            // Make sure there's valid data to process
            if (response.isEmpty()) return

            val jsonResponse = try {
                JSONObject(response)
            } catch (e: Exception) {
                Log.e("UserSyncWorker", "Invalid JSON response: $response")
                return
            }

            val jsonArray = if (jsonResponse.has("data")) {
                jsonResponse.getJSONArray("data")
            } else {
                Log.e("UserSyncWorker", "JSON response doesn't contain 'data' field: $response")
                return
            }

            if (jsonArray.length() == 0) return

            db.beginTransaction()
            try {
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val columns = mutableListOf<String>()
                    val placeholders = mutableListOf<String>()
                    val values = mutableListOf<String>()

                    obj.keys().forEach { key ->
                        columns.add(key)
                        placeholders.add("?")
                        values.add(obj.optString(key, ""))
                    }

                    val insertQuery = "INSERT OR REPLACE INTO $tableName (${columns.joinToString(", ")}) " +
                            "VALUES (${placeholders.joinToString(", ")})"

                    val stmt = db.compileStatement(insertQuery)
                    try {
                        values.forEachIndexed { index, value ->
                            stmt.bindString(index + 1, value)
                        }
                        stmt.execute()
                    } finally {
                        stmt.clearBindings()
                        stmt.close()
                    }
                }
                db.setTransactionSuccessful()
                Log.d("UserSyncWorker", "Stored ${jsonArray.length()} rows into $tableName")
            } catch (e: Exception) {
                Log.e("UserSyncWorker", "Database insert failed: ${e.message}", e)
            } finally {
                db.endTransaction()
            }
        } catch (e: Exception) {
            Log.e("UserSyncWorker", "Store response failed: ${e.message}", e)
        }
    }

    // Helper class to store table configuration
    private data class TableConfig(
        val name: String,
        val apiTableName: String,
        val query: String,
        val apiEndpoint: String
    )
}