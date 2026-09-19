package com.timekeeper.bibexpo.inventory.service.impl;

import com.timekeeper.bibexpo.inventory.repository.InventoryGoodieMappingRepository;
import com.timekeeper.bibexpo.participant.api.GoodieRemovalGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Keeps a goody on its event's list while it is still linked to an inventory item. The link is
 * something the organizer set up and can see, so it is theirs to undo rather than something to
 * sweep away quietly.
 */
@Component
@RequiredArgsConstructor
public class GoodieMappingRemovalGuard implements GoodieRemovalGuard {

    private final InventoryGoodieMappingRepository mappingRepository;

    @Override
    public Optional<String> findBlockingReason(Long eventId, String goodieName) {
        return mappingRepository.existsByEventIdAndGoodieName(eventId, goodieName)
                ? Optional.of("You must unlink this goody from its inventory item before removing it.")
                : Optional.empty();
    }
}
