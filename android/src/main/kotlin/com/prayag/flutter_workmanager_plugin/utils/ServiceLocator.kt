package com.prayag.flutter_workmanager_plugin.utils

import com.prayag.flutter_workmanager_plugin.domain.usecase.SyncDataUseCase
import com.prayag.flutter_workmanager_plugin.workmanager.data.datasource.local.DatabaseDataSource
import com.prayag.flutter_workmanager_plugin.workmanager.data.datasource.remote.ApiDataSource
import com.prayag.flutter_workmanager_plugin.data.repository.DataSyncRepository

/**
 * Simple service locator pattern implementation
 * Used to provide dependencies without a formal DI framework
 */
object ServiceLocator {
    private val databaseDataSource by lazy { DatabaseDataSource() }
    private val apiDataSource by lazy { ApiDataSource() }
    private val repository by lazy { DataSyncRepository(databaseDataSource, apiDataSource) }
    val syncDataUseCase by lazy { SyncDataUseCase(repository) }
}