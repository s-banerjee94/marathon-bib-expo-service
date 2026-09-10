package com.timekeeper.bibexpo.participant.service.impl;

import com.timekeeper.bibexpo.audit.api.AuditAction;
import com.timekeeper.bibexpo.audit.api.AuditEntityType;
import com.timekeeper.bibexpo.audit.api.Auditable;
import com.timekeeper.bibexpo.event.api.EventQuota;
import com.timekeeper.bibexpo.event.api.EventStore;
import com.timekeeper.bibexpo.event.limit.exception.EventLimitExceededException;
import com.timekeeper.bibexpo.event.model.dto.response.EventGoodieResponse;
import com.timekeeper.bibexpo.event.model.entity.Event;
import com.timekeeper.bibexpo.event.model.entity.EventGoodie;
import com.timekeeper.bibexpo.event.model.entity.GoodieSource;
import com.timekeeper.bibexpo.event.model.enums.EventOperation;
import com.timekeeper.bibexpo.event.service.validator.EventAccessValidator;
import com.timekeeper.bibexpo.event.service.validator.EventOperationGuard;
import com.timekeeper.bibexpo.participant.api.GoodieRemovalGuard;
import com.timekeeper.bibexpo.participant.exception.EventGoodieAlreadyExistsException;
import com.timekeeper.bibexpo.participant.exception.EventGoodieInUseException;
import com.timekeeper.bibexpo.participant.exception.EventGoodieNotFoundException;
import com.timekeeper.bibexpo.participant.model.dto.request.AddEventGoodieRequest;
import com.timekeeper.bibexpo.participant.model.dynamodb.ParticipantDDB;
import com.timekeeper.bibexpo.participant.repository.ParticipantDDBRepository;
import com.timekeeper.bibexpo.participant.service.EventGoodieService;
import com.timekeeper.bibexpo.participant.service.ParticipantStatisticsService;
import com.timekeeper.bibexpo.user.model.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventGoodieServiceImpl implements EventGoodieService {

    private static final int PARTICIPANT_PAGE_SIZE = 100;
    private static final String LIMIT_MESSAGE =
            "You have exceeded the maximum number of goodies allowed for this event.";

    private final EventStore eventStore;
    private final EventAccessValidator eventAccessValidator;
    private final EventOperationGuard eventOperationGuard;
    private final EventQuota eventQuota;
    private final ParticipantDDBRepository participantRepository;
    private final ParticipantStatisticsService participantStatisticsService;
    private final List<GoodieRemovalGuard> removalGuards;

    @Override
    public List<EventGoodieResponse> listGoodies(Long eventId, User currentUser) {
        Event event = eventStore.requireById(eventId);
        eventAccessValidator.validateUserAuthorizationForEvent(currentUser, event);
        return event.getEventGoodies().stream().map(EventGoodieResponse::from).toList();
    }

    @Auditable(entityType = AuditEntityType.EVENT, action = AuditAction.UPDATE)
    @Override
    @Transactional
    public EventGoodieResponse addGoodie(Long eventId, AddEventGoodieRequest request, User currentUser) {
        Event event = eventStore.requireById(eventId);
        eventAccessValidator.validateUserAuthorizationForEvent(currentUser, event);
        eventOperationGuard.requireAllowed(event, EventOperation.GOODIE_WRITE);

        String name = request.getName().trim();
        List<EventGoodie> goodies = new ArrayList<>(event.getEventGoodies());
        if (find(goodies, name) != null) {
            throw new EventGoodieAlreadyExistsException();
        }
        if (goodies.size() >= eventQuota.forEvent(eventId).maxGoodies()) {
            throw new EventLimitExceededException(LIMIT_MESSAGE);
        }

        EventGoodie goodie = new EventGoodie(name, GoodieSource.MANUAL);
        goodies.add(goodie);
        eventStore.saveGoodies(eventId, goodies);
        log.info("Added goody '{}' to event {} by user {}", name, eventId, currentUser.getUsername());
        return EventGoodieResponse.from(goodie);
    }

    @Auditable(entityType = AuditEntityType.EVENT, action = AuditAction.UPDATE)
    @Override
    @Transactional
    public void removeGoodie(Long eventId, String name, User currentUser) {
        Event event = eventStore.requireById(eventId);
        eventAccessValidator.validateUserAuthorizationForEvent(currentUser, event);

        List<EventGoodie> goodies = new ArrayList<>(event.getEventGoodies());
        EventGoodie goodie = find(goodies, name);
        if (goodie == null) {
            throw new EventGoodieNotFoundException();
        }
        boolean imported = goodie.source() == GoodieSource.IMPORT;
        eventOperationGuard.requireAllowed(event, imported ? EventOperation.GOODIE_PURGE : EventOperation.GOODIE_WRITE);
        for (GoodieRemovalGuard guard : removalGuards) {
            guard.findBlockingReason(eventId, goodie.name()).ifPresent(reason -> {
                throw new EventGoodieInUseException(reason);
            });
        }

        // The whole roster is read before anything is written, so a goody handed out to even one
        // participant stops the removal with every record still as it was.
        String key = key(goodie.name());
        String now = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString();
        List<ParticipantDDB> carrying = new ArrayList<>();
        for (Page<ParticipantDDB> page : participantRepository.findPagesByEventId(eventId, PARTICIPANT_PAGE_SIZE)) {
            for (ParticipantDDB participant : page.items()) {
                if (holds(participant.getGoodiesDistribution(), key)) {
                    throw new EventGoodieInUseException(EventGoodieInUseException.HANDED_OUT_MESSAGE);
                }
                if (imported && holds(participant.getGoodies(), key)) {
                    Map<String, String> kept = new HashMap<>(participant.getGoodies());
                    kept.keySet().removeIf(entry -> key(entry).equals(key));
                    participant.setGoodies(kept);
                    participant.setUpdatedAt(now);
                    participant.setUpdatedBy(currentUser.getUsername());
                    carrying.add(participant);
                }
            }
        }

        if (!carrying.isEmpty()) {
            participantRepository.batchSave(carrying);
        }
        goodies.remove(goodie);
        eventStore.saveGoodies(eventId, goodies);
        if (!carrying.isEmpty()) {
            rebuildCounters(event);
        }
        log.info("Removed goody '{}' from event {} and {} participant record(s) by user {}",
                goodie.name(), eventId, carrying.size(), currentUser.getUsername());
    }

    @Override
    @Transactional
    public void addImportedGoodies(Long eventId, List<String> names) {
        Event event = eventStore.findById(eventId).orElse(null);
        if (event == null) {
            log.warn("Event {} not found when adding imported goodies", eventId);
            return;
        }

        List<EventGoodie> goodies = new ArrayList<>(event.getEventGoodies());
        Set<String> known = new HashSet<>();
        goodies.forEach(goodie -> known.add(key(goodie.name())));
        List<EventGoodie> fresh = new ArrayList<>();
        boolean upgraded = false;
        for (String name : names) {
            if (name == null || name.isBlank()) {
                continue;
            }
            if (known.add(key(name))) {
                fresh.add(new EventGoodie(name, GoodieSource.IMPORT));
                continue;
            }
            EventGoodie existing = find(goodies, name);
            if (existing != null && existing.source() == GoodieSource.MANUAL) {
                // Participant records carry it now, so removing it later has to reach them too.
                goodies.set(goodies.indexOf(existing), new EventGoodie(existing.name(), GoodieSource.IMPORT));
                upgraded = true;
            }
        }

        int limit = eventQuota.forEvent(eventId).maxGoodies();
        if (goodies.size() + fresh.size() > limit) {
            log.warn("Skipping new goodies {} for event {}: the list would hold {} but the limit is {}",
                    fresh.stream().map(EventGoodie::name).toList(), eventId, goodies.size() + fresh.size(), limit);
            fresh.clear();
        }
        if (fresh.isEmpty() && !upgraded) {
            return;
        }
        goodies.addAll(fresh);
        eventStore.saveGoodies(eventId, goodies);
        log.info("Updated goodies of event {} from an import, adding {}",
                eventId, fresh.stream().map(EventGoodie::name).toList());
    }

    private void rebuildCounters(Event event) {
        try {
            participantStatisticsService.rebuild(event);
        } catch (Exception e) {
            log.warn("Counters of event {} were not rebuilt after a goody removal; the next reconcile will "
                    + "rebuild them", event.getId(), e);
        }
    }

    private static EventGoodie find(List<EventGoodie> goodies, String name) {
        String key = key(name);
        return goodies.stream().filter(goodie -> key(goodie.name()).equals(key)).findFirst().orElse(null);
    }

    private static boolean holds(Map<String, String> goodies, String key) {
        return goodies != null && goodies.keySet().stream().anyMatch(name -> key(name).equals(key));
    }

    // Compared the way race and category names are: trimmed and without regard to case.
    private static String key(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }
}
