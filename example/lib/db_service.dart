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
    // Create users table
    await db.execute('''
      CREATE TABLE users (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        name TEXT NOT NULL,
        description TEXT,
        address TEXT,
        grade TEXT
      )
    ''');

    // Create users_copy table
    await db.execute('''
      CREATE TABLE users_copy (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        name TEXT NOT NULL,
        description TEXT,
        address TEXT,
        grade TEXT,
        sync_status INTEGER DEFAULT 0
      )
    ''');

    // Insert sample data
    await _insertSampleData(db);
  }

  Future<void> _insertSampleData(Database db) async {
    final users = [
      {
        'name': 'User A',
        'description': 'Desc A',
        'address': 'Address A',
        'grade': 'A'
      },
      {
        'name': 'User B',
        'description': 'Desc B',
        'address': 'Address B',
        'grade': 'B'
      },
      {
        'name': 'User C',
        'description': 'Desc Y',
        'address': 'Address Y',
        'grade': 'Y'
      },
      {
        'name': 'User D',
        'description': 'Desc X',
        'address': 'Address X',
        'grade': 'X'
      },
      {
        'name': 'User E',
        'description': 'Desc Z',
        'address': 'Address Z',
        'grade': 'Z'
      },
    ];

    for (final user in users) {
      await db.insert('users', user);
    }
  }

  Future<void> clearUserCopy() async {
    final db = await database;
    await db.delete('users_copy');
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

      // Safe access with null assertion operator (!) since we initialized it as non-null
      schema['tables']![tableName] = await _getTableSchema(db, tableName, tableSql);
    }

    // Handle views
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
      String? tableSql
      ) async {
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
      String tableName
      ) async {
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
      String tableName
      ) async {
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

  // Helper to print schema to console
  Future<void> printSchema() async {
    final schema = await getSchemaAsJson();
    if (kDebugMode) {
      print(schema);
    }
  }
}