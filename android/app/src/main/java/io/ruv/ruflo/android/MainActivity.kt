package io.ruv.ruflo.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.ruv.ruflo.android.data.AgentStatus
import io.ruv.ruflo.android.ui.DashboardUiState
import io.ruv.ruflo.android.ui.DashboardViewModel
import io.ruv.ruflo.android.ui.theme.RufloTheme
import java.text.DateFormat
import java.util.Date

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RufloTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        RufloCompanionScreen()
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@androidx.compose.runtime.Composable
private fun RufloCompanionScreen(viewModel: DashboardViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.errorMessage, state.infoMessage) {
        val message = state.errorMessage ?: state.infoMessage
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Ruflo Companion", fontWeight = FontWeight.Bold)
                        Text("مراقبة بوابة الوكلاء", style = MaterialTheme.typography.labelSmall)
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refreshAgents, enabled = !state.isLoading) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "تحديث الوكلاء")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        DashboardContent(
            state = state,
            contentPadding = padding,
            onBaseUrlChange = viewModel::updateBaseUrl,
            onTokenChange = viewModel::updateBearerToken,
            onSave = viewModel::saveConnection,
            onRefresh = viewModel::refreshAgents
        )
    }
}

@androidx.compose.runtime.Composable
private fun DashboardContent(
    state: DashboardUiState,
    contentPadding: PaddingValues,
    onBaseUrlChange: (String) -> Unit,
    onTokenChange: (String) -> Unit,
    onSave: () -> Unit,
    onRefresh: () -> Unit
) {
    var showToken by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("اتصال آمن", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "أدخل عنوان بوابة متوافقة تدعم HTTPS. يطلب التطبيق GET /api/v1/agents ويقبل مصفوفة JSON أو كائنًا يحتوي agents.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = state.baseUrl,
                        onValueChange = onBaseUrlChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("عنوان البوابة") },
                        placeholder = { Text("https://ruflo.example.com") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = state.bearerToken,
                        onValueChange = onTokenChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("رمز Bearer (اختياري)") },
                        singleLine = true,
                        visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            TextButton(onClick = { showToken = !showToken }) {
                                Text(if (showToken) "إخفاء" else "إظهار")
                            }
                        }
                    )
                    Button(onClick = onSave, modifier = Modifier.align(Alignment.Start)) {
                        Text("حفظ الاتصال")
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("الوكلاء", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    val lastRefresh = state.lastRefreshEpochMs
                    if (lastRefresh != null) {
                        Text("آخر تحديث: ${formatTime(lastRefresh)}", style = MaterialTheme.typography.bodySmall)
                    }
                }
                Button(onClick = onRefresh, enabled = !state.isLoading) {
                    if (state.isLoading) {
                        CircularProgressIndicator(modifier = Modifier.height(18.dp).width(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (state.isLoading) "جارٍ التحديث" else "تحديث")
                }
            }
        }

        if (!state.isLoading && state.agents.isEmpty()) {
            item {
                Card {
                    Text(
                        text = "لا توجد بيانات بعد. احفظ عنوان بوابة Ruflo ثم حدّث القائمة.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        items(state.agents, key = { it.id }) { agent ->
            AgentCard(agent)
        }
    }
}

@androidx.compose.runtime.Composable
private fun AgentCard(agent: AgentStatus) {
    Card {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(agent.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("${agent.role} · ${agent.id}", style = MaterialTheme.typography.bodySmall)
                }
                AssistChip(
                    onClick = {},
                    label = { Text(agent.status) },
                    colors = androidx.compose.material3.AssistChipDefaults.assistChipColors(
                        labelColor = statusColor(agent.status)
                    )
                )
            }
            agent.currentTask?.let { task ->
                Text(task, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

private fun statusColor(status: String): Color = when (status.lowercase()) {
    "running", "active", "healthy", "online" -> Color(0xFF0B8F56)
    "failed", "error", "offline" -> Color(0xFFB3261E)
    else -> Color(0xFF765E00)
}

private fun formatTime(epochMs: Long): String =
    DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(epochMs))
