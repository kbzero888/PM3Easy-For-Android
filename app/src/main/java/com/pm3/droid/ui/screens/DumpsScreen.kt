package com.pm3.droid.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pm3.droid.engine.Pm3Engine
import com.pm3.droid.model.ConnectionState
import com.pm3.droid.model.DumpFileItem
import com.pm3.droid.ui.theme.HfCyan
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun DumpsScreen(
    engine: Pm3Engine,
    onSwitchToTerminal: () -> Unit
) {
    val dumps by engine.dumpFiles.collectAsState()
    val connState by engine.connectionState.collectAsState()
    val isReady = connState == ConnectionState.READY
    val context = LocalContext.current

    var selectedDump by remember { mutableStateOf<DumpFileItem?>(null) }
    var renamingDump by remember { mutableStateOf<DumpFileItem?>(null) }
    var newFileName by remember { mutableStateOf("") }

    val dateFormat = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }

    LaunchedEffect(Unit) {
        engine.refreshDumpFiles()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "卡片数据仓库 (Dumps)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "路径: Download/PM3_Workspace",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { engine.refreshDumpFiles() }) {
                Icon(Icons.Default.Refresh, contentDescription = "刷新列表")
            }
        }

        if (dumps.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = "暂无本地卡片数据文件",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "在工作台执行 M1 全自动破解后，.bin / .json 文件会自动出现在此处",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(dumps, key = { it.file.absolutePath }) { item ->
                    Surface(
                        onClick = { selectedDump = item },
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = item.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "UID: ${item.extractedUid}  ·  ${item.cardLabel}",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    color = HfCyan
                                )
                                Text(
                                    text = "修改时间: ${dateFormat.format(Date(item.lastModified))}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Row {
                                IconButton(
                                    onClick = {
                                        renamingDump = item
                                        newFileName = item.file.nameWithoutExtension
                                    }
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = "重命名", modifier = Modifier.size(18.dp))
                                }
                                IconButton(
                                    onClick = {
                                        engine.deleteDump(item)
                                        Toast.makeText(context, "已删除 ${item.name}", Toast.LENGTH_SHORT).show()
                                    }
                                ) {
                                    Icon(
                                        Icons.Default.DeleteOutline,
                                        contentDescription = "删除",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Hex Inspector & Write/Sim Dialog
    selectedDump?.let { dump ->
        val hexRows = remember(dump) { engine.readHexPreview(dump.file, 1024) }
        AlertDialog(
            onDismissRequest = { selectedDump = null },
            title = {
                Column {
                    Text(dump.name, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("${dump.cardLabel} | UID: ${dump.extractedUid}", fontSize = 12.sp, color = HfCyan)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "扇区 16 进制预览（红色=厂商块 / 青色=尾块密钥）",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Surface(
                        color = Color(0xFF0A0D12),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp)
                    ) {
                        LazyColumn(
                            contentPadding = PaddingValues(8.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            items(hexRows) { (blockIdx, hexStr) ->
                                val rowColor = when {
                                    blockIdx == 0 -> Color(0xFFF87171) // Manufacturer Block 0
                                    blockIdx % 4 == 3 -> HfCyan // Sector Trailer (Key A / Access / Key B)
                                    else -> Color(0xFFD1D5DB)
                                }
                                Text(
                                    text = "%02d: %s".format(blockIdx, hexStr),
                                    color = rowColor,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            val typeFlag = if (dump.sizeBytes == 4096L) "--4k" else "--1k"
                            engine.sendCommand("hf mf sim $typeFlag -f '${dump.file.absolutePath}'")
                            selectedDump = null
                            onSwitchToTerminal()
                        },
                        enabled = isReady
                    ) {
                        Text("启动模拟")
                    }
                    Button(
                        onClick = {
                            val typeFlag = if (dump.sizeBytes == 4096L) "--4k" else "--1k"
                            engine.sendCommand("hf mf cload $typeFlag -f '${dump.file.absolutePath}'")
                            selectedDump = null
                            onSwitchToTerminal()
                        },
                        enabled = isReady
                    ) {
                        Text("写入魔术卡")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedDump = null }) {
                    Text("关闭")
                }
            }
        )
    }

    // Rename Dialog
    renamingDump?.let { target ->
        AlertDialog(
            onDismissRequest = { renamingDump = null },
            title = { Text("重命名卡片文件") },
            text = {
                OutlinedTextField(
                    value = newFileName,
                    onValueChange = { newFileName = it },
                    singleLine = true,
                    label = { Text("文件名（无需输入后缀）") }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newFileName.isNotBlank()) {
                            engine.renameDump(target, newFileName)
                            renamingDump = null
                        }
                    }
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { renamingDump = null }) {
                    Text("取消")
                }
            }
        )
    }
}
