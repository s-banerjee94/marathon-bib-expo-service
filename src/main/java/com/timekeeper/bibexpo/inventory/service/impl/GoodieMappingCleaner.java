package com.timekeeper.bibexpo.inventory.service.impl;

import com.timekeeper.bibexpo.event.api.EventDeletionCleaner;
import com.timekeeper.bibexpo.inventory.repository.InventoryGoodieMappingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Drops an event's goody links when the event is deleted. A link says nothing on its own once the
 * event is gone, and holding a delete up over one would be an odd thing to ask of an organizer, so
 * these are cleaned away rather than guarded. The stock they pointed at is untouched.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GoodieMappingCleaner implements EventDeletionCleaner {

    private final InventoryGoodieMappingRepository mappingRepository;

    @Override
    public void purgeForEvent(Long eventId) {
        int removed = mappingRepository.deleteByEventId(eventId);
        if (removed > 0) {
            log.info("Deleted {} goody link(s) for event {}", removed, eventId);
        }
    }
}
