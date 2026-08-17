package io.ruv.ruflo.android.data

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

data class AgentStatus(
    val id: String,
    val name: String,
    val role: String,
    val status: String,
    val currentTask: String?
)

class RufloGatewayClient {
    fun listAgents(baseUrl: String, bearerToken: String?): List<AgentStatus> {
        val endpoint = endpoint(baseUrl, "/api/v1/agents")
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Accept", "application/json")
            bearerToken?.takeIf { it.isNotBlank() }?.let {
                setRequestProperty("Authorization", "Bearer $it")
            }
        }

        try {
            val statusCode = connection.responseCode
            val stream = if (statusCode in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
            check(statusCode in 200..299) {
                "بوابة Ruflo أعادت HTTP $statusCode${body.takeIf { it.isNotBlank() }?.let { ": ${it.take(160)}" }.orEmpty()}"
            }
            return parseAgents(body)
        } finally {
            connection.disconnect()
        }
    }

    internal fun endpoint(baseUrl: String, path: String): String {
        val normalized = baseUrl.trim().removeSuffix("/")
        val uri = runCatching { URI(normalized) }.getOrElse {
            throw IllegalArgumentException("عنوان البوابة غير صالح.")
        }
        require(uri.scheme == "https" && !uri.host.isNullOrBlank()) {
            "استخدم عنوان HTTPS كاملاً، مثل https://ruflo.example.com"
        }
        return "$normalized$path"
    }

    private fun parseAgents(payload: String): List<AgentStatus> {
        val root = JSONTokener(payload).nextValue()
        val agents = when (root) {
            is JSONArray -> root
            is JSONObject -> root.optJSONArray("agents") ?: root.optJSONArray("data") ?: JSONArray()
            else -> JSONArray()
        }

        return buildList {
            for (index in 0 until agents.length()) {
                val agent = agents.optJSONObject(index) ?: continue
                val id = agent.optString("id", agent.optString("agent_id", "agent-$index"))
                add(
                    AgentStatus(
                        id = id,
                        name = agent.optString("name", id),
                        role = agent.optString("role", agent.optString("type", "agent")),
                        status = agent.optString("status", "unknown"),
                        currentTask = agent.optString("currentTask", agent.optString("task", "")).ifBlank { null }
                    )
                )
            }
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 8_000
        const val READ_TIMEOUT_MS = 12_000
    }
}
