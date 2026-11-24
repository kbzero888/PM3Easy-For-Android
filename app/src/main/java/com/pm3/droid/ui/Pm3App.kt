package com.pm3.droid.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pm3.droid.engine.Pm3Engine
import com.pm3.droid.model.AppTab
import com.pm3.droid.model.ConnectionState
import com.pm3.droid.model.ThemeMode
import com.pm3.droid.ui.screens.DashboardScreen
import com.pm3.droid.ui.screens.DumpsScreen
import com.pm3.droid.ui.screens.TerminalScreen
import com.pm3.droid.ui.theme.HfCyan
import com.pm3.droid.ui.theme.LfAmber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Pm3App(
    engine: Pm3Engine,
    themeMode: ThemeMode,
    onCycleTheme: () -> Unit,
    keepScreenOn: Boolean,
    onToggleKeepScreenOn: () -> Unit
) {
    val connState by engine.connectionState.collectAsState()
    val statusText by engine.statusText.collectAsState()
    val enumProgress by engine.enumerationProgress.collectAsState()
    val fwVersion by engine.firmwareVersion.collectAsState()

    var currentTab by remember { mutableStateOf(AppTab.DASHBOARD) }

    val infiniteTransition = rememberInfiniteTransition(label = "led_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(550, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    val ledColor = when (connState) {
        ConnectionState.DISCONNECTED -> Color.Gray
        ConnectionState.ENUMERATING -> LfAmber
        ConnectionState.READY -> Color(0xFF22C55E)
        ConnectionState.BUSY -> HfCyan
        ConnectionState.ERROR -> MaterialTheme.colorScheme.error
    }

    val ledAlpha = if (connState == ConnectionState.ENUMERATING || connState == ConnectionState.BUSY) pulseAlpha else 1f

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .alpha(ledAlpha)
                                    .clip(CircleShape)
                                    .background(ledColor)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = statusText,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                                Text(
                                    text = fwVersion,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                    },
                    actions = {
                        // Manual Connect / Retry Button
                        if (connState == ConnectionState.DISCONNECTED || connState == ConnectionState.ERROR) {
                            IconButton(onClick = { engine.onUsbAttachedOrConnect() }) {
                                Icon(Icons.Default.Usb, contentDescription = "连接 PM3")
                            }
                        }

                        // Emergency Interrupt (Ctrl+C)
                        if (connState == ConnectionState.BUSY) {
                            IconButton(onClick = { engine.sendInterrupt() }) {
                                Icon(
                                    Icons.Default.StopCircle,
                                    contentDescription = "中断当前操作",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }

                        // Keep Screen On Toggle
                        IconButton(onClick = onToggleKeepScreenOn) {
                            Icon(
                                imageVector = if (keepScreenOn) Icons.Default.BrightnessHigh else Icons.Default.BrightnessLow,
                                contentDescription = "屏幕常亮",
                                tint = if (keepScreenOn) LfAmber else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Theme Switcher (Dark / Light / System)
                        IconButton(onClick = onCycleTheme) {
                            val icon = when (themeMode) {
                                ThemeMode.DARK -> Icons.Default.DarkMode
                                ThemeMode.LIGHT -> Icons.Default.LightMode
                                ThemeMode.SYSTEM -> Icons.Default.BrightnessAuto
                            }
                            Icon(icon, contentDescription = "切换主题")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )

                if (connState == ConnectionState.ENUMERATING) {
                    LinearProgressIndicator(
                        progress = { enumProgress },
                        color = LfAmber,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                    )
                }
            }
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                AppTab.entries.forEach { tab ->
                    val icon = when (tab) {
                        AppTab.DASHBOARD -> Icons.Default.Dashboard
                        AppTab.TERMINAL -> Icons.Default.Terminal
                        AppTab.DUMPS -> Icons.Default.SdStorage
                    }
                    NavigationBarItem(
                        selected = currentTab == tab,
                        onClick = { currentTab = tab },
                        icon = { Icon(icon, contentDescription = tab.title) },
                        label = { Text(tab.title) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentTab) {
                AppTab.DASHBOARD -> DashboardScreen(
                    engine = engine,
                    onNavigateToDumps = { currentTab = AppTab.DUMPS }
                )
                AppTab.TERMINAL -> TerminalScreen(engine = engine)
                AppTab.DUMPS -> DumpsScreen(
                    engine = engine,
                    onSwitchToTerminal = { currentTab = AppTab.TERMINAL }
                )
            }
        }
    }
}
