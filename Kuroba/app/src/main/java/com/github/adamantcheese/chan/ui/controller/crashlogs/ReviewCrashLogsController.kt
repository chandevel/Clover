package com.github.adamantcheese.chan.ui.controller.crashlogs

import android.content.Context
import com.github.adamantcheese.chan.R
import com.github.adamantcheese.chan.controller.Controller
import com.github.adamantcheese.chan.ui.controller.LoadingViewController
import com.github.adamantcheese.chan.ui.layout.crashlogs.CrashLog
import com.github.adamantcheese.chan.ui.layout.crashlogs.ReviewCrashLogsLayout
import com.github.adamantcheese.chan.ui.layout.crashlogs.ReviewCrashLogsLayoutCallbacks

class ReviewCrashLogsController(context: Context) : Controller(context), ReviewCrashLogsLayoutCallbacks {
    private var loadingViewController: LoadingViewController? = null

    override fun onCreate() {
        super.onCreate()
        navigation.setTitle(R.string.review_crashlogs_controller_title)

        view = ReviewCrashLogsLayout(context).apply { onCreate(this@ReviewCrashLogsController) }
    }

    override fun onDestroy() {
        super.onDestroy()

        (view as ReviewCrashLogsLayout).onDestroy()
    }

    override fun showProgressDialog() {
        hideProgressDialog()

        loadingViewController = LoadingViewController(context, true)
        presentController(loadingViewController)
    }

    override fun hideProgressDialog() {
        loadingViewController?.stopPresenting()
        loadingViewController = null
    }

    override fun onCrashLogClicked(crashLog: CrashLog) {
        navigationController.pushController(ViewFullCrashLogController(context, crashLog))
    }

    override fun onFinished() {
        navigationController.popController()
    }
}