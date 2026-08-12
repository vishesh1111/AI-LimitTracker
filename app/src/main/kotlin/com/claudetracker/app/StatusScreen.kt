package com.claudetracker.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.claudetracker.app.data.model.AccountUsage
import com.claudetracker.app.data.model.Platform

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusScreen(
    onLogout: () -> Unit,
    onAddAccount: () -> Unit,
    viewModel: StatusViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showLogoutDialog by remember { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* proceed either way */ }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = Manifest.permission.POST_NOTIFICATIONS
            if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(permission)
            }
        }
        viewModel.onScreenVisible()
    }

    LaunchedEffect(uiState) {
        if (uiState is StatusState.NoAccounts) {
            onLogout()
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Logout All Accounts?") },
            text = { Text("This will remove all saved accounts. You'll need to log in again.") },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutDialog = false
                        viewModel.logoutAll()
                        onLogout()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Logout All") }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Usage Tracker") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                actions = {
                    IconButton(onClick = { viewModel.refreshData() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = { showLogoutDialog = true }) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Logout All")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddAccount,
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Account")
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val state = uiState) {
                is StatusState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is StatusState.NoAccounts -> { /* navigates via LaunchedEffect */ }
                is StatusState.Error -> {
                    ErrorBanner(
                        message = state.message,
                        actionLabel = "Retry",
                        onAction = { viewModel.refreshData() },
                        visible = true,
                        modifier = Modifier.align(Alignment.TopCenter)
                    )
                }
                is StatusState.Loaded -> {
                    var isVisible by remember { mutableStateOf(false) }
                    LaunchedEffect(state) {
                        isVisible = true
                    }
                    
                    val grouped = state.accounts.groupBy { it.account.platform }
                    val platformOrder = listOf(Platform.CLAUDE, Platform.CODEX)
                    val expandedStates = remember { mutableStateMapOf<Platform, Boolean>() }

                    AnimatedVisibility(
                        visible = isVisible,
                        enter = fadeIn(tween(600)) + slideInVertically(tween(600), initialOffsetY = { 100 })
                    ) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            item { Spacer(modifier = Modifier.height(8.dp)) }

                        for (platform in platformOrder) {
                            val accounts = grouped[platform] ?: continue
                            val isExpanded = expandedStates[platform] ?: true
                            platformSection(
                                platform = platform,
                                accounts = accounts,
                                expanded = isExpanded,
                                onToggleExpand = { expandedStates[platform] = !isExpanded },
                                onRemove = { viewModel.removeAccount(it) },
                                onRelogin = onAddAccount
                            )
                        }

                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "Last updated: ${state.lastUpdated}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (state.isRefreshing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier
                                            .padding(start = 8.dp)
                                            .size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                }
                            }
                        }
                        item { Spacer(modifier = Modifier.height(72.dp)) }
                    }
                    } // End AnimatedVisibility
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════
// ── Expandable Platform Section ──────────────────────
// ═══════════════════════════════════════════════════════

private fun LazyListScope.platformSection(
    platform: Platform,
    accounts: List<AccountUsage>,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onRemove: (String) -> Unit,
    onRelogin: () -> Unit
) {
    val iconRes = when (platform) {
        Platform.CLAUDE -> R.drawable.ic_claude
        Platform.CODEX -> R.drawable.ic_codex
    }

    item(key = "header_${platform.name}") {
        val interactionSource = remember { MutableInteractionSource() }
        val isPressed by interactionSource.collectIsPressedAsState()
        val scale by androidx.compose.animation.core.animateFloatAsState(
            targetValue = if (isPressed) 0.97f else 1f,
            animationSpec = tween(150),
            label = "header_scale"
        )
        
        Column(modifier = Modifier.animateItem()) {
            // Platform header — clickable to expand/collapse
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .scale(scale)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = androidx.compose.foundation.LocalIndication.current,
                        onClick = onToggleExpand
                    ),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.foundation.Image(
                            painter = painterResource(id = iconRes),
                            contentDescription = platform.displayName,
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(6.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = platform.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${accounts.size} account${if (accounts.size != 1) "s" else ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (expanded) "Collapse" else "Expand",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }


    if (expanded) {
        items(
            items = accounts,
            key = { "account_${platform.name}_${it.account.id}" }
        ) { accountUsage ->
            Box(
                modifier = Modifier
                    .animateItem()
                    .padding(top = 8.dp)
            ) {
                SwipeToDismissAccountCard(
                    accountUsage = accountUsage,
                    onRemove = { onRemove(accountUsage.account.id) },
                    onRelogin = onRelogin
                )
            }
        }
        item(key = "spacer_${platform.name}") {
            Spacer(modifier = Modifier.height(12.dp).animateItem())
        }
    }
}

// ═══════════════════════════════════════════════════════
// ── Swipe-to-dismiss Account Card ────────────────────
// ═══════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToDismissAccountCard(
    accountUsage: AccountUsage,
    onRemove: () -> Unit,
    onRelogin: () -> Unit = {}
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { dismissValue ->
            if (dismissValue == SwipeToDismissBoxValue.EndToStart) {
                showDeleteDialog = true
                false // Don't dismiss yet — wait for confirmation
            } else false
        }
    )

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Remove Account?") },
            text = { Text("Are you sure you want to remove \"${accountUsage.account.displayName}\"? You'll need to log in again to add it back.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteDialog = false
                        onRemove()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val color by animateColorAsState(
                when (dismissState.targetValue) {
                    SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.errorContainer
                    else -> Color.Transparent
                },
                label = "dismiss_bg"
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color, MaterialTheme.shapes.large)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(Icons.Default.Delete, contentDescription = "Remove", tint = MaterialTheme.colorScheme.onErrorContainer)
            }
        },
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true
    ) {
        AccountCard(accountUsage, onRelogin = onRelogin)
    }
}

// ═══════════════════════════════════════════════════════
// ── Account Card ─────────────────────────────────────
// ═══════════════════════════════════════════════════════

@Composable
private fun AccountCard(accountUsage: AccountUsage, onRelogin: () -> Unit = {}) {
    val account = accountUsage.account
    val data = accountUsage.usageData

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: account name + plan badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = account.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = account.planName.ifBlank { "Unknown" },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            MaterialTheme.shapes.small
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            when {
                accountUsage.isAuthExpired -> {
                    Text("Session expired", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onRelogin, modifier = Modifier.fillMaxWidth()) {
                        Text("Re-login to refresh session")
                    }
                }
                accountUsage.error != null -> {
                    Text(
                        "Error: ${accountUsage.error}", 
                        style = MaterialTheme.typography.bodyMedium, 
                        color = MaterialTheme.colorScheme.error
                    )
                }
                data != null -> {
                    if (account.platform == Platform.CODEX) {
                        // Codex: Monthly limit + optional resets info
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            UsageCard(
                                label = "Monthly", percent = data.sessionPercentUsed,
                                resetTime = formatResetTime(data.sessionResetTimestamp),
                                color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f)
                            )
                        }
                        if (data.resetsAvailable >= 0) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.medium,
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Usage limit resets",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = if (data.resetsAvailable > 0) "${data.resetsAvailable} available" else "No resets available",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (data.resetsAvailable > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    )
                                }
                            }
                        }
                    } else {
                        // Claude: Session + Weekly display
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            UsageCard(
                                label = "Session (5h)", percent = data.sessionPercentUsed,
                                resetTime = formatResetTime(data.sessionResetTimestamp),
                                color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f)
                            )
                            UsageCard(
                                label = "Weekly", percent = data.weeklyPercentUsed,
                                resetTime = formatResetTime(data.weeklyResetTimestamp),
                                color = MaterialTheme.colorScheme.secondary, modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
                else -> {
                    Text("Loading usage data...", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private fun formatResetTime(isoTimestamp: String): String {
    if (isoTimestamp.isBlank()) return "N/A"
    return try {
        val parts = isoTimestamp.split("T")
        if (parts.size >= 2) {
            val timePart = parts[1].substringBefore(".")
            val datePart = parts[0].substring(5)
            "$datePart $timePart"
        } else isoTimestamp
    } catch (_: Exception) {
        isoTimestamp
    }
}
