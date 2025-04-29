package com.prayag.flutter_workmanager_plugin.domain.usecase

import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.prayag.flutter_workmanager_plugin.data.repository.DataSyncRepository
import com.prayag.flutter_workmanager_plugin.domain.model.TableConfig
import com.prayag.flutter_workmanager_plugin.data.datasource.remote.ApiCredentials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SyncDataUseCase(private val repository: DataSyncRepository) {
    companion object {
        private const val TAG = "SyncDataUseCase"
    }

    suspend fun execute(
        db: SQLiteDatabase,
        tableConfigs: List<TableConfig>,
        credentials: ApiCredentials
    ) = withContext(Dispatchers.IO) {
        try {
            for (tableConfig in tableConfigs) {
                repository.syncTable(db, tableConfig, credentials)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed: ${e.message}", e)
            false
        }
    }
}