package com.github.adamantcheese.chan.utils

import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable

/**
 * Forces the kotlin compiler to require handling of all branches in the "when" operator
 * */
val <T : Any?> T.exhaustive: T
    get() = this

operator fun CompositeDisposable.plusAssign(disposable: Disposable) {
    this.add(disposable)
}