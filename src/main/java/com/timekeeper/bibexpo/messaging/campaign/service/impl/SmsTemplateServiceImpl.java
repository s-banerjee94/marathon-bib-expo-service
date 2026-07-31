package com.timekeeper.bibexpo.messaging.campaign.service.impl;

import com.timekeeper.bibexpo.annotation.Auditable;
import com.timekeeper.bibexpo.exception.EventLimitExceededException;
import com.timekeeper.bibexpo.messaging.campaign.exception.InvalidSmsTemplateException;
import com.timekeeper.bibexpo.messaging.campaign.exception.SmsTemplateAlreadyExistsException;
import com.timekeeper.bibexpo.messaging.campaign.exception.SmsTemplateNotFoundException;
import com.timekeeper.bibexpo.messaging.campaign.model.dto.request.CreateSmsTemplateRequest;
import com.timekeeper.bibexpo.messaging.campaign.model.dto.request.UpdateSmsTemplateRequest;
import com.timekeeper.bibexpo.messaging.campaign.model.dto.response.SmsTemplateResponse;
import com.timekeeper.bibexpo.messaging.campaign.model.entity.SmsTemplate;
import com.timekeeper.bibexpo.messaging.campaign.model.enums.CampaignStatus;
import com.timekeeper.bibexpo.messaging.campaign.repository.SmsCampaignRepository;
import com.timekeeper.bibexpo.messaging.campaign.repository.SmsTemplateRepository;
import com.timekeeper.bibexpo.messaging.campaign.service.SmsTemplateService;
import com.timekeeper.bibexpo.messaging.campaign.util.TemplateSenderStamp;
import com.timekeeper.bibexpo.messaging.provider.model.enums.TemplateMode;
import com.timekeeper.bibexpo.messaging.shared.enums.MessageChannel;
import com.timekeeper.bibexpo.model.entity.Event;
import com.timekeeper.bibexpo.model.entity.EventLimit;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.model.enums.AuditAction;
import com.timekeeper.bibexpo.model.enums.AuditEntityType;
import com.timekeeper.bibexpo.repository.EventLimitRepository;
import com.timekeeper.bibexpo.repository.EventRepository;
import com.timekeeper.bibexpo.service.validator.EventAccessValidator;
import com.timekeeper.bibexpo.service.validator.EventOperationGuard;
import com.timekeeper.bibexpo.messaging.shared.template.MessageTemplateContext;
import com.timekeeper.bibexpo.messaging.shared.template.MessageTemplateParser;
import com.timekeeper.bibexpo.util.TextUtils;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SmsTemplateServiceImpl
        extends AbstractTemplateService<SmsTemplate, SmsTemplateResponse>
        implements SmsTemplateService {

    private final SmsTemplateRepository smsTemplateRepository;
    private final SmsCampaignRepository smsCampaignRepository;
    private final EventLimitRepository eventLimitRepository;
    private final TemplateSenderStamp senderStamp;

    public SmsTemplateServiceImpl(SmsTemplateRepository smsTemplateRepository,
                                  SmsCampaignRepository smsCampaignRepository,
                                  EventRepository eventRepository,
                                  EventAccessValidator eventAccessValidator,
                                  EventLimitRepository eventLimitRepository,
                                  EventOperationGuard eventOperationGuard,
                                  TemplateSenderStamp senderStamp) {
        super("SMS", smsTemplateRepository, eventRepository, eventAccessValidator, eventOperationGuard);
        this.smsTemplateRepository = smsTemplateRepository;
        this.smsCampaignRepository = smsCampaignRepository;
        this.eventLimitRepository = eventLimitRepository;
        this.senderStamp = senderStamp;
    }

    @Auditable(entityType = AuditEntityType.SMS_TEMPLATE, action = AuditAction.CREATE)
    @Override
    @Transactional
    public SmsTemplateResponse createSmsTemplate(Long eventId, CreateSmsTemplateRequest request, User currentUser) {
        log.info("Creating SMS template for event ID: {} by user: {}", eventId, currentUser.getUsername());

        Event event = validateEventAccess(eventId, currentUser);
        requireTemplateWriteAllowed(event);

        EventLimit limits = eventLimitRepository.findByEventId(eventId)
                .orElseGet(() -> EventLimit.builder().build());
        if (smsTemplateRepository.countByEventId(eventId) >= limits.getMaxSmsTemplates()) {
            throw new EventLimitExceededException("You have reached the maximum number of SMS templates allowed for this event.");
        }

        // Only a template that carries a provider template id can collide on one.
        if (request.getSmsTemplateId() != null && !request.getSmsTemplateId().isBlank()
                && smsTemplateRepository.existsBySmsTemplateIdAndEventId(request.getSmsTemplateId(), eventId)) {
            throw new SmsTemplateAlreadyExistsException(
                    "SMS template with ID '" + request.getSmsTemplateId() + "' already exists for this event");
        }

        TemplateSenderStamp.Stamp stamp = senderStamp.resolveForSave(MessageChannel.SMS,
                event.getOrganization() != null ? event.getOrganization().getId() : null,
                TemplateSenderStamp.fromSmsContent(request.getTemplate(),
                        joinBodyVariables(request.getBodyVariables())));
        TemplateMode renderMode = stamp.renderMode();

        requireContentForMode(renderMode, request.getTemplate(), request.getBodyVariables());
        validateTemplatePlaceholders(request.getTemplate());
        validateBodyVariables(request.getBodyVariables());

        SmsTemplate smsTemplate = SmsTemplate.builder()
                .name(request.getName().toLowerCase().trim())
                .smsTemplateId(request.getSmsTemplateId())
                .senderId(request.getSenderId())
                .template(request.getTemplate())
                .bodyVariables(joinBodyVariables(request.getBodyVariables()))
                .renderMode(renderMode)
                .providerSource(stamp.providerSource())
                .note(request.getNote())
                .event(event)
                .build();

        SmsTemplate savedTemplate = smsTemplateRepository.save(smsTemplate);
        log.info("Successfully created SMS template with ID: {} for event ID: {} by user: {}",
                savedTemplate.getId(), eventId, currentUser.getUsername());

        return SmsTemplateResponse.fromEntity(savedTemplate);
    }

    @Auditable(entityType = AuditEntityType.SMS_TEMPLATE, action = AuditAction.UPDATE)
    @Override
    @Transactional
    public SmsTemplateResponse updateSmsTemplate(Long eventId, Long templateId, UpdateSmsTemplateRequest request, User currentUser) {
        log.info("Updating SMS template ID: {} for event ID: {} by user: {}",
                templateId, eventId, currentUser.getUsername());

        Event updateEvent = validateEventAccess(eventId, currentUser);
        requireTemplateWriteAllowed(updateEvent);

        SmsTemplate smsTemplate = findTemplateOrThrow(templateId, eventId);

        if (smsCampaignRepository.existsBySmsTemplateIdAndStatusIn(
                smsTemplate.getId(), List.of(CampaignStatus.SENDING, CampaignStatus.SENT))) {
            throw new InvalidSmsTemplateException(
                    "You cannot edit this template while it is used in a campaign that is running or completed.");
        }

        if (request.getName() != null && !request.getName().isBlank()) {
            smsTemplate.setName(request.getName().toLowerCase().trim());
        }

        if (request.getSmsTemplateId() != null && !request.getSmsTemplateId().isBlank() &&
                !request.getSmsTemplateId().equals(smsTemplate.getSmsTemplateId())) {
            if (smsTemplateRepository.existsBySmsTemplateIdAndEventIdAndIdNot(request.getSmsTemplateId(), eventId, templateId)) {
                throw new SmsTemplateAlreadyExistsException(
                        "SMS template with ID '" + request.getSmsTemplateId() + "' already exists for this event");
            }
            smsTemplate.setSmsTemplateId(request.getSmsTemplateId());
        }

        // The mode the template was written in governs the edit, not whichever provider is resolved
        // now — a template must not change shape because the organization switched provider.
        TemplateMode renderMode = effectiveRenderMode(smsTemplate);
        requireFieldsAllowedForMode(renderMode, request.getTemplate(), request.getBodyVariables());
        smsTemplate.setRenderMode(renderMode);

        if (request.getTemplate() != null && !request.getTemplate().isBlank()) {
            validateTemplatePlaceholders(request.getTemplate());
            smsTemplate.setTemplate(request.getTemplate());
        }

        if (request.getBodyVariables() != null) {
            validateBodyVariables(request.getBodyVariables());
            smsTemplate.setBodyVariables(joinBodyVariables(request.getBodyVariables()));
        }

        TextUtils.applyIfSent(request.getSenderId(), smsTemplate::setSenderId);
        TextUtils.applyIfSent(request.getNote(), smsTemplate::setNote);

        SmsTemplate updatedTemplate = smsTemplateRepository.saveAndFlush(smsTemplate);
        log.info("Successfully updated SMS template ID: {} for event ID: {} by user: {}",
                updatedTemplate.getId(), eventId, currentUser.getUsername());

        return SmsTemplateResponse.fromEntity(updatedTemplate);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SmsTemplateResponse> getSmsTemplatesByEvent(Long eventId, String search, User currentUser) {
        return doList(eventId, search, currentUser);
    }

    @Override
    @Transactional(readOnly = true)
    public SmsTemplateResponse getSmsTemplateById(Long eventId, Long templateId, User currentUser) {
        log.info("Fetching SMS template ID: {} for event ID: {} by user: {}",
                templateId, eventId, currentUser.getUsername());

        validateEventAccess(eventId, currentUser);

        SmsTemplate smsTemplate = findTemplateOrThrow(templateId, eventId);

        log.info("Successfully fetched SMS template ID: {} by user: {}",
                smsTemplate.getId(), currentUser.getUsername());

        return SmsTemplateResponse.fromEntity(smsTemplate);
    }

    @Override
    @Transactional(readOnly = true)
    public SmsTemplateResponse getSmsTemplateBySmsTemplateId(Long eventId, String smsTemplateId, User currentUser) {
        log.info("Fetching SMS template by DLT ID: {} for event ID: {} by user: {}",
                smsTemplateId, eventId, currentUser.getUsername());

        validateEventAccess(eventId, currentUser);

        SmsTemplate smsTemplate = smsTemplateRepository.findBySmsTemplateIdAndEventId(smsTemplateId, eventId)
                .orElseThrow(SmsTemplateNotFoundException::new);

        log.info("Successfully fetched SMS template by DLT ID: {} by user: {}",
                smsTemplateId, currentUser.getUsername());

        return SmsTemplateResponse.fromEntity(smsTemplate);
    }

    @Auditable(entityType = AuditEntityType.SMS_TEMPLATE, action = AuditAction.DELETE)
    @Override
    @Transactional
    public void deleteSmsTemplate(Long eventId, Long templateId, User currentUser) {
        doDelete(eventId, templateId, currentUser);
    }

    @Override
    protected RuntimeException templateNotFound() {
        return new SmsTemplateNotFoundException();
    }

    @Override
    protected Predicate eventPredicate(Root<SmsTemplate> root, CriteriaBuilder cb, Long eventId) {
        return cb.equal(root.get("event").get("id"), eventId);
    }

    @Override
    protected Predicate searchPredicate(Root<SmsTemplate> root, CriteriaBuilder cb, String search) {
        String pattern = "%" + search.toLowerCase() + "%";
        return cb.or(
                cb.like(root.get("name"), pattern),
                cb.like(root.get("smsTemplateId"), "%" + search + "%")
        );
    }

    @Override
    protected void assertTemplateDeletable(SmsTemplate template) {
        if (smsCampaignRepository.existsBySmsTemplateId(template.getId())) {
            throw new InvalidSmsTemplateException("This template is used by one or more campaigns and cannot be deleted.");
        }
    }

    @Override
    protected SmsTemplateResponse toResponse(SmsTemplate template, Event event) {
        return SmsTemplateResponse.fromEntity(template);
    }

    private void validateTemplatePlaceholders(String template) {
        List<String> invalid = MessageTemplateParser.validatePlaceholders(template, MessageTemplateContext.class);
        if (!invalid.isEmpty()) {
            throw new InvalidSmsTemplateException(
                    "Invalid placeholders in template: " + invalid + ".");
        }
    }

    private TemplateMode effectiveRenderMode(SmsTemplate template) {
        return template.getRenderMode() != null
                ? template.getRenderMode()
                : TemplateSenderStamp.fromSmsContent(template.getTemplate(), template.getBodyVariables());
    }

    private void requireContentForMode(TemplateMode renderMode, String template, List<String> bodyVariables) {
        if (renderMode == TemplateMode.CLIENT_RENDERED && (template == null || template.isBlank())) {
            throw new InvalidSmsTemplateException(
                    "Add the message text — your SMS provider sends the message body itself.");
        }
        requireFieldsAllowedForMode(renderMode, template, bodyVariables);
    }

    /**
     * Rejects only the field the rendering cannot use. A provider-rendered template with no variables
     * stays valid — a registered template can be fully static, with nothing to fill in.
     */
    private void requireFieldsAllowedForMode(TemplateMode renderMode, String template, List<String> bodyVariables) {
        boolean hasBody = template != null && !template.isBlank();
        boolean hasVariables = bodyVariables != null && !bodyVariables.isEmpty();

        if (renderMode == TemplateMode.CLIENT_RENDERED && hasVariables) {
            throw new InvalidSmsTemplateException(
                    "Remove the template variables — your SMS provider expects the full message text instead.");
        }
        if (renderMode == TemplateMode.PROVIDER_RENDERED && hasBody) {
            throw new InvalidSmsTemplateException(
                    "Remove the message text — your SMS provider holds the registered template and expects only the variables.");
        }
    }

    private void validateBodyVariables(List<String> bodyVariables) {
        if (bodyVariables == null || bodyVariables.isEmpty()) {
            return;
        }
        List<String> invalid = MessageTemplateParser.validatePlaceholders(
                String.join(" ", bodyVariables), MessageTemplateContext.class);
        if (!invalid.isEmpty()) {
            throw new InvalidSmsTemplateException(
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
