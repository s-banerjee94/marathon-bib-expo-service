package com.timekeeper.bibexpo.service.impl;

import com.timekeeper.bibexpo.annotation.Auditable;
import com.timekeeper.bibexpo.aspect.AuditContextHolder;
import com.timekeeper.bibexpo.exception.*;
import com.timekeeper.bibexpo.model.enums.AuditAction;
import com.timekeeper.bibexpo.model.enums.AuditEntityType;
import com.timekeeper.bibexpo.model.dto.request.CreateSmsCampaignRequest;
import com.timekeeper.bibexpo.model.dto.request.UpdateSmsCampaignRequest;
import com.timekeeper.bibexpo.model.dto.response.SmsCampaignResponse;
import com.timekeeper.bibexpo.model.entity.Event;
import com.timekeeper.bibexpo.model.entity.EventLimit;
import com.timekeeper.bibexpo.model.entity.SmsCampaign;
import com.timekeeper.bibexpo.model.entity.SmsTemplate;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.model.enums.CampaignStatus;
import com.timekeeper.bibexpo.model.enums.CampaignTargetFilter;
import com.timekeeper.bibexpo.model.enums.CampaignTriggerType;
import com.timekeeper.bibexpo.model.enums.EventOperation;
import com.timekeeper.bibexpo.repository.EventLimitRepository;
import com.timekeeper.bibexpo.service.validator.EventOperationGuard;
import com.timekeeper.bibexpo.repository.EventRepository;
import com.timekeeper.bibexpo.repository.SmsCampaignRepository;
import com.timekeeper.bibexpo.repository.SmsTemplateRepository;
import com.timekeeper.bibexpo.service.SmsCampaignService;
import com.timekeeper.bibexpo.service.validator.EventAccessValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SmsCampaignServiceImpl implements SmsCampaignService {

    private static final int DISARM_CUTOFF_SECONDS = 30;
    private static final int MIN_SCHEDULE_AHEAD_MINUTES = 3;

    private final SmsCampaignRepository smsCampaignRepository;
    private final SmsTemplateRepository smsTemplateRepository;
    private final EventRepository eventRepository;
    private final EventAccessValidator eventAccessValidator;
    private final EventLimitRepository eventLimitRepository;
    private final EventOperationGuard eventOperationGuard;

    @Auditable(entityType = AuditEntityType.SMS_CAMPAIGN, action = AuditAction.CREATE)
    @Override
    @Transactional
    public SmsCampaignResponse createCampaign(Long eventId, CreateSmsCampaignRequest request, User currentUser) {
        log.info("Creating SMS campaign for event ID: {} by user: {}", eventId, currentUser.getUsername());

        Event event = validateEventAccess(eventId, currentUser);
        eventOperationGuard.requireAllowed(event, EventOperation.CAMPAIGN_WRITE);

        EventLimit limits = eventLimitRepository.findByEventId(eventId)
                .orElseGet(() -> EventLimit.builder().build());
        if (smsCampaignRepository.countByEventId(eventId) >= limits.getMaxSmsCampaigns()) {
            throw new EventLimitExceededException("You have reached the maximum number of SMS campaigns allowed for this event.");
        }

        SmsTemplate template = smsTemplateRepository.findByIdAndEventId(request.getSmsTemplateId(), eventId)
                .orElseThrow(SmsTemplateNotFoundException::new);

        SmsCampaign campaign = SmsCampaign.builder()
                .name(request.getName().trim())
                .event(event)
                .smsTemplate(template)
                .status(CampaignStatus.DRAFT)
                .build();

        if (request.getTriggerType() != null) {
            applyArm(campaign, request.getTriggerType(), request.getTargetFilter(),
                    request.getScheduledDate(), request.getScheduledTime(), event);
        }

        SmsCampaign saved = smsCampaignRepository.save(campaign);
        log.info("Successfully created SMS campaign ID: {} status: {} for event ID: {} by user: {}",
                saved.getId(), saved.getStatus(), eventId, currentUser.getUsername());

        return SmsCampaignResponse.fromEntity(saved);
    }

    @Auditable(entityType = AuditEntityType.SMS_CAMPAIGN, action = AuditAction.UPDATE)
    @Override
    @Transactional
    public SmsCampaignResponse updateCampaign(Long eventId, Long campaignId, UpdateSmsCampaignRequest request, User currentUser) {
        log.info("Updating SMS campaign ID: {} for event ID: {} by user: {}", campaignId, eventId, currentUser.getUsername());

        Event event = validateEventAccess(eventId, currentUser);
        eventOperationGuard.requireAllowed(event, EventOperation.CAMPAIGN_WRITE);

        SmsCampaign campaign = findCampaignOrThrow(campaignId, eventId);

        if (campaign.getStatus() != CampaignStatus.DRAFT) {
            throw new InvalidSmsCampaignException("Only draft campaigns can be modified. Disarm the campaign first.");
        }

        if (request.getName() != null && !request.getName().isBlank()) {
            campaign.setName(request.getName().trim());
        }

        if (request.getSmsTemplateId() != null) {
            SmsTemplate template = smsTemplateRepository.findByIdAndEventId(request.getSmsTemplateId(), eventId)
                    .orElseThrow(SmsTemplateNotFoundException::new);
            campaign.setSmsTemplate(template);
        }

        if (request.getTriggerType() != null) {
            applyArm(campaign, request.getTriggerType(), request.getTargetFilter(),
                    request.getScheduledDate(), request.getScheduledTime(), event);
        }

        SmsCampaign updated = smsCampaignRepository.saveAndFlush(campaign);
        log.info("Successfully updated SMS campaign ID: {} status: {} by user: {}",
                updated.getId(), updated.getStatus(), currentUser.getUsername());

        return SmsCampaignResponse.fromEntity(updated);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SmsCampaignResponse> getCampaignsByEvent(Long eventId, User currentUser) {
        log.info("Fetching SMS campaigns for event ID: {} by user: {}", eventId, currentUser.getUsername());

        validateEventAccess(eventId, currentUser);

        return smsCampaignRepository.findAll(
                (root, query, cb) -> cb.equal(root.get("event").get("id"), eventId)
        ).stream().map(SmsCampaignResponse::fromEntity).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public SmsCampaignResponse getCampaignById(Long eventId, Long campaignId, User currentUser) {
        log.info("Fetching SMS campaign ID: {} for event ID: {} by user: {}", campaignId, eventId, currentUser.getUsername());

        validateEventAccess(eventId, currentUser);

        return SmsCampaignResponse.fromEntity(findCampaignOrThrow(campaignId, eventId));
    }

    @Auditable(entityType = AuditEntityType.SMS_CAMPAIGN, action = AuditAction.STATUS_CHANGE)
    @Override
    @Transactional
    public SmsCampaignResponse disarmCampaign(Long eventId, Long campaignId, User currentUser) {
        log.info("Disarming SMS campaign ID: {} for event ID: {} by user: {}", campaignId, eventId, currentUser.getUsername());

        Event disarmEvent = validateEventAccess(eventId, currentUser);
        eventOperationGuard.requireAllowed(disarmEvent, EventOperation.CAMPAIGN_WRITE);

        SmsCampaign campaign = findCampaignOrThrow(campaignId, eventId);

        if (campaign.getStatus() != CampaignStatus.ACTIVE) {
            throw new InvalidSmsCampaignException("Only active campaigns can be disarmed.");
        }

        if (campaign.getTriggerType() == CampaignTriggerType.SCHEDULED && campaign.getScheduledAt() != null) {
            Instant cutoff = campaign.getScheduledAt().minusSeconds(DISARM_CUTOFF_SECONDS);
            if (Instant.now().isAfter(cutoff)) {
                throw new InvalidSmsCampaignException(
                        "Scheduled campaigns cannot be disarmed within " + DISARM_CUTOFF_SECONDS + " seconds of the scheduled time.");
            }
        }

        campaign.setStatus(CampaignStatus.DRAFT);
        campaign.setTriggerType(null);
        campaign.setTargetFilter(null);
        campaign.setScheduledAt(null);

        SmsCampaign updated = smsCampaignRepository.saveAndFlush(campaign);
        log.info("Successfully disarmed SMS campaign ID: {} by user: {}", campaignId, currentUser.getUsername());

        return SmsCampaignResponse.fromEntity(updated);
    }

    @Auditable(entityType = AuditEntityType.SMS_CAMPAIGN, action = AuditAction.DELETE)
    @Override
    @Transactional
    public void deleteCampaign(Long eventId, Long campaignId, User currentUser) {
        log.info("Deleting SMS campaign ID: {} for event ID: {} by user: {}", campaignId, eventId, currentUser.getUsername());

        Event deleteEvent = validateEventAccess(eventId, currentUser);
        eventOperationGuard.requireAllowed(deleteEvent, EventOperation.CAMPAIGN_WRITE);

        SmsCampaign campaign = findCampaignOrThrow(campaignId, eventId);

        if (campaign.getStatus() != CampaignStatus.DRAFT) {
            throw new InvalidSmsCampaignException("Only draft campaigns can be deleted.");
        }

        Long orgId = campaign.getEvent() != null && campaign.getEvent().getOrganization() != null
                ? campaign.getEvent().getOrganization().getId() : null;
        AuditContextHolder.setEntityLabel(campaign.getName());
        AuditContextHolder.setOrganizationId(orgId);

        smsCampaignRepository.delete(campaign);
        log.info("Successfully deleted SMS campaign ID: {} by user: {}", campaignId, currentUser.getUsername());
    }

    private void applyArm(SmsCampaign campaign, CampaignTriggerType triggerType,
                           CampaignTargetFilter targetFilter, String scheduledDate, String scheduledTime,
                           Event event) {
        if (targetFilter == null) {
            throw new InvalidSmsCampaignException("Target filter is required when arming a campaign.");
        }

        Instant scheduledAt = null;
        if (triggerType == CampaignTriggerType.SCHEDULED) {
            if (scheduledDate == null || scheduledTime == null) {
                throw new InvalidSmsCampaignException("Scheduled date and time are required for a scheduled campaign.");
            }
            if (event.getTimezone() == null) {
                throw new InvalidSmsCampaignException("The event does not have a timezone configured.");
            }
            try {
                scheduledAt = ZonedDateTime.of(
                        LocalDate.parse(scheduledDate),
                        LocalTime.parse(scheduledTime),
                        ZoneId.of(event.getTimezone())
                ).toInstant();
            } catch (DateTimeParseException e) {
                throw new InvalidSmsCampaignException("Invalid scheduled date or time format. Use yyyy-MM-dd and HH:mm.");
            }
            Instant minAllowed = Instant.now().plus(MIN_SCHEDULE_AHEAD_MINUTES, ChronoUnit.MINUTES);
            if (!scheduledAt.isAfter(minAllowed)) {
                throw new InvalidSmsCampaignException(
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
        boolean exists = smsCampaignRepository.existsByEventIdAndTriggerTypeAndStatus(
                eventId,
                CampaignTriggerType.AUTO_BIB_COLLECTED,
                CampaignStatus.ACTIVE
        );
        if (exists) {
            throw new SmsCampaignAlreadyActiveException(
                    "An active bib collection campaign already exists for this event. Please disarm it before arming a new one.");
        }
    }

    private SmsCampaign findCampaignOrThrow(Long campaignId, Long eventId) {
        return smsCampaignRepository.findByIdAndEventId(campaignId, eventId)
                .orElseThrow(SmsCampaignNotFoundException::new);
    }

    private Event validateEventAccess(Long eventId, User currentUser) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(EventNotFoundException::new);

        eventAccessValidator.validateUserAuthorizationForEvent(currentUser, event);

        return event;
    }
}
