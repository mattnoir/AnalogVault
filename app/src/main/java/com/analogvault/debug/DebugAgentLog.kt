package com.analogvault.debug

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object DebugAgentLog {
    private const val SESSION = "cb6867"
    private val endpoints = listOf(
        "http://10.0.2.2:7777/ingest/79477bbc-e77c-4e0c-aecf-514c4645539a",
        "http://127.0.0.1:7777/ingest/79477bbc-e77c-4e0c-aecf-514c4645539a"
    )

    fun log(
        location: String,
        message: String,
        hypothesisId: String,
        data: Map<String, Any?> = emptyMap(),
        runId: String = "pre-fix"
    ) {
        Thread {
            val payload = JSONObject().apply {
                put("sessionId", SESSION)
                put("location", location)
                put("message", message)
                put("hypothesisId", hypothesisId)
                put("timestamp", System.currentTimeMillis())
                put("runId", runId)
                put("data", JSONObject(data))
            }.toString()
            for (endpoint in endpoints) {
                try {
                    val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        setRequestProperty("Content-Type", "application/json")
                        setRequestProperty("X-Debug-Session-Id", SESSION)
                        doOutput = true
                        connectTimeout = 2000
                        readTimeout = 2000
                    }
                    conn.outputStream.use { it.write(payload.toByteArray()) }
                    conn.inputStream.use { }
                    conn.disconnect()
                    break
                } catch (_: Exception) {
                    // try next endpoint
                }
            }
        }.start()
    }
}
