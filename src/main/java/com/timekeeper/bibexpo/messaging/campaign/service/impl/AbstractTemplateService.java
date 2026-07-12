package com.timekeeper.bibexpo.messaging.campaign.service.impl;

import com.timekeeper.bibexpo.aspect.AuditContextHolder;
import com.timekeeper.bibexpo.exception.EventNotFoundException;
import com.timekeeper.bibexpo.messaging.campaign.model.entity.TemplateEntity;
import com.timekeeper.bibexpo.messaging.campaign.repository.TemplateBaseRepository;
import com.timekeeper.bibexpo.model.entity.Event;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.model.enums.EventOperation;
import com.timekeeper.bibexpo.repository.EventRepository;
import com.timekeeper.bibexpo.service.validator.EventAccessValidator;
import com.timekeeper.bibexpo.service.validator.EventOperationGuard;
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

    protected final Logger log = LoggerFactory.getLogger(getClass());

    // Channel name as it appears in log lines ("SMS", "WhatsApp"); constant per subclass
    private final String channelLabel;

    protected final TemplateBaseRepository<T> templateRepository;
    private final EventRepository eventRepository;
    private final EventAccessValidator eventAccessValidator;
    private final EventOperationGuard eventOperationGuard;

    protected AbstractTemplateService(String channelLabel,
                                      TemplateBaseRepository<T> templateRepository,
                                      EventRepository eventRepository,
                                      EventAccessValidator eventAccessValidator,
                                      EventOperationGuard eventOperationGuard) {
        this.channelLabel = channelLabel;
        this.templateRepository = templateRepository;
        this.eventRepository = eventRepository;
        this.eventAccessValidator = eventAccessValidator;
        this.eventOperationGuard = eventOperationGuard;
    }

    protected final List<R> doList(Long eventId, String search, User currentUser) {
        log.info("Fetching {} templates for event ID: {} search: {} by user: {}",
                channelLabel, eventId, search, currentUser.getUsername());

        Event event = validateEventAccess(eventId, currentUser);

        List<T> templates = templateRepository.findAll(buildSearchSpecification(eventId, search));

        log.info("Successfully fetched {} {} templates for event ID: {} by user: {}",
                templates.size(), channelLabel, eventId, currentUser.getUsername());

        return templates.stream().map(template -> toResponse(template, event)).toList();
    }

    protected final void doDelete(Long eventId, Long templateId, User currentUser) {
        log.info("Deleting {} template ID: {} for event ID: {} by user: {}",
                channelLabel, templateId, eventId, currentUser.getUsername());

        Event event = validateEventAccess(eventId, currentUser);
        requireTemplateWriteAllowed(event);

        T template = findTemplateOrThrow(templateId, eventId);

        assertTemplateDeletable(template);

        AuditContextHolder.setEntityLabel(template.getName());
        AuditContextHolder.setOrganizationId(
                event.getOrganization() != null ? event.getOrganization().getId() : null);

        templateRepository.delete(template);
        log.info("Successfully deleted {} template ID: {} by user: {}",
                channelLabel, templateId, currentUser.getUsername());
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
        Event event = eventRepository.findById(eventId)
                .orElseThrow(EventNotFoundException::new);

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
