import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import 'flutter_workmanager_plugin_platform_interface.dart';

/// An implementation of [FlutterWorkmanagerPluginPlatform] that uses method channels.
class MethodChannelFlutterWorkmanagerPlugin extends FlutterWorkmanagerPluginPlatform {
  /// The method channel used to interact with the native platform.
  @visibleForTesting
  final methodChannel = const MethodChannel('flutter_workmanager_plugin');

  @override
  Future<String?> getPlatformVersion() async {
    final version = await methodChannel.invokeMethod<String>('getPlatformVersion');
    return version;
  }
}
