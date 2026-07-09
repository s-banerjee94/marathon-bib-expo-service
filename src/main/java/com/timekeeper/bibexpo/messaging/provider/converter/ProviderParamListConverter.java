package com.timekeeper.bibexpo.messaging.provider.converter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.timekeeper.bibexpo.messaging.provider.model.ProviderParam;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.List;

/**
 * Stores the provider's request-parameter mapping as a JSON array in a single column.
 */
@Converter
public class ProviderParamListConverter implements AttributeConverter<List<ProviderParam>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<ProviderParam>> TYPE = new TypeReference<>() {
    };

    @Override
    public String convertToDatabaseColumn(List<ProviderParam> params) {
        if (params == null || params.isEmpty()) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(params);
        } catch (Exception e) {
            throw new IllegalStateException("Could not serialize provider params", e);
        }
    }

    @Override
    public List<ProviderParam> convertToEntityAttribute(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return MAPPER.readValue(json, TYPE);
        } catch (Exception e) {
            throw new IllegalStateException("Could not deserialize provider params", e);
        }
    }
}
