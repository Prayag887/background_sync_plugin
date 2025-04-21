import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:path_provider/path_provider.dart';

class FlutterWorkmanagerPlugin {
  static const MethodChannel _channel = MethodChannel('background_database_sync');

  static Future<void> startMonitoring(String dbPath, String dbName) async {
    if (kDebugMode) {
      print("Database path: $dbPath");
      print("Database name: $dbName");
    }

    await _channel.invokeMethod('startMonitoring', {
      'dbPath': dbPath,
      'dbName': dbName,
    });
  }

}
