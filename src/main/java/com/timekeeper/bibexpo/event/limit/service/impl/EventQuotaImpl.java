package com.timekeeper.bibexpo.event.limit.service.impl;

import com.timekeeper.bibexpo.event.api.EventLimits;
import com.timekeeper.bibexpo.event.api.EventQuota;
import com.timekeeper.bibexpo.event.limit.model.entity.EventLimit;
import com.timekeeper.bibexpo.event.limit.repository.EventLimitRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventQuotaImpl implements EventQuota {

    private final EventLimitRepository eventLimitRepository;

    @Override
    public EventLimits forEvent(Long eventId) {
        // An event with no limits row falls back to the entity's own defaults, which is what
        // every call site did for itself before this port existed.
        EventLimit limits = eventLimitRepository.findByEventId(eventId)
                .orElseGet(() -> EventLimit.builder().build());

        return new EventLimits(
                limits.getMaxParticipants(),
                limits.getMaxRaces(),
                limits.getMaxCategoriesPerRace(),
                limits.getMaxGoodies(),
                limits.getMaxSmsTemplates(),
                limits.getMaxSmsCampaigns(),
                limits.getMaxImports(),
                limits.getMaxAddOns(),
                limits.getUsedImports(),
                limits.getUsedAddOns());
    }

    @Override
    @Transactional
    public void recordFullImport(Long eventId) {
        warnIfMissing(eventLimitRepository.incrementUsedImports(eventId), eventId);
    }

    @Override
    @Transactional
    public void recordAddOnImport(Long eventId) {
        warnIfMissing(eventLimitRepository.incrementUsedAddOns(eventId), eventId);
    }

    // Every event gets a limits row on creation, so a miss means the row was removed underneath a
    // running import. Losing the count is preferable to failing an import that already succeeded.
    private void warnIfMissing(int updated, Long eventId) {
        if (updated == 0) {
            log.warn("No limits row for event {}; import usage was not recorded", eventId);
        }
    }
}
