package com.github.adamantcheese.chan.core.base

import com.github.adamantcheese.chan.core.repository.StaticResourceRepository.mainHandler
import java.util.concurrent.atomic.AtomicBoolean

open class Debouncer(
        private val eagerInitialization: Boolean
) {
    private val eagerlyInitialized = AtomicBoolean(!eagerInitialization)

    open fun post(runnable: Runnable, delayMs: Long) {
        if (eagerInitialization && !eagerlyInitialized.get()) {
            mainHandler.post {
                if (eagerlyInitialized.compareAndSet(false, true)) {
                    runnable.run()
                }
            }

            return
        }

        mainHandler.removeCallbacksAndMessages(null)
        mainHandler.postDelayed(runnable, delayMs)
    }
}