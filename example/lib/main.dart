import 'dart:io';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_workmanager_plugin/flutter_workmanager_plugin.dart';
import 'package:path_provider/path_provider.dart';
import 'package:sqflite/sqflite.dart';
import 'package:path/path.dart';

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
  static const platform = MethodChannel('background_database_sync');

  List<Map<String, dynamic>> langProgress = [];
  List<Map<String, dynamic>> langPractices = [];  // Changed variable name
  String schemaJson = '';
  bool showSchema = false;

  @override
  void initState() {
    super.initState();
    _startMonitoring();
    _loadData();
    _setupNativeListener();
  }

  Future<void> _startMonitoring() async {
    final dir = await getDatabasesPath();
    const dbName = 'app_database.db';
    const dbQueryProgress = "SELECT name FROM sqlite_master WHERE type = 'table';";
    const dbQueryPractice = "SELECT name FROM sqlite_master WHERE type = 'table';";
    const dbQueryAttempt = "SELECT name FROM sqlite_master WHERE type = 'table';";
    const dbQuerySuperSync = "SELECT name FROM sqlite_master WHERE type = 'table';";

    try {
      final files = Directory(dir).listSync(recursive: false, followLinks: false);

      if (files.isEmpty) {
        debugPrint("Directory is empty: $dir");
      } else {
        for (final file in files) {
          debugPrint("File/Dir: ${file.path}");
        }
      }

      await FlutterWorkmanagerPlugin.startMonitoring(dir, dbName, dbQueryProgress, dbQueryPractice, dbQueryAttempt, dbQuerySuperSync);
    } catch (e) {
      debugPrint('Error starting monitoring: $e');
    }
  }

  void _setupNativeListener() {
    platform.setMethodCallHandler((call) async {
      if (call.method == "onDataExtracted") {
        setState(() {
          schemaJson = call.arguments as String;
          showSchema = true;
        });
      }
    });
  }

  Future<void> _loadData() async {
    final db = await DatabaseHelper.instance.database;
    final result = await db.query('lang_progress');
    final practiceResult = await db.query('lang_practices');

    setState(() {
      langProgress = result;
      langPractices = practiceResult;
    });
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

  Widget _buildList(List<Map<String, dynamic>> data, String title) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 8.0),
          child: Text(
            title,
            style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 16),
          ),
        ),
        const SizedBox(height: 8),
        Expanded(
          child: ListView.builder(
            itemCount: data.length,
            itemBuilder: (_, index) {
              final item = data[index];
              return Card(
                margin: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                child: ListTile(
                  title: Text("ID: ${item["id"]}"),
                  subtitle: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text("Customer ID: ${item["customer_id"]}"),
                      Text("Native Language ID: ${item["native_language_id"]}"),
                      Text("Language ID: ${item["language_id"]}"),
                      Text("Practice Mode: ${item["practice_mode"]}"),
                      Text("Start At: ${item["start_at"]}"),
                      Text("End At: ${item["end_at"]}"),
                    ],
                  ),
                  trailing: Text("Group: ${item["practice_group"]}"),
                ),
              );
            },
          ),
        ),
      ],
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Language Progress Tracker'),
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
                onPressed: _loadData,
                icon: const Icon(Icons.refresh),
                label: const Text("Refresh"),
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
                Flexible(child: _buildList(langProgress, "Language Progress")),
                const VerticalDivider(),
                Flexible(child: _buildList(langPractices, "Language Practices")),  // Updated to use langPractices
              ],
            ),
          ),
        ],
      ),
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
              child: Text('Extracted Data (JSON)'),
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
                Row(
                  children: [
                    ElevatedButton.icon(
                      onPressed: () {
                        Clipboard.setData(ClipboardData(text: schemaJson));
                      },
                      icon: const Icon(Icons.copy),
                      label: const Text('Copy to Clipboard'),
                    ),
                  ],
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
}
