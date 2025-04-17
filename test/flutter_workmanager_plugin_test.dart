import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_workmanager_plugin/flutter_workmanager_plugin.dart';
import 'package:flutter_workmanager_plugin/flutter_workmanager_plugin_platform_interface.dart';
import 'package:flutter_workmanager_plugin/flutter_workmanager_plugin_method_channel.dart';
import 'package:plugin_platform_interface/plugin_platform_interface.dart';

class MockFlutterWorkmanagerPluginPlatform
    with MockPlatformInterfaceMixin
    implements FlutterWorkmanagerPluginPlatform {

  @override
  Future<String?> getPlatformVersion() => Future.value('42');
}

void main() {
  final FlutterWorkmanagerPluginPlatform initialPlatform = FlutterWorkmanagerPluginPlatform.instance;

  test('$MethodChannelFlutterWorkmanagerPlugin is the default instance', () {
    expect(initialPlatform, isInstanceOf<MethodChannelFlutterWorkmanagerPlugin>());
  });

  test('getPlatformVersion', () async {
    FlutterWorkmanagerPlugin flutterWorkmanagerPlugin = FlutterWorkmanagerPlugin();
    MockFlutterWorkmanagerPluginPlatform fakePlatform = MockFlutterWorkmanagerPluginPlatform();
    FlutterWorkmanagerPluginPlatform.instance = fakePlatform;

    // expect(await flutterWorkmanagerPlugin.getPlatformVersion(), '42');
  });
}
