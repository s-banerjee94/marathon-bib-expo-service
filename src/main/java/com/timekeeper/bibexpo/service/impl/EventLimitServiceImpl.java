package com.timekeeper.bibexpo.service.impl;

import com.timekeeper.bibexpo.exception.EventLimitExceededException;
import com.timekeeper.bibexpo.exception.EventNotFoundException;
import com.timekeeper.bibexpo.model.dto.request.UpdateEventLimitRequest;
import com.timekeeper.bibexpo.model.dto.response.EventLimitResponse;
import com.timekeeper.bibexpo.model.entity.Event;
import com.timekeeper.bibexpo.model.entity.EventLimit;
import com.timekeeper.bibexpo.model.entity.Race;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.model.enums.ImportMode;
import com.timekeeper.bibexpo.repository.CategoryRepository;
import com.timekeeper.bibexpo.repository.EventLimitRepository;
import com.timekeeper.bibexpo.repository.EventRepository;
import com.timekeeper.bibexpo.repository.ImportJobRepository;
import com.timekeeper.bibexpo.repository.RaceRepository;
import com.timekeeper.bibexpo.repository.SmsCampaignRepository;
import com.timekeeper.bibexpo.repository.SmsTemplateRepository;
import com.timekeeper.bibexpo.repository.dynamodb.EventStatsDDBRepository;
import com.timekeeper.bibexpo.service.EventLimitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
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
    private final SmsTemplateRepository smsTemplateRepository;
    private final SmsCampaignRepository smsCampaignRepository;
    private final ImportJobRepository importJobRepository;
    private final EventStatsDDBRepository eventStatsRepo;
    private final ObjectMapper objectMapper;

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
                () -> eventStatsRepo.getTotalParticipantCount(eventId.toString()),
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
                () -> countGoodiesItems(event.getEventGoodies()),
                limits::setMaxGoodies,
                "You cannot set the goodies limit below the current number of configured goodies (%d).");

        applyLimit(request.getMaxSmsTemplates(),
                () -> smsTemplateRepository.countByEventId(eventId),
                limits::setMaxSmsTemplates,
                "You cannot set the SMS template limit below the current number of templates (%d).");

        applyLimit(request.getMaxSmsCampaigns(),
                () -> smsCampaignRepository.countByEventId(eventId),
                limits::setMaxSmsCampaigns,
                "You cannot set the campaign limit below the current number of campaigns (%d).");

        applyLimit(request.getMaxImports(),
                () -> importJobRepository.countByEventIdAndMode(eventId, ImportMode.IMPORT),
                limits::setMaxImports,
                "You cannot set the import limit below the number of full imports already run (%d).");

        applyLimit(request.getMaxAddOns(),
                () -> importJobRepository.countByEventIdAndMode(eventId, ImportMode.ADD_ON),
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

    private int countGoodiesItems(String goodiesJson) {
        if (goodiesJson == null || goodiesJson.isBlank()) return 0;
        try {
            JsonNode node = objectMapper.readTree(goodiesJson);
            return node.size();
        } catch (Exception e) {
            return 0;
        }
    }
}
