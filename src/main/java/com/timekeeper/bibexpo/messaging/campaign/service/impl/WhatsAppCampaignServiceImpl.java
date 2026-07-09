package com.timekeeper.bibexpo.messaging.campaign.service.impl;

import com.timekeeper.bibexpo.annotation.Auditable;
import com.timekeeper.bibexpo.aspect.AuditContextHolder;
import com.timekeeper.bibexpo.exception.EventNotFoundException;
import com.timekeeper.bibexpo.model.entity.Event;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.model.enums.AuditAction;
import com.timekeeper.bibexpo.model.enums.AuditEntityType;
import com.timekeeper.bibexpo.messaging.campaign.model.enums.CampaignStatus;
import com.timekeeper.bibexpo.messaging.campaign.model.enums.CampaignTargetFilter;
import com.timekeeper.bibexpo.messaging.campaign.model.enums.CampaignTriggerType;
import com.timekeeper.bibexpo.repository.EventRepository;
import com.timekeeper.bibexpo.service.validator.EventAccessValidator;
import com.timekeeper.bibexpo.messaging.campaign.exception.InvalidWhatsAppCampaignException;
import com.timekeeper.bibexpo.messaging.campaign.exception.WhatsAppCampaignAlreadyActiveException;
import com.timekeeper.bibexpo.messaging.campaign.exception.WhatsAppCampaignNotFoundException;
import com.timekeeper.bibexpo.messaging.campaign.exception.WhatsAppTemplateNotFoundException;
import com.timekeeper.bibexpo.messaging.campaign.model.dto.request.CreateWhatsAppCampaignRequest;
import com.timekeeper.bibexpo.messaging.campaign.model.dto.request.UpdateWhatsAppCampaignRequest;
import com.timekeeper.bibexpo.messaging.campaign.model.dto.response.WhatsAppCampaignResponse;
import com.timekeeper.bibexpo.messaging.campaign.model.entity.WhatsAppCampaign;
import com.timekeeper.bibexpo.messaging.campaign.model.entity.WhatsAppTemplate;
import com.timekeeper.bibexpo.messaging.campaign.repository.WhatsAppCampaignRepository;
import com.timekeeper.bibexpo.messaging.campaign.repository.WhatsAppTemplateRepository;
import com.timekeeper.bibexpo.messaging.campaign.service.WhatsAppCampaignService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class WhatsAppCampaignServiceImpl implements WhatsAppCampaignService {

    private static final int MAX_CAMPAIGNS_PER_EVENT = 20;
    private static final int DISARM_CUTOFF_SECONDS = 30;
    private static final int MIN_SCHEDULE_AHEAD_MINUTES = 3;

    private final WhatsAppCampaignRepository campaignRepository;
    private final WhatsAppTemplateRepository templateRepository;
    private final EventRepository eventRepository;
    private final EventAccessValidator eventAccessValidator;

    @Auditable(entityType = AuditEntityType.WHATSAPP_CAMPAIGN, action = AuditAction.CREATE)
    @Override
    @Transactional
    public WhatsAppCampaignResponse createCampaign(Long eventId, CreateWhatsAppCampaignRequest request, User currentUser) {
        log.info("Creating WhatsApp campaign for event ID: {} by user: {}", eventId, currentUser.getUsername());

        Event event = validateEventAccess(eventId, currentUser);

        if (campaignRepository.countByEventId(eventId) >= MAX_CAMPAIGNS_PER_EVENT) {
            throw new InvalidWhatsAppCampaignException("An event can have a maximum of 20 WhatsApp campaigns.");
        }

        WhatsAppTemplate template = templateRepository.findByIdAndEventId(request.getWhatsAppTemplateId(), eventId)
                .orElseThrow(WhatsAppTemplateNotFoundException::new);

        WhatsAppCampaign campaign = WhatsAppCampaign.builder()
                .name(request.getName().trim())
                .eventId(eventId)
                .organizationId(event.getOrganization() != null ? event.getOrganization().getId() : null)
                .whatsAppTemplate(template)
                .status(CampaignStatus.DRAFT)
                .build();

        if (request.getTriggerType() != null) {
            applyArm(campaign, request.getTriggerType(), request.getTargetFilter(),
                    request.getScheduledDate(), request.getScheduledTime(), event);
        }

        WhatsAppCampaign saved = campaignRepository.save(campaign);
        log.info("Successfully created WhatsApp campaign ID: {} status: {} for event ID: {} by user: {}",
                saved.getId(), saved.getStatus(), eventId, currentUser.getUsername());

        return WhatsAppCampaignResponse.fromEntity(saved, event);
    }

    @Auditable(entityType = AuditEntityType.WHATSAPP_CAMPAIGN, action = AuditAction.UPDATE)
    @Override
    @Transactional
    public WhatsAppCampaignResponse updateCampaign(Long eventId, Long campaignId, UpdateWhatsAppCampaignRequest request, User currentUser) {
        log.info("Updating WhatsApp campaign ID: {} for event ID: {} by user: {}", campaignId, eventId, currentUser.getUsername());

        Event event = validateEventAccess(eventId, currentUser);

        WhatsAppCampaign campaign = findCampaignOrThrow(campaignId, eventId);

        if (campaign.getStatus() != CampaignStatus.DRAFT) {
            throw new InvalidWhatsAppCampaignException("Only draft campaigns can be modified. Disarm the campaign first.");
        }

        if (request.getName() != null && !request.getName().isBlank()) {
            campaign.setName(request.getName().trim());
        }

        if (request.getWhatsAppTemplateId() != null) {
            WhatsAppTemplate template = templateRepository.findByIdAndEventId(request.getWhatsAppTemplateId(), eventId)
                    .orElseThrow(WhatsAppTemplateNotFoundException::new);
            campaign.setWhatsAppTemplate(template);
        }

        if (request.getTriggerType() != null) {
            applyArm(campaign, request.getTriggerType(), request.getTargetFilter(),
                    request.getScheduledDate(), request.getScheduledTime(), event);
        }

        WhatsAppCampaign updated = campaignRepository.saveAndFlush(campaign);
        log.info("Successfully updated WhatsApp campaign ID: {} status: {} by user: {}",
                updated.getId(), updated.getStatus(), currentUser.getUsername());

        return WhatsAppCampaignResponse.fromEntity(updated, event);
    }

    @Override
    @Transactional(readOnly = true)
    public List<WhatsAppCampaignResponse> getCampaignsByEvent(Long eventId, User currentUser) {
        log.info("Fetching WhatsApp campaigns for event ID: {} by user: {}", eventId, currentUser.getUsername());

        Event event = validateEventAccess(eventId, currentUser);

        return campaignRepository.findAll(
                (root, query, cb) -> cb.equal(root.get("eventId"), eventId)
        ).stream().map(campaign -> WhatsAppCampaignResponse.fromEntity(campaign, event)).toList();
    }

    @Auditable(entityType = AuditEntityType.WHATSAPP_CAMPAIGN, action = AuditAction.STATUS_CHANGE)
    @Override
    @Transactional
    public WhatsAppCampaignResponse disarmCampaign(Long eventId, Long campaignId, User currentUser) {
        log.info("Disarming WhatsApp campaign ID: {} for event ID: {} by user: {}", campaignId, eventId, currentUser.getUsername());

        Event event = validateEventAccess(eventId, currentUser);

        WhatsAppCampaign campaign = findCampaignOrThrow(campaignId, eventId);

        if (campaign.getStatus() != CampaignStatus.ACTIVE) {
            throw new InvalidWhatsAppCampaignException("Only active campaigns can be disarmed.");
        }

        if (campaign.getTriggerType() == CampaignTriggerType.SCHEDULED && campaign.getScheduledAt() != null) {
            Instant cutoff = campaign.getScheduledAt().minusSeconds(DISARM_CUTOFF_SECONDS);
            if (Instant.now().isAfter(cutoff)) {
                throw new InvalidWhatsAppCampaignException(
                        "Scheduled campaigns cannot be disarmed within " + DISARM_CUTOFF_SECONDS + " seconds of the scheduled time.");
            }
        }

        campaign.setStatus(CampaignStatus.DRAFT);
        campaign.setTriggerType(null);
        campaign.setTargetFilter(null);
        campaign.setScheduledAt(null);

        WhatsAppCampaign updated = campaignRepository.saveAndFlush(campaign);
        log.info("Successfully disarmed WhatsApp campaign ID: {} by user: {}", campaignId, currentUser.getUsername());

        return WhatsAppCampaignResponse.fromEntity(updated, event);
    }

    @Auditable(entityType = AuditEntityType.WHATSAPP_CAMPAIGN, action = AuditAction.DELETE)
    @Override
    @Transactional
    public void deleteCampaign(Long eventId, Long campaignId, User currentUser) {
        log.info("Deleting WhatsApp campaign ID: {} for event ID: {} by user: {}", campaignId, eventId, currentUser.getUsername());

        validateEventAccess(eventId, currentUser);

        WhatsAppCampaign campaign = findCampaignOrThrow(campaignId, eventId);

        if (campaign.getStatus() != CampaignStatus.DRAFT) {
            throw new InvalidWhatsAppCampaignException("Only draft campaigns can be deleted.");
        }

        AuditContextHolder.setEntityLabel(campaign.getName());
        AuditContextHolder.setOrganizationId(campaign.getOrganizationId());

        campaignRepository.delete(campaign);
        log.info("Successfully deleted WhatsApp campaign ID: {} by user: {}", campaignId, currentUser.getUsername());
    }

    private void applyArm(WhatsAppCampaign campaign, CampaignTriggerType triggerType,
                          CampaignTargetFilter targetFilter, String scheduledDate, String scheduledTime,
                          Event event) {
        if (targetFilter == null) {
            throw new InvalidWhatsAppCampaignException("Target filter is required when arming a campaign.");
        }

        Instant scheduledAt = null;
        if (triggerType == CampaignTriggerType.SCHEDULED) {
            if (scheduledDate == null || scheduledTime == null) {
                throw new InvalidWhatsAppCampaignException("Scheduled date and time are required for a scheduled campaign.");
            }
            if (event.getTimezone() == null) {
                throw new InvalidWhatsAppCampaignException("The event does not have a timezone configured.");
            }
            try {
                scheduledAt = ZonedDateTime.of(
                        LocalDate.parse(scheduledDate),
                        LocalTime.parse(scheduledTime),
                        ZoneId.of(event.getTimezone())
                ).toInstant();
            } catch (DateTimeParseException e) {
                throw new InvalidWhatsAppCampaignException("Invalid scheduled date or time format. Use yyyy-MM-dd and HH:mm.");
            }
            Instant minAllowed = Instant.now().plus(MIN_SCHEDULE_AHEAD_MINUTES, ChronoUnit.MINUTES);
            if (!scheduledAt.isAfter(minAllowed)) {
                throw new InvalidWhatsAppCampaignException(
                        "Scheduled time must be at least " + MIN_SCHEDULE_AHEAD_MINUTES + " minutes in the future.");
            }
        }

        if (triggerType == CampaignTriggerType.AUTO_BIB_COLLECTED) {
            validateNoDuplicateAutoBibCollected(event.getId());
        }

        campaign.setTriggerType(triggerType);
        campaign.setTargetFilter(targetFilter);
        campaign.setScheduledAt(scheduledAt);
        campaign.setStatus(CampaignStatus.ACTIVE);
    }

    private void validateNoDuplicateAutoBibCollected(Long eventId) {
        boolean exists = campaignRepository.existsByEventIdAndTriggerTypeAndStatus(
                eventId,
                CampaignTriggerType.AUTO_BIB_COLLECTED,
                CampaignStatus.ACTIVE
        );
        if (exists) {
            throw new WhatsAppCampaignAlreadyActiveException(
                    "An active bib collection campaign already exists for this event. Please disarm it before arming a new one.");
        }
    }

    private WhatsAppCampaign findCampaignOrThrow(Long campaignId, Long eventId) {
        return campaignRepository.findByIdAndEventId(campaignId, eventId)
                .orElseThrow(WhatsAppCampaignNotFoundException::new);
    }

    private Event validateEventAccess(Long eventId, User currentUser) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(EventNotFoundException::new);

        eventAccessValidator.validateUserAuthorizationForEvent(currentUser, event);

        return event;
    }
}
