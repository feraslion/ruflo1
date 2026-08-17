package io.ruv.ruflo.android.data

import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.BufferedReader
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

enum class AgentControlAction(val endpointSegment: String, val label: String) {
    STOP("stop", "إيقاف"),
    RESTART("restart", "إعادة تشغيل")
}

class RufloGatewayClient {
    fun listAgents(baseUrl: String, accessToken: String): List<AgentStatus> {
        val response = request(baseUrl, "/api/v1/agents", "GET", accessToken)
        return parseAgents(response)
    }

    /**
     * Calls a privileged Ruflo gateway action. The gateway must verify the OAuth access token,
     * require the agents.control scope, record an audit event, and return 204 or JSON.
     */
    fun controlAgent(
        baseUrl: String,
        accessToken: String,
        agentId: String,
        action: AgentControlAction
    ) {
        require(agentId.isNotBlank()) { "معرّف الوكيل غير صالح." }
        val encodedAgentId = Uri.encode(agentId)
        request(
            baseUrl = baseUrl,
            path = "/api/v1/agents/$encodedAgentId/${action.endpointSegment}",
            method = "POST",
            accessToken = accessToken
        )
    }

    internal fun endpoint(baseUrl: String, path: String): String {
        val normalized = baseUrl.trim().removeSuffix("/")
        val uri = runCatching { URI(normalized) }.getOrElse {
            throw IllegalArgumentException("عنوان البوابة غير صالح.")
        }
        require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo.isNullOrBlank()) {
            "استخدم عنوان HTTPS كاملاً دون بيانات اعتماد مضمنة، مثل https://ruflo.example.com"
        }
        return "$normalized$path"
    }

    private fun request(baseUrl: String, path: String, method: String, accessToken: String): String {
        require(accessToken.isNotBlank()) { "سجّل الدخول أولًا للحصول على جلسة مصادق عليها." }
        val connection = (URL(endpoint(baseUrl, path)).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("X-Ruflo-Client", "android-companion")
            if (method != "GET") {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                outputStream.use { it.write("{}".toByteArray(Charsets.UTF_8)) }
            }
        }

        try {
            val statusCode = connection.responseCode
            val body = if (statusCode in 200..299) {
                connection.inputStream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
            } else {
                connection.errorStream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
            }
            when (statusCode) {
                401 -> throw SecurityException("انتهت الجلسة أو لم تعد صالحة. سجّل الدخول مجددًا.")
                403 -> throw SecurityException("لا تملك الجلسة الحالية صلاحية agents.control.")
                in 200..299 -> return body
                else -> throw IllegalStateException("بوابة Ruflo أعادت HTTP $statusCode.")
            }
        } finally {
            connection.disconnect()
        }
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
