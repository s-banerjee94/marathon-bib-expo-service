package com.timekeeper.bibexpo.service.impl;

import com.timekeeper.bibexpo.annotation.Auditable;
import com.timekeeper.bibexpo.aspect.AuditContextHolder;
import com.timekeeper.bibexpo.exception.EventNotFoundException;
import com.timekeeper.bibexpo.exception.RaceAlreadyExistsException;
import com.timekeeper.bibexpo.exception.RaceDeletionNotAllowedException;
import com.timekeeper.bibexpo.exception.RaceNotFoundException;
import com.timekeeper.bibexpo.model.dto.request.CreateRaceRequest;
import com.timekeeper.bibexpo.model.dto.request.UpdateRaceRequest;
import com.timekeeper.bibexpo.model.dto.response.RaceResponse;
import com.timekeeper.bibexpo.model.entity.Event;
import com.timekeeper.bibexpo.model.entity.Race;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.model.enums.AuditAction;
import com.timekeeper.bibexpo.model.enums.AuditEntityType;
import com.timekeeper.bibexpo.repository.EventRepository;
import com.timekeeper.bibexpo.repository.RaceRepository;
import com.timekeeper.bibexpo.service.RaceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
@Slf4j
public class RaceServiceImpl implements RaceService {

    private final RaceRepository raceRepository;
    private final EventRepository eventRepository;
    private final com.timekeeper.bibexpo.service.validator.EventAccessValidator eventAccessValidator;

    @Auditable(entityType = AuditEntityType.RACE, action = AuditAction.CREATE)
    @Override
    @Transactional
    public RaceResponse createRace(Long eventId, CreateRaceRequest request, User currentUser) {
        log.info("Creating race: {} for event ID: {} by user: {}",
                request.getRaceName(), eventId, currentUser.getUsername());

        Event event = eventRepository.findById(eventId)
                .orElseThrow(EventNotFoundException::new);

        eventAccessValidator.validateUserAuthorizationForEvent(currentUser, event);

        if (raceRepository.existsByRaceNameAndEventIdAndDeletedFalse(request.getRaceName(), eventId)) {
            throw new RaceAlreadyExistsException(
                    "Race with name '" + request.getRaceName() + "' already exists for this event");
        }

        Race race = Race.builder()
                .raceName(request.getRaceName())
                .raceDescription(request.getRaceDescription())
                .event(event)
                .deleted(false)
                .build();

        Race savedRace = raceRepository.save(race);
        log.info("Successfully created race with ID: {} by user: {}",
                savedRace.getId(), currentUser.getUsername());

        return RaceResponse.fromEntity(savedRace);
    }

    @Auditable(entityType = AuditEntityType.RACE, action = AuditAction.UPDATE)
    @Override
    @Transactional
    public RaceResponse updateRace(Long eventId, Long raceId, UpdateRaceRequest request, User currentUser) {
        log.info("Updating race with ID: {} for event ID: {} by user: {}",
                raceId, eventId, currentUser.getUsername());

        Event event = eventRepository.findById(eventId)
                .orElseThrow(EventNotFoundException::new);

        eventAccessValidator.validateUserAuthorizationForEvent(currentUser, event);

        Race race = raceRepository.findByIdAndDeletedFalse(raceId)
                .orElseThrow(RaceNotFoundException::new);

        if (!race.getEvent().getId().equals(eventId)) {
            throw new RaceNotFoundException();
        }

        if (request.getRaceName() != null && !request.getRaceName().isBlank() &&
                !request.getRaceName().equals(race.getRaceName())) {
            if (raceRepository.existsByRaceNameAndEventIdAndDeletedFalse(request.getRaceName(), eventId)) {
                throw new RaceAlreadyExistsException(
                        "Race with name '" + request.getRaceName() + "' already exists for this event");
            }
            race.setRaceName(request.getRaceName());
        }

        updateIfNotNull(request.getRaceDescription(), race::setRaceDescription);

        Race updatedRace = raceRepository.save(race);
        log.info("Successfully updated race with ID: {} by user: {}",
                updatedRace.getId(), currentUser.getUsername());

        return RaceResponse.fromEntity(updatedRace);
    }

    @Override
    @Transactional(readOnly = true)
    public RaceResponse getRaceById(Long eventId, Long raceId, User currentUser) {
        log.info("Fetching race by ID: {} for event ID: {} for user: {}",
                raceId, eventId, currentUser.getUsername());

        Event event = eventRepository.findById(eventId)
                .orElseThrow(EventNotFoundException::new);

        eventAccessValidator.validateUserAuthorizationForEvent(currentUser, event);

        Race race = raceRepository.findByIdAndDeletedFalse(raceId)
                .orElseThrow(RaceNotFoundException::new);

        if (!race.getEvent().getId().equals(eventId)) {
            throw new RaceNotFoundException();
        }

        log.info("Successfully fetched race with ID: {} for user: {}",
                race.getId(), currentUser.getUsername());

        return RaceResponse.fromEntity(race);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RaceResponse> getRacesByEventId(Long eventId, User currentUser) {
        log.info("Fetching races for event ID: {} by user: {}", eventId, currentUser.getUsername());

        Event event = eventRepository.findById(eventId)
                .orElseThrow(EventNotFoundException::new);

        eventAccessValidator.validateUserAuthorizationForEvent(currentUser, event);

        List<Race> races = raceRepository.findByEventIdAndDeletedFalse(eventId);

        List<RaceResponse> raceResponses = races.stream()
                .map(RaceResponse::fromEntity)
                .toList();

        log.info("Successfully fetched {} races for event ID: {} by user: {}",
                raceResponses.size(), eventId, currentUser.getUsername());

        return raceResponses;
    }

    @Auditable(entityType = AuditEntityType.RACE, action = AuditAction.DELETE)
    @Override
    @Transactional
    public void deleteRace(Long eventId, Long raceId, User currentUser) {
        log.info("Deleting race with ID: {} for event ID: {} by user: {}",
                raceId, eventId, currentUser.getUsername());

        Event event = eventRepository.findById(eventId)
                .orElseThrow(EventNotFoundException::new);

        eventAccessValidator.validateUserAuthorizationForEvent(currentUser, event);

        Race race = raceRepository.findById(raceId)
                .orElseThrow(RaceNotFoundException::new);

        if (!race.getEvent().getId().equals(eventId)) {
            throw new RaceNotFoundException();
        }

        if (race.getCategories() != null && !race.getCategories().isEmpty()) {
            throw new RaceDeletionNotAllowedException(
                    "Race cannot be deleted because it has categories. Please delete all categories first.");
        }

        AuditContextHolder.setEntityLabel(race.getRaceName());
        AuditContextHolder.setOrganizationId(event.getOrganization() != null ? event.getOrganization().getId() : null);

        raceRepository.delete(race);
        log.info("Successfully deleted race with ID: {} by user: {}",
                raceId, currentUser.getUsername());
    }

    @Override
    @Transactional(readOnly = true)
    public Race findByEventIdAndRaceName(Long eventId, String raceName, User currentUser) {
        log.info("Finding race by event ID: {} and race name: {} by user: {}",
                eventId, raceName, currentUser.getUsername());

        Event event = eventRepository.findById(eventId)
                .orElseThrow(EventNotFoundException::new);

        eventAccessValidator.validateUserAuthorizationForEvent(currentUser, event);

        return raceRepository.findByRaceNameAndEventIdAndDeletedFalse(raceName, eventId)
                .orElseThrow(RaceNotFoundException::new);
    }

    private <T> void updateIfNotNull(T value, Consumer<T> setter) {
        if (value != null) {
            setter.accept(value);
        }
    }
}
