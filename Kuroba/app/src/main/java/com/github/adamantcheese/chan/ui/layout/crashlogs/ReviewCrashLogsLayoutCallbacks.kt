package com.github.adamantcheese.chan.ui.layout.crashlogs

internal interface ReviewCrashLogsLayoutCallbacks {
    fun onCrashLogClicked(crashLog: CrashLog)
    fun showProgressDialog()
    fun hideProgressDialog()
    fun onFinished()
}