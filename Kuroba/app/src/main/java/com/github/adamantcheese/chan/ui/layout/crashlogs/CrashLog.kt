package com.github.adamantcheese.chan.ui.layout.crashlogs

import java.io.File

data class CrashLog(val file: File, val fileName: String, var send: Boolean)