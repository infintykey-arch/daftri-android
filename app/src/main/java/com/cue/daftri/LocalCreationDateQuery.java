package com.cue.daftri;

import java.util.Calendar;
import java.util.TimeZone;

/** Matches creation timestamps to a calendar day in the device's chosen time zone. */
final class LocalCreationDateQuery {
    private LocalCreationDateQuery() {
    }

    static boolean matches(Long timestampMillis, int year, int monthZeroBased, int dayOfMonth,
                           TimeZone timeZone) {
        if (timestampMillis == null || timestampMillis <= 0L || timeZone == null) return false;
        Calendar local = Calendar.getInstance(timeZone);
        local.setTimeInMillis(timestampMillis);
        return local.get(Calendar.YEAR) == year
                && local.get(Calendar.MONTH) == monthZeroBased
                && local.get(Calendar.DAY_OF_MONTH) == dayOfMonth;
    }
}
