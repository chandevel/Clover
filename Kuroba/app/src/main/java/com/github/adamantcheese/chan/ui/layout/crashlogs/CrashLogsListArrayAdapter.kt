package com.github.adamantcheese.chan.ui.layout.crashlogs

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.TextView
import com.github.adamantcheese.chan.R
import com.github.adamantcheese.chan.core.repository.StaticResourceRepository.mainHandler
import com.github.adamantcheese.chan.utils.LayoutUtils.inflate

internal class CrashLogsListArrayAdapter(
        context: Context,
        crashLogs: List<CrashLog>,
        private val callbacks: CrashLogsListCallbacks
) : ArrayAdapter<CrashLog>(context, R.layout.cell_crashlog_item) {
    private val notify: Runnable = Runnable { notifyDataSetChanged() }

    init {
        clear()
        addAll(crashLogs)
    }

    @SuppressLint("ViewHolder")
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val crashLog = checkNotNull(getItem(position)) {
            "Item with position $position is null! Items count = $count"
        }

        val cellView = inflate(context, R.layout.cell_crashlog_item, parent, false)
        val fileNameView = cellView.findViewById<TextView>(R.id.cell_crashlog_file_name)
        val checkBox = cellView.findViewById<CheckBox>(R.id.cell_crashlog_send_checkbox)
        val clickArea = cellView.findViewById<FrameLayout>(R.id.cell_crashlog_click_area)

        fileNameView.text = crashLog.fileName
        checkBox.isChecked = crashLog.markedToSend

        fileNameView.setOnClickListener {
            callbacks.onCrashLogClicked(crashLog)
        }
        clickArea.setOnClickListener {
            val crashLogItem = getItem(position)
                    ?: return@setOnClickListener

            crashLogItem.markedToSend = !crashLogItem.markedToSend
            checkBox.isChecked = crashLogItem.markedToSend

            mainHandler.removeCallbacks(notify)

            // Wait 100ms so that we have a little bit of time to show ripple effect
            mainHandler.postDelayed(notify, 100)
        }

        return cellView
    }

    fun updateAll() {
        notifyDataSetChanged()
    }

    fun deleteSelectedCrashLogs(selectedCrashLogs: List<CrashLog>): Int {
        if (selectedCrashLogs.isNotEmpty()) {
            selectedCrashLogs.forEach { crashLog -> remove(crashLog) }
            notifyDataSetChanged()
        }

        return count
    }

    fun getSelectedCrashLogs(): List<CrashLog> {
        val selectedCrashLogs = mutableListOf<CrashLog>()

        for (i in 0 until count) {
            val item = getItem(i)
                    ?: continue

            if (item.markedToSend) {
                selectedCrashLogs += item
            }
        }

        return selectedCrashLogs
    }

    fun onDestroy() {
        mainHandler.removeCallbacks(notify)
    }
}