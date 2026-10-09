package com.timekeeper.bibexpo.event.race.service.impl;

import com.timekeeper.bibexpo.audit.api.Auditable;
import com.timekeeper.bibexpo.audit.api.AuditAction;
import com.timekeeper.bibexpo.audit.api.AuditContextHolder;
import com.timekeeper.bibexpo.audit.api.AuditEntityType;
import com.timekeeper.bibexpo.event.api.EventLimits;
import com.timekeeper.bibexpo.event.api.EventQuota;
import com.timekeeper.bibexpo.event.limit.exception.EventLimitExceededException;
import com.timekeeper.bibexpo.event.exception.EventNotFoundException;
import com.timekeeper.bibexpo.event.race.exception.RaceAlreadyExistsException;
import com.timekeeper.bibexpo.event.race.exception.RaceDeletionNotAllowedException;
import com.timekeeper.bibexpo.event.race.exception.RaceNotFoundException;
import com.timekeeper.bibexpo.event.race.model.dto.request.CreateRaceRequest;
import com.timekeeper.bibexpo.event.race.model.dto.request.UpdateRaceRequest;
import com.timekeeper.bibexpo.event.race.model.dto.response.RaceResponse;
import com.timekeeper.bibexpo.event.model.entity.Event;
import com.timekeeper.bibexpo.event.race.model.entity.Race;
import com.timekeeper.bibexpo.event.model.enums.EventOperation;
import com.timekeeper.bibexpo.event.repository.EventRepository;
import com.timekeeper.bibexpo.event.race.repository.RaceRepository;
import com.timekeeper.bibexpo.event.race.service.RaceService;
import com.timekeeper.bibexpo.event.race.service.util.RaceCategoryNameResolver;
import com.timekeeper.bibexpo.event.service.validator.EventAccessValidator;
import com.timekeeper.bibexpo.event.service.validator.EventOperationGuard;
import com.timekeeper.bibexpo.shared.error.InvalidUserDataException;
import com.timekeeper.bibexpo.shared.util.EventDateTimeUtil;
import com.timekeeper.bibexpo.shared.util.NameNormalizer;
import com.timekeeper.bibexpo.shared.util.TextUtils;
import com.timekeeper.bibexpo.user.model.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class RaceServiceImpl implements RaceService {

    private static final String RACE_NAME_TAKEN = "A race with this name already exists for this event.";

    private final RaceRepository raceRepository;
    private final EventRepository eventRepository;
    private final EventAccessValidator eventAccessValidator;
    private final EventQuota eventQuota;
    private final EventOperationGuard eventOperationGuard;
    private final RaceCategoryNameResolver nameResolver;

    @Auditable(entityType = AuditEntityType.RACE, action = AuditAction.CREATE)
    @Override
    @Transactional
    public RaceResponse createRace(Long eventId, CreateRaceRequest request, User currentUser) {
        Event event = requireAccessibleEvent(eventId, currentUser);
        eventOperationGuard.requireAllowed(event, EventOperation.RACE_WRITE);

        EventLimits limits = eventQuota.forEvent(eventId);
        if (raceRepository.countByEventIdAndDeletedFalse(eventId) >= limits.maxRaces()) {
            throw new EventLimitExceededException("You have reached the maximum number of races allowed for this event.");
        }

        String raceName = NameNormalizer.toStoredName(request.getRaceName());
        if (raceRepository.existsByRaceNameAndEventIdAndDeletedFalse(raceName, eventId)) {
            throw new RaceAlreadyExistsException(RACE_NAME_TAKEN);
        }

        Race race = Race.builder()
                .raceName(raceName)
                .raceDescription(request.getRaceDescription())
                .reportingTime(resolveReportingInstant(event, request.getReportingDate(), request.getReportingTime()))
                .event(event)
                .deleted(false)
                .build();

        Race savedRace = raceRepository.save(race);
        nameResolver.evict(eventId);
        log.atInfo()
                .addKeyValue("event", "race.created")
                .addKeyValue("eventId", eventId)
                .addKeyValue("raceId", savedRace.getId())
                .log("Race created");

        return RaceResponse.fromEntity(savedRace);
    }

    @Auditable(entityType = AuditEntityType.RACE, action = AuditAction.UPDATE)
    @Override
    @Transactional
    public RaceResponse updateRace(Long eventId, Long raceId, UpdateRaceRequest request, User currentUser) {
        Event event = requireAccessibleEvent(eventId, currentUser);
        eventOperationGuard.requireAllowed(event, EventOperation.RACE_WRITE);

        Race race = requireRaceOfEvent(raceId, eventId);

        String newRaceName = NameNormalizer.toStoredName(request.getRaceName());
        if (newRaceName != null && !newRaceName.isBlank() &&
                !newRaceName.equals(race.getRaceName())) {
            if (raceRepository.existsByRaceNameAndEventIdAndDeletedFalse(newRaceName, eventId)) {
                throw new RaceAlreadyExistsException(RACE_NAME_TAKEN);
            }
            race.setRaceName(newRaceName);
        }

        TextUtils.applyIfSent(request.getRaceDescription(), race::setRaceDescription);
        Instant reportingInstant = resolveReportingInstant(event, request.getReportingDate(), request.getReportingTime());
        if (reportingInstant != null) {
            race.setReportingTime(reportingInstant);
        }

        Race updatedRace = raceRepository.save(race);
        nameResolver.evict(eventId);
        log.atInfo()
                .addKeyValue("event", "race.updated")
                .addKeyValue("eventId", eventId)
                .addKeyValue("raceId", raceId)
                .log("Race updated");

        return RaceResponse.fromEntity(updatedRace);
    }

    @Override
    @Transactional(readOnly = true)
    public RaceResponse getRaceById(Long eventId, Long raceId, User currentUser) {
        requireAccessibleEvent(eventId, currentUser);
        return RaceResponse.fromEntity(requireRaceOfEvent(raceId, eventId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<RaceResponse> getRacesByEventId(Long eventId, User currentUser) {
        requireAccessibleEvent(eventId, currentUser);
        return raceRepository.findByEventIdAndDeletedFalse(eventId).stream()
                .map(RaceResponse::fromEntity)
                .toList();
    }

    @Auditable(entityType = AuditEntityType.RACE, action = AuditAction.DELETE)
    @Override
    @Transactional
    public void deleteRace(Long eventId, Long raceId, User currentUser) {
        Event event = requireAccessibleEvent(eventId, currentUser);
        eventOperationGuard.requireAllowed(event, EventOperation.RACE_WRITE);

        Race race = requireRaceOfEvent(raceId, eventId);

        if (race.getCategories() != null && !race.getCategories().isEmpty()) {
            throw new RaceDeletionNotAllowedException(
                    "You cannot delete this race while it still has categories. Delete them first.");
        }

        AuditContextHolder.setEntityId(String.valueOf(raceId));
        AuditContextHolder.setEntityLabel(race.getRaceName());
        AuditContextHolder.setOrganizationId(event.getOrganization() != null ? event.getOrganization().getId() : null);

        raceRepository.delete(race);
        nameResolver.evict(eventId);
        log.atInfo()
                .addKeyValue("event", "race.deleted")
                .addKeyValue("eventId", eventId)
                .addKeyValue("raceId", raceId)
                .log("Race deleted");
    }

    private Event requireAccessibleEvent(Long eventId, User currentUser) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(EventNotFoundException::new);
        eventAccessValidator.validateUserAuthorizationForEvent(currentUser, event);
        return event;
    }

    // A race of another event reads as not found, so the response never confirms that the id exists.
    private Race requireRaceOfEvent(Long raceId, Long eventId) {
        Race race = raceRepository.findByIdAndDeletedFalse(raceId)
                .orElseThrow(RaceNotFoundException::new);
        if (!race.getEvent().getId().equals(eventId)) {
            throw new RaceNotFoundException();
        }
        return race;
    }

    private Instant resolveReportingInstant(Event event, String date, String time) {
        if (date == null && time == null) {
            return null;
        }
        if (date == null || time == null) {
            throw new InvalidUserDataException("Provide both the reporting date and time, or neither.");
        }
        Instant reporting = EventDateTimeUtil.toInstant(date, time, EventDateTimeUtil.zone(event.getTimezone()));
        if (reporting.isBefore(Instant.now().plus(Duration.ofHours(1)))) {
            throw new InvalidUserDataException("The reporting time must be at least one hour from now.");
        }
        return reporting;
    }
}
