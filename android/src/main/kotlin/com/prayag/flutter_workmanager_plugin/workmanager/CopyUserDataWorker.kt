package com.prayag.flutter_workmanager_plugin.workmanager

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.prayag.flutter_workmanager_plugin.database.DatabaseHelper

class CopyUserDataWorker(appContext: Context, workerParams: WorkerParameters) : Worker(appContext, workerParams) {

    override fun doWork(): Result {
        // Retrieve the database path or other necessary information
        val dbPath = inputData.getString("db_path")

        if (dbPath != null) {
            try {
                // Perform the actual database copy operation
                val dbHelper = DatabaseHelper(applicationContext)
                dbHelper.copyUserDataToUsersCopy()
                return Result.success()
            } catch (e: Exception) {
                return Result.failure()
            }
        } else {
            return Result.failure()
        }
    }
}