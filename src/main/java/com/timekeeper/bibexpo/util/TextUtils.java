package com.timekeeper.bibexpo.util;

import java.util.function.Consumer;

/**
 * Generic string helpers for normalising request and import values before storage or comparison.
 */
public final class TextUtils {

    private TextUtils() {
    }

    /**
     * Applies a sent optional string field under merge-patch semantics: an absent field
     * ({@code null}) is left unchanged, a blank field clears the value to {@code null}, and a
     * non-blank field is stored trimmed.
     *
     * @param value  the raw request value (absent fields arrive as {@code null})
     * @param setter consumes the resolved value when the field was sent
     */
    public static void applyIfSent(String value, Consumer<String> setter) {
        if (value != null) {
            setter.accept(trimToNull(value));
        }
    }

    /**
     * Applies a sent non-string optional field (e.g. {@code Double}, {@code Integer}, enums):
     * an absent value ({@code null}) is left unchanged, otherwise it is stored as sent. Such
     * fields cannot be cleared because the wire cannot express "clear" for a non-string.
     *
     * @param value  the raw request value
     * @param setter consumes the value when the field was sent
     */
    public static <T> void applyIfSent(T value, Consumer<T> setter) {
        if (value != null) {
            setter.accept(value);
        }
    }

    /**
     * Applies a sent required string field: an absent or blank value is left unchanged (the
     * field can never be cleared), otherwise the trimmed value is stored.
     *
     * @param value  the raw request value
     * @param setter consumes the trimmed value when a non-blank field was sent
     */
    public static void applyRequiredIfSent(String value, Consumer<String> setter) {
        if (value != null && !value.isBlank()) {
            setter.accept(value.trim());
        }
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
