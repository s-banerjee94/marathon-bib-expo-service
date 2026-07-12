package com.timekeeper.bibexpo.service.impl;

import com.timekeeper.bibexpo.annotation.Auditable;
import com.timekeeper.bibexpo.aspect.AuditContextHolder;
import com.timekeeper.bibexpo.exception.*;
import com.timekeeper.bibexpo.notification.model.dto.NotifyRequest;
import com.timekeeper.bibexpo.model.dto.request.CreateEventRequest;
import com.timekeeper.bibexpo.model.dto.request.UpdateEventRequest;
import com.timekeeper.bibexpo.model.dto.response.EventResponse;
import com.timekeeper.bibexpo.model.dto.response.PresignUploadResponse;
import com.timekeeper.bibexpo.model.entity.Event;
import com.timekeeper.bibexpo.model.entity.EventStatus;
import com.timekeeper.bibexpo.model.entity.Organization;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.model.entity.UserRole;
import com.timekeeper.bibexpo.model.enums.AuditAction;
import com.timekeeper.bibexpo.model.enums.AuditEntityType;
import com.timekeeper.bibexpo.notification.model.enums.NotificationAudience;
import com.timekeeper.bibexpo.notification.model.enums.NotificationType;
import com.timekeeper.bibexpo.model.enums.UploadCategory;
import com.timekeeper.bibexpo.model.event.EventStatusChangedEvent;
import com.timekeeper.bibexpo.model.entity.EventLimit;
import com.timekeeper.bibexpo.repository.EventLimitRepository;
import com.timekeeper.bibexpo.repository.EventRepository;
import com.timekeeper.bibexpo.repository.OrganizationRepository;
import com.timekeeper.bibexpo.repository.RaceRepository;
import com.timekeeper.bibexpo.service.EventBillingGuard;
import com.timekeeper.bibexpo.service.EventDeletionGuard;
import com.timekeeper.bibexpo.service.EventService;
import com.timekeeper.bibexpo.notification.service.NotificationService;
import com.timekeeper.bibexpo.service.StorageService;
import com.timekeeper.bibexpo.service.validator.EventAccessValidator;
import com.timekeeper.bibexpo.service.validator.EventStatusTransitionValidator;
import com.timekeeper.bibexpo.util.EventDateTimeUtil;
import com.timekeeper.bibexpo.util.TextUtils;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventServiceImpl implements EventService {

    private final EventRepository eventRepository;
    private final OrganizationRepository organizationRepository;
    private final EventLimitRepository eventLimitRepository;
    private final RaceRepository raceRepository;
    private final EventAccessValidator eventAccessValidator;
    private final EventStatusTransitionValidator statusTransitionValidator;
    private final EventBillingGuard eventBillingGuard;
    private final List<EventDeletionGuard> eventDeletionGuards;
    private final StorageService storageService;
    private final NotificationService notificationService;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    /**
     * Map an event to a response, presigning a short-lived URL for its logo so the
     * stored object key is never exposed directly. All read paths go through here.
     */
    private EventResponse toResponse(Event event) {
        EventResponse response = EventResponse.fromEntity(event);
        response.setLogoUrl(storageService.createDownloadUrl(event.getLogoObjectKey()));
        return response;
    }

    /** Best-effort object deletion: orphaned objects are tolerable, a failed cleanup must not roll back the entity change. */
    private void deleteQuietly(String objectKey) {
        try {
            storageService.delete(objectKey);
        } catch (Exception e) {
            log.warn("Failed to delete object {}: {}", objectKey, e.getMessage());
        }
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

    @Auditable(entityType = AuditEntityType.EVENT, action = AuditAction.CREATE)
    @Override
    @Transactional
    public EventResponse createEvent(CreateEventRequest request, User currentUser) {
        log.info("Creating event: {} for organization ID: {} by user: {}",
                request.getEventName(), request.getOrganizationId(), currentUser.getUsername());

        Organization organization = organizationRepository.findById(request.getOrganizationId())
                .orElseThrow(OrganizationNotFoundException::new);

        validateUserAuthorization(currentUser, organization);

        if (eventRepository.existsByEventNameAndOrganizationId(
                request.getEventName(), request.getOrganizationId())) {
            throw new EventAlreadyExistsException(
                    "An event with this name already exists for this organization.");
        }

        ZoneId zone = EventDateTimeUtil.zone(request.getTimezone());

        Instant startInstant = EventDateTimeUtil.toInstant(request.getEventStartDate(), request.getEventStartTime(), zone);
        Instant endInstant = EventDateTimeUtil.toInstant(request.getEventEndDate(), request.getEventEndTime(), zone);

        if (!startInstant.isAfter(Instant.now())) {
            throw new InvalidUserDataException("Event start date must be in the future.");
        }
        if (!endInstant.isAfter(startInstant)) {
            throw new InvalidUserDataException("Event end date must be after the start date.");
        }

        int goodiesCount = countGoodiesItems(request.getEventGoodies());
        EventLimit defaultLimits = EventLimit.builder().build();
        if (goodiesCount > defaultLimits.getMaxGoodies()) {
            throw new EventLimitExceededException(
                    "You have exceeded the maximum number of goodies allowed for this event.");
        }

        Event event = Event.builder()
                .eventName(request.getEventName())
                .eventDescription(request.getEventDescription())
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
                .status(EventStatus.DRAFT)
                .organization(organization)
                .eventGoodies(request.getEventGoodies())
                .build();

        Event savedEvent = eventRepository.save(event);
        eventLimitRepository.save(EventLimit.builder().event(savedEvent).build());
        log.info("Successfully created event with ID: {} by user: {}",
                savedEvent.getId(), currentUser.getUsername());

        notifyEventCreated(savedEvent, currentUser);

        return toResponse(savedEvent);
    }

    /** Alert the application owners (ROOT + ADMIN) when an organizer creates an event. */
    private void notifyEventCreated(Event event, User actor) {
        if (actor.getRole() != UserRole.ORGANIZER_ADMIN && actor.getRole() != UserRole.ORGANIZER_USER) {
            return;
        }
        publishAfterCommit(NotifyRequest.builder()
                .audience(NotificationAudience.PLATFORM_ADMINS)
                .type(NotificationType.EVENT_CREATED)
                .title("New Event Created")
                .message(String.format("%s created a new event \"%s\"%s.",
                        actor.getUsername(), event.getEventName(), orgSuffix(event)))
                .entityType("EVENT")
                .entityId(String.valueOf(event.getId()))
                .actor(actor)
                .build());
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

        TextUtils.applyIfSent(request.getEventDescription(), event::setEventDescription);

        applyTimezoneUpdate(event, request);
        applyEventDateUpdates(event, request);

        TextUtils.applyRequiredIfSent(request.getVenueName(), event::setVenueName);
        TextUtils.applyIfSent(request.getAddressLine1(), event::setAddressLine1);
        TextUtils.applyIfSent(request.getAddressLine2(), event::setAddressLine2);
        TextUtils.applyIfSent(request.getCity(), event::setCity);
        TextUtils.applyIfSent(request.getStateProvince(), event::setStateProvince);
        TextUtils.applyIfSent(request.getPostalCode(), event::setPostalCode);
        TextUtils.applyIfSent(request.getCountry(), event::setCountry);
        TextUtils.applyIfSent(request.getLatitude(), event::setLatitude);
        TextUtils.applyIfSent(request.getLongitude(), event::setLongitude);
        if (request.getEventGoodies() != null) {
            int goodiesCount = countGoodiesItems(request.getEventGoodies());
            EventLimit limits = eventLimitRepository.findByEventId(event.getId())
                    .orElseGet(() -> EventLimit.builder().build());
            if (goodiesCount > limits.getMaxGoodies()) {
                throw new EventLimitExceededException(
                        "You have exceeded the maximum number of goodies allowed for this event.");
            }
        }
        TextUtils.applyIfSent(request.getEventGoodies(), event::setEventGoodies);

        Event updatedEvent = eventRepository.save(event);
        log.info("Successfully updated event with ID: {} by user: {}", updatedEvent.getId(), currentUser.getUsername());

        return toResponse(updatedEvent);
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
        EventDateTimeUtil.zone(request.getTimezone());
        event.setTimezone(request.getTimezone());
    }

    private void applyEventDateUpdates(Event event, UpdateEventRequest request) {
        ZoneId effectiveZone = ZoneId.of(request.getTimezone() != null ? request.getTimezone() : event.getTimezone());
        boolean published = event.getStatus() == EventStatus.PUBLISHED;
        boolean finalised = event.getStatus() == EventStatus.COMPLETED;

        Instant newStart = resolveDateUpdate(effectiveZone, request.getEventStartDate(), request.getEventStartTime(), "start");
        if (newStart != null) {
            if (published || finalised) {
                throw new InvalidUserDataException("You cannot change the start date once the event is published.");
            }
            event.setEventStartDate(newStart);
        }

        Instant newEnd = resolveDateUpdate(effectiveZone, request.getEventEndDate(), request.getEventEndTime(), "end");
        if (newEnd != null) {
            if (finalised) {
                throw new InvalidUserDataException("You cannot change the end date after the event is completed.");
            }
            if (published && newEnd.isBefore(event.getEventEndDate())) {
                throw new InvalidUserDataException("You can only extend the end date, not move it earlier.");
            }
            event.setEventEndDate(newEnd);
        }

        if (event.getEventEndDate() != null && event.getEventStartDate() != null
                && !event.getEventEndDate().isAfter(event.getEventStartDate())) {
            throw new InvalidUserDataException("Event end date must be after the start date.");
        }
    }

    private Instant resolveDateUpdate(ZoneId effectiveZone, String date, String time, String fieldLabel) {
        if (date == null && time == null) {
            return null;
        }
        if (date == null || time == null) {
            throw new InvalidUserDataException(
                    "Both event " + fieldLabel + " date and time must be provided together.");
        }
        return EventDateTimeUtil.toInstant(date, time, effectiveZone);
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

        Page<EventResponse> responsePage = eventsPage.map(this::toResponse);

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
            throw new AccessForbiddenException(
                    "User does not belong to any organization");
        }

        Long organizationId = currentUser.getOrganization().getId();

        Specification<Event> spec = buildEventSpecification(organizationId, status, search);
        // A distributor is bound to a single event, so its listing is narrowed to that event only
        // (and is empty if it has none).
        if (currentUser.getRole() == UserRole.DISTRIBUTOR) {
            Long assignedEventId = currentUser.getEvent() != null ? currentUser.getEvent().getId() : null;
            spec = spec.and((root, query, cb) ->
                    assignedEventId == null ? cb.disjunction() : cb.equal(root.get("id"), assignedEventId));
        }

        Page<Event> eventsPage = eventRepository.findAll(spec, pageable);

        Page<EventResponse> responsePage = eventsPage.map(this::toResponse);

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

        return toResponse(event);
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

        return toResponse(updatedEvent);
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

        EventStatus current = event.getStatus();
        statusTransitionValidator.validateTransition(event, status, currentUser);

        // A final bill closes the event permanently — block any move away from a terminal state.
        if (status != current
                && (current == EventStatus.COMPLETED || current == EventStatus.CANCELLED)
                && eventBillingGuard.hasFinalInvoice(id)) {
            throw new InvalidUserDataException(
                    "You cannot reopen this event because a final bill has been issued.");
        }

        event.setStatus(status);

        Event updatedEvent = eventRepository.save(event);
        log.info("Successfully changed status for event with ID: {} to {} by user: {}",
                updatedEvent.getId(), status, currentUser.getUsername());

        eventPublisher.publishEvent(new EventStatusChangedEvent(updatedEvent.getId(), updatedEvent.getStatus()));

        if (status != current) {
            notifyEventStatusChange(updatedEvent, status, currentUser);
        }

        return toResponse(updatedEvent);
    }

    /**
     * PUBLISHED/COMPLETED concern the application owners (ROOT + ADMIN); CANCELLED concerns the
     * organization's own staff. A move back to DRAFT raises nothing.
     */
    private void notifyEventStatusChange(Event event, EventStatus status, User actor) {
        NotifyRequest.NotifyRequestBuilder builder = NotifyRequest.builder()
                .entityType("EVENT")
                .entityId(String.valueOf(event.getId()))
                .actor(actor);

        switch (status) {
            case PUBLISHED -> builder
                    .audience(NotificationAudience.PLATFORM_ADMINS)
                    .type(NotificationType.EVENT_PUBLISHED)
                    .title("Event Published")
                    .message(String.format("Event \"%s\"%s is now published.", event.getEventName(), orgSuffix(event)));
            case COMPLETED -> builder
                    .audience(NotificationAudience.PLATFORM_ADMINS)
                    .type(NotificationType.EVENT_COMPLETED)
                    .title("Event Completed")
                    .message(String.format("Event \"%s\"%s has been marked completed.", event.getEventName(), orgSuffix(event)));
            case CANCELLED -> builder
                    .audience(NotificationAudience.ORGANIZATION_STAFF)
                    .organizationId(event.getOrganization() != null ? event.getOrganization().getId() : null)
                    .type(NotificationType.EVENT_CANCELLED)
                    .title("Event Cancelled")
                    .message(String.format("Event \"%s\" has been cancelled.", event.getEventName()));
            default -> {
                return;
            }
        }
        publishAfterCommit(builder.build());
    }

    /**
     * Defers the send until the surrounding transaction commits, so a rolled-back create/update never
     * produces a notification. The request is built by the caller while the JPA session is open (lazy
     * fields are safe to read); only the send is postponed. {@link NotificationService#notify} is
     * itself non-blocking, so this adds no latency to the request.
     */
    private void publishAfterCommit(NotifyRequest request) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    notificationService.notify(request);
                }
            });
        } else {
            notificationService.notify(request);
        }
    }

    /** " (Org Name)" when the event's organization is known, else empty — for notification messages. */
    private String orgSuffix(Event event) {
        return event.getOrganization() != null ? " (" + event.getOrganization().getOrganizerName() + ")" : "";
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

        requireEmptyForDeletion(id);

        AuditContextHolder.setEntityLabel(event.getEventName());
        AuditContextHolder.setOrganizationId(event.getOrganization() != null ? event.getOrganization().getId() : null);

        String logoKey = event.getLogoObjectKey();
        // The event_limits row carries an FK to the event with no cascade, so it must be removed
        // (and flushed) before the event itself to avoid a constraint violation.
        eventLimitRepository.deleteById(id);
        eventLimitRepository.flush();
        eventRepository.delete(event);
        deleteQuietly(logoKey);
        log.info("Successfully deleted event with ID: {} by user: {}", id, currentUser.getUsername());
    }

    /**
     * An event is only deletable once it is empty. Blocking on races transitively guarantees no
     * categories or participants, since neither can exist without a race above it. Outer slices
     * contribute their own emptiness checks through {@link EventDeletionGuard}.
     */
    private void requireEmptyForDeletion(Long eventId) {
        if (raceRepository.countByEventIdAndDeletedFalse(eventId) > 0) {
            rejectDeletionFor("races");
        }
        for (EventDeletionGuard guard : eventDeletionGuards) {
            guard.findBlockingContent(eventId).ifPresent(this::rejectDeletionFor);
        }
    }

    private void rejectDeletionFor(String content) {
        throw new EventDeletionNotAllowedException(
                "You cannot delete this event while it still has " + content + ". Delete them first.");
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
                throw new AccessForbiddenException(
                        "Your account is not assigned to an organization.");
            }

            if (!currentUser.getOrganization().getId().equals(organization.getId())) {
                throw new AccessForbiddenException(
                        "You can only create events for your organization.");
            }
            return;
        }

        throw new AccessForbiddenException("You are not allowed to create events.");
    }

    @Override
    @Transactional(readOnly = true)
    public PresignUploadResponse createLogoUploadUrl(Long id, String contentType, User currentUser) {
        Event event = findAndValidateEvent(id, currentUser);
        return storageService.createUploadUrl(UploadCategory.EVENT_LOGO, event.getId(), contentType);
    }

    @Override
    @Transactional
    public EventResponse attachLogo(Long id, String objectKey, User currentUser) {
        log.info("Attaching logo for event ID: {} by user: {}", id, currentUser.getUsername());
        Event event = findAndValidateEvent(id, currentUser);

        if (UploadCategory.EVENT_LOGO.isForeignKeyFor(event.getId(), objectKey)) {
            throw new InvalidFileException("This upload does not belong to this event.");
        }
        if (!storageService.objectExists(objectKey)) {
            throw new InvalidFileException("The uploaded file could not be found.");
        }

        String previousKey = event.getLogoObjectKey();
        event.setLogoObjectKey(objectKey);
        Event saved = eventRepository.saveAndFlush(event);
        if (previousKey != null && !previousKey.equals(objectKey)) {
            deleteQuietly(previousKey);
        }
        log.info("Successfully attached logo for event ID: {}", id);
        return toResponse(saved);
    }

    @Override
    @Transactional
    public EventResponse removeLogo(Long id, User currentUser) {
        log.info("Removing logo for event ID: {} by user: {}", id, currentUser.getUsername());
        Event event = findAndValidateEvent(id, currentUser);

        String previousKey = event.getLogoObjectKey();
        event.setLogoObjectKey(null);
        Event saved = eventRepository.saveAndFlush(event);
        deleteQuietly(previousKey);
        log.info("Successfully removed logo for event ID: {}", id);
        return toResponse(saved);
    }
}
