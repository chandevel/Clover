package com.github.adamantcheese.chan.core.base

import androidx.annotation.CallSuper

abstract class BasePresenter<V> {
    private var view: V? = null

    @CallSuper
    open fun onCreate(view: V) {
        this.view = view
    }

    @CallSuper
    open fun onDestroy() {
        this.view = null
    }

    fun withView(func: (V) -> Unit) {
        view?.let { func(it) }
    }
}