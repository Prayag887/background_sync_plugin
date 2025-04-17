import 'package:flutter/services.dart';
import 'package:path_provider/path_provider.dart';

class FlutterWorkmanagerPlugin {
  static const MethodChannel _channel = MethodChannel('background_database_sync');

  static Future<void> startSync(String dbPath) async {
    await _channel.invokeMethod('startSync', {'dbPath': dbPath});
  }

  static Future<void> startMonitoring() async {
    await _channel.invokeMethod('startMonitoring');
  }

  static Future<void> clearUserCopy() async {
    await _channel.invokeMethod('clearUserCopy');
  }

}
