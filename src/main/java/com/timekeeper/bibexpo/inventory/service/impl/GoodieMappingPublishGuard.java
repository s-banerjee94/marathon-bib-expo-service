package com.timekeeper.bibexpo.inventory.service.impl;

import com.timekeeper.bibexpo.event.api.EventPublishGuard;
import com.timekeeper.bibexpo.inventory.repository.InventoryGoodieMappingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Holds an event in draft while a goody it has linked to inventory has no location behind it.
 *
 * <p>Linking is optional and an event that links nothing publishes exactly as it always did. But a
 * link with no location cannot deduct anything when the goody is handed over, and the counter on
 * expo morning is the wrong place to discover that.
 */
@Component
@RequiredArgsConstructor
public class GoodieMappingPublishGuard implements EventPublishGuard {

    private final InventoryGoodieMappingRepository mappingRepository;

    @Override
    public Optional<String> findBlockingReason(Long eventId) {
        return mappingRepository.existsByEventIdAndLocationIdIsNull(eventId)
                ? Optional.of("You must choose the location each linked goody is handed out from "
                + "before publishing this event.")
                : Optional.empty();
    }
}
