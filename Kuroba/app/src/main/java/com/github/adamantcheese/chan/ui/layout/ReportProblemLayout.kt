package com.github.adamantcheese.chan.ui.layout

import android.content.Context
import android.widget.FrameLayout
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatCheckBox
import com.github.adamantcheese.chan.Chan.inject
import com.github.adamantcheese.chan.R
import com.github.adamantcheese.chan.core.base.ModularResult
import com.github.adamantcheese.chan.core.manager.ReportManager
import com.github.adamantcheese.chan.ui.controller.LogsController
import com.github.adamantcheese.chan.ui.view.ReportProblemView
import com.github.adamantcheese.chan.utils.AndroidUtils.getString
import com.github.adamantcheese.chan.utils.AndroidUtils.showToast
import com.github.adamantcheese.chan.utils.Logger
import com.google.android.material.textfield.TextInputEditText
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import javax.inject.Inject

class ReportProblemLayout(context: Context) : FrameLayout(context), ReportProblemView {

    @Inject
    lateinit var reportManager: ReportManager

    private var callbacks: ReportProblemControllerCallbacks? = null
    private lateinit var compositeDisposable: CompositeDisposable

    private val reportActivityProblemTitle: TextInputEditText
    private val reportActivityProblemDescription: TextInputEditText
    private val reportActivityAttachLogsButton: AppCompatCheckBox
    private val reportActivityLogsText: TextInputEditText
    private val reportActivityCancel: AppCompatButton
    private val reportActivitySendReport: AppCompatButton

    init {
        inject(this)

        inflate(context, R.layout.activity_report, this).apply {
            reportActivityProblemTitle = findViewById(R.id.report_activity_problem_title)
            reportActivityProblemDescription = findViewById(R.id.report_activity_problem_description)
            reportActivityAttachLogsButton = findViewById(R.id.report_activity_attach_logs_button)
            reportActivityLogsText = findViewById(R.id.report_activity_logs_text)
            reportActivityCancel = findViewById(R.id.report_activity_cancel)
            reportActivitySendReport = findViewById(R.id.report_activity_send_report)
        }
    }

    fun onReady(controllerCallbacks: ReportProblemControllerCallbacks) {
        compositeDisposable = CompositeDisposable()

        val logs = LogsController.loadLogs()
        if (logs != null) {
            reportActivityLogsText.setText(logs)
        }

        reportActivityAttachLogsButton.setOnCheckedChangeListener { _, isChecked ->
            reportActivityLogsText.isEnabled = isChecked
        }
        reportActivityCancel.setOnClickListener { callbacks?.onFinished() }
        reportActivitySendReport.setOnClickListener { onSendReportClick() }

        this.callbacks = controllerCallbacks
    }

    fun destroy() {
        compositeDisposable.dispose()
        callbacks = null
    }

    private fun onSendReportClick() {
        if (callbacks == null) {
            return
        }

        val title = reportActivityProblemTitle.text?.toString() ?: ""
        val description = reportActivityProblemDescription.text?.toString() ?: ""
        val logs = reportActivityLogsText.text?.toString() ?: ""

        if (title.isEmpty()) {
            reportActivityProblemTitle.error = getString(R.string.report_activity_title_cannot_be_empty_error)
            return
        }

        if (
                description.isEmpty()
                && !(reportActivityAttachLogsButton.isChecked && logs.isNotEmpty())
        ) {
            reportActivityProblemDescription.error = getString(R.string.report_activity_description_cannot_be_empty_error)
            return
        }

        if (reportActivityAttachLogsButton.isChecked && logs.isEmpty()) {
            reportActivityLogsText.error = getString(R.string.report_activity_logs_are_empty_error)
            return
        }

        val logsParam = if (!reportActivityAttachLogsButton.isChecked) {
            null
        } else {
            logs
        }

        callbacks?.showProgressDialog()

        reportManager.sendReport(title, description, logsParam)
                .observeOn(AndroidSchedulers.mainThread())
                .doOnTerminate { callbacks?.hideProgressDialog() }
                .subscribe({ result ->
                    handleResult(result)
                }, { error ->
                    Logger.e(TAG, "Send report error", error)

                    val errorMessage = error.message ?: "No error message"
                    val formattedMessage = getString(
                            R.string.report_activity_error_while_trying_to_send_report,
                            errorMessage
                    )

                    showToast(context, formattedMessage)
                })
                .also { disposable -> compositeDisposable.add(disposable) }
    }

    private fun handleResult(result: ModularResult<Boolean>) {
        when (result) {
            is ModularResult.Value -> {
                showToast(context, R.string.report_activity_report_sent_message)
                callbacks?.onFinished()
            }
            is ModularResult.Error -> {
                val errorMessage = result.error.message ?: "No error message"
                val formattedMessage = getString(
                        R.string.report_activity_error_while_trying_to_send_report,
                        errorMessage
                )

                showToast(context, formattedMessage)
            }
        }
    }

    interface ReportProblemControllerCallbacks {
        fun showProgressDialog()
        fun hideProgressDialog()
        fun onFinished()
    }

    companion object {
        private const val TAG = "ReportProblemLayout"
    }
}