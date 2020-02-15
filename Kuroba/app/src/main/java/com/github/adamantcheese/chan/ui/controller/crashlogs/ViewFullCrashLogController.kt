package com.github.adamantcheese.chan.ui.controller.crashlogs

import android.content.Context
import com.github.adamantcheese.chan.controller.Controller
import com.github.adamantcheese.chan.ui.layout.crashlogs.CrashLog
import com.github.adamantcheese.chan.ui.layout.crashlogs.ViewFullCrashLogLayout

class ViewFullCrashLogController(
        context: Context,
        private val crashLog: CrashLog
) : Controller(context), ViewFullCrashLogLayout.ViewFullCrashLogLayoutCallbacks {

    override fun onCreate() {
        super.onCreate()
        navigation.setTitle(crashLog.fileName)

        view = ViewFullCrashLogLayout(context, crashLog).apply {
            onCreate(this@ViewFullCrashLogController)
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        (view as ViewFullCrashLogLayout).onDestroy()
    }

    override fun onFinished() {
        navigationController.popController()
    }
}