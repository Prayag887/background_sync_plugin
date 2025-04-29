package com.prayag.flutter_workmanager_plugin.data.repository

import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.prayag.flutter_workmanager_plugin.data.datasource.local.DatabaseDataSource
import com.prayag.flutter_workmanager_plugin.data.model.SyncData
import com.prayag.flutter_workmanager_plugin.domain.model.TableConfig
import com.prayag.flutter_workmanager_plugin.workmanager.data.datasource.remote.ApiCredentials
import com.prayag.flutter_workmanager_plugin.workmanager.data.datasource.remote.ApiDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DataSyncRepository(
    private val databaseDataSource: DatabaseDataSource,
    private val apiDataSource: ApiDataSource
) {
    companion object {
        private const val TAG = "DataSyncRepository"
        private const val BATCH_SIZE = 5000
    }

    suspend fun syncTable(
        db: SQLiteDatabase,
        tableConfig: TableConfig,
        credentials: ApiCredentials
    ) = withContext(Dispatchers.IO) {
        Log.d(TAG, "Starting sync for table: ${tableConfig.name}")
        var offset = 0

        while (true) {
            // Fetch data from database
            val (batch, count) = databaseDataSource.fetchDataBatch(db, tableConfig, offset)

            if (count == 0) break

            offset += count

            if (batch.length() > 0) {
                // Send data to API
                Log.d(TAG, "Sending ${batch.length()} records to API for ${tableConfig.name}")

                val syncData = SyncData(tableConfig.apiTableName, batch)
                val response = apiDataSource.postData(syncData, tableConfig.apiEndpoint, credentials)

                // Process response if successful
                if (response.success && response.data != null && response.data.length() > 0) {
                    // Store API response back to database
                    databaseDataSource.storeResponseData(db, tableConfig, response.data)
                }
            }

            if (count < BATCH_SIZE) {
                break // No more data to process
            }
        }

        Log.d(TAG, "Completed sync for table: ${tableConfig.name}")
    }
}