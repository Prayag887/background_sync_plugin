package com.prayag.flutter_workmanager_plugin.workmanager

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.embedding.engine.dart.DartExecutor
import io.flutter.embedding.engine.loader.FlutterLoader
import io.flutter.plugin.common.MethodChannel
import io.flutter.view.FlutterCallbackInformation
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class DartCallbackWorker(
    context: Context,-
    workerParams: WorkerParameters
) : Worker(context, workerParams) {

    private val TAG = "DartCallbackWorker"
    private val taskCompletionLatch = CountDownLatch(1)
    private var taskResult = false
    private var flutterEngine: FlutterEngine? = null

    override fun doWork(): Result {
        Log.d(TAG, "Starting background Dart task")

        val callbackHandle = inputData.getLong("callback_handle", 0L)
        if (callbackHandle == 0L) {
            Log.e(TAG, "No valid callback handle provided")
            return Result.failure()
        }

        return try {
            executeCallbackOnMainThread(callbackHandle)

            // Wait for task completion or timeout after 60 seconds
            val completed = taskCompletionLatch.await(60, TimeUnit.SECONDS)

            // Clean up
            Handler(Looper.getMainLooper()).post {
                flutterEngine?.destroy()
                flutterEngine = null
            }

            if (!completed) {
                Log.w(TAG, "Task execution timed out")
                Result.failure()
            } else if (taskResult) {
                Log.d(TAG, "Task completed successfully")
                Result.success()
            } else {
                Log.d(TAG, "Task failed")
                Result.failure()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error executing Dart callback", e)
            Result.failure()
        }
    }

    private fun executeCallbackOnMainThread(callbackHandle: Long) {
        val latch = CountDownLatch(1)

        Handler(Looper.getMainLooper()).post {
            try {
                val callbackInfo = FlutterCallbackInformation.lookupCallbackInformation(callbackHandle)
                if (callbackInfo == null) {
                    Log.e(TAG, "Callback lookup failed for handle: $callbackHandle")
                    taskCompletionLatch.countDown()
                    latch.countDown()
                    return@post
                }

                Log.d(TAG, "Callback info retrieved: ${callbackInfo.callbackName}")

                val flutterLoader = FlutterLoader()
                if (!flutterLoader.initialized()) {
                    flutterLoader.startInitialization(applicationContext)
                    flutterLoader.ensureInitializationComplete(applicationContext, null)
                }

                flutterEngine = FlutterEngine(applicationContext)

                val backgroundChannel = MethodChannel(
                    flutterEngine!!.dartExecutor.binaryMessenger,
                    "background_database_sync_background"
                )

                backgroundChannel.setMethodCallHandler { call, result ->
                    when (call.method) {
                        "ready" -> {
                            Log.d(TAG, "Received ready signal from Dart")
                            result.success(true)

                            backgroundChannel.invokeMethod(
                                "onTaskExecute",
                                mapOf(
                                    "taskName" to "database_sync",
                                    "inputData" to mapOf<String, Any>()
                                ),
                                object : MethodChannel.Result {
                                    override fun success(result: Any?) {
                                        Log.d(TAG, "Task execution result: $result")
                                        taskResult = result as? Boolean ?: false
                                        taskCompletionLatch.countDown()
                                    }

                                    override fun error(errorCode: String, errorMessage: String?, errorDetails: Any?) {
                                        Log.e(TAG, "Task error: $errorCode - $errorMessage")
                                        taskCompletionLatch.countDown()
                                    }

                                    override fun notImplemented() {
                                        Log.e(TAG, "Task method not implemented")
                                        taskCompletionLatch.countDown()
                                    }
                                }
                            )
                        }
                        else -> result.notImplemented()
                    }
                }

                val dartBundlePath = flutterLoader.findAppBundlePath()
                flutterEngine!!.dartExecutor.executeDartCallback(
                    DartExecutor.DartCallback(
                        applicationContext.assets,
                        dartBundlePath,
                        callbackInfo
                    )
                )

                Log.d(TAG, "Dart code execution started")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to execute Dart callback", e)
                taskCompletionLatch.countDown()
            } finally {
                latch.countDown()
            }
        }

        // Wait until the engine is set up before continuing
        latch.await()
    }
}
