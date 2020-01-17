package com.github.adamantcheese.chan.report

import android.content.Context
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatCheckBox
import com.github.adamantcheese.chan.Chan
import com.github.adamantcheese.chan.R
import com.github.adamantcheese.chan.controller.Controller
import com.github.adamantcheese.chan.core.base.MResult
import com.github.adamantcheese.chan.ui.controller.LoadingViewController
import com.github.adamantcheese.chan.ui.controller.LogsController
import com.github.adamantcheese.chan.utils.AndroidUtils
import com.github.adamantcheese.chan.utils.AndroidUtils.inflate
import com.github.adamantcheese.chan.utils.Logger
import com.google.android.material.textfield.TextInputEditText
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers

class ReportProblemController(context: Context) : Controller(context) {
    private lateinit var compositeDisposable: CompositeDisposable
    private lateinit var reportActivityProblemTitle: TextInputEditText
    private lateinit var reportActivityProblemDescription: TextInputEditText
    private lateinit var reportActivityAttachLogsButton: AppCompatCheckBox
    private lateinit var reportActivityLogsText: TextInputEditText
    private lateinit var reportActivityCancel: AppCompatButton
    private lateinit var reportActivitySendReport: AppCompatButton

    private val presenter: ReportProblemPresenter = ReportProblemPresenter()
    private var loadingViewController: LoadingViewController? = null
    private var onFinishedListener: (() -> Unit)? = null

    override fun onCreate() {
        Chan.inject(this)

        view = inflate(context, R.layout.activity_report).apply {
            reportActivityProblemTitle = findViewById(R.id.report_activity_problem_title)
            reportActivityProblemDescription = findViewById(R.id.report_activity_problem_description)
            reportActivityAttachLogsButton = findViewById(R.id.report_activity_attach_logs_button)
            reportActivityLogsText = findViewById(R.id.report_activity_logs_text)
            reportActivityCancel = findViewById(R.id.report_activity_cancel)
            reportActivitySendReport = findViewById(R.id.report_activity_send_report)
        }

        presenter.onCreate(this)
        compositeDisposable = CompositeDisposable()

        val logs = LogsController.loadLogs()
        if (logs != null) {
            reportActivityLogsText.setText(logs)
        }

        reportActivityAttachLogsButton.setOnCheckedChangeListener { _, isChecked ->
            reportActivityLogsText.isEnabled = isChecked
        }
        reportActivityCancel.setOnClickListener { onFinishedListener?.invoke() }
        reportActivitySendReport.setOnClickListener { onSendReportClick() }
    }

    override fun onDestroy() {
        super.onDestroy()

        onFinishedListener = null
        presenter.onDestroy()
        compositeDisposable.dispose()
    }

    fun setOnFinishedCallback(callback: () -> Unit) {
        this.onFinishedListener = callback
    }

    private fun onSendReportClick() {
        val title = reportActivityProblemTitle.text?.toString() ?: ""
        val description = reportActivityProblemDescription.text?.toString() ?: ""
        val logs = reportActivityLogsText.text?.toString() ?: ""

        if (title.isEmpty()) {
            reportActivityProblemTitle.error =
                    context.getString(R.string.report_activity_title_cannot_be_empty_error)
            return
        }

        if (description.isEmpty()) {
            reportActivityProblemDescription.error =
                    context.getString(R.string.report_activity_description_cannot_be_empty_error)
            return
        }

        if (reportActivityAttachLogsButton.isChecked && logs.isEmpty()) {
            reportActivityLogsText.error =
                    context.getString(R.string.report_activity_logs_are_empty_error)
            return
        }

        val logsParam = if (!reportActivityAttachLogsButton.isChecked) {
            null
        } else {
            logs
        }

        showProgressDialog()

        presenter.sendReport(title, description, logsParam)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnTerminate { hideProgressDialog() }
                .subscribe({ result ->
                    handleResult(result)
                }, { error ->
                    Logger.e(TAG, "Error", error)

                    val errorMessage = error.message ?: "No error message"
                    AndroidUtils.showToast(
                            context.getString(R.string.report_activity_error_while_trying_to_send_report,
                                    errorMessage)
                    )
                })
                .also { disposable -> compositeDisposable.add(disposable) }
    }

    private fun handleResult(result: MResult<Boolean>) {
        when (result) {
            is MResult.Value -> {
                AndroidUtils.showToast(context.getString(R.string.report_activity_report_sent_message))
                onFinishedListener?.invoke()
            }
            is MResult.Error -> {
                val errorMessage = result.error.message ?: "No error message"
                AndroidUtils.showToast(
                        context.getString(R.string.report_activity_error_while_trying_to_send_report,
                                errorMessage)
                )
            }
        }
    }

    private fun showProgressDialog() {
        hideProgressDialog()

        loadingViewController = LoadingViewController(context, true)
        presentController(loadingViewController)
    }

    private fun hideProgressDialog() {
        if (loadingViewController != null) {
            loadingViewController!!.stopPresenting()
            loadingViewController = null
        }
    }

    companion object {
        private const val TAG = "ReportActivity"
    }
}