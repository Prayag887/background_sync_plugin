package com.prayag.flutter_workmanager_plugin.data.datasource.remote

import android.util.Log
import com.prayag.flutter_workmanager_plugin.data.model.ApiResponse
import com.prayag.flutter_workmanager_plugin.data.model.SyncData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class ApiDataSource {
    companion object {
        private const val TAG = "ApiDataSource"
    }

    suspend fun postData(
        syncData: SyncData,
        apiEndpoint: String,
        credentials: ApiCredentials
    ): ApiResponse = withContext(Dispatchers.IO) {
        try {
            val url = URL(apiEndpoint)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            conn.setRequestProperty("Authorization", credentials.authorization)
            conn.setRequestProperty("X-Package-ID", credentials.packageId)
            conn.setRequestProperty("Fingerprint", credentials.fingerprint)
            conn.setRequestProperty("Device-Type", credentials.deviceType)
            conn.setRequestProperty("Version", credentials.version)
            conn.doOutput = true
            conn.connectTimeout = 30000
            conn.readTimeout = 30000

            val jsonBody = syncData.toJsonRequestBody()

            // Log request details
            Log.d(TAG, "Sending request to $apiEndpoint with ${syncData.records.length()} records")
            val truncatedBody = if (jsonBody.length > 300) "${jsonBody.substring(0, 300)}..." else jsonBody
            Log.d(TAG, "Request body sample: $truncatedBody")

            OutputStreamWriter(conn.outputStream).use { it.write(jsonBody) }

            val responseCode = conn.responseCode
            Log.d(TAG, "API response code: $responseCode")

            if (responseCode in 200..299) {
                val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()

                try {
                    val jsonResponse = JSONObject(responseText)
                    val dataArray = if (jsonResponse.has("data")) {
                        jsonResponse.getJSONArray("data")
                    } else {
                        JSONArray()
                    }
                    ApiResponse(true, dataArray)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse API response: ${e.message}", e)
                    ApiResponse(false, null)
                }
            } else {
                val errorMsg = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
                Log.e(TAG, "API request failed: $responseCode, $errorMsg")
                conn.disconnect()
                ApiResponse(false, null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "API request exception: ${e.message}", e)
            ApiResponse(false, null)
        }
    }
}

data class ApiCredentials(
    val fingerprint: String,
    val authorization: String,
    val packageId: String,
    val deviceType: String,
    val version: String
)