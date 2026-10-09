package com.timekeeper.bibexpo.shared.persistence;

import com.timekeeper.bibexpo.shared.error.InvalidUserDataException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Turns a DynamoDB lastEvaluatedKey into an opaque page token and back. Every token carries the
 * scope of the list that issued it (its partition and index), so a token replayed against another
 * event or another list is refused with a 400 instead of reaching DynamoDB, which answers a start
 * key from another partition or key schema with a ValidationException (a 500).
 */
@Component
@RequiredArgsConstructor
public class DynamoDBPaginationCodec {

    private static final String INVALID = "Your page token is not valid, so please start again from the first page.";
    private static final String OTHER_LIST = "Your page token belongs to a different list, so please start again from the first page.";

    private final JsonMapper objectMapper;

    /**
     * @param lastEvaluatedKey the key DynamoDB returned, or null/empty on the last page
     * @param scope            identifies the list: its partition, index and anything else that shapes the query
     * @return the page token, or null when there is no further page
     */
    public String encode(Map<String, AttributeValue> lastEvaluatedKey, String scope) {
        if (lastEvaluatedKey == null || lastEvaluatedKey.isEmpty()) {
            return null;
        }
        Map<String, Object> key = new HashMap<>();
        lastEvaluatedKey.forEach((name, value) -> key.put(name, attributeValueToObject(value)));
        String json = objectMapper.writeValueAsString(Map.of("scope", scope, "key", key));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * @param token the page token from the client, or null/blank for the first page
     * @param scope the scope of the list being read, as passed to {@link #encode}
     * @return the exclusive start key, or null for the first page
     * @throws InvalidUserDataException when the token is malformed or was issued by another list
     */
    @SuppressWarnings("unchecked")
    public Map<String, AttributeValue> decode(String token, String scope) {
        if (token == null || token.isBlank()) {
            return null;
        }
        Map<String, Object> envelope;
        try {
            String json = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
            envelope = objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            throw new InvalidUserDataException(INVALID);
        }
        if (!scope.equals(envelope.get("scope"))) {
            throw new InvalidUserDataException(OTHER_LIST);
        }
        if (!(envelope.get("key") instanceof Map<?, ?> key) || key.isEmpty()) {
            throw new InvalidUserDataException(INVALID);
        }
        Map<String, AttributeValue> attributeMap = new HashMap<>();
        ((Map<String, Object>) key).forEach((name, value) -> attributeMap.put(name, objectToAttributeValue(value)));
        return attributeMap;
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
        if (!(value instanceof Map<?, ?> map)) {
            throw new InvalidUserDataException(INVALID);
        }
        if (map.get("S") instanceof String s) {
            return AttributeValue.builder().s(s).build();
        } else if (map.get("N") instanceof String n) {
            return AttributeValue.builder().n(n).build();
        } else if (map.get("BOOL") instanceof Boolean b) {
            return AttributeValue.builder().bool(b).build();
        } else if (map.containsKey("NULL")) {
            return AttributeValue.builder().nul(true).build();
        }
        throw new InvalidUserDataException(INVALID);
    }
}
