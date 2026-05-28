package com.timekeeper.bibexpo.service.impl;

import com.timekeeper.bibexpo.annotation.Auditable;
import com.timekeeper.bibexpo.aspect.AuditContextHolder;
import com.timekeeper.bibexpo.exception.*;
import com.timekeeper.bibexpo.model.dto.request.CreateEventRequest;
import com.timekeeper.bibexpo.model.dto.request.UpdateEventRequest;
import com.timekeeper.bibexpo.model.dto.response.EventResponse;
import com.timekeeper.bibexpo.model.dto.response.EventSummaryResponse;
import com.timekeeper.bibexpo.model.entity.Event;
import com.timekeeper.bibexpo.model.entity.EventStatus;
import com.timekeeper.bibexpo.model.entity.Organization;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.model.entity.UserRole;
import com.timekeeper.bibexpo.model.enums.AuditAction;
import com.timekeeper.bibexpo.model.enums.AuditEntityType;
import com.timekeeper.bibexpo.repository.EventRepository;
import com.timekeeper.bibexpo.repository.OrganizationRepository;
import com.timekeeper.bibexpo.service.EventService;
import com.timekeeper.bibexpo.service.validator.EventAccessValidator;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventServiceImpl implements EventService {

    private final EventRepository eventRepository;
    private final OrganizationRepository organizationRepository;
    private final EventAccessValidator eventAccessValidator;

    @Auditable(entityType = AuditEntityType.EVENT, action = AuditAction.CREATE)
    @Override
    @Transactional
    public EventResponse createEvent(CreateEventRequest request, User currentUser) {
        log.info("Creating event: {} for organization ID: {} by user: {}",
                request.getEventName(), request.getOrganizationId(), currentUser.getUsername());

        Organization organization = organizationRepository.findByIdAndDeletedFalse(request.getOrganizationId())
                .orElseThrow(OrganizationNotFoundException::new);

        validateUserAuthorization(currentUser, organization);

        if (eventRepository.existsByEventNameAndOrganizationId(
                request.getEventName(), request.getOrganizationId())) {
            throw new EventAlreadyExistsException(
                    "An event with this name already exists for this organization.");
        }

        ZoneId zone = validateTimezone(request.getTimezone());

        Instant startInstant = parseToInstant(request.getEventStartDate(), request.getEventStartTime(), zone);
        Instant endInstant = parseToInstant(request.getEventEndDate(), request.getEventEndTime(), zone);

        if (!startInstant.isAfter(Instant.now())) {
            throw new InvalidUserDataException("Event start date must be in the future.");
        }
        if (!endInstant.isAfter(startInstant)) {
            throw new InvalidUserDataException("Event end date must be after the start date.");
        }

        Event event = Event.builder()
                .eventName(request.getEventName())
                .eventDescription(request.getEventDescription())
                .logoUrl(request.getLogoUrl())
                .timezone(request.getTimezone())
                .eventStartDate(startInstant)
                .eventEndDate(endInstant)
                .venueName(request.getVenueName())
                .addressLine1(request.getAddressLine1())
                .addressLine2(request.getAddressLine2())
                .city(request.getCity())
                .stateProvince(request.getStateProvince())
                .postalCode(request.getPostalCode())
                .country(request.getCountry())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .status(request.getStatus() != null ? request.getStatus() : EventStatus.DRAFT)
                .organization(organization)
                .eventGoodies(request.getEventGoodies())
                .build();

        Event savedEvent = eventRepository.save(event);
        log.info("Successfully created event with ID: {} by user: {}",
                savedEvent.getId(), currentUser.getUsername());

        return EventResponse.fromEntity(savedEvent);
    }

    @Auditable(entityType = AuditEntityType.EVENT, action = AuditAction.UPDATE)
    @Override
    @Transactional
    public EventResponse updateEvent(Long id, UpdateEventRequest request, User currentUser) {
        log.info("Updating event with ID: {} by user: {}", id, currentUser.getUsername());

        Event event = eventRepository.findById(id)
                .orElseThrow(EventNotFoundException::new);

        eventAccessValidator.validateUserAuthorizationForEvent(currentUser, event);

        applyEventNameUpdate(event, request);

        updateIfNotNull(request.getEventDescription(), event::setEventDescription);
        updateIfNotNull(request.getLogoUrl(), event::setLogoUrl);

        applyTimezoneUpdate(event, request);
        applyEventDateUpdates(event, request);

        updateRequiredStringIfNotBlank(request.getVenueName(), event::setVenueName);
        updateIfNotNull(request.getAddressLine1(), event::setAddressLine1);
        updateIfNotNull(request.getAddressLine2(), event::setAddressLine2);
        updateIfNotNull(request.getCity(), event::setCity);
        updateIfNotNull(request.getStateProvince(), event::setStateProvince);
        updateIfNotNull(request.getPostalCode(), event::setPostalCode);
        updateIfNotNull(request.getCountry(), event::setCountry);
        updateIfNotNull(request.getLatitude(), event::setLatitude);
        updateIfNotNull(request.getLongitude(), event::setLongitude);
        updateIfNotNull(request.getEventGoodies(), event::setEventGoodies);
        updateIfNotNull(request.getStatus(), event::setStatus);

        Event updatedEvent = eventRepository.save(event);
        log.info("Successfully updated event with ID: {} by user: {}", updatedEvent.getId(), currentUser.getUsername());

        return EventResponse.fromEntity(updatedEvent);
    }

    private void applyEventNameUpdate(Event event, UpdateEventRequest request) {
        if (request.getEventName() == null || request.getEventName().isBlank()
                || request.getEventName().equals(event.getEventName())) {
            return;
        }
        if (eventRepository.existsByEventNameAndOrganizationId(
                request.getEventName(), event.getOrganization().getId())) {
            throw new EventAlreadyExistsException(
                    "Event with name '" + request.getEventName() + "' already exists for this organization");
        }
        event.setEventName(request.getEventName());
    }

    private void applyTimezoneUpdate(Event event, UpdateEventRequest request) {
        if (request.getTimezone() == null) {
            return;
        }
        if (event.getStatus() == EventStatus.PUBLISHED || event.getStatus() == EventStatus.COMPLETED) {
            throw new InvalidUserDataException("Timezone cannot be changed after the event is published.");
        }
        validateTimezone(request.getTimezone());
        event.setTimezone(request.getTimezone());
    }

    private void applyEventDateUpdates(Event event, UpdateEventRequest request) {
        boolean datesLocked = event.getStatus() == EventStatus.PUBLISHED || event.getStatus() == EventStatus.COMPLETED;
        ZoneId effectiveZone = ZoneId.of(request.getTimezone() != null ? request.getTimezone() : event.getTimezone());

        applyDateUpdate(datesLocked, effectiveZone, request.getEventStartDate(),
                request.getEventStartTime(), "start", event::setEventStartDate);
        applyDateUpdate(datesLocked, effectiveZone, request.getEventEndDate(),
                request.getEventEndTime(), "end", event::setEventEndDate);

        if (event.getEventEndDate() != null && event.getEventStartDate() != null
                && !event.getEventEndDate().isAfter(event.getEventStartDate())) {
            throw new InvalidUserDataException("Event end date must be after the start date.");
        }
    }

    private void applyDateUpdate(boolean datesLocked, ZoneId effectiveZone, String date,
                                 String time, String fieldLabel, Consumer<Instant> setter) {
        if (date == null && time == null) {
            return;
        }
        if (date == null || time == null) {
            throw new InvalidUserDataException(
                    "Both event " + fieldLabel + " date and time must be provided together.");
        }
        if (datesLocked) {
            throw new InvalidUserDataException("Event dates cannot be changed after the event is published.");
        }
        setter.accept(parseToInstant(date, time, effectiveZone));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<EventResponse> getAllEvents(
            Long organizationId, EventStatus status, String search,
            Pageable pageable, User currentUser) {
        log.info("Fetching all events with filters - organizationId: {}, status: {}, search: {}, user: {}",
                organizationId, status, search, currentUser.getUsername());

        Specification<Event> spec = buildEventSpecification(organizationId, status, search);

        Page<Event> eventsPage = eventRepository.findAll(spec, pageable);

        Page<EventResponse> responsePage = eventsPage.map(EventResponse::fromEntity);

        log.info("Successfully fetched {} events (page {} of {})",
                responsePage.getNumberOfElements(),
                responsePage.getNumber() + 1,
                responsePage.getTotalPages());

        return responsePage;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<EventResponse> getOrganizationEvents(
            EventStatus status, String search, Pageable pageable, User currentUser) {
        log.info("Fetching organization events with filters - status: {}, search: {}, user: {}",
                status, search, currentUser.getUsername());

        if (currentUser.getOrganization() == null) {
            throw new UnauthorizedAccessException(
                    "User does not belong to any organization");
        }

        Long organizationId = currentUser.getOrganization().getId();

        Specification<Event> spec = buildEventSpecification(organizationId, status, search);

        Page<Event> eventsPage = eventRepository.findAll(spec, pageable);

        Page<EventResponse> responsePage = eventsPage.map(EventResponse::fromEntity);

        log.info("Successfully fetched {} organization events (page {} of {})",
                responsePage.getNumberOfElements(),
                responsePage.getNumber() + 1,
                responsePage.getTotalPages());

        return responsePage;
    }

    @Override
    @Transactional(readOnly = true)
    public EventResponse getEventById(Long id, User currentUser) {
        log.info("Fetching event by ID: {} for user: {}", id, currentUser.getUsername());

        Event event = findAndValidateEvent(id, currentUser);

        log.info("Successfully fetched event with ID: {} for user: {}",
                event.getId(), currentUser.getUsername());

        return EventResponse.fromEntity(event);
    }

    @Auditable(entityType = AuditEntityType.EVENT, action = AuditAction.STATUS_CHANGE)
    @Override
    @Transactional
    public EventResponse toggleEventEnabled(Long id, User currentUser) {
        log.info("Toggling enabled status for event with ID: {} by user: {}", id, currentUser.getUsername());

        Event event = eventRepository.findById(id)
                .orElseThrow(EventNotFoundException::new);

        event.setEnabled(!event.getEnabled());

        Event updatedEvent = eventRepository.save(event);
        log.info("Successfully toggled enabled status for event with ID: {} to {} by user: {}",
                updatedEvent.getId(), updatedEvent.getEnabled(), currentUser.getUsername());

        return EventResponse.fromEntity(updatedEvent);
    }

    @Auditable(entityType = AuditEntityType.EVENT, action = AuditAction.STATUS_CHANGE)
    @Override
    @Transactional
    public EventResponse changeEventStatus(Long id, EventStatus status, User currentUser) {
        log.info("Changing status for event with ID: {} to {} by user: {}", id, status, currentUser.getUsername());

        Event event = findAndValidateEvent(id, currentUser);

        if (status == null) {
            throw new InvalidUserDataException("Event status is required.");
        }

        event.setStatus(status);

        Event updatedEvent = eventRepository.save(event);
        log.info("Successfully changed status for event with ID: {} to {} by user: {}",
                updatedEvent.getId(), status, currentUser.getUsername());

        return EventResponse.fromEntity(updatedEvent);
    }

    @Override
    public void validateEventEnabled(Event event, User currentUser) {
        eventAccessValidator.validateEventAvailability(currentUser, event);
    }

    private Event findAndValidateEvent(Long id, User currentUser) {
        Event event = eventRepository.findById(id)
                .orElseThrow(EventNotFoundException::new);

        eventAccessValidator.validateUserAuthorizationForEvent(currentUser, event);

        return event;
    }

    @Auditable(entityType = AuditEntityType.EVENT, action = AuditAction.DELETE)
    @Override
    @Transactional
    public void deleteEvent(Long id, User currentUser) {
        log.info("Deleting event with ID: {} by user: {}", id, currentUser.getUsername());

        Event event = findAndValidateEvent(id, currentUser);

        if (event.getStatus() != EventStatus.DRAFT && event.getStatus() != EventStatus.CANCELLED) {
            throw new EventDeletionNotAllowedException(
                    "Only events in DRAFT or CANCELLED status can be deleted.");
        }

        // TODO: Check if participant list is empty once participant feature is fully implemented
        // if (!event.getParticipants().isEmpty()) {
        //     throw new EventDeletionNotAllowedException(
        //             "Event cannot be deleted because it has registered participants");
        // }

        AuditContextHolder.setEntityLabel(event.getEventName());
        AuditContextHolder.setOrganizationId(event.getOrganization() != null ? event.getOrganization().getId() : null);

        eventRepository.delete(event);
        log.info("Successfully deleted event with ID: {} by user: {}", id, currentUser.getUsername());
    }

    @Override
    @Transactional(readOnly = true)
    public EventSummaryResponse getEventSummary(Long id, User currentUser) {
        log.info("Fetching event summary for event ID: {} by user: {}", id, currentUser.getUsername());

        Event event = findAndValidateEvent(id, currentUser);

        event.getRaces().forEach(race -> race.getCategories().forEach(category -> {}));

        EventSummaryResponse summary = EventSummaryResponse.fromEntity(event);

        log.info("Successfully fetched event summary for event ID: {} with {} races and {} categories by user: {}",
                id, summary.getTotalRaces(), summary.getTotalCategories(), currentUser.getUsername());

        return summary;
    }

    private Specification<Event> buildEventSpecification(
            Long organizationId, EventStatus status, String search) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (query.getResultType() != Long.class && query.getResultType() != long.class) {
                root.fetch("organization", jakarta.persistence.criteria.JoinType.LEFT);
            }

            if (organizationId != null) {
                predicates.add(criteriaBuilder.equal(root.get("organization").get("id"), organizationId));
            }

            if (status != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), status));
            }

            if (search != null && !search.isBlank()) {
                String searchPattern = "%" + search.toLowerCase() + "%";

                Predicate eventNamePredicate = criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("eventName")),
                        searchPattern
                );

                Predicate eventDescriptionPredicate = criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("eventDescription")),
                        searchPattern
                );

                Predicate venueNamePredicate = criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("venueName")),
                        searchPattern
                );

                predicates.add(criteriaBuilder.or(
                        eventNamePredicate,
                        eventDescriptionPredicate,
                        venueNamePredicate
                ));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    private void validateUserAuthorization(User currentUser, Organization organization) {
        UserRole role = currentUser.getRole();

        if (role == UserRole.ROOT || role == UserRole.ADMIN) {
            return;
        }

        if (role == UserRole.ORGANIZER_ADMIN || role == UserRole.ORGANIZER_USER) {
            if (currentUser.getOrganization() == null) {
                throw new UnauthorizedAccessException(
                        "Your account is not assigned to an organization.");
            }

            if (!currentUser.getOrganization().getId().equals(organization.getId())) {
                throw new UnauthorizedAccessException(
                        "You can only create events for your organization.");
            }
            return;
        }

        throw new UnauthorizedAccessException("You are not allowed to create events.");
    }

    private <T> void updateIfNotNull(T value, Consumer<T> setter) {
        if (value != null) {
            setter.accept(value);
        }
    }

    private void updateRequiredStringIfNotBlank(String value, Consumer<String> setter) {
        if (value != null && !value.isBlank()) {
            setter.accept(value);
        }
    }

    private ZoneId validateTimezone(String timezone) {
        try {
            return ZoneId.of(timezone);
        } catch (DateTimeException e) {
            throw new InvalidUserDataException("Invalid timezone. Use a valid IANA timezone ID such as 'Asia/Kolkata' or 'Europe/London'.");
        }
    }

    private Instant parseToInstant(String date, String time, ZoneId zone) {
        try {
            return ZonedDateTime.of(LocalDate.parse(date), LocalTime.parse(time), zone).toInstant();
        } catch (DateTimeParseException e) {
            throw new InvalidUserDataException("Invalid date or time format. Use yyyy-MM-dd and HH:mm.");
        }
    }
}
