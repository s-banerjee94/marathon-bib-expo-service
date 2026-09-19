package com.timekeeper.bibexpo.event.model.converter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.timekeeper.bibexpo.event.model.entity.EventGoodie;
import com.timekeeper.bibexpo.event.model.entity.GoodieSource;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.ArrayList;
import java.util.List;

/**
 * Stores an event's goodies as a JSON array of {@code {"name", "source"}} objects in one column.
 *
 * <p>Lists written before the source was recorded come in two older shapes, and both still read.
 * A plain array of names was only ever written by the importer, so each name reads as
 * {@link GoodieSource#IMPORT}. An object keyed by name was only ever written by the event form, so
 * each key reads as {@link GoodieSource#MANUAL}.
 */
@Converter
public class EventGoodieListConverter implements AttributeConverter<List<EventGoodie>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(List<EventGoodie> goodies) {
        if (goodies == null || goodies.isEmpty()) {
            return null;
        }
        ArrayNode array = MAPPER.createArrayNode();
        goodies.forEach(goodie -> array.addObject()
                .put("name", goodie.name())
                .put("source", goodie.source().name()));
        try {
            return MAPPER.writeValueAsString(array);
        } catch (Exception e) {
            throw new IllegalStateException("Could not serialize the event goodies list", e);
        }
    }

    @Override
    public List<EventGoodie> convertToEntityAttribute(String json) {
        List<EventGoodie> goodies = new ArrayList<>();
        if (json == null || json.isBlank()) {
            return goodies;
        }
        try {
            JsonNode root = MAPPER.readTree(json);
            if (root.isObject()) {
                root.fieldNames().forEachRemaining(name -> goodies.add(new EventGoodie(name, GoodieSource.MANUAL)));
                return goodies;
            }
            for (JsonNode node : root) {
                goodies.add(node.isTextual()
                        ? new EventGoodie(node.asText(), GoodieSource.IMPORT)
                        : new EventGoodie(node.path("name").asText(),
                        GoodieSource.valueOf(node.path("source").asText(GoodieSource.IMPORT.name()))));
            }
            return goodies;
        } catch (Exception e) {
            throw new IllegalStateException("Could not read the event goodies list", e);
        }
    }
}
