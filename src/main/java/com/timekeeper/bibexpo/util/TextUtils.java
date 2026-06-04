package com.timekeeper.bibexpo.util;

/**
 * Generic string helpers for normalising request and import values before storage or comparison.
 */
public final class TextUtils {

    private TextUtils() {
    }

    /**
     * Trims the value and collapses a blank result to {@code null}.
     *
     * @param value raw value
     * @return the trimmed value, or {@code null} when null or blank
     */
    public static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * Trims and upper-cases the value, collapsing a blank result to {@code null}.
     *
     * @param value raw value
     * @return the trimmed, upper-cased value, or {@code null} when null or blank
     */
    public static String toUpperOrNull(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? null : trimmed.toUpperCase();
    }

    /**
     * Trims and lower-cases the value, collapsing a blank result to {@code null}.
     *
     * @param value raw value
     * @return the trimmed, lower-cased value, or {@code null} when null or blank
     */
    public static String toLowerOrNull(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? null : trimmed.toLowerCase();
    }

    /**
     * @param value raw value
     * @return the value, or an empty string when null
     */
    public static String nullSafe(String value) {
        return value != null ? value : "";
    }
}
