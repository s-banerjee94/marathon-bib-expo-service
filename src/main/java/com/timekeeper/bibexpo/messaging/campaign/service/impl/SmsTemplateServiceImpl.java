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

    public SmsTemplateServiceImpl(SmsTemplateRepository smsTemplateRepository,
                                  SmsCampaignRepository smsCampaignRepository,
                                  EventRepository eventRepository,
                                  EventAccessValidator eventAccessValidator,
                                  EventLimitRepository eventLimitRepository,
                                  EventOperationGuard eventOperationGuard) {
        super("SMS", smsTemplateRepository, eventRepository, eventAccessValidator, eventOperationGuard);
        this.smsTemplateRepository = smsTemplateRepository;
        this.smsCampaignRepository = smsCampaignRepository;
        this.eventLimitRepository = eventLimitRepository;
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

        if (smsTemplateRepository.existsBySmsTemplateIdAndEventId(request.getSmsTemplateId(), eventId)) {
            throw new SmsTemplateAlreadyExistsException(
                    "SMS template with ID '" + request.getSmsTemplateId() + "' already exists for this event");
        }

        validateTemplatePlaceholders(request.getTemplate());

        SmsTemplate smsTemplate = SmsTemplate.builder()
                .name(request.getName().toLowerCase().trim())
                .smsTemplateId(request.getSmsTemplateId())
                .template(request.getTemplate())
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

        if (request.getTemplate() != null && !request.getTemplate().isBlank()) {
            validateTemplatePlaceholders(request.getTemplate());
            smsTemplate.setTemplate(request.getTemplate());
        }

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
}
