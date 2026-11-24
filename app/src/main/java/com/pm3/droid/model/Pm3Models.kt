package com.pm3.droid.model

import java.io.File

enum class ConnectionState {
    DISCONNECTED,
    ENUMERATING,
    READY,
    BUSY,
    ERROR
}

enum class ThemeMode {
    DARK,
    LIGHT,
    SYSTEM
}

enum class AppTab(val title: String) {
    DASHBOARD("工作台"),
    TERMINAL("终端"),
    DUMPS("卡包")
}

enum class CardFrequency {
    HF,
    LF,
    UNKNOWN
}

data class CardInfo(
    val frequency: CardFrequency,
    val type: String,
    val uid: String,
    val atqa: String? = null,
    val sak: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

data class TuneResult(
    val hfVoltage: Float? = null,
    val lfVoltage: Float? = null,
    val rawSummary: String = "尚未检测天线电压"
)

data class LogLine(
    val id: Long,
    val text: String,
    val level: LogLevel = LogLevel.NORMAL
)

enum class LogLevel {
    NORMAL,
    SUCCESS,
    WARNING,
    ERROR,
    INFO,
    COMMAND
}

data class DumpFileItem(
    val file: File,
    val name: String,
    val sizeBytes: Long,
    val lastModified: Long,
    val cardLabel: String,
    val extractedUid: String
)
