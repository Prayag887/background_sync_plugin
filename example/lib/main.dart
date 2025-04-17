import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_workmanager_plugin/flutter_workmanager_plugin.dart';
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
  List<Map<String, dynamic>> users = [];
  List<Map<String, dynamic>> usersCopy = [];
  String schemaJson = '';
  bool showSchema = false;

  @override
  void initState() {
    super.initState();
    _startMonitoring();
    _loadData();
  }

  Future<void> _startMonitoring() async {
    try {
      await FlutterWorkmanagerPlugin.startMonitoring();
    } catch (e) {
      debugPrint('Error starting monitoring: $e');
    }
  }

  Future<void> _loadData() async {
    // Load from SQLite database
    final db = await DatabaseHelper.instance.database;

    final usersResult = await db.query('users');
    final usersCopyResult = await db.query('users_copy');

    setState(() {
      users = usersResult;
      usersCopy = usersCopyResult;
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

  void _clearUserCopy() async {
    try {
      await DatabaseHelper.instance.clearUserCopy();
      await _loadData(); // reload UI
    } catch (e) {
      debugPrint('Failed to clear user copy: $e');
    }
  }

  Widget _buildList(List<Map<String, dynamic>> data, String title) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(title,
            style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 16)),
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
                label: const Text("Clear Copy"),
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
                Flexible(child: _buildList(usersCopy, "Users Copy")),
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
            Padding(
              padding: const EdgeInsets.all(16.0),
              child: Text(
                'Database Schema (JSON)',
                // style: Theme.of(context).textTheme.titleLarge,
              ),
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
                        // ScaffoldMessenger.of(context).showSnackBar(
                        //     const SnackBar(content: Text('Schema copied to clipboard'))
                        // );
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