package com.github.adamantcheese.chan.report

import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.PersistableBundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatCheckBox
import com.github.adamantcheese.chan.Chan.inject
import com.github.adamantcheese.chan.R
import com.github.adamantcheese.chan.core.base.MResult
import com.github.adamantcheese.chan.ui.controller.LogsController
import com.github.adamantcheese.chan.ui.theme.ThemeHelper
import com.github.adamantcheese.chan.utils.AndroidUtils.showToast
import com.github.adamantcheese.chan.utils.Logger
import com.google.android.material.textfield.TextInputEditText
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import javax.inject.Inject


class ReportActivity : AppCompatActivity() {
    @Inject
    lateinit var themeHelper: ThemeHelper

    private val presenter: ReportActivityPresenter = ReportActivityPresenter()

    private lateinit var compositeDisposable: CompositeDisposable
    private lateinit var reportActivityProblemTitle: TextInputEditText
    private lateinit var reportActivityProblemDescription: TextInputEditText
    private lateinit var reportActivityAttachLogsButton: AppCompatCheckBox
    private lateinit var reportActivityLogsText: TextInputEditText
    private lateinit var reportActivityCancel: AppCompatButton
    private lateinit var reportActivitySendReport: AppCompatButton

    private var progressDialog: ProgressDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        inject(this)

        themeHelper.setupContext(this)
        setContentView(R.layout.activity_report)

        reportActivityProblemTitle = findViewById(R.id.report_activity_problem_title)
        reportActivityProblemDescription = findViewById(R.id.report_activity_problem_description)
        reportActivityAttachLogsButton = findViewById(R.id.report_activity_attach_logs_button)
        reportActivityLogsText = findViewById(R.id.report_activity_logs_text)
        reportActivityCancel = findViewById(R.id.report_activity_cancel)
        reportActivitySendReport = findViewById(R.id.report_activity_send_report)

        presenter.onCreate(this)
        compositeDisposable = CompositeDisposable()

        if (savedInstanceState != null) {
            reportActivityProblemTitle.setText(savedInstanceState.getString(REPORT_TITLE_KEY, ""))
            reportActivityProblemDescription.setText(savedInstanceState.getString(REPORT_DESCRIPTION_KEY, ""))
            reportActivityAttachLogsButton.isChecked = savedInstanceState.getBoolean(REPORT_ATTACH_LOGS_KEY, false)
        }

        val logs = LogsController.loadLogs()
        if (logs != null) {
            reportActivityLogsText.setText(logs)
        }

        reportActivityAttachLogsButton.setOnCheckedChangeListener { _, isChecked ->
            reportActivityLogsText.isEnabled = isChecked
        }
        reportActivityCancel.setOnClickListener { finish() }
        reportActivitySendReport.setOnClickListener { onSendReportClick() }
    }

    override fun onDestroy() {
        super.onDestroy()

        presenter.onDestroy()
        compositeDisposable.dispose()
    }

    private fun onSendReportClick() {
        val title = reportActivityProblemTitle.text?.toString() ?: ""
        val description = reportActivityProblemDescription.text?.toString() ?: ""
        val logs = reportActivityLogsText.text?.toString() ?: ""

        if (title.isEmpty()) {
            reportActivityProblemTitle.error =
                    getString(R.string.report_activity_title_cannot_be_empty_error)
            return
        }

        if (description.isEmpty()) {
            reportActivityProblemDescription.error =
                    getString(R.string.report_activity_description_cannot_be_empty_error)
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
                    showToast(
                            getString(R.string.report_activity_error_while_trying_to_send_report,
                                    errorMessage)
                    )
                })
                .also { disposable -> compositeDisposable.add(disposable) }
    }

    private fun handleResult(result: MResult<Boolean>) {
        when (result) {
            is MResult.Value -> {
                showToast(getString(R.string.report_activity_report_sent_message))
                finish()
            }
            is MResult.Error -> {
                val errorMessage = result.error.message ?: "No error message"
                showToast(
                        getString(R.string.report_activity_error_while_trying_to_send_report,
                                errorMessage)
                )
            }
        }
    }

    private fun showProgressDialog() {
        if (progressDialog != null) {
            progressDialog!!.dismiss()
            progressDialog = null
        }

        progressDialog = ProgressDialog(this).apply {
            setMessage(getString(R.string.report_activity_sending_report_message))
            show()
        }
    }

    private fun hideProgressDialog() {
        if (progressDialog != null) {
            progressDialog!!.dismiss()
            progressDialog = null
        }
    }

    override fun onSaveInstanceState(outState: Bundle, outPersistentState: PersistableBundle?) {
        super.onSaveInstanceState(outState, outPersistentState)

        outState.putString(REPORT_TITLE_KEY, reportActivityProblemTitle.text.toString())
        outState.putString(REPORT_DESCRIPTION_KEY, reportActivityProblemDescription.text.toString())
        outState.putBoolean(REPORT_ATTACH_LOGS_KEY, reportActivityAttachLogsButton.isChecked)
    }

    companion object {
        private const val TAG = "ReportActivity"
        private const val REPORT_TITLE_KEY = "report_title"
        private const val REPORT_DESCRIPTION_KEY = "report_description"
        private const val REPORT_ATTACH_LOGS_KEY = "report_attach_logs"

        @JvmStatic
        fun startActivity(context: Context) {
            val intent = Intent(context, ReportActivity::class.java)
            context.startActivity(intent)
        }
    }
}
