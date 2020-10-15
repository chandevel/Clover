package com.github.adamantcheese.chan.ui.controller

import android.content.Context
import com.github.adamantcheese.chan.R
import com.github.adamantcheese.chan.controller.Controller
import com.github.adamantcheese.chan.ui.view.ReportProblemView

class ReportProblemController(context: Context) : Controller(context) {
    override fun onCreate() {
        super.onCreate()
        navigation.setTitle(R.string.report_controller_report_an_error_problem)
        view = ReportProblemView(context)
    }
}