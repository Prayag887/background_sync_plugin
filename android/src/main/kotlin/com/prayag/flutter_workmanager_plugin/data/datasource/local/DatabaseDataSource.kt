package com.prayag.flutter_workmanager_plugin.workmanager.data.datasource.local

import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.prayag.flutter_workmanager_plugin.domain.model.TableConfig
import org.json.JSONArray
import org.json.JSONObject

class DatabaseDataSource {
    companion object {
        private const val TAG = "DatabaseDataSource"
        private const val BATCH_SIZE = 5000
    }

    fun fetchDataBatch(db: SQLiteDatabase, tableConfig: TableConfig, offset: Int): Pair<JSONArray, Int> {
        val batch = JSONArray()
        val paginatedQuery = "${tableConfig.selectQuery} LIMIT $BATCH_SIZE OFFSET $offset"

        db.rawQuery(paginatedQuery, null).use { cursor ->
            while (cursor.moveToNext()) {
                val row = JSONObject()
                for (i in 0 until cursor.columnCount) {
                    val columnName = cursor.getColumnName(i)
                    val columnValue = cursor.getString(i)
                    row.put(columnName, columnValue ?: JSONObject.NULL)
                }
                batch.put(row)
            }
            return Pair(batch, cursor.count)
        }
    }

    fun storeResponseData(db: SQLiteDatabase, tableConfig: TableConfig, apiResponse: JSONArray) {
        if (apiResponse.length() == 0) return

        Log.d("DatabaseDataSource", "Inserting data: $apiResponse")

        try {
            db.beginTransaction()

            // Process each response item
            for (i in 0 until apiResponse.length()) {
                val jsonObject = apiResponse.getJSONObject(i)

                // Replace placeholders in the insert query with actual values
                var insertQuery = tableConfig.insertQuery

                // Get all keys to process
                val keys = mutableListOf<String>()
                val iterator = jsonObject.keys()
                while (iterator.hasNext()) {
                    keys.add(iterator.next())
                }

                // For each key in the JSON response, replace the placeholder in the query
                for (key in keys) {
                    val value = jsonObject.optString(key, "")
                    // Replace placeholders in format :key or ?
                    insertQuery = insertQuery.replace(":$key", "'$value'")
                }

                // Replace any remaining ? placeholders with NULL (simplified approach)
                insertQuery = insertQuery.replace("?", "NULL")

                // Execute the query
                try {
                    db.execSQL(insertQuery)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to execute insert query: $insertQuery", e)
                }
            }

            db.setTransactionSuccessful()
            Log.d(TAG, "Stored ${apiResponse.length()} rows into ${tableConfig.name}")
        } catch (e: Exception) {
            Log.e(TAG, "Database insert failed: ${e.message}", e)
        } finally {
            db.endTransaction()
        }
    }
}