package com.timekeeper.bibexpo.participant.model.enums;

/**
 * A column group of the participant CSV export. The declaration order is the column order of an
 * export that names no fields, and each column header is the constant's name in words
 * ({@code BIB_NUMBER} becomes "Bib Number").
 */
public enum ExportField {
    CHIP_NUMBER,
    BIB_NUMBER,
    FULL_NAME,
    PHONE_NUMBER,
    EMAIL,
    RACE_NAME,
    CATEGORY_NAME,
    AGE,
    GENDER,
    DATE_OF_BIRTH,
    CITY,
    COUNTRY,
    /** One column per goody any participant carries. */
    GOODIES,
    BIB_COLLECTED_AT,
    EMERGENCY_CONTACT_NAME,
    EMERGENCY_CONTACT_PHONE,
    NOTES,
    /** One column per additional-field key any participant carries. */
    ADDITIONAL_FIELDS
}
