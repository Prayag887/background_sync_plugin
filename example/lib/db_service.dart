import 'dart:convert';
import 'dart:io';
import 'package:flutter/foundation.dart';
import 'package:sqflite/sqflite.dart';
import 'package:path/path.dart' as path;

class DatabaseHelper {
  static final DatabaseHelper instance = DatabaseHelper._init();
  static Database? _database;

  DatabaseHelper._init();

  Future<Database> get database async {
    if (_database != null) return _database!;
    _database = await initDatabase();
    return _database!;
  }

  Future<Database> initDatabase() async {
    final dbPath = await getDatabasesPath();
    final pathToDb = path.join(dbPath, 'app_database.db');

    return await openDatabase(
      pathToDb,
      version: 1,
      onCreate: _createDB,
    );
  }

  Future _createDB(Database db, int version) async {
    await db.execute('''
      CREATE TABLE lang_progress (
        id INTEGER PRIMARY KEY,
        remote_id INTEGER,
        customer_id INTEGER,
        object_id INTEGER,
        language_id INTEGER,
        listening_progress REAL,
        speaking_progress REAL,
        writing_progress REAL,
        reading_progress REAL,
        mixed_progress REAL,
        group_name TEXT,
        model_type TEXT,
        model_id INTEGER,
        created_at TEXT,
        updated_at TEXT,
        synced_at TEXT,
        deleted_at TEXT,
        extras TEXT
      )
    ''');

    await _insertSampleData(db);
  }

  Future<void> _insertSampleData(Database db) async {
    const String jsonData = '''{
      "table_name": "lang_progress",
      "records": [
        {
          "id": 34,
          "remote_id": null,
          "customer_id": 5,
          "object_id": 112,
          "language_id": 2,
          "listening_progress": null,
          "speaking_progress": null,
          "writing_progress": 1.0,
          "reading_progress": null,
          "mixed_progress": null,
          "group_name": null,
          "model_type": "App\\\\Models\\\\Language\\\\LangObject",
          "model_id": 112,
          "created_at": "2025-04-22T15:15:31.453616",
          "updated_at": "2025-04-22T15:17:15.427977",
          "synced_at": null,
          "deleted_at": null,
          "extras": null
        },
        {
          "id": 82,
          "remote_id": null,
          "customer_id": 5,
          "object_id": 38,
          "language_id": 2,
          "listening_progress": null,
          "speaking_progress": null,
          "writing_progress": 0.0,
          "reading_progress": null,
          "mixed_progress": null,
          "group_name": null,
          "model_type": "App\\\\Models\\\\Language\\\\LangObject",
          "model_id": 38,
          "created_at": "2025-04-22T15:15:31.785278",
          "updated_at": "2025-04-22T15:17:03.872527",
          "synced_at": null,
          "deleted_at": null,
          "extras": null
        },
        {
          "id": 112,
          "remote_id": null,
          "customer_id": 5,
          "object_id": 156,
          "language_id": 2,
          "listening_progress": null,
          "speaking_progress": null,
          "writing_progress": 1.0,
          "reading_progress": null,
          "mixed_progress": null,
          "group_name": null,
          "model_type": "App\\\\Models\\\\Language\\\\LangObject",
          "model_id": 156,
          "created_at": "2025-04-22T15:15:32.065481",
          "updated_at": "2025-04-22T15:17:55.654569",
          "synced_at": null,
          "deleted_at": null,
          "extras": null
        }
      ]
    }''';

    final Map<String, dynamic> parsed = jsonDecode(jsonData);
    final List<dynamic> records = parsed['records'];

    final batch = db.batch();
    for (final record in records) {
      batch.insert('lang_progress', record as Map<String, dynamic>);
    }

    await batch.commit(noResult: true);
  }

  Future<void> clearUserCopy() async {
    try {
      final db = await database;
      final deletedCount = await db.delete('users_copy');
      debugPrint('Deleted $deletedCount records from users_copy');
    } catch (e) {
      debugPrint('Error clearing users_copy table: $e');
    }
  }

  Future<String> getSchemaAsJson() async {
    final db = await database;
    final schema = <String, dynamic>{
      'database': {
        'version': await db.getVersion(),
        'path': (await getDatabasesPath()).replaceAll(' ', '%20'),
      },
      'tables': <String, dynamic>{},
      'views': [],
    };

    final List<Map<String, dynamic>> tables = await db.rawQuery(
        "SELECT name, sql FROM sqlite_master WHERE type='table'");

    for (var tableObj in tables) {
      final tableName = tableObj['name'] as String;
      final tableSql = tableObj['sql'] as String?;

      if (_shouldSkipTable(tableName)) continue;

      schema['tables']![tableName] = await _getTableSchema(db, tableName, tableSql);
    }

    final List<Map<String, dynamic>> views = await db.rawQuery(
        "SELECT name, sql FROM sqlite_master WHERE type='view'");
    schema['views'] = views;

    return _formatJson(schema);
  }

  bool _shouldSkipTable(String tableName) {
    return tableName.startsWith('sqlite_') || tableName.startsWith('android_');
  }

  Future<Map<String, dynamic>> _getTableSchema(Database db, String tableName, String? tableSql) async {
    return {
      'create_statement': tableSql,
      'columns': await db.rawQuery("PRAGMA table_info($tableName)"),
      'indices': await _getIndexInfo(db, tableName),
      'foreign_keys': await db.rawQuery("PRAGMA foreign_key_list($tableName)"),
      'triggers': await _getTriggers(db, tableName),
    };
  }

  Future<List<Map<String, dynamic>>> _getIndexInfo(Database db, String tableName) async {
    final indices = <Map<String, dynamic>>[];
    final indexList = await db.rawQuery("PRAGMA index_list($tableName)");

    for (var index in indexList) {
      final indexName = index['name'] as String;
      final indexInfo = await db.rawQuery("PRAGMA index_info($indexName)");
      final indexXInfo = await db.rawQuery("PRAGMA index_xinfo($indexName)");

      indices.add({
        ...index,
        'info_columns': indexInfo,
        'extended_info': indexXInfo,
      });
    }

    return indices;
  }

  Future<List<Map<String, dynamic>>> _getTriggers(Database db, String tableName) async {
    return await db.rawQuery(
        "SELECT name, sql FROM sqlite_master WHERE type='trigger' AND tbl_name='$tableName'");
  }

  String _formatJson(Map<String, dynamic> schema) {
    return JsonEncoder.withIndent('  ').convert(schema);
  }

  Future<File> saveSchemaToFile({String fileName = 'schema.json'}) async {
    final jsonString = await getSchemaAsJson();
    final dbPath = await getDatabasesPath();
    final file = File(path.join(dbPath, fileName));

    await file.writeAsString(jsonString);
    return file;
  }

  Future<void> printSchema() async {
    final schema = await getSchemaAsJson();
    if (kDebugMode) {
      print(schema);
    }
  }
}
