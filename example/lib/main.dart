import 'dart:async';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_workmanager_plugin/flutter_workmanager_plugin.dart';
import 'package:sqflite/sqflite.dart';

import 'db_service.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return const MaterialApp(
      home: MainScreen(),
    );
  }
}

class MainScreen extends StatefulWidget {
  const MainScreen({super.key});

  @override
  State<MainScreen> createState() => _MainScreenState();
}

class _MainScreenState extends State<MainScreen> {
  final FlutterWorkmanagerPlugin _plugin = FlutterWorkmanagerPlugin();
  final ValueNotifier<List<Map<String, dynamic>>> usersCopyNotifier = ValueNotifier([]);
  List<Map<String, dynamic>> users = [];
  String schemaJson = '';
  bool showSchema = false;
  bool isLoading = true;
  Timer? _pollingTimer;

  @override
  void initState() {
    super.initState();
    _startMonitoring();
    _loadData();
    _pollingTimer = Timer.periodic(const Duration(seconds: 3), (_) => _loadData());
  }

  @override
  void dispose() {
    _pollingTimer?.cancel();
    super.dispose();
  }

  Future<void> _startMonitoring() async {
    final dir = await getDatabasesPath();
    const dbName = 'app_database.db';
    const dbQuery = "SELECT name FROM sqlite_master WHERE type = 'table';";
    const dbQueryProgress = "SELECT name FROM sqlite_master WHERE type = 'table';";
    const dbQueryPractice = "SELECT name FROM sqlite_master WHERE type = 'table';";
    const dbQueryAttempts = "SELECT name FROM sqlite_master WHERE type = 'table';";
    const dbQuerySuperSync = "SELECT name FROM sqlite_master WHERE type = 'table';";
    const periodicSyncDuration = 5000;
    const apiRouteProgress = "https://api.ambition.guru/api/gprs/v2/super-sync/sync-records";
    const apiRoutePractice = "https://api.ambition.guru/api/gprs/v2/super-sync/sync-records";
    const apiRouteAttempts = "https://api.ambition.guru/api/gprs/v2/super-sync/sync-records";
    const apiRouteSuperSync = "https://api.ambition.guru/api/gprs/v2/super-sync/sync-records";
    const fingerprint = "3fb538378fb30a5c";
    const authorization = "Bearer eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiJ9.eyJhdWQiOiIxIiwianRpIjoiZjFmZDE0ZGJlMTU0YTgyY2MxYjMzNTMyMzc5OTBkY2M5YzkzNTRmYjY4OWQwYmY3MjBmNzQ3N2YwNDU4NDgxMjhjNzIwMDBhNjE2M2I3NjYiLCJpYXQiOjE3NDUzMTQxOTUuMjY4ODYsIm5iZiI6MTc0NTMxNDE5NS4yNjg4NjQsImV4cCI6MTc3NjQxODE5NS4yNTM5ODEsInN1YiI6IjUiLCJzY29wZXMiOlsiY3VzdG9tZXJzIl19.KcLIhwQboCLhH-N8dvfZtlUDmk4vUp8JoKMPDk5kENKptKmSiya1QoK-amwOpGurSXTMYNWlIfuf-pEMYpNoquhy_eOInUHceMn50bQGXG5CPIabj61ScJ84SPjsJ9w03Cu7Gdhrq7ae6m-WWzB_VzPI9D63aCa1p8Ov44pCLqTgt1vdFXRtVn8HkOCY_jctcfvCKd6VzQa3qU4SfoCZttUIwwygwyzM6Jwf_nqx04t_68N6ce997iY09Cm0JDrdzwwSPBZosMZ1n4yJfegmEm0Oh3mhHzCCO29W46GqqTy9graYVSinYCKqQ0H5ePEqanffoGL9mLWtZvt3NQw_JnIXQyaJ9hADMNoanLxpUMohkA-lc3Z7Dw7C0doj2_Ymh5aUCp15hvZWWvosHpdRi1hvfEbZRHAbYJRtWnIkGMaDsQWZYaQfVRzeQO3u-WZF_co9VjsLlkk_lPPyQCOySeBkAnfDB3_2LQiJVitl4Szc-_6sPrGe0wr-ecfuZv_ZOKVA7hAOdRNgGwcI2SCHbUv_iuEa5SpOdkrYd35Rl1bdEgPiyOXh_jIe908X2MaD9vts5_fLmmN_M-MDgGfiSwdVYgY1Q8y7FPchtR9bAW_2wMvErIaFsABJ7g3bf6amHQfsYzYgSmknEN4shDVfUP7EYVGz_KAFsRXrmDd-UFk";
    const x_package_id = "1507";
    const deviceType = "android";
    const version = "1.3.32 417";

    try {
      final files = Directory(dir).listSync(recursive: false, followLinks: false);
      for (final file in files) {
        debugPrint("File/Dir: ${file.path}");
      }

      await FlutterWorkmanagerPlugin.startMonitoring(
          dir,
          dbName,
          dbQueryPractice,
          dbQueryProgress,
          dbQueryAttempts,
          dbQuerySuperSync,
          periodicSyncDuration,
          apiRouteProgress,
          apiRoutePractice,
          apiRouteAttempts,
          apiRouteSuperSync,
          fingerprint,
          authorization,
          x_package_id,
          deviceType,
          version
      );
    } catch (e) {
      debugPrint('Error starting monitoring: $e');
    }
  }

  Future<void> _loadData() async {
    final db = await DatabaseHelper.instance.database;
    final usersResult = await db.query('users');
    final usersCopyResult = await db.query('users_copy');

    setState(() {
      users = usersResult;
      isLoading = false;
    });
    usersCopyNotifier.value = usersCopyResult;
  }

  Future<void> _generateSchemaInfo() async {
    final schemaInfo = await DatabaseHelper.instance.getSchemaAsJson();
    setState(() {
      schemaJson = schemaInfo;
      showSchema = true;
    });
  }

  void _closeApp() {
    SystemNavigator.pop();
  }

  void _clearUserCopy() async {
    try {
      await DatabaseHelper.instance.clearUserCopy();
      await _loadData();
    } catch (e) {
      debugPrint('Failed to clear user copy: $e');
    }
  }

  Widget _buildList(List<Map<String, dynamic>> data, String title) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(title, style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 16)),
        const SizedBox(height: 8),
        Expanded(
          child: ListView.builder(
            itemCount: data.length,
            itemBuilder: (_, index) {
              final user = data[index];
              return Card(
                child: ListTile(
                  title: Text(user["name"] ?? ''),
                  subtitle: Text('${user["description"] ?? ''} | ${user["grade"] ?? ''}'),
                  trailing: Text(user["address"] ?? ''),
                ),
              );
            },
          ),
        )
      ],
    );
  }

  Widget _buildUsersCopyList() {
    return ValueListenableBuilder<List<Map<String, dynamic>>>(
      valueListenable: usersCopyNotifier,
      builder: (context, data, _) {
        if (isLoading || data.isEmpty) {
          return const Center(child: CircularProgressIndicator());
        }

        return Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text("Users Copy", style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16)),
            const SizedBox(height: 8),
            Expanded(
              child: ListView.builder(
                itemCount: data.length,
                itemBuilder: (_, index) {
                  final user = data[index];
                  return Card(
                    child: ListTile(
                      title: Text(user["name"] ?? ''),
                      subtitle: Text('${user["description"] ?? ''} | ${user["grade"] ?? ''}'),
                      trailing: Text(user["address"] ?? ''),
                    ),
                  );
                },
              ),
            )
          ],
        );
      },
    );
  }

  Widget _buildSchemaView() {
    return Column(
      children: [
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            const Padding(
              padding: EdgeInsets.all(16.0),
              child: Text('Database Schema (JSON)'),
            ),
            IconButton(
              icon: const Icon(Icons.close),
              onPressed: () => setState(() => showSchema = false),
            ),
          ],
        ),
        Expanded(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(16.0),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                ElevatedButton.icon(
                  onPressed: () {
                    Clipboard.setData(ClipboardData(text: schemaJson));
                  },
                  icon: const Icon(Icons.copy),
                  label: const Text('Copy to Clipboard'),
                ),
                const SizedBox(height: 16),
                Container(
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(
                    color: Colors.grey[200],
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: Text(
                    schemaJson,
                    style: const TextStyle(fontFamily: 'monospace'),
                  ),
                ),
              ],
            ),
          ),
        ),
      ],
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Database Sync Plugin UI'),
        backgroundColor: Colors.teal,
      ),
      body: showSchema
          ? _buildSchemaView()
          : Column(
        children: [
          const SizedBox(height: 10),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceEvenly,
            children: [
              ElevatedButton.icon(
                onPressed: _closeApp,
                icon: const Icon(Icons.close),
                label: const Text("Close"),
              ),
              ElevatedButton.icon(
                onPressed: _clearUserCopy,
                icon: const Icon(Icons.delete),
                label: const Text("Clear"),
              ),
              ElevatedButton.icon(
                onPressed: _generateSchemaInfo,
                icon: const Icon(Icons.schema),
                label: const Text("Show Schema"),
              ),
            ],
          ),
          const SizedBox(height: 10),
          Expanded(
            child: Row(
              children: [
                Flexible(child: _buildList(users, "Users")),
                const VerticalDivider(),
                Flexible(child: _buildUsersCopyList()),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
