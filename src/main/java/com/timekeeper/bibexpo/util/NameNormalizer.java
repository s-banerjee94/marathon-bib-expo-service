package com.timekeeper.bibexpo.util;

/**
 * Canonicalises race and category names for storage: trimmed and upper-cased,
 * applied on every create, update, and CSV-import write so names are stored consistently.
 */
public final class NameNormalizer {

    private NameNormalizer() {
    }

    /**
     * Returns the trimmed, upper-cased form of the value, or {@code null} when the value is null.
     *
     * @param value raw name from a request or import row
     * @return canonical stored name, or null when input is null
     */
    public static String toStoredName(String value) {
        return value == null ? null : value.trim().toUpperCase();
    }
}
