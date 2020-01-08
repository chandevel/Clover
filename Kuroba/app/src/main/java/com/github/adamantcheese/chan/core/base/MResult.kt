package com.github.adamantcheese.chan.core.base

sealed class MResult<V> {
    data class Value<V>(val value: V) : MResult<V>()
    data class Error<V>(val error: Throwable) : MResult<V>()

    companion object {
        fun <V> value(value: V): MResult<V> {
            return Value(value)
        }

        fun <V> error(error: Throwable): MResult<V> {
            return Error(error)
        }
    }
}