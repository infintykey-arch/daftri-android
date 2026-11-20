package com.cue.daftri;

/** Bounded retry state for the single native-ad slot in the home screen. */
final class NativeAdRetryPolicy {
    static final int MAX_RETRIES = 2;
    static final long RETRY_DELAY_MS = 5_000L;

    private int retriesScheduled;

    /** Reserves one retry, returning false once the per-cycle cap is reached. */
    boolean tryScheduleRetry() {
        if (retriesScheduled >= MAX_RETRIES) return false;
        retriesScheduled++;
        return true;
    }

    int getRetriesScheduled() {
        return retriesScheduled;
    }

    /** Starts a fresh bounded cycle after an ad is loaded/removed or consent changes. */
    void reset() {
        retriesScheduled = 0;
    }
}
