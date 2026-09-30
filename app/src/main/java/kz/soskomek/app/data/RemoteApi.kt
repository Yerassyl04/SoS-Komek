package kz.soskomek.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * Minimal client for an optional backend. Only HTTPS URLs are accepted
 * (cleartext traffic is also blocked by network_security_config.xml).
 *
 * Endpoints expected on the server:
 *   POST /v1/sos-events                      – SOS event (no name, phone or medical data)
 *   POST /v1/incidents                       – anonymous incident report (goes to moderation)
 *   POST /v1/incidents/{id}/complaints       – complaint about a publication
 *   GET  /v1/incidents?lat=&lon=&radiusKm=&days= – moderated publications nearby
 */
class RemoteApi(baseUrl: String) {
    private val base = baseUrl.trim().trimEnd('/')
    val isConfigured: Boolean = base.startsWith("https://")

    suspend fun post(path: String, body: JSONObject): Boolean = withContext(Dispatchers.IO) {
        if (!isConfigured) return@withContext false
        runCatching {
            val conn = (URL(base + path).openConnection() as HttpsURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10_000
                readTimeout = 15_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
            try {
                conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                conn.responseCode in 200..299
            } finally {
                conn.disconnect()
            }
        }.getOrDefault(false)
    }

    suspend fun getArray(path: String): JSONArray? = withContext(Dispatchers.IO) {
        if (!isConfigured) return@withContext null
        runCatching {
            val conn = (URL(base + path).openConnection() as HttpsURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 15_000
            }
            try {
                if (conn.responseCode !in 200..299) null
                else JSONArray(conn.inputStream.bufferedReader().use { it.readText() })
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
    }

    suspend fun sendSosEvent(event: SosEvent): Boolean = post("/v1/sos-events", event.toServerJson())
}
