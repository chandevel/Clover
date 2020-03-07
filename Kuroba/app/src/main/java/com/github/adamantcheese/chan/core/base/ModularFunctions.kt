package com.github.adamantcheese.chan.core.base

interface VoidFunction {
    fun invoke()
}

interface ModularFunction<T> {
    fun invoke(param: T)
}

interface GenericFunction<R> {
    fun invoke(): R
}

interface GenericModularFunction<T, R> {
    fun invoke(param: T): R
}