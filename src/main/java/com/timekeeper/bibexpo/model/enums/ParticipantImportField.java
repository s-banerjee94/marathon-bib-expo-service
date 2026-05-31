package com.timekeeper.bibexpo.model.enums;

import java.util.Arrays;
import java.util.List;

/**
 * Canonical set of participant fields a CSV column can be mapped to during a dynamic import.
 * The {@code key} is the wire value the frontend sends as {@code targetField}; the backend
 * validates every mapping against this enum so unknown targets are rejected up front.
 */
public enum ParticipantImportField {

    CHIP_NUMBER("chipNumber", "Chip number", true),
    BIB_NUMBER("bibNumber", "Bib number", true),
    FULL_NAME("fullName", "Name", true),
    DATE_OF_BIRTH("dateOfBirth", "Date of birth", false),
    AGE("age", "Age", true),
    GENDER("gender", "Gender", true),
    RACE_NAME("raceName", "Race", true),
    CATEGORY_NAME("categoryName", "Category", true),
    PHONE_NUMBER("phoneNumber", "Phone number", true),
    EMAIL("email", "Email", false),
    COUNTRY("country", "Country", false),
    CITY("city", "City", false);

    private final String key;
    private final String label;
    private final boolean required;

    ParticipantImportField(String key, String label, boolean required) {
        this.key = key;
        this.label = label;
        this.required = required;
    }

    public String getKey() {
        return key;
    }

    public String getLabel() {
        return label;
    }

    public boolean isRequired() {
        return required;
    }

    public static ParticipantImportField fromKey(String key) {
        if (key == null) return null;
        return Arrays.stream(values())
                .filter(f -> f.key.equals(key))
                .findFirst()
                .orElse(null);
    }

    public static boolean isKnown(String key) {
        return fromKey(key) != null;
    }

    public static List<ParticipantImportField> requiredFields() {
        return Arrays.stream(values()).filter(ParticipantImportField::isRequired).toList();
    }
}
