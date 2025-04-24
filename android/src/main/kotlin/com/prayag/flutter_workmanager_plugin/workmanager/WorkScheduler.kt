package com.prayag.flutter_workmanager_plugin.workmanager

import android.content.Context
import android.util.Log
import androidx.work.*
import java.util.concurrent.TimeUnit

object WorkScheduler {

    private const val TAG = "WorkScheduler"

    fun schedulePeriodicDartCallback(context: Context, callbackHandle: Long) {
        val workManager = WorkManager.getInstance(context)

        val inputData = Data.Builder()
            .putLong("callback_handle", callbackHandle)
            .build()

        val workRequest = PeriodicWorkRequestBuilder<DartCallbackWorker>(
            15, TimeUnit.MINUTES // Minimum repeat interval allowed
        )
            .setInputData(inputData)
            .addTag("dart_callback_periodic_task")
            .build()

        workManager.enqueueUniquePeriodicWork(
            "dart_periodic_task",
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )

        Log.d(TAG, "Scheduled periodic Dart task every 15 minutes.")
    }

    fun cancelScheduledTask(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork("dart_periodic_task")
        Log.d(TAG, "Canceled periodic Dart task.")
    }
}
