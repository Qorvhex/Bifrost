package com.bifrost.twp.ui.components

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bifrost.twp.R
import com.bifrost.twp.core.BifrostBridgeService
import com.bifrost.twp.core.BridgeState
import com.bifrost.twp.data.ProxyRepository
import com.bifrost.twp.model.ProxyConfig
import com.bifrost.twp.ui.theme.CardBackground
import com.bifrost.twp.ui.theme.CardStroke
import com.bifrost.twp.ui.theme.DangerRed
import com.bifrost.twp.ui.theme.DarkBackground
import com.bifrost.twp.ui.theme.NeonCyan
import com.bifrost.twp.ui.theme.NeonEmerald
import com.bifrost.twp.ui.theme.TextMuted
import com.bifrost.twp.ui.theme.TextPrimary
import com.bifrost.twp.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    repository: ProxyRepository,
    onLaunchQrScanner: () -> Unit
) {
    val context = LocalContext.current

    val proxies by repository.proxiesFlow.collectAsState()
    val activeProxy by repository.activeProxyFlow.collectAsState()
    val localPort by repository.localPortFlow.collectAsState()

    val bridgeState by BifrostBridgeService.bridgeState.collectAsState()
    val activeConnections by BifrostBridgeService.connectionCount.collectAsState()

    // Dialog States
    var showAddEditDialog by remember { mutableStateOf(false) }
    var editingProxy by remember { mutableStateOf<ProxyConfig?>(null) }
    var sharingProxy by remember { mutableStateOf<ProxyConfig?>(null) }
    var showPortDialog by remember { mutableStateOf(false) }

    // FAB Speed Dial State
    var isFabExpanded by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "BIFROST",
                            color = TextPrimary,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp,
                            fontSize = 20.sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(NeonCyan)
                        )
                    }
                },
                actions = {
                    // Telegram Channel
                    IconButton(onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/Qorvhex_Channel")).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(intent)
                        } catch (_: Exception) {}
                    }) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_telegram),
                            contentDescription = "Telegram Channel",
                            tint = Color.Unspecified,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // GitHub Repository
                    IconButton(onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Qorvhex/Bifrost")).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(intent)
                        } catch (_: Exception) {}
                    }) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_github),
                            contentDescription = "GitHub Repository",
                            tint = Color.Unspecified,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Local Port Settings
                    IconButton(onClick = { showPortDialog = true }) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = "Settings",
                            tint = TextSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground
                )
            )
        },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Speed Dial Sub-actions
                AnimatedVisibility(
                    visible = isFabExpanded,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // 1. Manual Add
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.action_add_manual),
                                color = TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(CardBackground)
                                    .border(1.dp, CardStroke, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            SmallFloatingActionButton(
                                onClick = {
                                    isFabExpanded = false
                                    editingProxy = null
                                    showAddEditDialog = true
                                },
                                containerColor = CardBackground,
                                contentColor = NeonCyan,
                                shape = CircleShape
                            ) {
                                Icon(Icons.Outlined.Edit, contentDescription = "Add Manually", modifier = Modifier.size(18.dp))
                            }
                        }

                        // 2. Smart Clipboard Import
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.action_paste_clipboard),
                                color = TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(CardBackground)
                                    .border(1.dp, CardStroke, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            SmallFloatingActionButton(
                                onClick = {
                                    isFabExpanded = false
                                    importFromClipboard(context, repository)
                                },
                                containerColor = CardBackground,
                                contentColor = NeonEmerald,
                                shape = CircleShape
                            ) {
                                Icon(Icons.Outlined.ContentPaste, contentDescription = "Import Clipboard", modifier = Modifier.size(18.dp))
                            }
                        }

                        // 3. Scan QR Code
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.action_scan_qr),
                                color = TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(CardBackground)
                                    .border(1.dp, CardStroke, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            SmallFloatingActionButton(
                                onClick = {
                                    isFabExpanded = false
                                    onLaunchQrScanner()
                                },
                                containerColor = CardBackground,
                                contentColor = NeonCyan,
                                shape = CircleShape
                            ) {
                                Icon(Icons.Outlined.QrCodeScanner, contentDescription = "Scan QR", modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }

                // Power Toggle Floating Action Button (Turn Proxy On / Off)
                val isBridgeRunning = bridgeState != BridgeState.STOPPED
                FloatingActionButton(
                    onClick = {
                        if (isBridgeRunning) {
                            repository.setBridgeEnabled(false)
                            BifrostBridgeService.stop(context)
                            Toast.makeText(context, "Proxy bridge stopped", Toast.LENGTH_SHORT).show()
                        } else {
                            if (activeProxy != null) {
                                repository.setBridgeEnabled(true)
                                BifrostBridgeService.start(context)
                                Toast.makeText(context, "Proxy bridge started (Zero-Idle)", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Please select or add a worker config first", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    containerColor = CardBackground,
                    contentColor = if (isBridgeRunning) NeonEmerald else DangerRed.copy(alpha = 0.85f),
                    shape = CircleShape,
                    modifier = Modifier
                        .size(56.dp)
                        .border(
                            width = if (isBridgeRunning) 2.dp else 1.2.dp,
                            color = if (isBridgeRunning) NeonEmerald else CardStroke,
                            shape = CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Outlined.PowerSettingsNew,
                        contentDescription = if (isBridgeRunning) "Stop Proxy" else "Start Proxy",
                        modifier = Modifier.size(26.dp)
                    )
                }

                // Main FAB
                FloatingActionButton(
                    onClick = { isFabExpanded = !isFabExpanded },
                    containerColor = NeonCyan,
                    contentColor = DarkBackground,
                    shape = CircleShape,
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        imageVector = if (isFabExpanded) Icons.Default.Close else Icons.Default.Add,
                        contentDescription = "Add Proxy",
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Status Header
            item {
                StatusHeader(
                    bridgeState = bridgeState,
                    activeConnections = activeConnections,
                    localPort = localPort,
                    activeProxy = activeProxy,
                    onEditPortClick = { showPortDialog = true }
                )
            }

            // Section Title
            item {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SAVED WORKERS",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "${proxies.size} configured",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }
            }

            // Empty State
            if (proxies.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.msg_no_proxies),
                            color = TextMuted,
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }

            // Proxies List
            items(proxies, key = { it.id }) { config ->
                ProxyCard(
                    config = config,
                    onSelect = {
                        repository.setActiveProxy(config.id)
                        if (isBridgeRunning) {
                            BifrostBridgeService.start(context)
                        }
                    },
                    onEdit = {
                        editingProxy = config
                        showAddEditDialog = true
                    },
                    onDelete = {
                        repository.deleteProxy(config.id)
                    },
                    onShare = {
                        sharingProxy = config
                    }
                )
            }

            // Bottom Spacing for FAB
            item {
                Spacer(modifier = Modifier.height(72.dp))
            }
        }
    }

    // Add / Edit Proxy Dialog
    if (showAddEditDialog) {
        AddEditProxyDialog(
            initialConfig = editingProxy,
            onDismiss = {
                showAddEditDialog = false
                editingProxy = null
            },
            onSave = { updatedConfig ->
                repository.addOrUpdateProxy(updatedConfig, makeActive = true)
                if (isBridgeRunning) {
                    BifrostBridgeService.start(context)
                }
                showAddEditDialog = false
                editingProxy = null
            }
        )
    }

    // Share Modal Dialog
    sharingProxy?.let { config ->
        ShareProxyDialog(
            config = config,
            onDismiss = { sharingProxy = null }
        )
    }

    // Settings Port Dialog
    if (showPortDialog) {
        SettingsPortDialog(
            currentPort = localPort,
            onDismiss = { showPortDialog = false },
            onSavePort = { newPort ->
                repository.setLocalPort(newPort)
                showPortDialog = false
            }
        )
    }
}

private fun importFromClipboard(context: Context, repository: ProxyRepository) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = clipboard.primaryClip
    if (clip != null && clip.itemCount > 0) {
        val text = clip.getItemAt(0).text?.toString()
        if (!text.isNullOrBlank()) {
            val imported = repository.importFromLink(text, makeActive = true)
            if (imported != null) {
                BifrostBridgeService.start(context)
                Toast.makeText(context, context.getString(R.string.msg_proxy_saved), Toast.LENGTH_SHORT).show()
                return
            }
        }
    }
    Toast.makeText(context, context.getString(R.string.msg_invalid_clipboard), Toast.LENGTH_SHORT).show()
}
