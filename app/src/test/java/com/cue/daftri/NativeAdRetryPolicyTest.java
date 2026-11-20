package com.cue.daftri;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NativeAdRetryPolicyTest {
    @Test
    public void redrawTriggeredFailuresCannotReenterPastRetryCap() {
        NativeAdRetryPolicy policy = new NativeAdRetryPolicy();

        assertTrue(policy.tryScheduleRetry());
        assertTrue(policy.tryScheduleRetry());
        assertEquals(NativeAdRetryPolicy.MAX_RETRIES, policy.getRetriesScheduled());

        // Repeated card redraws can trigger more failure paths, but do not reset this cycle.
        for (int redraw = 0; redraw < 50; redraw++) {
            assertFalse(policy.tryScheduleRetry());
        }
        assertEquals(NativeAdRetryPolicy.MAX_RETRIES, policy.getRetriesScheduled());
    }

    @Test
    public void resetBeginsANewBoundedCycle() {
        NativeAdRetryPolicy policy = new NativeAdRetryPolicy();
        assertTrue(policy.tryScheduleRetry());
        assertTrue(policy.tryScheduleRetry());
        assertFalse(policy.tryScheduleRetry());

        policy.reset();

        assertEquals(0, policy.getRetriesScheduled());
        assertTrue(policy.tryScheduleRetry());
    }

    @Test
    public void nativeRetryDelayIsFiveSeconds() {
        assertEquals(5_000L, NativeAdRetryPolicy.RETRY_DELAY_MS);
    }
}
