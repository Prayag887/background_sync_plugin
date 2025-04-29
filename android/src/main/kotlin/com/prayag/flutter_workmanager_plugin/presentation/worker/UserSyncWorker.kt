package com.prayag.flutter_workmanager_plugin.presentation.worker

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.prayag.flutter_workmanager_plugin.domain.model.TableConfig
import com.prayag.flutter_workmanager_plugin.utils.ServiceLocator
import com.prayag.flutter_workmanager_plugin.data.datasource.remote.ApiCredentials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

class UserSyncWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    companion object {
        private const val TAG = "UserSyncWorker"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        // Database details
        val dbPath = inputData.getString("dbPath") ?: return@withContext Result.failure()
        val dbName = inputData.getString("dbName") ?: return@withContext Result.failure()

        // Get all select queries
        val dbQueryProgress = inputData.getString("dbQueryProgress") ?: return@withContext Result.failure()
        val dbQueryPractice = inputData.getString("dbQueryPractice") ?: return@withContext Result.failure()
        val dbQueryAttempts = inputData.getString("dbQueryAttempts") ?: return@withContext Result.failure()
        val dbQuerySuperSync = inputData.getString("dbQuerySuperSync") ?: return@withContext Result.failure()

        // DB insert queries
        val dbInsertQueryProgress = inputData.getString("dbInsertQueryProgress") ?: return@withContext Result.failure()
        val dbInsertQueryPractice = inputData.getString("dbInsertQueryPractice") ?: return@withContext Result.failure()
        val dbInsertQueryAttempts = inputData.getString("dbInsertQueryAttempts") ?: return@withContext Result.failure()
        val dbInsertQuerySuperSync = inputData.getString("dbInsertQuerySuperSync") ?: return@withContext Result.failure()

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

        Log.d(TAG, "Starting user sync work with database: $dbPath/$dbName")

        val filePath = File(dbPath, dbName).absolutePath
        val dbFile = File(filePath)
        if (!dbFile.exists()) {
            Log.e(TAG, "Database not found: $filePath")
            scheduleNextRun() // Schedule next run even on failure
            return@withContext Result.failure()
        }

        val db = SQLiteDatabase.openDatabase(filePath, null, SQLiteDatabase.OPEN_READWRITE)
        try {
            // Process all tables
            val tableConfigs = listOf(
                TableConfig(
                    name = "progress",
                    apiTableName = "lang_progress",
                    selectQuery = dbQueryProgress,
                    insertQuery = dbInsertQueryProgress,
                    apiEndpoint = apiRouteProgress
                ),
                TableConfig(
                    name = "practice",
                    apiTableName = "lang_practice",
                    selectQuery = dbQueryPractice,
                    insertQuery = dbInsertQueryPractice,
                    apiEndpoint = apiRoutePractice
                ),
                TableConfig(
                    name = "attempts",
                    apiTableName = "lang_attempts",
                    selectQuery = dbQueryAttempts,
                    insertQuery = dbInsertQueryAttempts,
                    apiEndpoint = apiRouteAttempts
                ),
                TableConfig(
                    name = "supersync",
                    apiTableName = "lang_supersync",
                    selectQuery = dbQuerySuperSync,
                    insertQuery = dbInsertQuerySuperSync,
                    apiEndpoint = apiRouteSuperSync
                )
            )

            val credentials = ApiCredentials(
                fingerprint = fingerprint,
                authorization = authorization,
                packageId = x_package_id,
                deviceType = deviceType,
                version = version
            )

            // Use the ServiceLocator to get the use case
            val syncDataUseCase = ServiceLocator.syncDataUseCase
            val success = syncDataUseCase.execute(db, tableConfigs, credentials)

            Log.d(TAG, "Finished syncing all tables. Success: $success")
            scheduleNextRun() // Schedule next run

            if (success) Result.success() else Result.retry()
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed: ${e.message}", e)
            scheduleNextRun() // Schedule next run even on failure
            Result.retry()
        } finally {
            db.close()
        }
    }

    private fun scheduleNextRun() {
        val data = workDataOf(
            "dbPath" to inputData.getString("dbPath"),
            "dbName" to inputData.getString("dbName"),
            "dbQueryProgress" to inputData.getString("dbQueryProgress"),
            "dbQueryPractice" to inputData.getString("dbQueryPractice"),
            "dbQueryAttempts" to inputData.getString("dbQueryAttempts"),
            "dbQuerySuperSync" to inputData.getString("dbQuerySuperSync"),

            // db insert queries
            "dbInsertQueryProgress" to inputData.getString("dbInsertQueryProgress"),
            "dbInsertQueryPractice" to inputData.getString("dbInsertQueryPractice"),
            "dbInsertQueryAttempts" to inputData.getString("dbInsertQueryAttempts"),
            "dbInsertQuerySuperSync" to inputData.getString("dbInsertQuerySuperSync"),

            //api routes and authentication details
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
        Log.d(TAG, "Scheduled next run in 1 minute")
    }
}