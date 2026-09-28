package com.timekeeper.bibexpo.event.service.impl;

import com.timekeeper.bibexpo.audit.api.Auditable;
import com.timekeeper.bibexpo.audit.api.AuditAction;
import com.timekeeper.bibexpo.audit.api.AuditContextHolder;
import com.timekeeper.bibexpo.audit.api.AuditEntityType;
import com.timekeeper.bibexpo.event.exception.EventAlreadyExistsException;
import com.timekeeper.bibexpo.event.exception.EventDeletionNotAllowedException;
import com.timekeeper.bibexpo.event.exception.EventNotFoundException;
import com.timekeeper.bibexpo.event.model.dto.request.CreateEventRequest;
import com.timekeeper.bibexpo.event.model.dto.request.UpdateEventRequest;
import com.timekeeper.bibexpo.event.model.dto.response.EventResponse;
import com.timekeeper.bibexpo.event.model.entity.Event;
import com.timekeeper.bibexpo.event.limit.model.entity.EventLimit;
import com.timekeeper.bibexpo.event.model.entity.EventStatus;
import com.timekeeper.bibexpo.event.model.event.EventStatusChangedEvent;
import com.timekeeper.bibexpo.notification.model.dto.NotifyRequest;
import com.timekeeper.bibexpo.notification.model.enums.NotificationAudience;
import com.timekeeper.bibexpo.notification.model.enums.NotificationType;
import com.timekeeper.bibexpo.notification.service.NotificationService;
import com.timekeeper.bibexpo.organization.api.OrganizationDirectory;
import com.timekeeper.bibexpo.organization.exception.OrganizationNotFoundException;
import com.timekeeper.bibexpo.organization.model.entity.Organization;
import com.timekeeper.bibexpo.event.limit.repository.EventLimitRepository;
import com.timekeeper.bibexpo.event.repository.EventRepository;
import com.timekeeper.bibexpo.event.race.repository.RaceRepository;
import com.timekeeper.bibexpo.event.api.EventBillingGuard;
import com.timekeeper.bibexpo.event.api.EventDeletionCleaner;
import com.timekeeper.bibexpo.event.api.EventDeletionGuard;
import com.timekeeper.bibexpo.event.api.EventPublishGuard;
import com.timekeeper.bibexpo.event.service.EventService;
import com.timekeeper.bibexpo.event.model.enums.EventOperation;
import com.timekeeper.bibexpo.event.service.validator.EventAccessValidator;
import com.timekeeper.bibexpo.event.service.validator.EventOperationGuard;
import com.timekeeper.bibexpo.event.service.validator.EventStatusTransitionValidator;
import com.timekeeper.bibexpo.shared.error.AccessForbiddenException;
import com.timekeeper.bibexpo.shared.error.InvalidUserDataException;
import com.timekeeper.bibexpo.shared.security.UserRole;
import com.timekeeper.bibexpo.shared.util.EventDateTimeUtil;
import com.timekeeper.bibexpo.shared.util.TextUtils;
import com.timekeeper.bibexpo.storage.exception.InvalidFileException;
import com.timekeeper.bibexpo.storage.model.dto.response.PresignUploadResponse;
import com.timekeeper.bibexpo.storage.model.enums.UploadCategory;
import com.timekeeper.bibexpo.storage.service.StorageService;
import com.timekeeper.bibexpo.user.model.entity.User;
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


import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventServiceImpl implements EventService {

    private static final String EVENT_NAME_TAKEN = "An event with this name already exists for this organization.";

    private final EventRepository eventRepository;
    private final OrganizationDirectory organizationDirectory;
    private final EventLimitRepository eventLimitRepository;
    private final RaceRepository raceRepository;
    private final EventAccessValidator eventAccessValidator;
    private final EventStatusTransitionValidator statusTransitionValidator;
    private final EventOperationGuard eventOperationGuard;
    private final EventBillingGuard eventBillingGuard;
    private final List<EventDeletionGuard> eventDeletionGuards;
    private final List<EventDeletionCleaner> eventDeletionCleaners;
    private final List<EventPublishGuard> eventPublishGuards;
    private final StorageService storageService;
    private final NotificationService notificationService;
    private final ApplicationEventPublisher eventPublisher;

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
            log.atWarn()
                    .addKeyValue("event", "storage.delete.failed")
                    .addKeyValue("objectKey", objectKey)
                    .addKeyValue("error", e.getMessage())
                    .log("Stored object could not be deleted");
        }
    }

    @Auditable(entityType = AuditEntityType.EVENT, action = AuditAction.CREATE)
    @Override
    @Transactional
    public EventResponse createEvent(CreateEventRequest request, User currentUser) {
        requireOwnOrganization(currentUser, request.getOrganizationId());
        Organization organization = organizationDirectory.requireById(request.getOrganizationId());

        if (eventRepository.existsByEventNameAndOrganizationId(
                request.getEventName(), request.getOrganizationId())) {
            throw new EventAlreadyExistsException(EVENT_NAME_TAKEN);
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
                .build();

        Event savedEvent = eventRepository.save(event);
        eventLimitRepository.save(EventLimit.builder().event(savedEvent).build());
        log.atInfo()
                .addKeyValue("event", "event.created")
                .addKeyValue("eventId", savedEvent.getId())
                .addKeyValue("targetOrgId", organization.getId())
                .log("Event created");

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
        Event event = findAndValidateEvent(id, currentUser);
        eventOperationGuard.requireAllowed(event, EventOperation.PROFILE_WRITE);

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

        Event updatedEvent = eventRepository.save(event);
        log.atInfo()
                .addKeyValue("event", "event.updated")
                .addKeyValue("eventId", id)
                .log("Event updated");

        return toResponse(updatedEvent);
    }

    private void applyEventNameUpdate(Event event, UpdateEventRequest request) {
        if (request.getEventName() == null || request.getEventName().isBlank()
                || request.getEventName().equals(event.getEventName())) {
            return;
        }
        if (eventRepository.existsByEventNameAndOrganizationId(
                request.getEventName(), event.getOrganization().getId())) {
            throw new EventAlreadyExistsException(EVENT_NAME_TAKEN);
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
        Specification<Event> spec = buildEventSpecification(organizationId, status, search);
        return eventRepository.findAll(spec, pageable).map(this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<EventResponse> getOrganizationEvents(
            EventStatus status, String search, Pageable pageable, User currentUser) {
        if (currentUser.getOrganization() == null) {
            throw new AccessForbiddenException("Your account is not assigned to an organization.");
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

        return eventRepository.findAll(spec, pageable).map(this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public EventResponse getEventById(Long id, User currentUser) {
        return toResponse(findAndValidateEvent(id, currentUser));
    }

    @Auditable(entityType = AuditEntityType.EVENT, action = AuditAction.STATUS_CHANGE)
    @Override
    @Transactional
    public EventResponse toggleEventEnabled(Long id, User currentUser) {
        Event event = eventRepository.findById(id)
                .orElseThrow(EventNotFoundException::new);

        if (currentUser.getRole() != UserRole.ROOT && eventBillingGuard.hasFinalInvoice(id)) {
            throw new AccessForbiddenException(
                    "You cannot enable or disable this event once its bill has been finalized.");
        }

        event.setEnabled(!event.getEnabled());

        Event updatedEvent = eventRepository.save(event);
        log.atInfo()
                .addKeyValue("event", "event.enabled.changed")
                .addKeyValue("eventId", id)
                .addKeyValue("enabled", updatedEvent.getEnabled())
                .log("Event enabled flag changed");

        return toResponse(updatedEvent);
    }

    @Auditable(entityType = AuditEntityType.EVENT, action = AuditAction.STATUS_CHANGE)
    @Override
    @Transactional
    public EventResponse changeEventStatus(Long id, EventStatus status, User currentUser) {
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

        if (status == EventStatus.PUBLISHED && current != EventStatus.PUBLISHED) {
            requirePublishable(id);
        }

        event.setStatus(status);

        Event updatedEvent = eventRepository.save(event);

        eventPublisher.publishEvent(new EventStatusChangedEvent(updatedEvent.getId(), updatedEvent.getStatus()));

        if (status != current) {
            log.atInfo()
                    .addKeyValue("event", "event.status.changed")
                    .addKeyValue("eventId", id)
                    .addKeyValue("fromStatus", current)
                    .addKeyValue("toStatus", status)
                    .log("Event status changed");
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
        Event event = findAndValidateEvent(id, currentUser);

        if (event.getStatus() != EventStatus.DRAFT && event.getStatus() != EventStatus.CANCELLED) {
            throw new EventDeletionNotAllowedException(
                    "Only events in DRAFT or CANCELLED status can be deleted.");
        }
        if (eventBillingGuard.hasFinalInvoice(id)) {
            throw new EventDeletionNotAllowedException(
                    "You cannot delete this event once its bill has been finalized.");
        }

        requireEmptyForDeletion(id);

        AuditContextHolder.setEntityLabel(event.getEventName());
        AuditContextHolder.setOrganizationId(event.getOrganization() != null ? event.getOrganization().getId() : null);

        String logoKey = event.getLogoObjectKey();
        for (EventDeletionCleaner cleaner : eventDeletionCleaners) {
            cleaner.purgeForEvent(id);
        }
        // The event_limits row carries an FK to the event with no cascade, so it must be removed
        // (and flushed) before the event itself to avoid a constraint violation.
        eventLimitRepository.deleteById(id);
        eventLimitRepository.flush();
        eventRepository.delete(event);
        deleteQuietly(logoKey);
        log.atInfo()
                .addKeyValue("event", "event.deleted")
                .addKeyValue("eventId", id)
                .log("Event deleted");
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

    /**
     * Publishing is the last moment an outer slice can insist on configuration it will need once
     * the event is live, so each one is asked through {@link EventPublishGuard}. A slice the
     * organization does not use contributes nothing and the event publishes as it always did.
     */
    private void requirePublishable(Long eventId) {
        for (EventPublishGuard guard : eventPublishGuards) {
            guard.findBlockingReason(eventId).ifPresent(reason -> {
                throw new InvalidUserDataException(reason);
            });
        }
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
                String pattern = "%" + search.toLowerCase() + "%";
                predicates.add(criteriaBuilder.or(
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("eventName")), pattern),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("eventDescription")), pattern),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("venueName")), pattern)));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    // Runs before the organization is loaded, and answers another organization's id exactly like a
    // missing one, so an organization-scoped caller cannot learn which ids exist.
    private void requireOwnOrganization(User currentUser, Long organizationId) {
        UserRole role = currentUser.getRole();
        if (role == UserRole.ROOT || role == UserRole.ADMIN) {
            return;
        }
        if (currentUser.getOrganization() == null) {
            throw new AccessForbiddenException("Your account is not assigned to an organization.");
        }
        if (!currentUser.getOrganization().getId().equals(organizationId)) {
            log.atWarn()
                    .addKeyValue("event", "authz.denied")
                    .addKeyValue("reason", "organization_not_own")
                    .addKeyValue("targetOrgId", organizationId)
                    .log("Organization access hidden as not found");
            throw new OrganizationNotFoundException();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PresignUploadResponse createLogoUploadUrl(Long id, String contentType, User currentUser) {
        Event event = findAndValidateEvent(id, currentUser);
        eventOperationGuard.requireAllowed(event, EventOperation.LOGO_WRITE);
        return storageService.createUploadUrl(UploadCategory.EVENT_LOGO, event.getId(), contentType);
    }

    @Override
    @Transactional
    public EventResponse attachLogo(Long id, String objectKey, User currentUser) {
        Event event = findAndValidateEvent(id, currentUser);
        eventOperationGuard.requireAllowed(event, EventOperation.LOGO_WRITE);

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
        logLogoChanged(id, "attached");
        return toResponse(saved);
    }

    @Override
    @Transactional
    public EventResponse removeLogo(Long id, User currentUser) {
        Event event = findAndValidateEvent(id, currentUser);
        eventOperationGuard.requireAllowed(event, EventOperation.LOGO_WRITE);

        String previousKey = event.getLogoObjectKey();
        event.setLogoObjectKey(null);
        Event saved = eventRepository.saveAndFlush(event);
        deleteQuietly(previousKey);
        logLogoChanged(id, "removed");
        return toResponse(saved);
    }

    private void logLogoChanged(Long eventId, String action) {
        log.atInfo()
                .addKeyValue("event", "event.logo.changed")
                .addKeyValue("eventId", eventId)
                .addKeyValue("action", action)
                .log("Event logo changed");
    }
}
