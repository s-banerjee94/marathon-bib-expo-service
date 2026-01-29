package com.timekeeper.bibexpo.service.impl;

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
import com.timekeeper.bibexpo.repository.EventRepository;
import com.timekeeper.bibexpo.repository.OrganizationRepository;
import com.timekeeper.bibexpo.service.EventService;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventServiceImpl implements EventService {

    public static final String EVENT_NOT_FOUND_WITH_ID = "Event not found with ID: ";
    private final EventRepository eventRepository;
    private final OrganizationRepository organizationRepository;

    @Override
    @Transactional
    public EventResponse createEvent(CreateEventRequest request, User currentUser) {
        log.info("Creating event: {} for organization ID: {} by user: {}",
                request.getEventName(), request.getOrganizationId(), currentUser.getUsername());

        Organization organization = organizationRepository.findByIdAndDeletedFalse(request.getOrganizationId())
                .orElseThrow(() -> new OrganizationNotFoundException(
                        "Organization not found with ID: " + request.getOrganizationId()));

        validateUserAuthorization(currentUser, organization);

        if (eventRepository.existsByEventNameAndOrganizationId(
                request.getEventName(), request.getOrganizationId())) {
            throw new EventAlreadyExistsException(
                    "Event with name '" + request.getEventName() +
                    "' already exists for this organization");
        }

        Event event = Event.builder()
                .eventName(request.getEventName())
                .eventDescription(request.getEventDescription())
                .logoUrl(request.getLogoUrl())
                .eventStartDate(request.getEventStartDate())
                .eventEndDate(request.getEventEndDate())
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

    @Override
    @Transactional
    public EventResponse updateEvent(Long id, UpdateEventRequest request, User currentUser) {
        log.info("Updating event with ID: {} by user: {}", id, currentUser.getUsername());

        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new EventNotFoundException(EVENT_NOT_FOUND_WITH_ID + id));

        validateUserAuthorizationForView(currentUser, event);
        validateEventEnabled(event, currentUser);

        if (request.getEventName() != null && !request.getEventName().isBlank() &&
                !request.getEventName().equals(event.getEventName())) {
            if (eventRepository.existsByEventNameAndOrganizationId(
                    request.getEventName(), event.getOrganization().getId())) {
                throw new EventAlreadyExistsException(
                        "Event with name '" + request.getEventName() + "' already exists for this organization");
            }
            event.setEventName(request.getEventName());
        }

        updateIfNotNull(request.getEventDescription(), event::setEventDescription);
        updateIfNotNull(request.getLogoUrl(), event::setLogoUrl);
        updateIfNotNull(request.getEventStartDate(), event::setEventStartDate);
        updateIfNotNull(request.getEventEndDate(), event::setEventEndDate);
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

        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new EventNotFoundException(
                        EVENT_NOT_FOUND_WITH_ID + id));

        validateUserAuthorizationForView(currentUser, event);
        validateEventEnabled(event, currentUser);

        log.info("Successfully fetched event with ID: {} for user: {}",
                event.getId(), currentUser.getUsername());

        return EventResponse.fromEntity(event);
    }

    @Override
    @Transactional
    public EventResponse toggleEventEnabled(Long id, User currentUser) {
        log.info("Toggling enabled status for event with ID: {} by user: {}", id, currentUser.getUsername());

        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new EventNotFoundException(EVENT_NOT_FOUND_WITH_ID + id));

        event.setEnabled(!event.getEnabled());

        Event updatedEvent = eventRepository.save(event);
        log.info("Successfully toggled enabled status for event with ID: {} to {} by user: {}",
                updatedEvent.getId(), updatedEvent.getEnabled(), currentUser.getUsername());

        return EventResponse.fromEntity(updatedEvent);
    }

    @Override
    @Transactional
    public EventResponse changeEventStatus(Long id, EventStatus status, User currentUser) {
        log.info("Changing status for event with ID: {} to {} by user: {}", id, status, currentUser.getUsername());

        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new EventNotFoundException(EVENT_NOT_FOUND_WITH_ID + id));

        validateUserAuthorizationForView(currentUser, event);
        validateEventEnabled(event, currentUser);

        if (status == null) {
            throw new InvalidUserDataException("Event status cannot be null");
        }

        event.setStatus(status);

        Event updatedEvent = eventRepository.save(event);
        log.info("Successfully changed status for event with ID: {} to {} by user: {}",
                updatedEvent.getId(), status, currentUser.getUsername());

        return EventResponse.fromEntity(updatedEvent);
    }

    @Override
    public void validateEventEnabled(Event event, User currentUser) {
        UserRole role = currentUser.getRole();

        if (role == UserRole.ROOT || role == UserRole.ADMIN) {
            return;
        }

        if (Boolean.FALSE.equals(event.getEnabled())) {
            throw new com.timekeeper.bibexpo.exception.EventDisabledException(
                    "Event with ID " + event.getId() + " is currently disabled and cannot be accessed");
        }
    }

    @Override
    @Transactional
    public void deleteEvent(Long id, User currentUser) {
        log.info("Deleting event with ID: {} by user: {}", id, currentUser.getUsername());

        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new EventNotFoundException(EVENT_NOT_FOUND_WITH_ID + id));

        validateUserAuthorizationForView(currentUser, event);
        validateEventEnabled(event, currentUser);

        if (event.getStatus() != EventStatus.DRAFT && event.getStatus() != EventStatus.CANCELLED) {
            throw new EventDeletionNotAllowedException(
                    "Event can only be deleted if status is DRAFT or CANCELLED. Current status: " + event.getStatus());
        }

        // TODO: Check if participant list is empty once participant feature is fully implemented
        // if (!event.getParticipants().isEmpty()) {
        //     throw new EventDeletionNotAllowedException(
        //             "Event cannot be deleted because it has registered participants");
        // }

        eventRepository.delete(event);
        log.info("Successfully deleted event with ID: {} by user: {}", id, currentUser.getUsername());
    }

    @Override
    @Transactional(readOnly = true)
    public EventSummaryResponse getEventSummary(Long id, User currentUser) {
        log.info("Fetching event summary for event ID: {} by user: {}", id, currentUser.getUsername());

        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new EventNotFoundException(EVENT_NOT_FOUND_WITH_ID + id));

        validateUserAuthorizationForView(currentUser, event);
        validateEventEnabled(event, currentUser);

        Event eventWithRacesAndCategories = eventRepository.findById(id).orElseThrow();
        eventWithRacesAndCategories.getRaces().forEach(race -> race.getCategories().forEach(category -> {}));

        EventSummaryResponse summary = EventSummaryResponse.fromEntity(eventWithRacesAndCategories);

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
                        "User does not belong to any organization");
            }

            if (!currentUser.getOrganization().getId().equals(organization.getId())) {
                throw new UnauthorizedAccessException(
                        "User can only create events for their own organization");
            }
            return;
        }

        throw new UnauthorizedAccessException(
                "User does not have permission to create events");
    }

    private void validateUserAuthorizationForView(User currentUser, Event event) {
        UserRole role = currentUser.getRole();

        if (role == UserRole.ROOT || role == UserRole.ADMIN) {
            return;
        }

        if (role == UserRole.ORGANIZER_ADMIN || role == UserRole.ORGANIZER_USER) {
            if (currentUser.getOrganization() == null) {
                throw new UnauthorizedAccessException(
                        "User does not belong to any organization");
            }

            if (!event.getOrganization().getId().equals(currentUser.getOrganization().getId())) {
                throw new UnauthorizedAccessException(
                        "User can only view events from their own organization");
            }
            return;
        }

        throw new UnauthorizedAccessException(
                "User does not have permission to view events");
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
}
