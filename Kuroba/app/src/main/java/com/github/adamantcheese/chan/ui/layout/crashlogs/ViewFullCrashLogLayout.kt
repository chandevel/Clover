package com.github.adamantcheese.chan.ui.layout.crashlogs

import android.annotation.SuppressLint
import android.content.Context
import android.widget.FrameLayout
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatEditText
import com.github.adamantcheese.chan.R

@SuppressLint("ViewConstructor")
class ViewFullCrashLogLayout(context: Context, private val crashLog: CrashLog) : FrameLayout(context) {

    private var callbacks: ViewFullCrashLogLayoutCallbacks? = null

    private val crashLogText: AppCompatEditText
    private val save: AppCompatButton

    init {
        inflate(context, R.layout.layout_view_full_crashlog, this).apply {
            crashLogText = findViewById(R.id.view_full_crashlog_text)
            save = findViewById(R.id.view_full_crashlog_save)
            crashLogText.setText(crashLog.file.readText())

            save.setOnClickListener {
                val text = crashLogText.text.toString()

                if (text.isNotEmpty()) {
                    crashLog.file.writeText(text)
                }

                callbacks?.onFinished()
            }
        }
    }

    fun onCreate(callbacks: ViewFullCrashLogLayoutCallbacks) {
        this.callbacks = callbacks
    }

    fun onDestroy() {
        this.callbacks = null
    }

    interface ViewFullCrashLogLayoutCallbacks {
        fun onFinished()
    }
}