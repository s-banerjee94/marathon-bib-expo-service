package com.timekeeper.bibexpo.event.limit.service.impl;

import com.timekeeper.bibexpo.event.api.EventLimits;
import com.timekeeper.bibexpo.event.api.EventQuota;
import com.timekeeper.bibexpo.event.limit.model.entity.EventLimit;
import com.timekeeper.bibexpo.event.limit.repository.EventLimitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
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
                limits.getMaxAddOns());
    }
}
