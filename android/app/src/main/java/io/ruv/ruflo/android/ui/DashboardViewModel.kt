package io.ruv.ruflo.android.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.ruv.ruflo.android.data.AgentStatus
import io.ruv.ruflo.android.data.ConnectionConfig
import io.ruv.ruflo.android.data.ConnectionSettings
import io.ruv.ruflo.android.data.RufloGatewayClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DashboardUiState(
    val baseUrl: String = "",
    val bearerToken: String = "",
    val isLoading: Boolean = false,
    val agents: List<AgentStatus> = emptyList(),
    val errorMessage: String? = null,
    val infoMessage: String? = null,
    val lastRefreshEpochMs: Long? = null
)

class DashboardViewModel(application: Application) : AndroidViewModel(application) {
    private val settings = ConnectionSettings(application.applicationContext)
    private val gatewayClient = RufloGatewayClient()
    private val _uiState = MutableStateFlow(DashboardUiState().with(settings.load()))
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    fun updateBaseUrl(value: String) = _uiState.update { it.copy(baseUrl = value, errorMessage = null) }

    fun updateBearerToken(value: String) = _uiState.update { it.copy(bearerToken = value, errorMessage = null) }

    fun saveConnection() {
        val state = _uiState.value
        settings.save(ConnectionConfig(state.baseUrl, state.bearerToken))
        _uiState.update { it.copy(infoMessage = "تم حفظ إعداد الاتصال مشفّرًا على هذا الجهاز.") }
    }

    fun clearMessage() = _uiState.update { it.copy(errorMessage = null, infoMessage = null) }

    fun refreshAgents() {
        val state = _uiState.value
        if (state.baseUrl.isBlank()) {
            _uiState.update { it.copy(errorMessage = "أدخل عنوان بوابة Ruflo أولًا.") }
            return
        }

        _uiState.update { it.copy(isLoading = true, errorMessage = null, infoMessage = null) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { gatewayClient.listAgents(state.baseUrl, state.bearerToken) }
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
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "تعذر الاتصال ببوابة Ruflo."
                    )
                }
            }
        }
    }

    private fun DashboardUiState.with(config: ConnectionConfig): DashboardUiState = copy(
        baseUrl = config.baseUrl,
        bearerToken = config.bearerToken
    )
}
