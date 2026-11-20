package com.cue.daftri;

import org.junit.Test;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LocalCreationDateQueryTest {
    @Test
    public void matchesDeviceLocalDateInsteadOfUtcDate() {
        Calendar utc = new GregorianCalendar(TimeZone.getTimeZone("UTC"), Locale.US);
        utc.clear();
        utc.set(2026, Calendar.JANUARY, 1, 22, 30, 0);
        long timestamp = utc.getTimeInMillis();
        TimeZone cairo = TimeZone.getTimeZone("Africa/Cairo");

        assertTrue(LocalCreationDateQuery.matches(timestamp, 2026, Calendar.JANUARY, 2, cairo));
        assertFalse(LocalCreationDateQuery.matches(timestamp, 2026, Calendar.JANUARY, 1, cairo));
    }

    @Test
    public void timestampNearLocalMidnightBelongsToItsLocalCalendarDay() {
        TimeZone cairo = TimeZone.getTimeZone("Africa/Cairo");
        Calendar local = new GregorianCalendar(cairo, Locale.US);
        local.clear();
        local.set(2026, Calendar.MARCH, 1, 0, 5, 0);
        long timestamp = local.getTimeInMillis();

        assertTrue(LocalCreationDateQuery.matches(timestamp, 2026, Calendar.MARCH, 1, cairo));
        assertFalse(LocalCreationDateQuery.matches(timestamp, 2026, Calendar.FEBRUARY, 28, cairo));
    }

    @Test
    public void unknownAndInvalidTimestampsNeverMatchAnExactDate() {
        TimeZone local = TimeZone.getDefault();

        assertFalse(LocalCreationDateQuery.matches(null, 2026, Calendar.MARCH, 1, local));
        assertFalse(LocalCreationDateQuery.matches(0L, 2026, Calendar.MARCH, 1, local));
        assertFalse(LocalCreationDateQuery.matches(-1L, 2026, Calendar.MARCH, 1, local));
        assertFalse(LocalCreationDateQuery.matches(1L, 2026, Calendar.MARCH, 1, null));
    }
}
