package com.timekeeper.bibexpo.service.dashboard;

/**
 * Bounds for the numeric dashboard query parameters, shared by the organization and platform
 * dashboards so both clamp identically. Out-of-range values are pulled into range rather than
 * rejected: these are display knobs, and a bad one should not fail the whole rollup. The bounds
 * are also what the {@code @Parameter} descriptions on the controller APIs advertise.
 */
public final class DashboardQueryLimits {

    public static final int MIN_TREND_BUCKETS = 1;
    public static final int MAX_TREND_BUCKETS = 90;
    public static final int MIN_TOP_N = 1;
    public static final int MAX_TOP_N = 20;

    private DashboardQueryLimits() {
    }

    /** Clamps the requested trend bucket count into the supported range. */
    public static int trendBuckets(int requested) {
        return clamp(requested, MIN_TREND_BUCKETS, MAX_TREND_BUCKETS);
    }

    /** Clamps a "top N" size (cities, organizations) into the supported range. */
    public static int topN(int requested) {
        return clamp(requested, MIN_TOP_N, MAX_TOP_N);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
