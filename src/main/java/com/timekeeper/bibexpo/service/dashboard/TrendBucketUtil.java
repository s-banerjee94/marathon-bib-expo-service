package com.timekeeper.bibexpo.service.dashboard;

import com.timekeeper.bibexpo.model.enums.TrendInterval;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared helpers for laying out trend buckets (start dates, end dates, labels) used by the
 * platform growth-trend and revenue-trend builders. Buckets are oldest-first and the last one
 * is the current (partial) bucket.
 */
public final class TrendBucketUtil {

    private static final DateTimeFormatter MONTH_LABEL = DateTimeFormatter.ofPattern("yyyy-MM");

    private TrendBucketUtil() {
    }

    /** The start date of each bucket, oldest first, ending with the bucket containing {@code today}. */
    public static List<LocalDate> bucketStarts(TrendInterval interval, int buckets, LocalDate today) {
        List<LocalDate> dates = new ArrayList<>(buckets);
        switch (interval) {
            case DAY -> {
                for (int i = buckets - 1; i >= 0; i--) dates.add(today.minusDays(i));
            }
            case WEEK -> {
                LocalDate monday = today.with(DayOfWeek.MONDAY);
                for (int i = buckets - 1; i >= 0; i--) dates.add(monday.minusWeeks(i));
            }
            case MONTH -> {
                LocalDate firstOfMonth = today.withDayOfMonth(1);
                for (int i = buckets - 1; i >= 0; i--) dates.add(firstOfMonth.minusMonths(i));
            }
        }
        return dates;
    }

    /** The (inclusive) last day of the bucket beginning at {@code bucketStart}. */
    public static LocalDate bucketEnd(TrendInterval interval, LocalDate bucketStart) {
        return switch (interval) {
            case DAY   -> bucketStart;
            case WEEK  -> bucketStart.plusDays(6);
            case MONTH -> bucketStart.plusMonths(1).minusDays(1);
        };
    }

    /** Bucket label — {@code yyyy-MM} for MONTH, ISO date otherwise. */
    public static String label(TrendInterval interval, LocalDate bucketStart) {
        return interval == TrendInterval.MONTH ? bucketStart.format(MONTH_LABEL) : bucketStart.toString();
    }
}
