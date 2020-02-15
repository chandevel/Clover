package com.github.adamantcheese.chan.ui.layout.crashlogs

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.widget.AppCompatCheckBox
import com.github.adamantcheese.chan.R

internal class CrashLogsListArrayAdapter(
        context: Context,
        private val crashLogs: List<CrashLog>,
        private val callbacks: CrashLogsListCallbacks
) : ArrayAdapter<CrashLog>(context, R.layout.cell_crashlog_item) {
    private val inflater: LayoutInflater =
            context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
    private val handler = Handler(Looper.getMainLooper())

    init {
        clear()
        addAll(crashLogs)
    }

    @SuppressLint("ViewHolder")
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val crashLog = crashLogs[position]

        val cellView = inflater.inflate(R.layout.cell_crashlog_item, parent, false)
        val fileNameView = cellView.findViewById<TextView>(R.id.cell_crashlog_file_name)
        val checkBox = cellView.findViewById<AppCompatCheckBox>(R.id.cell_crashlog_send_checkbox)
        val clickArea = cellView.findViewById<FrameLayout>(R.id.cell_crashlog_click_area)

        fileNameView.text = crashLog.fileName
        checkBox.isChecked = crashLog.send

        fileNameView.setOnClickListener {
            callbacks.onCrashLogClicked(crashLog)
        }
        clickArea.setOnClickListener {
            val crashLogItem = getItem(position)
                    ?: return@setOnClickListener

            crashLogItem.send = !crashLogItem.send
            checkBox.isChecked = crashLogItem.send

            handler.removeCallbacksAndMessages(null)

            // Wait 100ms so that we have a little bit of time to show ripple effect
            handler.postDelayed({ notifyDataSetChanged() }, 100)
        }

        return cellView
    }

    override fun getCount(): Int {
        return crashLogs.size
    }

    fun updateAll() {
        notifyDataSetChanged()
    }

    fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
    }

    fun getSelectedCrashLogs(): List<CrashLog> {
        return crashLogs.filter { crashLog -> crashLog.send }
    }
}