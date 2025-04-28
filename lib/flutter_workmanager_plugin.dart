import 'package:flutter/services.dart';
import 'package:path_provider/path_provider.dart';

class FlutterWorkmanagerPlugin {
  static const MethodChannel _channel = MethodChannel('background_database_sync');

  static Future<void> startSync(String dbPath) async {
    await _channel.invokeMethod('startSync', {'dbPath': dbPath});
  }

  static Future<void> startMonitoring(String dbPath, String dbName, String dbQueryProgress, String dbQueryPractice,
      String dbQueryAttempts, String dbQuerySuperSync, int periodicSyncDuration, String apiRouteProgress,
      String apiRoutePractice, String apiRouteAttempts, String apiRouteSuperSync, String fingerprint,
      String authorization, String x_package_id, String deviceType, String version) async {
    await _channel.invokeMethod('startMonitoring', {
      'dbPath': dbPath,
      'dbName': dbName,
      'dbQueryProgress': dbQueryProgress,
      'dbQueryPractice': dbQueryPractice,
      'dbQueryAttempt': dbQueryAttempts,
      'dbQuerySuperSync': dbQuerySuperSync,
      'periodicSyncDuration' : periodicSyncDuration,

      'apiRouteProgress': apiRouteProgress,
      'apiRoutePractice': apiRoutePractice,
      'apiRouteAttempts': apiRouteAttempts,
      'apiRouteSuperSync': apiRouteSuperSync,
      'fingerprint': fingerprint,
      'authorization': authorization,
      'x_package_id': x_package_id,
      'deviceType': deviceType,
      'version': version,

    });
  }

  static Future<void> clearUserCopy() async {
    await _channel.invokeMethod('clearUserCopy');
  }

}
