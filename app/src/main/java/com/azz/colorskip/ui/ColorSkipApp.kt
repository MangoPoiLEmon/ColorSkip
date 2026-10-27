package com.azz.colorskip.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.azz.colorskip.ui.components.KeepAliveDialog
import com.azz.colorskip.ui.components.SnapshotDialog
import com.azz.colorskip.ui.screens.DashboardScreen
import com.azz.colorskip.ui.screens.LogsScreen
import com.azz.colorskip.ui.screens.RulesScreen
import com.azz.colorskip.ui.screens.SettingsScreen
import com.azz.colorskip.ui.theme.Ink
import com.azz.colorskip.ui.theme.InkSoft
import com.azz.colorskip.ui.theme.Lime
import com.azz.colorskip.ui.theme.TextMuted

private data class NavItem(val label: String, val icon: ImageVector)

private val navItems = listOf(
    NavItem("首页", Icons.Default.Home),
    NavItem("规则", Icons.Default.List),
    NavItem("日志", Icons.Default.Info),
    NavItem("设置", Icons.Default.Settings),
)

@Composable
fun ColorSkipApp(vm: MainViewModel, keepAliveRequest: Int) {
    val permissions by vm.permissions.collectAsStateWithLifecycle()
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showKeepAlive by rememberSaveable { mutableStateOf(false) }
    var notificationAsked by rememberSaveable { mutableStateOf(false) }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { vm.refreshPermissions() }

    LaunchedEffect(keepAliveRequest) {
        if (keepAliveRequest > 0) showKeepAlive = true
    }
    LaunchedEffect(Unit) {
        vm.messages.collect { message ->
            snackbar.showSnackbar(message)
        }
    }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 && !notificationAsked && !permissions.notifications) {
            notificationAsked = true
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    BackHandler(enabled = tab != 0) { tab = 0 }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Ink, InkSoft, Color(0xFF08110D)),
                ),
            ),
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = {
                SnackbarHost(snackbar) { data ->
                    Snackbar(
                        snackbarData = data,
                        containerColor = Color(0xFF1B2B22),
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        actionColor = Lime,
                    )
                }
            },
            bottomBar = {
                NavigationBar(
                    containerColor = Ink.copy(alpha = 0.97f),
                    tonalElevation = 0.dp,
                    modifier = Modifier.navigationBarsPadding(),
                ) {
                    navItems.forEachIndexed { index, item ->
                        NavigationBarItem(
                            selected = tab == index,
                            onClick = { tab = index },
                            icon = {
                                Icon(
                                    item.icon,
                                    contentDescription = item.label,
                                    modifier = Modifier.padding(1.dp),
                                )
                            },
                            label = { Text(item.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Ink,
                                selectedTextColor = Lime,
                                indicatorColor = Lime,
                                unselectedIconColor = TextMuted,
                                unselectedTextColor = TextMuted,
                            ),
                        )
                    }
                }
            },
        ) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                when (tab) {
                    0 -> DashboardScreen(
                        vm = vm,
                        permissions = permissions,
                        onOpenKeepAlive = { showKeepAlive = true },
                        onOpenRules = { tab = 1 },
                        onOpenLogs = { tab = 2 },
                    )
                    1 -> RulesScreen(vm)
                    2 -> LogsScreen(vm)
                    3 -> SettingsScreen(
                        vm = vm,
                        permissions = permissions,
                        onOpenKeepAlive = { showKeepAlive = true },
                    )
                }
            }
        }
    }

    if (showKeepAlive) {
        KeepAliveDialog(
            permissions = permissions,
            onDismiss = { showKeepAlive = false },
            onOpenRecents = { vm.openRecents() },
            onToggleRecentsLocked = {
                vm.setRecentsLocked(!permissions.recentsLocked)
            },
        )
    }

    snapshot?.let { text ->
        SnapshotDialog(text = text, onDismiss = { vm.dismissSnapshot() })
    }
}
