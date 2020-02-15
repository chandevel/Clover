package com.github.adamantcheese.chan.ui.layout.crashlogs

import android.content.Context
import android.widget.FrameLayout
import android.widget.ListView
import androidx.appcompat.widget.AppCompatButton
import com.github.adamantcheese.chan.Chan.inject
import com.github.adamantcheese.chan.R
import com.github.adamantcheese.chan.core.manager.ReportManager
import com.github.adamantcheese.chan.utils.AndroidUtils.showToast
import com.github.adamantcheese.chan.utils.Logger
import com.github.adamantcheese.chan.utils.plusAssign
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import javax.inject.Inject


internal class ReviewCrashLogsLayout(context: Context) : FrameLayout(context), CrashLogsListCallbacks {

    @Inject
    lateinit var reportManager: ReportManager

    private lateinit var compositeDisposable: CompositeDisposable
    private var callbacks: ReviewCrashLogsLayoutCallbacks? = null
    private val crashLogsList: ListView
    private val sendCrashLogsButton: AppCompatButton

    init {
        inject(this)

        inflate(context, R.layout.controller_review_crashlogs, this).apply {
            crashLogsList = findViewById(R.id.review_crashlogs_controller_crashlogs_list)
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

            sendCrashLogsButton.setOnClickListener { onSendCrashLogsButtonClicked(adapter) }
        }
    }

    private fun onSendCrashLogsButtonClicked(adapter: CrashLogsListArrayAdapter) {
        compositeDisposable += reportManager.sendCrashLogs(adapter.getSelectedCrashLogs())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnSubscribe { callbacks?.showProgressDialog() }
                .subscribe({
                    callbacks?.hideProgressDialog()
                    callbacks?.onFinished()
                }, { error ->
                    val message = "Error while trying to send logs: ${error.message}"
                    Logger.e(TAG, message, error)
                    showToast(context, message)

                    callbacks?.onFinished()
                })
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
