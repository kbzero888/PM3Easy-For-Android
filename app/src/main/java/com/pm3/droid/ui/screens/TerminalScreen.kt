package com.pm3.droid.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pm3.droid.engine.Pm3Engine
import com.pm3.droid.model.ConnectionState
import com.pm3.droid.model.LogLevel
import com.pm3.droid.ui.theme.HfCyan
import com.pm3.droid.ui.theme.LfAmber

private val quickCommands = listOf(
    "hf search",
    "hf 14a info",
    "hf mf autopwn",
    "hf mf fchk",
    "hf mf info",
    "lf search",
    "lf em 410x reader",
    "hw tune",
    "hw status",
    "hf list 14a"
)

@Composable
fun TerminalScreen(engine: Pm3Engine) {
    val logs by engine.logs.collectAsState()
    val connState by engine.connectionState.collectAsState()
    val isConnected = connState == ConnectionState.READY || connState == ConnectionState.BUSY

    var inputCommand by remember { mutableStateOf("") }
    var autoScroll by remember { mutableStateOf(true) }
    val commandHistory = remember { mutableStateListOf<String>() }
    var historyIndex by remember { mutableIntStateOf(-1) }

    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current

    LaunchedEffect(logs.size, autoScroll) {
        if (autoScroll && logs.isNotEmpty()) {
            listState.scrollToItem(logs.lastIndex)
        }
    }

    fun submitCommand(cmd: String) {
        val clean = cmd.trim()
        if (clean.isEmpty()) return
        if (commandHistory.lastOrNull() != clean) {
            commandHistory.add(clean)
        }
        historyIndex = -1
        engine.sendCommand(clean)
        inputCommand = ""
        focusManager.clearFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                focusManager.clearFocus()
            }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Console Header Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "冰人原生命令行 (${logs.size} 行)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                FilterChip(
                    selected = autoScroll,
                    onClick = { autoScroll = !autoScroll },
                    label = { Text("自动滚屏", fontSize = 11.sp) },
                    modifier = Modifier.height(28.dp)
                )
                IconButton(
                    onClick = { engine.clearLogs() },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = "清空终端", modifier = Modifier.size(18.dp))
                }
            }
        }

        // Terminal Output Box
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF0A0D12),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(10.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(logs, key = { it.id }) { line ->
                    val textColor = when (line.level) {
                        LogLevel.SUCCESS -> Color(0xFF4ADE80)
                        LogLevel.ERROR -> Color(0xFFF87171)
                        LogLevel.WARNING -> LfAmber
                        LogLevel.INFO -> HfCyan
                        LogLevel.COMMAND -> Color(0xFF60A5FA)
                        LogLevel.NORMAL -> Color(0xFFD1D5DB)
                    }
                    Text(
                        text = line.text,
                        color = textColor,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        fontWeight = if (line.level == LogLevel.COMMAND) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        // Horizontal Quick Command Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            quickCommands.forEach { cmd ->
                SuggestionChip(
                    onClick = { inputCommand = cmd },
                    label = {
                        Text(
                            text = cmd,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        )
                    },
                    modifier = Modifier.height(30.dp)
                )
            }
        }

        // Bottom Input Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // History Up Button
            IconButton(
                onClick = {
                    if (commandHistory.isNotEmpty()) {
                        historyIndex = if (historyIndex <= 0) commandHistory.lastIndex else historyIndex - 1
                        inputCommand = commandHistory[historyIndex]
                    }
                },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(Icons.Default.History, contentDescription = "上一条指令", modifier = Modifier.size(20.dp))
            }

            OutlinedTextField(
                value = inputCommand,
                onValueChange = { inputCommand = it },
                placeholder = { Text("输入 pm3 指令 (如 hf 14a info)...", fontSize = 13.sp) },
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { submitCommand(inputCommand) }),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            )

            FilledIconButton(
                onClick = { submitCommand(inputCommand) },
                enabled = isConnected && inputCommand.isNotBlank(),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.size(48.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "发送")
            }
        }
    }
}
