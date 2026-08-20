package com.timekeeper.bibexpo.event.service.util;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Reads the goodies list an event stores as a JSON array in {@code events.event_goodies}.
 */
@Component
@RequiredArgsConstructor
public class EventGoodiesReader {

    private final ObjectMapper objectMapper;

    /**
     * Counts the entries in an event's goodies list.
     *
     * @param goodiesJson the stored JSON array, may be {@code null} or blank
     * @return the number of entries, or 0 when the value is absent or cannot be read — an
     *         unreadable value must not block a limit check that is not about it
     */
    public int count(String goodiesJson) {
        if (goodiesJson == null || goodiesJson.isBlank()) return 0;
        try {
            JsonNode node = objectMapper.readTree(goodiesJson);
            return node.size();
        } catch (Exception e) {
            return 0;
        }
    }
}
