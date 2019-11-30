package com.github.adamantcheese.chan.utils

import com.github.adamantcheese.chan.core.model.orm.Loadable

object ChanUtils {

    /**
     * Extracts and converts to a string only the info that we are interested in from this loadable
     */
    @JvmStatic
    fun loadableToString(loadable: Loadable): String {
        return "[" + loadable.site.name() + ", " + loadable.boardCode + ", " + loadable.no + "]"
    }

}