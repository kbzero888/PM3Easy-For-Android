package com.pm3.droid.engine

import android.content.Context
import android.os.Environment
import com.pm3.droid.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.*
import java.util.concurrent.atomic.AtomicLong

class Pm3Engine(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var rootProcess: Process? = null
    private var processWriter: BufferedWriter? = null
    private var readerJob: Job? = null
    private var connectJob: Job? = null

    private val logIdCounter = AtomicLong(0L)

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _statusText = MutableStateFlow("未检测到 PM3 设备")
    val statusText: StateFlow<String> = _statusText.asStateFlow()

    private val _enumerationProgress = MutableStateFlow(0f)
    val enumerationProgress: StateFlow<Float> = _enumerationProgress.asStateFlow()

    private val _firmwareVersion = MutableStateFlow("未知固件")
    val firmwareVersion: StateFlow<String> = _firmwareVersion.asStateFlow()

    private val _logs = MutableStateFlow<List<LogLine>>(emptyList())
    val logs: StateFlow<List<LogLine>> = _logs.asStateFlow()

    private val _detectedCard = MutableStateFlow<CardInfo?>(null)
    val detectedCard: StateFlow<CardInfo?> = _detectedCard.asStateFlow()

    private val _tuneResult = MutableStateFlow(TuneResult())
    val tuneResult: StateFlow<TuneResult> = _tuneResult.asStateFlow()

    private val _dumpFiles = MutableStateFlow<List<DumpFileItem>>(emptyList())
    val dumpFiles: StateFlow<List<DumpFileItem>> = _dumpFiles.asStateFlow()

    var onHapticFeedback: (() -> Unit)? = null

    val workspaceDir: File by lazy {
        val base = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        File(base, "PM3_Workspace").apply { mkdirs() }
    }

    val dumpsDir: File by lazy {
        File(workspaceDir, "dumps").apply { mkdirs() }
    }

    private val ansiRegex = Regex("""\u001B\[[;?0-9]*[a-zA-Z]""")
    private val uidRegex = Regex("""UID\s*:\s*([0-9A-Fa-f ]{4,32})""")
    private val lfIdRegex = Regex("""(?:EM 410x ID|HID Prox TAG ID|ID)\s*:\s*([0-9A-Fa-f]{6,16})""")
    private val atqaRegex = Regex("""ATQA\s*:\s*([0-9A-Fa-f ]{4,8})""")
    private val sakRegex = Regex("""SAK\s*:\s*([0-9A-Fa-f]{2})""")
    private val hfVoltRegex = Regex("""13\.56\s*MHz\s*:\s*([0-9]+\.[0-9]+)\s*V""", RegexOption.IGNORE_CASE)
    private val lfVoltRegex = Regex("""125\.00\s*kHz\s*:\s*([0-9]+\.[0-9]+)\s*V""", RegexOption.IGNORE_CASE)

    private var pendingUid: String? = null
    private var pendingAtqa: String? = null
    private var pendingSak: String? = null
    private var pendingHfVolt: Float? = null
    private var pendingLfVolt: Float? = null

    init {
        scope.launch {
            prepareAssetsIfNeeded()
            refreshDumpFiles()
        }
    }

    private fun prepareAssetsIfNeeded() {
        try {
            val dictTarget = File(workspaceDir, "dictionaries")
            if (!dictTarget.exists() || (dictTarget.listFiles()?.isEmpty() != false)) {
                appendLog("[=] 正在首次释放冰人密钥字典至 ${workspaceDir.absolutePath} ...", LogLevel.INFO)
                copyAssetFolder("pm3", workspaceDir)
                appendLog("[+] 字典与脚本资源释放完成", LogLevel.SUCCESS)
            }
        } catch (e: Exception) {
            appendLog("[!] 资源释放提示: ${e.message}", LogLevel.WARNING)
        }
    }

    private fun copyAssetFolder(assetPath: String, targetDir: File) {
        val assetManager = context.assets
        val files = assetManager.list(assetPath) ?: return
        if (files.isEmpty()) {
            targetDir.parentFile?.mkdirs()
            assetManager.open(assetPath).use { input ->
                FileOutputStream(targetDir).use { output ->
                    input.copyTo(output)
                }
            }
        } else {
            targetDir.mkdirs()
            for (file in files) {
                copyAssetFolder("$assetPath/$file", File(targetDir, file))
            }
        }
    }

    /**
     * Triggered when USB device is attached or user clicks manual connect.
     * Polls /dev/ttyACM0 via `su` for up to 6.5s to accommodate Qualcomm dwc3 enumeration delay (~4.5s).
     */
    fun onUsbAttachedOrConnect() {
        if (_connectionState.value == ConnectionState.READY || _connectionState.value == ConnectionState.ENUMERATING) {
            return
        }
        connectJob?.cancel()
        connectJob = scope.launch {
            _connectionState.value = ConnectionState.ENUMERATING
            _statusText.value = "检测到 USB 插入，等待 ttyACM 枚举..."
            appendLog("[=] 检测到硬件连接，正在等待内核生成 /dev/ttyACM0（约 4.5s）...", LogLevel.INFO)

            val maxWaitMs = 6500L
            val stepMs = 300L
            var elapsed = 0L
            var foundNode: String? = null

            while (elapsed <= maxWaitMs && isActive) {
                _enumerationProgress.value = (elapsed.toFloat() / maxWaitMs).coerceIn(0f, 1f)
                val node = probeTtyNodeViaRoot()
                if (node != null) {
                    foundNode = node
                    break
                }
                delay(stepMs)
                elapsed += stepMs
            }

            _enumerationProgress.value = 1f

            if (foundNode == null) {
                _connectionState.value = ConnectionState.ERROR
                _statusText.value = "未找到 ttyACM0（请确认 Root 授权与 OTG）"
                appendLog("[!] 超时未检测到 /dev/ttyACM0。请检查 KernelSU 是否已授予本应用 Root 权限，以及系统 OTG 开关是否开启。", LogLevel.ERROR)
                return@launch
            }

            appendLog("[+] 设备节点已就绪: $foundNode，正在启动冰人客户端...", LogLevel.SUCCESS)
            startPm3ClientProcess(foundNode)
        }
    }

    private fun probeTtyNodeViaRoot(): String? {
        return try {
            val proc = ProcessBuilder("su", "-c", "ls /dev/ttyACM0 /dev/ttyACM1 2>/dev/null | head -n 1")
                .redirectErrorStream(true)
                .start()
            val output = proc.inputStream.bufferedReader().readText().trim()
            proc.waitFor()
            if (output.startsWith("/dev/ttyACM")) output else null
        } catch (e: Exception) {
            null
        }
    }

    private fun startPm3ClientProcess(ttyNode: String) {
        cleanupProcessOnly()
        try {
            val nativeDir = context.applicationInfo.nativeLibraryDir
            val pm3Bin = File(nativeDir, "libproxmark3.so")

            if (!pm3Bin.exists()) {
                _connectionState.value = ConnectionState.ERROR
                _statusText.value = "缺少 libproxmark3.so 核心"
                appendLog("[!] 未在 $nativeDir 找到 libproxmark3.so，请确认通过 GitHub Actions 编译并打包。", LogLevel.ERROR)
                return
            }

            // Directly execute client with uid=0 via su; no chmod 666 required
            val launchCmd = buildString {
                append("mkdir -p '${dumpsDir.absolutePath}' && ")
                append("cd '${workspaceDir.absolutePath}' && ")
                append("export LD_LIBRARY_PATH='$nativeDir':\$LD_LIBRARY_PATH && ")
                append("export HOME='${workspaceDir.absolutePath}' && ")
                append("exec '${pm3Bin.absolutePath}' '$ttyNode'")
            }

            val process = ProcessBuilder("su", "-c", launchCmd)
                .redirectErrorStream(true)
                .start()

            rootProcess = process
            processWriter = BufferedWriter(OutputStreamWriter(process.outputStream))

            _connectionState.value = ConnectionState.READY
            _statusText.value = "已直连 $ttyNode (Root)"

            readerJob = scope.launch {
                try {
                    val reader = BufferedReader(InputStreamReader(process.inputStream))
                    while (isActive) {
                        val line = reader.readLine() ?: break
                        val cleanLine = line.replace(ansiRegex, "").trimEnd()
                        if (cleanLine.isNotBlank()) {
                            parseLineAndUpdateState(cleanLine)
                        }
                    }
                } catch (e: Exception) {
                    if (isActive) {
                        appendLog("[!] 串口读取流中断: ${e.message}", LogLevel.WARNING)
                    }
                } finally {
                    if (_connectionState.value != ConnectionState.DISCONNECTED) {
                        _connectionState.value = ConnectionState.DISCONNECTED
                        _statusText.value = "PM3 进程已退出"
                    }
                }
            }
        } catch (e: Exception) {
            _connectionState.value = ConnectionState.ERROR
            _statusText.value = "Root 启动失败"
            appendLog("[!] 启动 Root 进程失败: ${e.message}", LogLevel.ERROR)
        }
    }

    fun sendCommand(command: String) {
        val trimmed = command.trim()
        if (trimmed.isEmpty()) return

        if (_connectionState.value != ConnectionState.READY && _connectionState.value != ConnectionState.BUSY) {
            appendLog("[!] 设备未就绪，无法发送指令: $trimmed", LogLevel.WARNING)
            return
        }

        scope.launch {
            try {
                appendLog("pm3 --> $trimmed", LogLevel.COMMAND)
                if (isLongRunningCommand(trimmed)) {
                    _connectionState.value = ConnectionState.BUSY
                    _statusText.value = "正在执行: $trimmed"
                }
                processWriter?.apply {
                    write(trimmed)
                    newLine()
                    flush()
                }
            } catch (e: Exception) {
                appendLog("[!] 发送失败: ${e.message}", LogLevel.ERROR)
            }
        }
    }

    fun sendInterrupt() {
        scope.launch {
            try {
                appendLog("[!] 发送中断信号 (Ctrl+C)...", LogLevel.WARNING)
                processWriter?.apply {
                    write(3)
                    flush()
                }
                delay(300)
                if (_connectionState.value == ConnectionState.BUSY) {
                    _connectionState.value = ConnectionState.READY
                    _statusText.value = "已就绪 (Root)"
                }
            } catch (e: Exception) {
                appendLog("[!] 中断异常，正在重启通信会话...", LogLevel.ERROR)
                onUsbDetached()
                delay(300)
                onUsbAttachedOrConnect()
            }
        }
    }

    fun onUsbDetached() {
        connectJob?.cancel()
        cleanupProcessOnly()
        _connectionState.value = ConnectionState.DISCONNECTED
        _statusText.value = "设备已断开"
        _enumerationProgress.value = 0f
        appendLog("[-] USB 设备已断开连接", LogLevel.WARNING)
    }

    private fun cleanupProcessOnly() {
        readerJob?.cancel()
        try {
            processWriter?.write("exit\n")
            processWriter?.flush()
            processWriter?.close()
        } catch (_: Exception) {
        }
        try {
            rootProcess?.destroy()
        } catch (_: Exception) {
        }
        processWriter = null
        rootProcess = null
    }

    private fun isLongRunningCommand(cmd: String): Boolean {
        val lower = cmd.lowercase()
        return lower.contains("autopwn") ||
            lower.contains("sim") ||
            lower.contains("chk") ||
            lower.contains("nested") ||
            lower.contains("darkside") ||
            lower.contains("sniff") ||
            lower.contains("tune")
    }

    private fun parseLineAndUpdateState(line: String) {
        val level = when {
            line.startsWith("[+]") -> LogLevel.SUCCESS
            line.startsWith("[!]") || line.contains("error", ignoreCase = true) -> LogLevel.ERROR
            line.startsWith("[?]") -> LogLevel.WARNING
            line.startsWith("[=]") -> LogLevel.INFO
            else -> LogLevel.NORMAL
        }
        appendLog(line, level)

        // Prompt return indicates command completed
        if (line.contains("pm3 -->") && _connectionState.value == ConnectionState.BUSY) {
            _connectionState.value = ConnectionState.READY
            _statusText.value = "已就绪 (Root)"
            refreshDumpFiles()
        }

        // Parse Firmware Version
        if (line.contains("Iceman/") || line.contains("RRG/")) {
            val ver = line.substringAfter("[").substringBefore("]").ifBlank { line.take(36) }
            _firmwareVersion.value = ver
        }

        // Parse Antenna Voltages
        hfVoltRegex.find(line)?.let { match ->
            pendingHfVolt = match.groupValues[1].toFloatOrNull()
            updateTuneState()
        }
        lfVoltRegex.find(line)?.let { match ->
            pendingLfVolt = match.groupValues[1].toFloatOrNull()
            updateTuneState()
        }

        // Parse HF Card info
        uidRegex.find(line)?.let { match ->
            pendingUid = match.groupValues[1].trim().uppercase()
        }
        atqaRegex.find(line)?.let { match ->
            pendingAtqa = match.groupValues[1].trim().uppercase()
        }
        sakRegex.find(line)?.let { match ->
            pendingSak = match.groupValues[1].trim().uppercase()
            val uid = pendingUid
            if (uid != null) {
                val cardType = inferHfCardType(pendingSak)
                _detectedCard.value = CardInfo(
                    frequency = CardFrequency.HF,
                    type = cardType,
                    uid = uid,
                    atqa = pendingAtqa,
                    sak = pendingSak
                )
                onHapticFeedback?.invoke()
                pendingUid = null
                pendingAtqa = null
                pendingSak = null
            }
        }

        // Parse Valid ISO14443-A / NTAG fallback if SAK wasn't matched
        if (line.contains("Valid ISO 14443-A tag found", ignoreCase = true) && pendingUid != null) {
            _detectedCard.value = CardInfo(
                frequency = CardFrequency.HF,
                type = "ISO 14443-A 标签",
                uid = pendingUid!!,
                atqa = pendingAtqa,
                sak = pendingSak
            )
            onHapticFeedback?.invoke()
            pendingUid = null
        }

        // Parse LF Card info
        lfIdRegex.find(line)?.let { match ->
            val lfId = match.groupValues[1].trim().uppercase()
            val lfType = when {
                line.contains("EM 410x", ignoreCase = true) -> "EM410x 低频卡"
                line.contains("HID", ignoreCase = true) -> "HID Prox 门禁卡"
                else -> "LF 125kHz 卡片"
            }
            _detectedCard.value = CardInfo(
                frequency = CardFrequency.LF,
                type = lfType,
                uid = lfId
            )
            onHapticFeedback?.invoke()
        }

        // Auto-refresh dumps when saved
        if (line.contains("saved to", ignoreCase = true) || line.contains(".bin") || line.contains(".json")) {
            refreshDumpFiles()
        }
    }

    private fun updateTuneState() {
        val hf = pendingHfVolt
        val lf = pendingLfVolt
        if (hf != null || lf != null) {
            _tuneResult.value = TuneResult(
                hfVoltage = hf ?: _tuneResult.value.hfVoltage,
                lfVoltage = lf ?: _tuneResult.value.lfVoltage,
                rawSummary = "HF: ${hf ?: "--"}V | LF: ${lf ?: "--"}V"
            )
        }
    }

    private fun inferHfCardType(sak: String?): String {
        return when (sak) {
            "08" -> "MIFARE Classic 1K (S50)"
            "18" -> "MIFARE Classic 4K (S70)"
            "09" -> "MIFARE Mini (0.3K)"
            "00" -> "MIFARE Ultralight / NTAG"
            "20" -> "CPU 卡 / DESFire / ISO 14443-4"
            "28" -> "JCOP / 模拟 M1 复合卡"
            else -> "ISO 14443-A (SAK: ${sak ?: "??"})"
        }
    }

    fun appendLog(text: String, level: LogLevel = LogLevel.NORMAL) {
        val item = LogLine(id = logIdCounter.incrementAndGet(), text = text, level = level)
        _logs.update { current ->
            val next = current + item
            if (next.size > 600) next.takeLast(500) else next
        }
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }

    fun refreshDumpFiles() {
        scope.launch {
            val candidates = mutableListOf<File>()
            workspaceDir.listFiles()?.filter { it.isFile && (it.extension == "bin" || it.extension == "json") }?.let {
                candidates.addAll(it)
            }
            dumpsDir.listFiles()?.filter { it.isFile && (it.extension == "bin" || it.extension == "json") }?.let {
                candidates.addAll(it)
            }

            val items = candidates
                .sortedByDescending { it.lastModified() }
                .map { file ->
                    val label = when {
                        file.extension == "json" -> "JSON 转储数据"
                        file.length() == 1024L -> "M1 1K (1024B)"
                        file.length() == 4096L -> "M1 4K (4096B)"
                        file.length() in 64L..900L -> "Ultralight / NTAG (${file.length()}B)"
                        else -> "二进制 Dump (${file.length()}B)"
                    }
                    val uid = extractUidFromDump(file)
                    DumpFileItem(
                        file = file,
                        name = file.name,
                        sizeBytes = file.length(),
                        lastModified = file.lastModified(),
                        cardLabel = label,
                        extractedUid = uid
                    )
                }
            _dumpFiles.value = items
        }
    }

    private fun extractUidFromDump(file: File): String {
        return try {
            if (file.extension == "bin" && file.length() >= 4) {
                val bytes = ByteArray(4)
                FileInputStream(file).use { it.read(bytes) }
                bytes.joinToString(" ") { "%02X".format(it) }
            } else {
                file.nameWithoutExtension.substringAfterLast("-").take(14).uppercase()
            }
        } catch (_: Exception) {
            "未知 UID"
        }
    }

    fun readHexPreview(file: File, maxBytes: Int = 1024): List<Pair<Int, String>> {
        return try {
            val bytes = file.readBytes().take(maxBytes)
            val rows = mutableListOf<Pair<Int, String>>()
            for (i in bytes.indices step 16) {
                val slice = bytes.subList(i, minOf(i + 16, bytes.size))
                val hex = slice.joinToString(" ") { "%02X".format(it) }
                rows.add((i / 16) to hex)
            }
            rows
        } catch (e: Exception) {
            listOf(0 to "读取失败: ${e.message}")
        }
    }

    fun renameDump(item: DumpFileItem, newName: String): Boolean {
        val cleanName = newName.trim().let {
            if (it.endsWith(".${item.file.extension}")) it else "$it.${item.file.extension}"
        }
        val target = File(item.file.parentFile, cleanName)
        val ok = item.file.renameTo(target)
        if (ok) refreshDumpFiles()
        return ok
    }

    fun deleteDump(item: DumpFileItem) {
        if (item.file.delete()) {
            refreshDumpFiles()
        }
    }

    fun destroy() {
        cleanupProcessOnly()
        scope.cancel()
    }
}
