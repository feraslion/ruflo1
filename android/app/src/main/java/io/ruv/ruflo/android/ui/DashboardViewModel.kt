package io.ruv.ruflo.android.ui

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.ruv.ruflo.android.data.AgentControlAction
import io.ruv.ruflo.android.data.AgentStatus
import io.ruv.ruflo.android.data.ConnectionConfig
import io.ruv.ruflo.android.data.ConnectionSettings
import io.ruv.ruflo.android.data.OAuthSession
import io.ruv.ruflo.android.data.OidcAuthenticationManager
import io.ruv.ruflo.android.data.RufloGatewayClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PendingAgentControl(
    val agent: AgentStatus,
    val action: AgentControlAction
)

data class DashboardUiState(
    val baseUrl: String = "",
    val oauthClientId: String = "",
    val isAuthenticating: Boolean = false,
    val isLoading: Boolean = false,
    val controlInProgressForAgentId: String? = null,
    val agents: List<AgentStatus> = emptyList(),
    val isAuthenticated: Boolean = false,
    val isControlAuthorized: Boolean = false,
    val sessionExpiresAtEpochMs: Long? = null,
    val pendingControl: PendingAgentControl? = null,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
    val lastRefreshEpochMs: Long? = null
)

class DashboardViewModel(application: Application) : AndroidViewModel(application) {
    private val settings = ConnectionSettings(application.applicationContext)
    private val gatewayClient = RufloGatewayClient()
    private val authenticationManager = OidcAuthenticationManager(application.applicationContext)
    private var config = settings.load()
    private val _uiState = MutableStateFlow(stateFrom(config))
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    fun updateBaseUrl(value: String) = _uiState.update { it.copy(baseUrl = value, errorMessage = null) }

    fun updateOAuthClientId(value: String) = _uiState.update { it.copy(oauthClientId = value, errorMessage = null) }

    fun saveConnection() {
        val state = _uiState.value
        val endpointChanged = config.baseUrl != state.baseUrl.trim() || config.oauthClientId != state.oauthClientId.trim()
        config = if (endpointChanged) {
            ConnectionConfig(baseUrl = state.baseUrl.trim(), oauthClientId = state.oauthClientId.trim())
        } else {
            config.copy(baseUrl = state.baseUrl.trim(), oauthClientId = state.oauthClientId.trim())
        }
        settings.save(config)
        _uiState.value = stateFrom(config).copy(
            infoMessage = if (endpointChanged) {
                "تم حفظ الاتصال. سجّل الدخول لإنشاء جلسة للبوابة المحددة."
            } else {
                "تم حفظ إعداد الاتصال."
            }
        )
    }

    fun beginSignIn(onAuthorizationIntentReady: (Intent) -> Unit) {
        saveConnection()
        val savedConfig = config
        if (savedConfig.baseUrl.isBlank() || savedConfig.oauthClientId.isBlank()) {
            _uiState.update { it.copy(errorMessage = "أدخل عنوان البوابة ومعرّف عميل OAuth أولًا.") }
            return
        }

        _uiState.update { it.copy(isAuthenticating = true, errorMessage = null, infoMessage = null) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { authenticationManager.createAuthorizationIntent(savedConfig.baseUrl, savedConfig.oauthClientId) }
            }
            result.onSuccess(onAuthorizationIntentReady).onFailure { error ->
                _uiState.update {
                    it.copy(
                        isAuthenticating = false,
                        errorMessage = error.message ?: "تعذر بدء مصادقة OpenID Connect."
                    )
                }
            }
        }
    }

    fun completeSignIn(responseIntent: Intent?) {
        _uiState.update { it.copy(isAuthenticating = true, errorMessage = null) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { authenticationManager.completeAuthorization(responseIntent) }
            }
            result.onSuccess(::applySession).onFailure { error ->
                _uiState.update {
                    it.copy(
                        isAuthenticating = false,
                        errorMessage = error.message ?: "لم تكتمل المصادقة."
                    )
                }
            }
        }
    }

    fun signOut() {
        config = config.copy(
            accessToken = "",
            accessTokenExpiresAtEpochMs = 0L,
            grantedScopes = emptySet(),
            subject = ""
        )
        settings.save(config)
        _uiState.value = stateFrom(config).copy(infoMessage = "تم إنهاء الجلسة وحذف رمز الوصول من التطبيق.")
    }

    fun clearMessage() = _uiState.update { it.copy(errorMessage = null, infoMessage = null) }

    fun refreshAgents() {
        val accessToken = accessTokenFor("agents.read") ?: return
        _uiState.update { it.copy(isLoading = true, errorMessage = null, infoMessage = null) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { gatewayClient.listAgents(config.baseUrl, accessToken) }
            }
            result.onSuccess { agents ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        agents = agents,
                        lastRefreshEpochMs = System.currentTimeMillis(),
                        infoMessage = "تم تحديث قائمة الوكلاء (${agents.size})."
                    )
                }
            }.onFailure(::handleGatewayFailure)
        }
    }

    fun requestControl(agent: AgentStatus, action: AgentControlAction) {
        if (accessTokenFor("agents.control") == null) return
        _uiState.update { it.copy(pendingControl = PendingAgentControl(agent, action), errorMessage = null) }
    }

    fun cancelControl() = _uiState.update { it.copy(pendingControl = null) }

    fun confirmControl() {
        val pending = _uiState.value.pendingControl ?: return
        val accessToken = accessTokenFor("agents.control") ?: return
        _uiState.update {
            it.copy(
                pendingControl = null,
                controlInProgressForAgentId = pending.agent.id,
                errorMessage = null,
                infoMessage = null
            )
        }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    gatewayClient.controlAgent(config.baseUrl, accessToken, pending.agent.id, pending.action)
                    gatewayClient.listAgents(config.baseUrl, accessToken)
                }
            }
            result.onSuccess { agents ->
                _uiState.update {
                    it.copy(
                        controlInProgressForAgentId = null,
                        agents = agents,
                        lastRefreshEpochMs = System.currentTimeMillis(),
                        infoMessage = "تم إرسال أمر ${pending.action.label} للوكيل ${pending.agent.name} وتحديث الحالة."
                    )
                }
            }.onFailure(::handleGatewayFailure)
        }
    }

    private fun applySession(session: OAuthSession) {
        config = config.copy(
            accessToken = session.accessToken,
            accessTokenExpiresAtEpochMs = session.expiresAtEpochMs,
            grantedScopes = session.grantedScopes
        )
        settings.save(config)
        _uiState.value = stateFrom(config).copy(
            infoMessage = "تمت المصادقة. يمكنك الآن قراءة الحالة والتحكم وفق الصلاحيات الممنوحة."
        )
        refreshAgents()
    }

    private fun accessTokenFor(requiredScope: String): String? {
        if (config.baseUrl.isBlank() || config.oauthClientId.isBlank()) {
            _uiState.update { it.copy(errorMessage = "احفظ عنوان البوابة ومعرّف عميل OAuth أولًا.") }
            return null
        }
        if (!isSessionUsable(config)) {
            _uiState.update { it.copy(errorMessage = "سجّل الدخول بجلسة OAuth صالحة قبل المتابعة.") }
            return null
        }
        if (requiredScope !in config.grantedScopes) {
            _uiState.update { it.copy(errorMessage = "لا تتضمن الجلسة الحالية الصلاحية $requiredScope المطلوبة.") }
            return null
        }
        return config.accessToken
    }

    private fun handleGatewayFailure(error: Throwable) {
        val message = error.message ?: "تعذر الاتصال ببوابة Ruflo."
        _uiState.update {
            it.copy(
                isLoading = false,
                controlInProgressForAgentId = null,
                errorMessage = message
            )
        }
    }

    private fun stateFrom(config: ConnectionConfig): DashboardUiState = DashboardUiState(
        baseUrl = config.baseUrl,
        oauthClientId = config.oauthClientId,
        isAuthenticated = isSessionUsable(config),
        isControlAuthorized = isSessionUsable(config) && "agents.control" in config.grantedScopes,
        sessionExpiresAtEpochMs = config.accessTokenExpiresAtEpochMs.takeIf { it > 0L }
    )

    private fun isSessionUsable(config: ConnectionConfig): Boolean =
        config.accessToken.isNotBlank() && config.accessTokenExpiresAtEpochMs > System.currentTimeMillis() + SESSION_SKEW_MS

    override fun onCleared() {
        authenticationManager.close()
        super.onCleared()
    }

    private companion object {
        const val SESSION_SKEW_MS = 60_000L
    }
}
