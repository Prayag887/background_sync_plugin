import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:path_provider/path_provider.dart';

class FlutterWorkmanagerPlugin {
  static const MethodChannel _channel = MethodChannel('background_database_sync');

  static Future<void> startMonitoring(String dbPath, String dbName, String dbQueryProgress, String dbQueryPractice,  String dbQueryAttempt, String dbQuerySuperSync) async {
    await _channel.invokeMethod('startMonitoring', {
      'dbPath': dbPath,
      'dbName': dbName,
      'dbQueryProgress': dbQueryProgress,
      'dbQueryPractice': dbQueryPractice,
      'dbQueryAttempt': dbQueryAttempt,
      'dbQuerySuperSync': dbQuerySuperSync,
    });
  }

}
