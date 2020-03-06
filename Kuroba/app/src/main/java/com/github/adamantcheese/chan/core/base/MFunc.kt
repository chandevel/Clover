package com.github.adamantcheese.chan.core.base

interface MFunc {
    fun invoke()
}

interface MFuncT<T> {
    fun invoke(param: T)
}

interface MFuncR<R> {
    fun invoke(): R
}

interface MFuncTR<T, R> {
    fun invoke(param: T): R
}