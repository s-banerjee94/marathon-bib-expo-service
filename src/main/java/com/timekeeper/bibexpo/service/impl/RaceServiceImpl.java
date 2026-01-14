package com.timekeeper.bibexpo.service.impl;

import com.timekeeper.bibexpo.exception.EventNotFoundException;
import com.timekeeper.bibexpo.exception.RaceAlreadyExistsException;
import com.timekeeper.bibexpo.exception.RaceDeletionNotAllowedException;
import com.timekeeper.bibexpo.exception.RaceNotFoundException;
import com.timekeeper.bibexpo.exception.UnauthorizedAccessException;
import com.timekeeper.bibexpo.model.dto.request.CreateRaceRequest;
import com.timekeeper.bibexpo.model.dto.request.UpdateRaceRequest;
import com.timekeeper.bibexpo.model.dto.response.RaceResponse;
import com.timekeeper.bibexpo.model.entity.Event;
import com.timekeeper.bibexpo.model.entity.Race;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.model.entity.UserRole;
import com.timekeeper.bibexpo.repository.EventRepository;
import com.timekeeper.bibexpo.repository.RaceRepository;
import com.timekeeper.bibexpo.service.RaceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RaceServiceImpl implements RaceService {

    public static final String EVENT_NOT_FOUND_WITH_ID = "Event not found with ID: ";
    public static final String RACE_NOT_FOUND_WITH_ID = "Race not found with ID: ";
    private final RaceRepository raceRepository;
    private final EventRepository eventRepository;

    @Override
    @Transactional
    public RaceResponse createRace(Long eventId, CreateRaceRequest request, User currentUser) {
        log.info("Creating race: {} for event ID: {} by user: {}",
                request.getRaceName(), eventId, currentUser.getUsername());

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException(EVENT_NOT_FOUND_WITH_ID + eventId));

        validateUserAuthorizationForEvent(currentUser, event);

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

    @Override
    @Transactional
    public RaceResponse updateRace(Long eventId, Long raceId, UpdateRaceRequest request, User currentUser) {
        log.info("Updating race with ID: {} for event ID: {} by user: {}",
                raceId, eventId, currentUser.getUsername());

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException(EVENT_NOT_FOUND_WITH_ID + eventId));

        validateUserAuthorizationForEvent(currentUser, event);

        Race race = raceRepository.findByIdAndDeletedFalse(raceId)
                .orElseThrow(() -> new RaceNotFoundException(RACE_NOT_FOUND_WITH_ID + raceId));

        if (!race.getEvent().getId().equals(eventId)) {
            throw new RaceNotFoundException("Race with ID: " + raceId + " does not belong to event with ID: " + eventId);
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
                .orElseThrow(() -> new EventNotFoundException(EVENT_NOT_FOUND_WITH_ID + eventId));

        validateUserAuthorizationForEvent(currentUser, event);

        Race race = raceRepository.findByIdAndDeletedFalse(raceId)
                .orElseThrow(() -> new RaceNotFoundException(RACE_NOT_FOUND_WITH_ID + raceId));

        if (!race.getEvent().getId().equals(eventId)) {
            throw new RaceNotFoundException("Race with ID: " + raceId + " does not belong to event with ID: " + eventId);
        }

        log.info("Successfully fetched race with ID: {} for user: {}",
                race.getId(), currentUser.getUsername());

        return RaceResponse.fromEntity(race);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RaceResponse> getRacesByEventId(Long eventId, Boolean enabled, User currentUser) {
        log.info("Fetching races for event ID: {} with enabled filter: {} by user: {}",
                eventId, enabled, currentUser.getUsername());

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException(EVENT_NOT_FOUND_WITH_ID + eventId));

        validateUserAuthorizationForEvent(currentUser, event);

        List<Race> races;
        if (enabled != null && enabled) {
            races = raceRepository.findByEventIdAndEnabledTrueAndDeletedFalse(eventId);
        } else {
            races = raceRepository.findByEventIdAndDeletedFalse(eventId);
        }

        List<RaceResponse> raceResponses = races.stream()
                .map(RaceResponse::fromEntity)
                .toList();

        log.info("Successfully fetched {} races for event ID: {} by user: {}",
                raceResponses.size(), eventId, currentUser.getUsername());

        return raceResponses;
    }

    @Override
    @Transactional
    public void deleteRace(Long eventId, Long raceId, User currentUser) {
        log.info("Deleting race with ID: {} for event ID: {} by user: {}",
                raceId, eventId, currentUser.getUsername());

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException(EVENT_NOT_FOUND_WITH_ID + eventId));

        validateUserAuthorizationForEvent(currentUser, event);

        Race race = raceRepository.findById(raceId)
                .orElseThrow(() -> new RaceNotFoundException(RACE_NOT_FOUND_WITH_ID + raceId));

        if (!race.getEvent().getId().equals(eventId)) {
            throw new RaceNotFoundException("Race with ID: " + raceId + " does not belong to event with ID: " + eventId);
        }

        if (race.getCategories() != null && !race.getCategories().isEmpty()) {
            throw new RaceDeletionNotAllowedException(
                    "Race cannot be deleted because it has categories. Please delete all categories first.");
        }

        raceRepository.delete(race);
        log.info("Successfully deleted race with ID: {} by user: {}",
                raceId, currentUser.getUsername());
    }

    @Override
    @Transactional
    public RaceResponse toggleRaceEnabled(Long eventId, Long raceId, User currentUser) {
        log.info("Toggling enabled status for race with ID: {} for event ID: {} by user: {}",
                raceId, eventId, currentUser.getUsername());

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException(EVENT_NOT_FOUND_WITH_ID + eventId));

        validateUserAuthorizationForEvent(currentUser, event);

        Race race = raceRepository.findByIdAndDeletedFalse(raceId)
                .orElseThrow(() -> new RaceNotFoundException(RACE_NOT_FOUND_WITH_ID + raceId));

        if (!race.getEvent().getId().equals(eventId)) {
            throw new RaceNotFoundException("Race with ID: " + raceId + " does not belong to event with ID: " + eventId);
        }

        race.setEnabled(!race.getEnabled());

        Race updatedRace = raceRepository.save(race);
        log.info("Successfully toggled enabled status for race with ID: {} to {} by user: {}",
                updatedRace.getId(), updatedRace.getEnabled(), currentUser.getUsername());

        return RaceResponse.fromEntity(updatedRace);
    }

    private void validateUserAuthorizationForEvent(User currentUser, Event event) {
        UserRole role = currentUser.getRole();

        if (role == UserRole.ROOT || role == UserRole.ADMIN) {
            return;
        }

        if (role == UserRole.ORGANIZER_ADMIN || role == UserRole.ORGANIZER_USER) {
            if (currentUser.getOrganization() == null) {
                throw new UnauthorizedAccessException("User does not belong to any organization");
            }

            if (!event.getOrganization().getId().equals(currentUser.getOrganization().getId())) {
                throw new UnauthorizedAccessException(
                        "User can only access races from their own organization's events");
            }
            return;
        }

        throw new UnauthorizedAccessException("User does not have permission to access races");
    }

    @Override
    @Transactional(readOnly = true)
    public Race findByEventIdAndRaceName(Long eventId, String raceName, User currentUser) {
        log.info("Finding race by event ID: {} and race name: {} by user: {}",
                eventId, raceName, currentUser.getUsername());

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException(EVENT_NOT_FOUND_WITH_ID + eventId));

        validateUserAuthorizationForEvent(currentUser, event);

        return raceRepository.findByRaceNameAndEventIdAndDeletedFalse(raceName, eventId)
                .orElseThrow(() -> new RaceNotFoundException(
                        "Race with name '" + raceName + "' not found for event with ID: " + eventId));
    }

    private <T> void updateIfNotNull(T value, Consumer<T> setter) {
        if (value != null) {
            setter.accept(value);
        }
    }
}
