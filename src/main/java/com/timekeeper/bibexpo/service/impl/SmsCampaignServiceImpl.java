package com.timekeeper.bibexpo.service.impl;

import com.timekeeper.bibexpo.exception.*;
import com.timekeeper.bibexpo.model.dto.request.CreateSmsCampaignRequest;
import com.timekeeper.bibexpo.model.dto.request.UpdateSmsCampaignRequest;
import com.timekeeper.bibexpo.model.dto.response.SmsCampaignResponse;
import com.timekeeper.bibexpo.model.entity.Event;
import com.timekeeper.bibexpo.model.entity.SmsCampaign;
import com.timekeeper.bibexpo.model.entity.SmsTemplate;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.model.enums.SmsCampaignStatus;
import com.timekeeper.bibexpo.model.enums.SmsCampaignTargetFilter;
import com.timekeeper.bibexpo.model.enums.SmsCampaignTriggerType;
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

    private static final int MAX_CAMPAIGNS_PER_EVENT = 20;
    private static final int DISARM_CUTOFF_SECONDS = 30;
    private static final int MIN_SCHEDULE_AHEAD_MINUTES = 3;

    private final SmsCampaignRepository smsCampaignRepository;
    private final SmsTemplateRepository smsTemplateRepository;
    private final EventRepository eventRepository;
    private final EventAccessValidator eventAccessValidator;

    @Override
    @Transactional
    public SmsCampaignResponse createCampaign(Long eventId, CreateSmsCampaignRequest request, User currentUser) {
        log.info("Creating SMS campaign for event ID: {} by user: {}", eventId, currentUser.getUsername());

        Event event = validateEventAccess(eventId, currentUser);

        if (smsCampaignRepository.countByEventId(eventId) >= MAX_CAMPAIGNS_PER_EVENT) {
            throw new InvalidSmsCampaignException("An event can have a maximum of 20 SMS campaigns.");
        }

        SmsTemplate template = smsTemplateRepository.findByIdAndEventId(request.getSmsTemplateId(), eventId)
                .orElseThrow(SmsTemplateNotFoundException::new);

        SmsCampaign campaign = SmsCampaign.builder()
                .name(request.getName().trim())
                .event(event)
                .smsTemplate(template)
                .status(SmsCampaignStatus.DRAFT)
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

    @Override
    @Transactional
    public SmsCampaignResponse updateCampaign(Long eventId, Long campaignId, UpdateSmsCampaignRequest request, User currentUser) {
        log.info("Updating SMS campaign ID: {} for event ID: {} by user: {}", campaignId, eventId, currentUser.getUsername());

        Event event = validateEventAccess(eventId, currentUser);

        SmsCampaign campaign = findCampaignOrThrow(campaignId, eventId);

        if (campaign.getStatus() != SmsCampaignStatus.DRAFT) {
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

    @Override
    @Transactional
    public SmsCampaignResponse disarmCampaign(Long eventId, Long campaignId, User currentUser) {
        log.info("Disarming SMS campaign ID: {} for event ID: {} by user: {}", campaignId, eventId, currentUser.getUsername());

        validateEventAccess(eventId, currentUser);

        SmsCampaign campaign = findCampaignOrThrow(campaignId, eventId);

        if (campaign.getStatus() != SmsCampaignStatus.ACTIVE) {
            throw new InvalidSmsCampaignException("Only active campaigns can be disarmed.");
        }

        if (campaign.getTriggerType() == SmsCampaignTriggerType.SCHEDULED && campaign.getScheduledAt() != null) {
            Instant cutoff = campaign.getScheduledAt().minusSeconds(DISARM_CUTOFF_SECONDS);
            if (Instant.now().isAfter(cutoff)) {
                throw new InvalidSmsCampaignException(
                        "Scheduled campaigns cannot be disarmed within " + DISARM_CUTOFF_SECONDS + " seconds of the scheduled time.");
            }
        }

        campaign.setStatus(SmsCampaignStatus.DRAFT);
        campaign.setTriggerType(null);
        campaign.setTargetFilter(null);
        campaign.setScheduledAt(null);

        SmsCampaign updated = smsCampaignRepository.saveAndFlush(campaign);
        log.info("Successfully disarmed SMS campaign ID: {} by user: {}", campaignId, currentUser.getUsername());

        return SmsCampaignResponse.fromEntity(updated);
    }

    @Override
    @Transactional
    public void deleteCampaign(Long eventId, Long campaignId, User currentUser) {
        log.info("Deleting SMS campaign ID: {} for event ID: {} by user: {}", campaignId, eventId, currentUser.getUsername());

        validateEventAccess(eventId, currentUser);

        SmsCampaign campaign = findCampaignOrThrow(campaignId, eventId);

        if (campaign.getStatus() != SmsCampaignStatus.DRAFT) {
            throw new InvalidSmsCampaignException("Only draft campaigns can be deleted.");
        }

        smsCampaignRepository.delete(campaign);
        log.info("Successfully deleted SMS campaign ID: {} by user: {}", campaignId, currentUser.getUsername());
    }

    private void applyArm(SmsCampaign campaign, SmsCampaignTriggerType triggerType,
                           SmsCampaignTargetFilter targetFilter, String scheduledDate, String scheduledTime,
                           Event event) {
        if (targetFilter == null) {
            throw new InvalidSmsCampaignException("Target filter is required when arming a campaign.");
        }

        Instant scheduledAt = null;
        if (triggerType == SmsCampaignTriggerType.SCHEDULED) {
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

        if (triggerType == SmsCampaignTriggerType.AUTO_BIB_COLLECTED) {
            validateNoDuplicateAutoBibCollected(event.getId());
        }

        campaign.setTriggerType(triggerType);
        campaign.setTargetFilter(targetFilter);
        campaign.setScheduledAt(scheduledAt);
        campaign.setStatus(SmsCampaignStatus.ACTIVE);
    }

    private void validateNoDuplicateAutoBibCollected(Long eventId) {
        boolean exists = smsCampaignRepository.existsByEventIdAndTriggerTypeAndStatus(
                eventId,
                SmsCampaignTriggerType.AUTO_BIB_COLLECTED,
                SmsCampaignStatus.ACTIVE
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
