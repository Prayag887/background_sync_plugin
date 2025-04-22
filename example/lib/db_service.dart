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
    // First, create all tables
    await db.execute('''
    CREATE TABLE lang_progress (
      id INTEGER PRIMARY KEY,
      remote_id INTEGER,
      customer_id INTEGER,
      object_id INTEGER,
      language_id INTEGER,
      listening_progress INTEGER,
      speaking_progress INTEGER,
      writing_progress INTEGER,
      reading_progress INTEGER,
      mixed_progress INTEGER,
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

    await db.execute('''
    CREATE TABLE lang_practices (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      remote_id INTEGER,
      customer_id INTEGER,
      native_language_id INTEGER,
      language_id INTEGER,
      practice_mode INTEGER,
      start_at TEXT,
      end_at TEXT,
      extras TEXT,
      created_at TEXT,
      updated_at TEXT,
      synced_at TEXT,
      deleted_at TEXT,
      submitted_at TEXT,
      practice_group INTEGER
    );
  ''');

    // Then, insert sample data after all tables are created
    await _insertSampleData(db);
  }

  Future<void> _insertSampleData(Database db) async {
    // Insert lang_progress sample data
    final progressRecords = [
      {
        "id": 90231,
        "remote_id": null,
        "customer_id": 5,
        "object_id": 75,
        "language_id": 2,
        "listening_progress": null,
        "speaking_progress": 1,
        "writing_progress": 1,
        "reading_progress": null,
        "mixed_progress": null,
        "group_name": null,
        "model_type": "App\\Models\\Language\\LangObject",
        "model_id": 75,
        "created_at": null,
        "updated_at": "2025-04-20T16:50:14.205728",
        "synced_at": null,
        "deleted_at": null,
        "extras": null
      },
      {
        "id": 90779,
        "remote_id": 14678,
        "customer_id": 5,
        "object_id": 50,
        "language_id": 2,
        "listening_progress": null,
        "speaking_progress": null,
        "writing_progress": 0,
        "reading_progress": null,
        "mixed_progress": null,
        "group_name": null,
        "model_type": "App\\Models\\Language\\LangObject",
        "model_id": 50,
        "created_at": "2025-04-20T12:19:11.536258",
        "updated_at": "2025-04-20T16:49:27.316544",
        "synced_at": null,
        "deleted_at": null,
        "extras": null
      }
    ];

    for (final record in progressRecords) {
      await db.insert('lang_progress', record);
    }

    // Insert lang_practices sample data
    await db.insert('lang_practices', {
      "id": 15,
      "remote_id": null,
      "customer_id": 5,
      "native_language_id": 1,
      "language_id": 2,
      "practice_mode": 3,
      "start_at": "2025-04-20T16:42:13.537914",
      "end_at": "2025-04-20T16:50:32.505996",
      "extras": "{\"results\":{\"retry_attempts\":3,\"wrong_attempts\":1,\"total_questions\":10,\"correct_attempts\":1,\"skipped_attempts\":1,\"un_attempts\":7}}",
      "created_at": "2025-04-20T16:42:13.537883",
      "updated_at": "2025-04-20T16:42:13.537744",
      "synced_at": null,
      "deleted_at": null,
      "submitted_at": "2025-04-20T16:50:32.297865",
      "practice_group": 1
    });
  }

  // Enhanced schema generation method
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
        "SELECT name, sql FROM sqlite_master WHERE type='table'"
    );

    for (var tableObj in tables) {
      final tableName = tableObj['name'] as String;
      final tableSql = tableObj['sql'] as String?;

      if (_shouldSkipTable(tableName)) continue;

      schema['tables']![tableName] =
      await _getTableSchema(db, tableName, tableSql);
    }

    final List<Map<String, dynamic>> views = await db.rawQuery(
        "SELECT name, sql FROM sqlite_master WHERE type='view'"
    );
    schema['views'] = views;

    return _formatJson(schema);
  }

  bool _shouldSkipTable(String tableName) {
    return tableName.startsWith('sqlite_') || tableName.startsWith('android_');
  }

  Future<Map<String, dynamic>> _getTableSchema(
      Database db,
      String tableName,
      String? tableSql) async {
    return {
      'create_statement': tableSql,
      'columns': await db.rawQuery("PRAGMA table_info($tableName)"),
      'indices': await _getIndexInfo(db, tableName),
      'foreign_keys': await db.rawQuery("PRAGMA foreign_key_list($tableName)"),
      'triggers': await _getTriggers(db, tableName),
    };
  }

  Future<List<Map<String, dynamic>>> _getIndexInfo(
      Database db,
      String tableName) async {
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

  Future<List<Map<String, dynamic>>> _getTriggers(
      Database db,
      String tableName) async {
    return await db.rawQuery(
        "SELECT name, sql FROM sqlite_master "
            "WHERE type='trigger' AND tbl_name='$tableName'"
    );
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
