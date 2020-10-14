package com.github.adamantcheese.chan.utils

import android.os.Handler
import android.os.Looper
import java.util.concurrent.atomic.AtomicBoolean

open class Debouncer(
        private val eagerInitialization: Boolean
) {
    private val eagerlyInitialized = AtomicBoolean(!eagerInitialization)
    private val handler: Handler = Handler(Looper.getMainLooper())

    open fun post(runnable: Runnable, delayMs: Long) {
        if (eagerInitialization && !eagerlyInitialized.get()) {
            handler.post {
                if (eagerlyInitialized.compareAndSet(false, true)) {
                    runnable.run()
                }
            }

            return
        }

        handler.removeCallbacksAndMessages(null)
        handler.postDelayed(runnable, delayMs)
    }
}