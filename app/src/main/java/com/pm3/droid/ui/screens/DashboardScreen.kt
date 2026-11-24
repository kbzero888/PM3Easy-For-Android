package com.pm3.droid.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pm3.droid.engine.Pm3Engine
import com.pm3.droid.model.CardFrequency
import com.pm3.droid.model.ConnectionState
import com.pm3.droid.ui.theme.HfCyan
import com.pm3.droid.ui.theme.LfAmber

@Composable
fun DashboardScreen(
    engine: Pm3Engine,
    onNavigateToDumps: () -> Unit
) {
    val connState by engine.connectionState.collectAsState()
    val detectedCard by engine.detectedCard.collectAsState()
    val tuneResult by engine.tuneResult.collectAsState()

    val isReady = connState == ConnectionState.READY
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    var showT5577Dialog by remember { mutableStateOf(false) }
    var em410xInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Antenna Health Card (hw tune)
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Sensors,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "天线健康度检测 (hw tune)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    FilledTonalButton(
                        onClick = { engine.sendCommand("hw tune") },
                        enabled = isReady,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("一键测压", fontSize = 12.sp)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    VoltageGauge(
                        label = "HF 13.56MHz",
                        voltage = tuneResult.hfVoltage,
                        maxVoltage = 45f,
                        accentColor = HfCyan,
                        modifier = Modifier.weight(1f)
                    )
                    VoltageGauge(
                        label = "LF 125kHz",
                        voltage = tuneResult.lfVoltage,
                        maxVoltage = 50f,
                        accentColor = LfAmber,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // 2. Dynamic Detected Card Highlight Banner
        AnimatedVisibility(visible = detectedCard != null) {
            detectedCard?.let { card ->
                val accent = if (card.frequency == CardFrequency.HF) HfCyan else LfAmber
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.5.dp, accent)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AssistChip(
                                onClick = {},
                                label = {
                                    Text(
                                        text = "${card.frequency.name} · ${card.type}",
                                        fontWeight = FontWeight.SemiBold,
                                        color = accent
                                    )
                                }
                            )
                            IconButton(
                                onClick = {
                                    clipboard.setText(AnnotatedString(card.uid))
                                    Toast.makeText(context, "已复制卡号: ${card.uid}", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "复制卡号", modifier = Modifier.size(18.dp))
                            }
                        }

                        Text(
                            text = "UID: ${card.uid}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        if (card.atqa != null || card.sak != null) {
                            Text(
                                text = "ATQA: ${card.atqa ?: "--"}   |   SAK: ${card.sak ?: "--"}",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (card.frequency == CardFrequency.HF) {
                                Button(
                                    onClick = { engine.sendCommand("hf mf autopwn") },
                                    enabled = isReady,
                                    colors = ButtonDefaults.buttonColors(containerColor = HfCyan, contentColor = Color.Black),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("全自动破解", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                                OutlinedButton(
                                    onClick = {
                                        val cleanUid = card.uid.replace(" ", "")
                                        engine.sendCommand("hf 14a sim -t 1 -u $cleanUid")
                                    },
                                    enabled = isReady,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("模拟此 UID", fontSize = 12.sp)
                                }
                            } else {
                                Button(
                                    onClick = {
                                        em410xInput = card.uid.replace(" ", "")
                                        showT5577Dialog = true
                                    },
                                    enabled = isReady,
                                    colors = ButtonDefaults.buttonColors(containerColor = LfAmber, contentColor = Color.Black),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("写入 T5577", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                                OutlinedButton(
                                    onClick = {
                                        val cleanId = card.uid.replace(" ", "")
                                        engine.sendCommand("lf em 410x sim --id $cleanId")
                                    },
                                    enabled = isReady,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("低频模拟", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. HF 13.56MHz Action Grid
        SectionHeader(title = "高频操作 (HF 13.56MHz)", accentColor = HfCyan)

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionGridCard(
                title = "HF 一键寻卡",
                subtitle = "hf search",
                icon = Icons.Default.Nfc,
                accent = HfCyan,
                enabled = isReady,
                modifier = Modifier.weight(1f),
                onClick = { engine.sendCommand("hf search") }
            )
            ActionGridCard(
                title = "M1 全自动破解",
                subtitle = "hf mf autopwn",
                icon = Icons.Default.VpnKey,
                accent = HfCyan,
                enabled = isReady,
                modifier = Modifier.weight(1f),
                onClick = { engine.sendCommand("hf mf autopwn") }
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionGridCard(
                title = "写入魔术卡",
                subtitle = "从卡包选择 cload",
                icon = Icons.Default.EditNote,
                accent = HfCyan,
                enabled = true,
                modifier = Modifier.weight(1f),
                onClick = onNavigateToDumps
            )
            ActionGridCard(
                title = "M1 卡模拟",
                subtitle = "从卡包选择 sim",
                icon = Icons.Default.Contactless,
                accent = HfCyan,
                enabled = true,
                modifier = Modifier.weight(1f),
                onClick = onNavigateToDumps
            )
        }

        // 4. LF 125kHz Action Grid
        SectionHeader(title = "低频操作 (LF 125kHz)", accentColor = LfAmber)

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionGridCard(
                title = "LF 一键寻卡",
                subtitle = "lf search",
                icon = Icons.Default.WifiTethering,
                accent = LfAmber,
                enabled = isReady,
                modifier = Modifier.weight(1f),
                onClick = { engine.sendCommand("lf search") }
            )
            ActionGridCard(
                title = "读取 EM410x",
                subtitle = "lf em 410x reader",
                icon = Icons.Default.Badge,
                accent = LfAmber,
                enabled = isReady,
                modifier = Modifier.weight(1f),
                onClick = { engine.sendCommand("lf em 410x reader") }
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionGridCard(
                title = "写入 T5577",
                subtitle = "lf em 410x clone",
                icon = Icons.Default.SaveAs,
                accent = LfAmber,
                enabled = isReady,
                modifier = Modifier.weight(1f),
                onClick = {
                    em410xInput = detectedCard?.takeIf { it.frequency == CardFrequency.LF }?.uid?.replace(" ", "") ?: ""
                    showT5577Dialog = true
                }
            )
            ActionGridCard(
                title = "低频模拟",
                subtitle = "lf em 410x sim",
                icon = Icons.Default.CellTower,
                accent = LfAmber,
                enabled = isReady,
                modifier = Modifier.weight(1f),
                onClick = {
                    val id = detectedCard?.takeIf { it.frequency == CardFrequency.LF }?.uid?.replace(" ", "")
                    if (!id.isNullOrBlank()) {
                        engine.sendCommand("lf em 410x sim --id $id")
                    } else {
                        Toast.makeText(context, "请先执行 LF 寻卡获取卡号", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
    }

    if (showT5577Dialog) {
        AlertDialog(
            onDismissRequest = { showT5577Dialog = false },
            title = { Text("写入 EM410x 卡号到 T5577") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("请输入 10 位 16 进制 EM410x 卡号（例如 0F0368568B）：", fontSize = 13.sp)
                    OutlinedTextField(
                        value = em410xInput,
                        onValueChange = { em410xInput = it.uppercase().filter { c -> c in '0'..'9' || c in 'A'..'F' }.take(10) },
                        singleLine = true,
                        label = { Text("10位 HEX ID") }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (em410xInput.length == 10) {
                            engine.sendCommand("lf em 410x clone --id $em410xInput")
                            showT5577Dialog = false
                        } else {
                            Toast.makeText(context, "卡号长度必须为 10 位 16 进制字符", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("立即写入")
                }
            },
            dismissButton = {
                TextButton(onClick = { showT5577Dialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun VoltageGauge(
    label: String,
    voltage: Float?,
    maxVoltage: Float,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val progress = ((voltage ?: 0f) / maxVoltage).coerceIn(0f, 1f)
    val isLow = voltage != null && voltage < 10f
    val barColor = if (isLow) MaterialTheme.colorScheme.error else accentColor

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = if (voltage != null) "%.2f V".format(voltage) else "-- V",
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = barColor
            )
        }
        LinearProgressIndicator(
            progress = { progress },
            color = barColor,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
        )
    }
}

@Composable
private fun SectionHeader(title: String, accentColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            color = accentColor,
            shape = RoundedCornerShape(2.dp),
            modifier = Modifier
                .width(4.dp)
                .height(16.dp)
        ) {}
        Spacer(Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ActionGridCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (enabled) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.surfaceVariant),
        modifier = modifier.height(82.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) accent else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(22.dp)
            )
            Column {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
