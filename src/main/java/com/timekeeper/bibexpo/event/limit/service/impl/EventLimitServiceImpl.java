package com.timekeeper.bibexpo.event.limit.service.impl;

import com.timekeeper.bibexpo.event.limit.exception.EventLimitExceededException;
import com.timekeeper.bibexpo.event.exception.EventNotFoundException;
import com.timekeeper.bibexpo.event.limit.model.dto.request.UpdateEventLimitRequest;
import com.timekeeper.bibexpo.event.limit.model.dto.response.EventLimitResponse;
import com.timekeeper.bibexpo.event.model.entity.Event;
import com.timekeeper.bibexpo.event.limit.model.entity.EventLimit;
import com.timekeeper.bibexpo.event.race.model.entity.Race;
import com.timekeeper.bibexpo.event.race.category.repository.CategoryRepository;
import com.timekeeper.bibexpo.event.api.EventStatsQuery;
import com.timekeeper.bibexpo.event.limit.repository.EventLimitRepository;
import com.timekeeper.bibexpo.event.repository.EventRepository;
import com.timekeeper.bibexpo.event.race.repository.RaceRepository;
import com.timekeeper.bibexpo.event.api.EventCampaignUsage;
import com.timekeeper.bibexpo.event.limit.service.EventLimitService;
import com.timekeeper.bibexpo.event.service.util.EventGoodiesReader;
import com.timekeeper.bibexpo.user.model.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.LongSupplier;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventLimitServiceImpl implements EventLimitService {

    private final EventLimitRepository eventLimitRepository;
    private final EventRepository eventRepository;
    private final RaceRepository raceRepository;
    private final CategoryRepository categoryRepository;
    private final EventCampaignUsage eventCampaignUsage;
    private final EventStatsQuery eventStatsQuery;
    private final EventGoodiesReader goodiesReader;

    @Override
    public EventLimitResponse getEventLimits(Long eventId, User currentUser) {
        if (!eventRepository.existsById(eventId)) {
            throw new EventNotFoundException();
        }
        EventLimit limits = eventLimitRepository.findByEventId(eventId)
                .orElseGet(() -> EventLimit.builder().eventId(eventId).build());
        return EventLimitResponse.fromEntity(limits);
    }

    @Override
    @Transactional
    public EventLimitResponse updateEventLimits(Long eventId, UpdateEventLimitRequest request, User currentUser) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(EventNotFoundException::new);

        EventLimit limits = eventLimitRepository.findByEventId(eventId)
                .orElseGet(() -> EventLimit.builder().build());

        applyLimit(request.getMaxParticipants(),
                () -> eventStatsQuery.participantCount(eventId),
                limits::setMaxParticipants,
                "You cannot set the participant limit below the current number of participants (%d).");

        applyLimit(request.getMaxRaces(),
                () -> raceRepository.countByEventIdAndDeletedFalse(eventId),
                limits::setMaxRaces,
                "You cannot set the race limit below the current number of races (%d).");

        applyLimit(request.getMaxCategoriesPerRace(),
                () -> maxCategoriesAcrossRaces(eventId),
                limits::setMaxCategoriesPerRace,
                "You cannot set the categories-per-race limit below the highest category count in any race (%d).");

        applyLimit(request.getMaxGoodies(),
                () -> goodiesReader.count(event.getEventGoodies()),
                limits::setMaxGoodies,
                "You cannot set the goodies limit below the current number of configured goodies (%d).");

        applyLimit(request.getMaxSmsTemplates(),
                () -> eventCampaignUsage.countSmsTemplates(eventId),
                limits::setMaxSmsTemplates,
                "You cannot set the SMS template limit below the current number of templates (%d).");

        applyLimit(request.getMaxSmsCampaigns(),
                () -> eventCampaignUsage.countSmsCampaigns(eventId),
                limits::setMaxSmsCampaigns,
                "You cannot set the campaign limit below the current number of campaigns (%d).");

        applyLimit(request.getMaxImports(),
                limits::getUsedImports,
                limits::setMaxImports,
                "You cannot set the import limit below the number of full imports already run (%d).");

        applyLimit(request.getMaxAddOns(),
                limits::getUsedAddOns,
                limits::setMaxAddOns,
                "You cannot set the add-on limit below the number of add-on imports already run (%d).");

        EventLimit saved = eventLimitRepository.save(limits);
        log.info("Updated limits for event {} by user {}", eventId, currentUser.getUsername());
        return EventLimitResponse.fromEntity(saved);
    }

    private void applyLimit(Integer requested, LongSupplier currentCount, IntConsumer setter, String message) {
        if (requested == null) return;
        long current = currentCount.getAsLong();
        if (requested < current) {
            throw new EventLimitExceededException(String.format(message, current));
        }
        setter.accept(requested);
    }

    private int maxCategoriesAcrossRaces(Long eventId) {
        List<Race> races = raceRepository.findByEventIdAndDeletedFalse(eventId);
        int max = 0;
        for (Race race : races) {
            int count = categoryRepository.countByRaceId(race.getId());
            if (count > max) max = count;
        }
        return max;
    }

}
