package com.prayag.flutter_workmanager_plugin

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.prayag.flutter_workmanager_plugin.viewmodels.PluginViewModel
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

class FlutterWorkmanagerPlugin : FlutterPlugin, MethodChannel.MethodCallHandler {

  private lateinit var context: Context
  private lateinit var channel: MethodChannel
  private val viewModel = PluginViewModel()

  override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
    context = binding.applicationContext
    channel = MethodChannel(binding.binaryMessenger, "background_database_sync")
    channel.setMethodCallHandler(this)

    val app = context as Application
    app.registerActivityLifecycleCallbacks(viewModel.getLifecycleCallbacks(context))
  }

  override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
    (context as Application).unregisterActivityLifecycleCallbacks(viewModel.getLifecycleCallbacks(context))
    channel.setMethodCallHandler(null)
  }

  override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
    when (call.method) {
      "startMonitoring" -> {
        viewModel.updateDbConfig(call)
        viewModel.updateApiConfig(call)
        viewModel.startMonitoringService(context)
        result.success("Monitoring started")
      }
      else -> result.notImplemented()
    }
  }
}
