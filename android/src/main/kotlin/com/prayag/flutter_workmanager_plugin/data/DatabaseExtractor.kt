package com.prayag.flutter_workmanager_plugin.data

import android.content.Context
import android.content.PeriodicSync
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.prayag.flutter_workmanager_plugin.network.ApiSender
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object DatabaseExtractor {

    fun extractAndSendDatabase(context: Context, path: String?, name: String?, dbQueryProgress: String?, dbQueryPractice: String?, dbQueryAttempts: String?, dbQuerySuperSync: String?) {
        val filePath = File(path, name).absolutePath
        val dbFile = File(filePath)
        if (!dbFile.exists()) {
            Log.e("TAG", "DB doesn't exist: $filePath")
            return
        }

        val db = SQLiteDatabase.openDatabase(filePath, null, SQLiteDatabase.OPEN_READONLY)
        val payload = JSONArray()

        try {
            val cursor = db.rawQuery(dbQueryProgress!!, null)
            while (cursor.moveToNext()) {
                val tableName = cursor.getString(cursor.getColumnIndex("name"))
                val tableData = JSONObject().apply {
                    put("table_name", tableName)
                    put("records", JSONArray().apply {
                        val dataCursor = db.rawQuery(dbQueryProgress!!, null)
                        while (dataCursor.moveToNext()) {
                            val row = JSONObject()
                            for (i in 0 until dataCursor.columnCount) {
                                val col = dataCursor.getColumnName(i)
                                val valStr = dataCursor.getString(i)
                                row.put(col, if (col == "progress_data") "{\"$valStr\":$valStr}" else valStr)
                            }
                            put(row)
                        }
                        dataCursor.close()
                    })
                }
                payload.put(tableData)
            }
            cursor.close()
            db.close()
            ApiSender.sendPayload(payload)
        } catch (e: Exception) {
            Log.e("TAG", "DB extraction failed: ${e.message}")
            db.close()
        }
    }
}
