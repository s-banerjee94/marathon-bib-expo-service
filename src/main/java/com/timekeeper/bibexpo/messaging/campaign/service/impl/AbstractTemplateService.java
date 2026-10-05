package com.timekeeper.bibexpo.messaging.campaign.service.impl;

import com.timekeeper.bibexpo.audit.api.AuditContextHolder;
import com.timekeeper.bibexpo.messaging.campaign.model.entity.TemplateEntity;
import com.timekeeper.bibexpo.messaging.campaign.repository.TemplateBaseRepository;
import com.timekeeper.bibexpo.messaging.shared.enums.MessageChannel;
import com.timekeeper.bibexpo.event.model.entity.Event;
import com.timekeeper.bibexpo.event.model.enums.EventOperation;
import com.timekeeper.bibexpo.event.api.EventStore;
import com.timekeeper.bibexpo.event.service.validator.EventAccessValidator;
import com.timekeeper.bibexpo.event.service.validator.EventOperationGuard;
import com.timekeeper.bibexpo.user.model.entity.User;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * Channel-agnostic message-template flows: event-scoped lookup, list-with-search, and the
 * guarded delete with its in-use check. Create/update stay in the channel subclasses — their
 * field logic (DLT ID + message text vs Content SID + body variables) genuinely differs.
 * Subclasses keep the public API methods so {@code @Auditable}/{@code @Transactional} stay on
 * the Spring proxy boundary.
 *
 * @param <T> template entity of the channel
 * @param <R> response DTO of the channel
 */
public abstract class AbstractTemplateService<T extends TemplateEntity, R> {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final MessageChannel channel;

    protected final TemplateBaseRepository<T> templateRepository;
    private final EventStore eventStore;
    private final EventAccessValidator eventAccessValidator;
    private final EventOperationGuard eventOperationGuard;

    protected AbstractTemplateService(MessageChannel channel,
                                      TemplateBaseRepository<T> templateRepository,
                                      EventStore eventStore,
                                      EventAccessValidator eventAccessValidator,
                                      EventOperationGuard eventOperationGuard) {
        this.channel = channel;
        this.templateRepository = templateRepository;
        this.eventStore = eventStore;
        this.eventAccessValidator = eventAccessValidator;
        this.eventOperationGuard = eventOperationGuard;
    }

    protected final List<R> doList(Long eventId, String search, User currentUser) {
        Event event = validateEventAccess(eventId, currentUser);

        return templateRepository.findAll(buildSearchSpecification(eventId, search))
                .stream().map(template -> toResponse(template, event)).toList();
    }

    protected final void doDelete(Long eventId, Long templateId, User currentUser) {
        Event event = validateEventAccess(eventId, currentUser);
        requireTemplateWriteAllowed(event);

        T template = findTemplateOrThrow(templateId, eventId);

        assertTemplateDeletable(template);

        AuditContextHolder.setEntityId(String.valueOf(templateId));
        AuditContextHolder.setEntityLabel(template.getName());
        AuditContextHolder.setOrganizationId(
                event.getOrganization() != null ? event.getOrganization().getId() : null);

        templateRepository.delete(template);
        logWrite("campaign.template.deleted", eventId, templateId);
    }

    protected final void logWrite(String eventName, Long eventId, Long templateId) {
        log.atInfo()
                .addKeyValue("event", eventName)
                .addKeyValue("channel", channel)
                .addKeyValue("eventId", eventId)
                .addKeyValue("templateId", templateId)
                .log("Campaign template changed");
    }

    private Specification<T> buildSearchSpecification(Long eventId, String search) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            predicates.add(eventPredicate(root, cb, eventId));

            if (search != null && !search.isBlank()) {
                predicates.add(searchPredicate(root, cb, search));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    protected final T findTemplateOrThrow(Long templateId, Long eventId) {
        return templateRepository.findByIdAndEventId(templateId, eventId)
                .orElseThrow(this::templateNotFound);
    }

    protected final Event validateEventAccess(Long eventId, User currentUser) {
        Event event = eventStore.requireById(eventId);

        eventAccessValidator.validateUserAuthorizationForEvent(currentUser, event);

        return event;
    }

    protected final void requireTemplateWriteAllowed(Event event) {
        eventOperationGuard.requireAllowed(event, EventOperation.TEMPLATE_WRITE);
    }

    /**
     * Channel's template-not-found exception.
     */
    protected abstract RuntimeException templateNotFound();

    /**
     * Predicate matching the template's event — the channels map the event differently
     * (association vs plain ID column).
     */
    protected abstract Predicate eventPredicate(Root<T> root, CriteriaBuilder cb, Long eventId);

    /**
     * Predicate matching the search term against the channel's searchable fields.
     */
    protected abstract Predicate searchPredicate(Root<T> root, CriteriaBuilder cb, String search);

    /**
     * Reject deletion when the template is still referenced by a campaign.
     */
    protected abstract void assertTemplateDeletable(T template);

    /**
     * Map the template to the channel's response DTO.
     */
    protected abstract R toResponse(T template, Event event);
}
