package com.prayag.flutter_workmanager_plugin.network

import android.util.Log
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import kotlin.system.exitProcess

object ApiSender {
    fun sendPayload(payload: JSONArray) {
        Thread {
            var attempt = 1
            while (attempt <= 5) {
                try {
                    Log.d("TAG", "Sending attempt $attempt")
                    val url = URL("https://jsonplaceholder.typicode.com/posts")
                    val conn = (url.openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        doOutput = true
                        setRequestProperty("Content-Type", "application/json")
                        connectTimeout = 5000
                        readTimeout = 5000
                    }

                    conn.outputStream.use { it.write(payload.toString().toByteArray()) }

                    val responseCode = conn.responseCode
                    val responseBody = conn.inputStream.bufferedReader().readText()

                    Log.d("TAG", "Response Code: $responseCode")
                    Log.d("TAG", "Response: $responseBody")

                    conn.disconnect()

                    if (responseCode in 200..299) {
                        Log.d("TAG", "Success, exiting app")
                        exitProcess(0)
                    }
                } catch (e: Exception) {
                    Log.e("TAG", "API error: ${e.message}")
                }
                attempt++
                Thread.sleep(2000)
            }

            Log.e("TAG", "Failed after 5 attempts")
        }.start()
    }
}
