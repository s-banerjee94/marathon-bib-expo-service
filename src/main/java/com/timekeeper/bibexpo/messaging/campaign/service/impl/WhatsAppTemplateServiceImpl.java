package com.timekeeper.bibexpo.messaging.campaign.service.impl;

import com.timekeeper.bibexpo.annotation.Auditable;
import com.timekeeper.bibexpo.aspect.AuditContextHolder;
import com.timekeeper.bibexpo.exception.EventNotFoundException;
import com.timekeeper.bibexpo.model.entity.Event;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.model.enums.AuditAction;
import com.timekeeper.bibexpo.model.enums.AuditEntityType;
import com.timekeeper.bibexpo.repository.EventRepository;
import com.timekeeper.bibexpo.service.validator.EventAccessValidator;
import com.timekeeper.bibexpo.util.SmsTemplateContext;
import com.timekeeper.bibexpo.util.SmsTemplateParser;
import com.timekeeper.bibexpo.util.TextUtils;
import com.timekeeper.bibexpo.messaging.campaign.exception.InvalidWhatsAppTemplateException;
import com.timekeeper.bibexpo.messaging.campaign.exception.WhatsAppTemplateAlreadyExistsException;
import com.timekeeper.bibexpo.messaging.campaign.exception.WhatsAppTemplateNotFoundException;
import com.timekeeper.bibexpo.messaging.campaign.model.dto.request.CreateWhatsAppTemplateRequest;
import com.timekeeper.bibexpo.messaging.campaign.model.dto.request.UpdateWhatsAppTemplateRequest;
import com.timekeeper.bibexpo.messaging.campaign.model.dto.response.WhatsAppTemplateResponse;
import com.timekeeper.bibexpo.messaging.campaign.model.entity.WhatsAppTemplate;
import com.timekeeper.bibexpo.messaging.campaign.repository.WhatsAppCampaignRepository;
import com.timekeeper.bibexpo.messaging.campaign.repository.WhatsAppTemplateRepository;
import com.timekeeper.bibexpo.messaging.campaign.service.WhatsAppTemplateService;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class WhatsAppTemplateServiceImpl implements WhatsAppTemplateService {

    private static final int MAX_TEMPLATES_PER_EVENT = 20;

    private final WhatsAppTemplateRepository templateRepository;
    private final WhatsAppCampaignRepository campaignRepository;
    private final EventRepository eventRepository;
    private final EventAccessValidator eventAccessValidator;

    @Auditable(entityType = AuditEntityType.WHATSAPP_TEMPLATE, action = AuditAction.CREATE)
    @Override
    @Transactional
    public WhatsAppTemplateResponse createTemplate(Long eventId, CreateWhatsAppTemplateRequest request, User currentUser) {
        log.info("Creating WhatsApp template for event ID: {} by user: {}", eventId, currentUser.getUsername());

        Event event = validateEventAccess(eventId, currentUser);

        if (templateRepository.countByEventId(eventId) >= MAX_TEMPLATES_PER_EVENT) {
            throw new InvalidWhatsAppTemplateException("An event can have a maximum of 20 WhatsApp templates.");
        }

        if (templateRepository.existsByContentSidAndEventId(request.getContentSid(), eventId)) {
            throw new WhatsAppTemplateAlreadyExistsException(
                    "A WhatsApp template with this Content SID already exists for this event.");
        }

        validateBodyVariables(request.getBodyVariables());

        WhatsAppTemplate template = WhatsAppTemplate.builder()
                .name(request.getName().toLowerCase().trim())
                .contentSid(request.getContentSid())
                .body(request.getBody().trim())
                .bodyVariables(joinBodyVariables(request.getBodyVariables()))
                .note(request.getNote())
                .eventId(eventId)
                .organizationId(event.getOrganization() != null ? event.getOrganization().getId() : null)
                .build();

        WhatsAppTemplate saved = templateRepository.save(template);
        log.info("Successfully created WhatsApp template with ID: {} for event ID: {} by user: {}",
                saved.getId(), eventId, currentUser.getUsername());

        return WhatsAppTemplateResponse.fromEntity(saved, event);
    }

    @Auditable(entityType = AuditEntityType.WHATSAPP_TEMPLATE, action = AuditAction.UPDATE)
    @Override
    @Transactional
    public WhatsAppTemplateResponse updateTemplate(Long eventId, Long templateId, UpdateWhatsAppTemplateRequest request, User currentUser) {
        log.info("Updating WhatsApp template ID: {} for event ID: {} by user: {}",
                templateId, eventId, currentUser.getUsername());

        Event event = validateEventAccess(eventId, currentUser);

        WhatsAppTemplate template = findTemplateOrThrow(templateId, eventId);

        if (request.getName() != null && !request.getName().isBlank()) {
            template.setName(request.getName().toLowerCase().trim());
        }

        if (request.getContentSid() != null && !request.getContentSid().isBlank() &&
                !request.getContentSid().equals(template.getContentSid())) {
            if (templateRepository.existsByContentSidAndEventIdAndIdNot(request.getContentSid(), eventId, templateId)) {
                throw new WhatsAppTemplateAlreadyExistsException(
                        "A WhatsApp template with this Content SID already exists for this event.");
            }
            template.setContentSid(request.getContentSid());
        }

        if (request.getBodyVariables() != null) {
            validateBodyVariables(request.getBodyVariables());
            template.setBodyVariables(joinBodyVariables(request.getBodyVariables()));
        }

        TextUtils.applyRequiredIfSent(request.getBody(), template::setBody);
        TextUtils.applyIfSent(request.getNote(), template::setNote);

        WhatsAppTemplate updated = templateRepository.saveAndFlush(template);
        log.info("Successfully updated WhatsApp template ID: {} for event ID: {} by user: {}",
                updated.getId(), eventId, currentUser.getUsername());

        return WhatsAppTemplateResponse.fromEntity(updated, event);
    }

    @Override
    @Transactional(readOnly = true)
    public List<WhatsAppTemplateResponse> getTemplatesByEvent(Long eventId, String search, User currentUser) {
        log.info("Fetching WhatsApp templates for event ID: {} search: {} by user: {}",
                eventId, search, currentUser.getUsername());

        Event event = validateEventAccess(eventId, currentUser);

        Specification<WhatsAppTemplate> spec = buildTemplateSpecification(eventId, search);
        List<WhatsAppTemplate> templates = templateRepository.findAll(spec);

        log.info("Successfully fetched {} WhatsApp templates for event ID: {} by user: {}",
                templates.size(), eventId, currentUser.getUsername());

        return templates.stream().map(template -> WhatsAppTemplateResponse.fromEntity(template, event)).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public WhatsAppTemplateResponse getTemplateById(Long eventId, Long templateId, User currentUser) {
        log.info("Fetching WhatsApp template ID: {} for event ID: {} by user: {}",
                templateId, eventId, currentUser.getUsername());

        Event event = validateEventAccess(eventId, currentUser);

        WhatsAppTemplate template = findTemplateOrThrow(templateId, eventId);

        return WhatsAppTemplateResponse.fromEntity(template, event);
    }

    @Auditable(entityType = AuditEntityType.WHATSAPP_TEMPLATE, action = AuditAction.DELETE)
    @Override
    @Transactional
    public void deleteTemplate(Long eventId, Long templateId, User currentUser) {
        log.info("Deleting WhatsApp template ID: {} for event ID: {} by user: {}",
                templateId, eventId, currentUser.getUsername());

        Event event = validateEventAccess(eventId, currentUser);

        WhatsAppTemplate template = findTemplateOrThrow(templateId, eventId);

        if (campaignRepository.existsByWhatsAppTemplateId(template.getId())) {
            throw new InvalidWhatsAppTemplateException("This template is used by one or more campaigns and cannot be deleted.");
        }

        AuditContextHolder.setEntityLabel(template.getName());
        AuditContextHolder.setOrganizationId(event.getOrganization() != null ? event.getOrganization().getId() : null);

        templateRepository.delete(template);
        log.info("Successfully deleted WhatsApp template ID: {} by user: {}", templateId, currentUser.getUsername());
    }

    private Specification<WhatsAppTemplate> buildTemplateSpecification(Long eventId, String search) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            predicates.add(cb.equal(root.get("eventId"), eventId));

            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(root.get("name"), pattern),
                        cb.like(cb.lower(root.get("contentSid")), pattern)
                ));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private WhatsAppTemplate findTemplateOrThrow(Long templateId, Long eventId) {
        return templateRepository.findByIdAndEventId(templateId, eventId)
                .orElseThrow(WhatsAppTemplateNotFoundException::new);
    }

    private Event validateEventAccess(Long eventId, User currentUser) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(EventNotFoundException::new);

        eventAccessValidator.validateUserAuthorizationForEvent(currentUser, event);

        return event;
    }

    private void validateBodyVariables(List<String> bodyVariables) {
        if (bodyVariables == null || bodyVariables.isEmpty()) {
            return;
        }
        List<String> invalid = SmsTemplateParser.validatePlaceholders(
                String.join(" ", bodyVariables), SmsTemplateContext.class);
        if (!invalid.isEmpty()) {
            throw new InvalidWhatsAppTemplateException(
                    "Invalid placeholders in template variables: " + invalid + ".");
        }
    }

    private String joinBodyVariables(List<String> bodyVariables) {
        if (bodyVariables == null || bodyVariables.isEmpty()) {
            return null;
        }
        return String.join("\n", bodyVariables.stream().map(String::trim).toList());
    }
}
