package com.github.adamantcheese.chan.utils

/**
 * Forces the kotlin compiler to require handling of all branches in the "when" operator
 * */
val <T : Any?> T.exhaustive: T
    get() = this