import 'package:plugin_platform_interface/plugin_platform_interface.dart';

import 'flutter_workmanager_plugin_method_channel.dart';

abstract class FlutterWorkmanagerPluginPlatform extends PlatformInterface {
  /// Constructs a FlutterWorkmanagerPluginPlatform.
  FlutterWorkmanagerPluginPlatform() : super(token: _token);

  static final Object _token = Object();

  static FlutterWorkmanagerPluginPlatform _instance = MethodChannelFlutterWorkmanagerPlugin();

  /// The default instance of [FlutterWorkmanagerPluginPlatform] to use.
  ///
  /// Defaults to [MethodChannelFlutterWorkmanagerPlugin].
  static FlutterWorkmanagerPluginPlatform get instance => _instance;

  /// Platform-specific implementations should set this with their own
  /// platform-specific class that extends [FlutterWorkmanagerPluginPlatform] when
  /// they register themselves.
  static set instance(FlutterWorkmanagerPluginPlatform instance) {
    PlatformInterface.verifyToken(instance, _token);
    _instance = instance;
  }

  Future<String?> getPlatformVersion() {
    throw UnimplementedError('platformVersion() has not been implemented.');
  }
}
