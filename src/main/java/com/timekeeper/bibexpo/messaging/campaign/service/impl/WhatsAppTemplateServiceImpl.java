package com.timekeeper.bibexpo.messaging.campaign.service.impl;

import com.timekeeper.bibexpo.annotation.Auditable;
import com.timekeeper.bibexpo.messaging.campaign.exception.InvalidWhatsAppTemplateException;
import com.timekeeper.bibexpo.messaging.campaign.exception.WhatsAppTemplateAlreadyExistsException;
import com.timekeeper.bibexpo.messaging.campaign.exception.WhatsAppTemplateNotFoundException;
import com.timekeeper.bibexpo.messaging.campaign.model.dto.request.CreateWhatsAppTemplateRequest;
import com.timekeeper.bibexpo.messaging.campaign.model.dto.request.UpdateWhatsAppTemplateRequest;
import com.timekeeper.bibexpo.messaging.campaign.model.dto.response.WhatsAppTemplateResponse;
import com.timekeeper.bibexpo.messaging.campaign.model.entity.WhatsAppTemplate;
import com.timekeeper.bibexpo.messaging.campaign.model.enums.CampaignStatus;
import com.timekeeper.bibexpo.messaging.campaign.repository.WhatsAppCampaignRepository;
import com.timekeeper.bibexpo.messaging.campaign.repository.WhatsAppTemplateRepository;
import com.timekeeper.bibexpo.messaging.campaign.service.WhatsAppTemplateService;
import com.timekeeper.bibexpo.model.entity.Event;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.model.enums.AuditAction;
import com.timekeeper.bibexpo.model.enums.AuditEntityType;
import com.timekeeper.bibexpo.repository.EventRepository;
import com.timekeeper.bibexpo.service.validator.EventAccessValidator;
import com.timekeeper.bibexpo.service.validator.EventOperationGuard;
import com.timekeeper.bibexpo.util.SmsTemplateContext;
import com.timekeeper.bibexpo.util.SmsTemplateParser;
import com.timekeeper.bibexpo.util.TextUtils;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class WhatsAppTemplateServiceImpl
        extends AbstractTemplateService<WhatsAppTemplate, WhatsAppTemplateResponse>
        implements WhatsAppTemplateService {

    private static final int MAX_TEMPLATES_PER_EVENT = 20;

    private final WhatsAppTemplateRepository templateRepository;
    private final WhatsAppCampaignRepository campaignRepository;

    public WhatsAppTemplateServiceImpl(WhatsAppTemplateRepository templateRepository,
                                       WhatsAppCampaignRepository campaignRepository,
                                       EventRepository eventRepository,
                                       EventAccessValidator eventAccessValidator,
                                       EventOperationGuard eventOperationGuard) {
        super("WhatsApp", templateRepository, eventRepository, eventAccessValidator, eventOperationGuard);
        this.templateRepository = templateRepository;
        this.campaignRepository = campaignRepository;
    }

    @Auditable(entityType = AuditEntityType.WHATSAPP_TEMPLATE, action = AuditAction.CREATE)
    @Override
    @Transactional
    public WhatsAppTemplateResponse createTemplate(Long eventId, CreateWhatsAppTemplateRequest request, User currentUser) {
        log.info("Creating WhatsApp template for event ID: {} by user: {}", eventId, currentUser.getUsername());

        Event event = validateEventAccess(eventId, currentUser);
        requireTemplateWriteAllowed(event);

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
        requireTemplateWriteAllowed(event);

        WhatsAppTemplate template = findTemplateOrThrow(templateId, eventId);

        if (campaignRepository.existsByWhatsAppTemplateIdAndStatusIn(
                template.getId(), List.of(CampaignStatus.SENDING, CampaignStatus.SENT))) {
            throw new InvalidWhatsAppTemplateException(
                    "You cannot edit this template while it is used in a campaign that is running or completed.");
        }

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
        return doList(eventId, search, currentUser);
    }

    @Auditable(entityType = AuditEntityType.WHATSAPP_TEMPLATE, action = AuditAction.DELETE)
    @Override
    @Transactional
    public void deleteTemplate(Long eventId, Long templateId, User currentUser) {
        doDelete(eventId, templateId, currentUser);
    }

    @Override
    protected RuntimeException templateNotFound() {
        return new WhatsAppTemplateNotFoundException();
    }

    @Override
    protected Predicate eventPredicate(Root<WhatsAppTemplate> root, CriteriaBuilder cb, Long eventId) {
        return cb.equal(root.get("eventId"), eventId);
    }

    @Override
    protected Predicate searchPredicate(Root<WhatsAppTemplate> root, CriteriaBuilder cb, String search) {
        String pattern = "%" + search.toLowerCase() + "%";
        return cb.or(
                cb.like(root.get("name"), pattern),
                cb.like(cb.lower(root.get("contentSid")), pattern)
        );
    }

    @Override
    protected void assertTemplateDeletable(WhatsAppTemplate template) {
        if (campaignRepository.existsByWhatsAppTemplateId(template.getId())) {
            throw new InvalidWhatsAppTemplateException("This template is used by one or more campaigns and cannot be deleted.");
        }
    }

    @Override
    protected WhatsAppTemplateResponse toResponse(WhatsAppTemplate template, Event event) {
        return WhatsAppTemplateResponse.fromEntity(template, event);
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
