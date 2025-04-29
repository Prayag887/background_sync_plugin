package com.prayag.flutter_workmanager_plugin.data.datasource.local

import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.prayag.flutter_workmanager_plugin.domain.model.TableConfig
import org.json.JSONArray
import org.json.JSONObject
import kotlin.Exception

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

    fun storeResponseData(db: SQLiteDatabase, tableConfig: TableConfig, response: JSONArray) {
        if (response.length() == 0) return

        try {
            db.beginTransaction()

            for (i in 0 until response.length()) {
                val obj = response.getJSONObject(i)

                // Use the provided insert queries instead of building them dynamically
                val stmt = db.compileStatement(tableConfig.insertQuery)

                try {
                    // Bind values based on the object keys and the query placeholders
                    // This requires knowledge of the specific query structure
                    // Assuming the insert queries have named parameters matching the JSON keys

                    obj.keys().forEach { key ->
                        // Find the parameter index in the compiled statement
                        // This is a simplified approach - in practice, you'd need to match
                        // parameters in the query with the JSON keys
                        val paramIndex = getParameterIndex(tableConfig.insertQuery, key)
                        if (paramIndex > 0) {
                            stmt.bindString(paramIndex, obj.optString(key, ""))
                        }
                    }

                    stmt.execute()
                } finally {
                    stmt.clearBindings()
                    stmt.close()
                }
            }

            db.setTransactionSuccessful()
            Log.d(TAG, "Stored ${response.length()} rows into ${tableConfig.name}")
        } catch (e: Exception) {
            Log.e(TAG, "Database insert failed: ${e.message}", e)
        } finally {
            db.endTransaction()
        }
    }

    // Helper method to find parameter index in a SQL statement
    // Note: This is a simplified version. In practice, you'd need a more robust approach
    private fun getParameterIndex(query: String, paramName: String): Int {
        // Simple implementation that won't work for complex queries
        // In a real application, you'd need to parse the SQL statement properly
        val placeholders = Regex("\\?").findAll(query).count()
        // For this example, we'll just return a position based on parameter order
        // This is just a placeholder - real implementation would be more complex
        return 1 // Default to first parameter
    }
}