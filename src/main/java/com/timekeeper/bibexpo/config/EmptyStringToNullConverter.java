package com.timekeeper.bibexpo.config;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * JPA converter that automatically converts empty/blank strings to null before persisting to database.
 * This prevents unique constraint violations on optional string fields.
 * Apply to entity fields with @Convert annotation.
 */
@Converter
public class EmptyStringToNullConverter implements AttributeConverter<String, String> {

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return (attribute == null || attribute.isBlank()) ? null : attribute;
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return dbData;
    }
}
