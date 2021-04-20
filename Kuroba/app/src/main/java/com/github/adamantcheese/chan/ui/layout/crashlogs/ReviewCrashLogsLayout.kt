package com.github.adamantcheese.chan.ui.layout.crashlogs

import android.content.Context
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ListView
import com.github.adamantcheese.chan.Chan.inject
import com.github.adamantcheese.chan.R
import com.github.adamantcheese.chan.core.manager.ReportManager
import com.github.adamantcheese.chan.ui.widget.CancellableToast.showToast
import com.github.adamantcheese.chan.utils.AndroidUtils.getString
import com.github.adamantcheese.chan.utils.Logger
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import javax.inject.Inject


internal class ReviewCrashLogsLayout(context: Context) : FrameLayout(context), CrashLogsListCallbacks {

    @Inject
    lateinit var reportManager: ReportManager

    private lateinit var compositeDisposable: CompositeDisposable
    private var callbacks: ReviewCrashLogsLayoutCallbacks? = null
    private val crashLogsList: ListView
    private val deleteCrashLogsButton: Button
    private val sendCrashLogsButton: Button

    init {
        inject(this)

        inflate(context, R.layout.controller_review_crashlogs, this).apply {
            crashLogsList = findViewById(R.id.review_crashlogs_controller_crashlogs_list)
            deleteCrashLogsButton = findViewById(R.id.review_crashlogs_controller_delete_crashlogs_button)
            sendCrashLogsButton = findViewById(R.id.review_crashlogs_controller_send_crashlogs_button)

            val crashLogs = reportManager.getCrashLogs()
                    .map { crashLogFile -> CrashLog(crashLogFile, crashLogFile.name, false) }

            val adapter = CrashLogsListArrayAdapter(
                    context,
                    crashLogs,
                    this@ReviewCrashLogsLayout
            )

            crashLogsList.adapter = adapter
            adapter.updateAll()

            deleteCrashLogsButton.setOnClickListener { onDeleteCrashLogsButtonClicked(adapter) }
            sendCrashLogsButton.setOnClickListener { onSendCrashLogsButtonClicked(adapter) }
        }
    }

    private fun onDeleteCrashLogsButtonClicked(adapter: CrashLogsListArrayAdapter) {
        val selectedCrashLogs = adapter.getSelectedCrashLogs()
        if (selectedCrashLogs.isEmpty()) {
            return
        }

        reportManager.deleteCrashLogs(selectedCrashLogs)

        val newCrashLogsAmount = adapter.deleteSelectedCrashLogs(selectedCrashLogs)
        if (newCrashLogsAmount == 0) {
            callbacks?.onFinished()
        }

        showToast(context, getString(R.string.deleted_n_crashlogs, selectedCrashLogs.size))
    }

    private fun onSendCrashLogsButtonClicked(adapter: CrashLogsListArrayAdapter) {
        val selectedCrashLogs = adapter.getSelectedCrashLogs()
        if (selectedCrashLogs.isEmpty()) {
            return
        }

        compositeDisposable.add(reportManager.sendCrashLogs(selectedCrashLogs)
                .observeOn(AndroidSchedulers.mainThread())
                .doOnSubscribe { callbacks?.showProgressDialog() }
                .subscribe({
                    callbacks?.hideProgressDialog()

                    if (selectedCrashLogs.size == adapter.count) {
                        callbacks?.onFinished()
                    } else {
                        adapter.deleteSelectedCrashLogs(selectedCrashLogs)
                    }

                    showToast(context, getString(R.string.sent_n_crashlogs, selectedCrashLogs.size))
                }, { error ->
                    val message = "Error while trying to send logs: ${error.message}"
                    Logger.e(TAG, message, error)
                    showToast(context, message)

                    callbacks?.hideProgressDialog()
                }))
    }

    fun onCreate(callbacks: ReviewCrashLogsLayoutCallbacks) {
        this.callbacks = callbacks
        this.compositeDisposable = CompositeDisposable()
    }

    fun onDestroy() {
        callbacks = null
        compositeDisposable.dispose()
        (crashLogsList.adapter as CrashLogsListArrayAdapter).onDestroy()
    }

    override fun onCrashLogClicked(crashLog: CrashLog) {
        callbacks?.onCrashLogClicked(crashLog)
    }

    companion object {
        private const val TAG = "ReviewCrashLogsLayout"
    }
}
