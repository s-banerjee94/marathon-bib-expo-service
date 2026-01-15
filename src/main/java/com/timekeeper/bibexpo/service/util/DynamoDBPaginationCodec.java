package com.timekeeper.bibexpo.service.util;

import com.timekeeper.bibexpo.exception.InvalidUserDataException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import tools.jackson.databind.json.JsonMapper;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class DynamoDBPaginationCodec {

    private final JsonMapper objectMapper;

    public String encode(Map<String, AttributeValue> lastEvaluatedKey) {
        if (lastEvaluatedKey == null || lastEvaluatedKey.isEmpty()) {
            return "";
        }

        try {
            Map<String, Object> simpleMap = new HashMap<>();
            for (Map.Entry<String, AttributeValue> entry : lastEvaluatedKey.entrySet()) {
                simpleMap.put(entry.getKey(), attributeValueToObject(entry.getValue()));
            }
            String json = objectMapper.writeValueAsString(simpleMap);
            return Base64.getEncoder().encodeToString(json.getBytes());
        } catch (Exception e) {
            log.error("Failed to encode lastEvaluatedKey", e);
            return "";
        }
    }

    public Map<String, AttributeValue> decode(String encodedKey) {
        if (encodedKey == null || encodedKey.isEmpty()) {
            return Map.of();
        }

        try {
            String json = new String(Base64.getDecoder().decode(encodedKey));
            Map<String, Object> simpleMap = objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructMapType(HashMap.class, String.class, Object.class));

            Map<String, AttributeValue> attributeMap = new HashMap<>();
            for (Map.Entry<String, Object> entry : simpleMap.entrySet()) {
                attributeMap.put(entry.getKey(), objectToAttributeValue(entry.getValue()));
            }
            return attributeMap;
        } catch (Exception e) {
            log.error("Failed to decode pagination key: {}", encodedKey, e);
            throw new InvalidUserDataException("Invalid pagination key format");
        }
    }

    private Object attributeValueToObject(AttributeValue attributeValue) {
        if (attributeValue.s() != null) {
            return Map.of("S", attributeValue.s());
        } else if (attributeValue.n() != null) {
            return Map.of("N", attributeValue.n());
        } else if (attributeValue.bool() != null) {
            return Map.of("BOOL", attributeValue.bool());
        }
        return Map.of("NULL", true);
    }

    private AttributeValue objectToAttributeValue(Object value) {
        if (!(value instanceof Map)) {
            throw new InvalidUserDataException("Invalid attribute value format");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) value;

        if (map.containsKey("S")) {
            return AttributeValue.builder().s((String) map.get("S")).build();
        } else if (map.containsKey("N")) {
            return AttributeValue.builder().n((String) map.get("N")).build();
        } else if (map.containsKey("BOOL")) {
            return AttributeValue.builder().bool((Boolean) map.get("BOOL")).build();
        } else if (map.containsKey("NULL")) {
            return AttributeValue.builder().nul(true).build();
        }

        throw new InvalidUserDataException("Unsupported attribute value type");
    }
}
