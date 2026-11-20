package com.cue.daftri;

import org.junit.Test;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;

public class TrashCleanupWorkerTest {
    private static final long THIRTY_DAYS_MILLIS = TimeUnit.DAYS.toMillis(30L);

    @Test
    public void expiryIsThirtyDaysForShortAndLongCalendarMonths() {
        long januaryEnd = utcMillis(2025, Calendar.JANUARY, 31);
        long februaryEnd = utcMillis(2024, Calendar.FEBRUARY, 29);
        long marchEnd = utcMillis(2025, Calendar.MARCH, 31);

        assertEquals(THIRTY_DAYS_MILLIS, TrashCleanupWorker.expiryTime(januaryEnd) - januaryEnd);
        assertEquals(THIRTY_DAYS_MILLIS, TrashCleanupWorker.expiryTime(februaryEnd) - februaryEnd);
        assertEquals(THIRTY_DAYS_MILLIS, TrashCleanupWorker.expiryTime(marchEnd) - marchEnd);
    }

    private long utcMillis(int year, int month, int day) {
        Calendar date = new GregorianCalendar(TimeZone.getTimeZone("UTC"), Locale.US);
        date.clear();
        date.set(year, month, day, 12, 0, 0);
        return date.getTimeInMillis();
    }
}
