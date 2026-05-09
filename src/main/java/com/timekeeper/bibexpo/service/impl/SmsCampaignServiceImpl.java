package com.timekeeper.bibexpo.service.impl;

import com.timekeeper.bibexpo.exception.*;
import com.timekeeper.bibexpo.model.dto.request.CreateSmsCampaignRequest;
import com.timekeeper.bibexpo.model.dto.request.UpdateSmsCampaignRequest;
import com.timekeeper.bibexpo.model.dto.response.SmsCampaignResponse;
import com.timekeeper.bibexpo.model.entity.Event;
import com.timekeeper.bibexpo.model.entity.SmsCampaign;
import com.timekeeper.bibexpo.model.entity.SmsTemplate;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.model.entity.UserRole;
import com.timekeeper.bibexpo.model.enums.SmsCampaignStatus;
import com.timekeeper.bibexpo.model.enums.SmsCampaignTriggerType;
import com.timekeeper.bibexpo.repository.EventRepository;
import com.timekeeper.bibexpo.repository.SmsCampaignRepository;
import com.timekeeper.bibexpo.repository.SmsTemplateRepository;
import com.timekeeper.bibexpo.service.SmsCampaignService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SmsCampaignServiceImpl implements SmsCampaignService {

    private final SmsCampaignRepository smsCampaignRepository;
    private final SmsTemplateRepository smsTemplateRepository;
    private final EventRepository eventRepository;

    @Override
    @Transactional
    public SmsCampaignResponse createCampaign(Long eventId, CreateSmsCampaignRequest request, User currentUser) {
        log.info("Creating SMS campaign for event ID: {} by user: {}", eventId, currentUser.getUsername());

        Event event = validateEventAccess(eventId, currentUser);

        SmsTemplate template = smsTemplateRepository.findByIdAndEventId(request.getSmsTemplateId(), eventId)
                .orElseThrow(() -> new SmsTemplateNotFoundException(
                        "SMS template not found with ID: " + request.getSmsTemplateId() + " for event: " + eventId));

        if (request.getTriggerType() == SmsCampaignTriggerType.SCHEDULED && request.getScheduledAt() == null) {
            throw new InvalidSmsCampaignException("Scheduled date and time is required for a scheduled campaign.");
        }

        if (request.getTriggerType() == SmsCampaignTriggerType.AUTO_BIB_COLLECTED) {
            validateNoDuplicateAutoBibCollected(eventId, null);
        }

        SmsCampaignStatus initialStatus = request.getTriggerType() == SmsCampaignTriggerType.AUTO_BIB_COLLECTED
                ? SmsCampaignStatus.ACTIVE
                : SmsCampaignStatus.DRAFT;

        SmsCampaign campaign = SmsCampaign.builder()
                .name(request.getName().trim())
                .event(event)
                .smsTemplate(template)
                .triggerType(request.getTriggerType())
                .targetFilter(request.getTargetFilter())
                .scheduledAt(request.getTriggerType() == SmsCampaignTriggerType.SCHEDULED ? request.getScheduledAt() : null)
                .status(initialStatus)
                .build();

        SmsCampaign saved = smsCampaignRepository.save(campaign);
        log.info("Successfully created SMS campaign ID: {} for event ID: {} by user: {}",
                saved.getId(), eventId, currentUser.getUsername());

        return SmsCampaignResponse.fromEntity(saved);
    }

    @Override
    @Transactional
    public SmsCampaignResponse updateCampaign(Long eventId, Long campaignId, UpdateSmsCampaignRequest request, User currentUser) {
        log.info("Updating SMS campaign ID: {} for event ID: {} by user: {}", campaignId, eventId, currentUser.getUsername());

        validateEventAccess(eventId, currentUser);

        SmsCampaign campaign = findCampaignOrThrow(campaignId, eventId);

        if (campaign.getStatus() == SmsCampaignStatus.SENT) {
            throw new InvalidSmsCampaignException("You cannot edit a campaign that has already been sent.");
        }

        if (request.getName() != null && !request.getName().isBlank()) {
            campaign.setName(request.getName().trim());
        }

        if (request.getSmsTemplateId() != null) {
            SmsTemplate template = smsTemplateRepository.findByIdAndEventId(request.getSmsTemplateId(), eventId)
                    .orElseThrow(() -> new SmsTemplateNotFoundException(
                            "SMS template not found with ID: " + request.getSmsTemplateId() + " for event: " + eventId));
            campaign.setSmsTemplate(template);
        }

        if (request.getTriggerType() != null && request.getTriggerType() != campaign.getTriggerType()) {
            if (request.getTriggerType() == SmsCampaignTriggerType.AUTO_BIB_COLLECTED) {
                validateNoDuplicateAutoBibCollected(eventId, campaignId);
                campaign.setStatus(SmsCampaignStatus.ACTIVE);
            } else {
                campaign.setStatus(SmsCampaignStatus.DRAFT);
            }
            campaign.setTriggerType(request.getTriggerType());
        }

        if (request.getTargetFilter() != null) {
            campaign.setTargetFilter(request.getTargetFilter());
        }

        SmsCampaignTriggerType effectiveTriggerType = campaign.getTriggerType();
        if (effectiveTriggerType == SmsCampaignTriggerType.SCHEDULED) {
            if (request.getScheduledAt() != null) {
                campaign.setScheduledAt(request.getScheduledAt());
            }
            if (campaign.getScheduledAt() == null) {
                throw new InvalidSmsCampaignException("Scheduled date and time is required for a scheduled campaign.");
            }
        } else {
            campaign.setScheduledAt(null);
        }

        SmsCampaign updated = smsCampaignRepository.save(campaign);
        log.info("Successfully updated SMS campaign ID: {} for event ID: {} by user: {}",
                updated.getId(), eventId, currentUser.getUsername());

        return SmsCampaignResponse.fromEntity(updated);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<SmsCampaignResponse> getCampaignsByEvent(Long eventId, Pageable pageable, User currentUser) {
        log.info("Fetching SMS campaigns for event ID: {} by user: {}", eventId, currentUser.getUsername());

        validateEventAccess(eventId, currentUser);

        return smsCampaignRepository.findAll(
                (root, query, cb) -> cb.equal(root.get("event").get("id"), eventId),
                pageable
        ).map(SmsCampaignResponse::fromEntity);
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
    public SmsCampaignResponse deactivateCampaign(Long eventId, Long campaignId, User currentUser) {
        log.info("Deactivating SMS campaign ID: {} for event ID: {} by user: {}", campaignId, eventId, currentUser.getUsername());

        validateEventAccess(eventId, currentUser);

        SmsCampaign campaign = findCampaignOrThrow(campaignId, eventId);

        if (campaign.getStatus() != SmsCampaignStatus.ACTIVE) {
            throw new InvalidSmsCampaignException("Only active campaigns can be deactivated.");
        }

        campaign.setStatus(SmsCampaignStatus.DRAFT);
        SmsCampaign updated = smsCampaignRepository.save(campaign);
        log.info("Successfully deactivated SMS campaign ID: {} by user: {}", campaignId, currentUser.getUsername());

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

    private void validateNoDuplicateAutoBibCollected(Long eventId, Long excludeCampaignId) {
        boolean exists = smsCampaignRepository.existsByEventIdAndTriggerTypeAndStatusIn(
                eventId,
                SmsCampaignTriggerType.AUTO_BIB_COLLECTED,
                List.of(SmsCampaignStatus.DRAFT, SmsCampaignStatus.ACTIVE)
        );
        if (exists) {
            throw new SmsCampaignAlreadyActiveException(
                    "An active bib collection campaign already exists for this event. Please cancel it before creating a new one.");
        }
    }

    private SmsCampaign findCampaignOrThrow(Long campaignId, Long eventId) {
        return smsCampaignRepository.findByIdAndEventId(campaignId, eventId)
                .orElseThrow(() -> new SmsCampaignNotFoundException(
                        "SMS campaign not found with ID: " + campaignId + " for event: " + eventId));
    }

    private Event validateEventAccess(Long eventId, User currentUser) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException("Event not found with ID: " + eventId));

        validateUserAuthorizationForEvent(currentUser, event);

        return event;
    }

    private void validateUserAuthorizationForEvent(User currentUser, Event event) {
        UserRole role = currentUser.getRole();

        if (role == UserRole.ROOT || role == UserRole.ADMIN) {
            return;
        }

        if (role == UserRole.ORGANIZER_ADMIN || role == UserRole.ORGANIZER_USER) {
            if (currentUser.getOrganization() == null) {
                throw new UnauthorizedAccessException("Your account is not assigned to an organization.");
            }
            if (!event.getOrganization().getId().equals(currentUser.getOrganization().getId())) {
                throw new UnauthorizedAccessException("You can only manage SMS campaigns for your organization's events.");
            }
            return;
        }

        throw new UnauthorizedAccessException("You are not allowed to manage SMS campaigns.");
    }
}
