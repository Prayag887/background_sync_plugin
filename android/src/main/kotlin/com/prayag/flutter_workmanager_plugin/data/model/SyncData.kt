package com.prayag.flutter_workmanager_plugin.data.model

import org.json.JSONArray
import org.json.JSONObject

data class SyncData(
    val tableName: String,
    val records: JSONArray
) {
    fun toJsonRequestBody(): String {
        return JSONObject()
            .put("table_name", tableName)
            .put("records", records)
            .toString()
    }
}

data class ApiResponse(
    val success: Boolean,
    val data: JSONArray?
)