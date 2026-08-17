package io.ruv.ruflo.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.ruv.ruflo.android.data.AgentControlAction
import io.ruv.ruflo.android.data.AgentStatus
import io.ruv.ruflo.android.ui.DashboardUiState
import io.ruv.ruflo.android.ui.DashboardViewModel
import io.ruv.ruflo.android.ui.PendingAgentControl
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
    val authorizationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.completeSignIn(result.data)
    }

    LaunchedEffect(state.errorMessage, state.infoMessage) {
        val message = state.errorMessage ?: state.infoMessage
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.clearMessage()
        }
    }

    state.pendingControl?.let { pending ->
        ControlConfirmationDialog(
            pending = pending,
            onDismiss = viewModel::cancelControl,
            onConfirm = viewModel::confirmControl
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Ruflo Companion", fontWeight = FontWeight.Bold)
                        Text("مراقبة وتحكم مؤمّن", style = MaterialTheme.typography.labelSmall)
                    }
                },
                actions = {
                    IconButton(
                        onClick = viewModel::refreshAgents,
                        enabled = state.isAuthenticated && !state.isLoading
                    ) {
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
            onClientIdChange = viewModel::updateOAuthClientId,
            onSave = viewModel::saveConnection,
            onSignIn = { viewModel.beginSignIn(authorizationLauncher::launch) },
            onSignOut = viewModel::signOut,
            onRefresh = viewModel::refreshAgents,
            onStop = { viewModel.requestControl(it, AgentControlAction.STOP) },
            onRestart = { viewModel.requestControl(it, AgentControlAction.RESTART) }
        )
    }
}

@androidx.compose.runtime.Composable
private fun DashboardContent(
    state: DashboardUiState,
    contentPadding: PaddingValues,
    onBaseUrlChange: (String) -> Unit,
    onClientIdChange: (String) -> Unit,
    onSave: () -> Unit,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onRefresh: () -> Unit,
    onStop: (AgentStatus) -> Unit,
    onRestart: (AgentStatus) -> Unit
) {
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
                    Text("مصادقة وتفويض", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "تستخدم هذه الواجهة OpenID Connect عبر المتصفح وAuthorization Code مع PKCE. يلزم أن يمنح خادمك agents.read وagents.control قبل إتاحة التحكم.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = state.baseUrl,
                        onValueChange = onBaseUrlChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("عنوان جهة إصدار OIDC / البوابة") },
                        placeholder = { Text("https://ruflo.example.com") },
                        singleLine = true,
                        enabled = !state.isAuthenticating
                    )
                    OutlinedTextField(
                        value = state.oauthClientId,
                        onValueChange = onClientIdChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("معرّف عميل OAuth لتطبيق Android") },
                        placeholder = { Text("ruflo-android-companion") },
                        singleLine = true,
                        enabled = !state.isAuthenticating
                    )
                    AuthenticationStatus(state)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onSave, enabled = !state.isAuthenticating) {
                            Text("حفظ")
                        }
                        if (state.isAuthenticated) {
                            Button(onClick = onSignOut) { Text("إنهاء الجلسة") }
                        } else {
                            Button(onClick = onSignIn, enabled = !state.isAuthenticating) {
                                if (state.isAuthenticating) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.height(18.dp).width(18.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(Modifier.width(8.dp))
                                }
                                Text(if (state.isAuthenticating) "جارٍ فتح المصادقة" else "تسجيل الدخول")
                            }
                        }
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
                Button(onClick = onRefresh, enabled = state.isAuthenticated && !state.isLoading) {
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
                        text = if (state.isAuthenticated) {
                            "لا توجد بيانات بعد. حدّث القائمة بعد التحقق من صلاحية agents.read في البوابة."
                        } else {
                            "سجّل الدخول أولًا بجلسة OAuth لعرض الوكلاء والتحكم بهم."
                        },
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        items(state.agents, key = { it.id }) { agent ->
            AgentCard(
                agent = agent,
                isControlAllowed = state.isControlAuthorized,
                isExecuting = state.controlInProgressForAgentId == agent.id,
                onStop = onStop,
                onRestart = onRestart
            )
        }
    }
}

@androidx.compose.runtime.Composable
private fun AuthenticationStatus(state: DashboardUiState) {
    val text = when {
        state.isAuthenticated && state.isControlAuthorized -> "الجلسة صالحة للتحكم: agents.read وagents.control"
        state.isAuthenticated -> "الجلسة صالحة للقراءة، لكنها لا تملك agents.control"
        else -> "لا توجد جلسة مصادق عليها"
    }
    AssistChip(
        onClick = {},
        label = { Text(text) },
        colors = AssistChipDefaults.assistChipColors(
            labelColor = if (state.isControlAuthorized) Color(0xFF0B8F56) else Color(0xFF765E00)
        )
    )
    state.sessionExpiresAtEpochMs?.let { expiry ->
        Text("تنتهي الجلسة تقريبًا عند ${formatTime(expiry)}.", style = MaterialTheme.typography.bodySmall)
    }
}

@androidx.compose.runtime.Composable
private fun AgentCard(
    agent: AgentStatus,
    isControlAllowed: Boolean,
    isExecuting: Boolean,
    onStop: (AgentStatus) -> Unit,
    onRestart: (AgentStatus) -> Unit
) {
    Card {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(agent.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("${agent.role} · ${agent.id}", style = MaterialTheme.typography.bodySmall)
                }
                AssistChip(
                    onClick = {},
                    label = { Text(agent.status) },
                    colors = AssistChipDefaults.assistChipColors(labelColor = statusColor(agent.status))
                )
            }
            agent.currentTask?.let { task ->
                Text(task, style = MaterialTheme.typography.bodyMedium)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onRestart(agent) },
                    enabled = isControlAllowed && !isExecuting
                ) {
                    Text("إعادة تشغيل")
                }
                Button(
                    onClick = { onStop(agent) },
                    enabled = isControlAllowed && !isExecuting,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    if (isExecuting) {
                        CircularProgressIndicator(
                            modifier = Modifier.height(16.dp).width(16.dp),
                            color = MaterialTheme.colorScheme.onError,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("إيقاف")
                }
            }
            if (!isControlAllowed) {
                Text("يتطلب التحكم نطاق agents.control.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun ControlConfirmationDialog(
    pending: PendingAgentControl,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تأكيد ${pending.action.label}") },
        text = {
            Text(
                "سيُرسل التطبيق أمر ${pending.action.label} إلى الوكيل ${pending.agent.name} (${pending.agent.id}) عبر بوابتك المصادق عليها. يجب أن تسجّل البوابة هذا الإجراء في سجل التدقيق. هل تريد المتابعة؟"
            )
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = if (pending.action == AgentControlAction.STOP) {
                    ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                } else {
                    ButtonDefaults.buttonColors()
                }
            ) {
                Text("تأكيد ${pending.action.label}")
            }
        }
    )
}

private fun statusColor(status: String): Color = when (status.lowercase()) {
    "running", "active", "healthy", "online" -> Color(0xFF0B8F56)
    "failed", "error", "offline" -> Color(0xFFB3261E)
    else -> Color(0xFF765E00)
}

private fun formatTime(epochMs: Long): String =
    DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(epochMs))
