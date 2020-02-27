package com.github.adamantcheese.chan.utils

import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormatterBuilder
import org.joda.time.format.ISODateTimeFormat

object TimeUtils {
    private val REPORT_DATE_TIME_PRINTER = DateTimeFormatterBuilder()
            .append(ISODateTimeFormat.date())
            .appendLiteral(' ')
            .append(ISODateTimeFormat.hourMinuteSecond())
            .appendLiteral(" UTC")
            .toFormatter()
            .withZoneUTC()

    @JvmStatic
    fun getCurrentDateAndTimeUTC(): String {
        return REPORT_DATE_TIME_PRINTER.print(DateTime.now())
    }
}