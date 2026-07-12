package com.timekeeper.bibexpo.messaging.campaign.service.impl;

import com.timekeeper.bibexpo.aspect.AuditContextHolder;
import com.timekeeper.bibexpo.exception.EventNotFoundException;
import com.timekeeper.bibexpo.messaging.campaign.model.dto.request.CampaignWriteRequest;
import com.timekeeper.bibexpo.messaging.campaign.model.entity.CampaignEntity;
import com.timekeeper.bibexpo.messaging.campaign.model.enums.CampaignStatus;
import com.timekeeper.bibexpo.messaging.campaign.model.enums.CampaignTargetFilter;
import com.timekeeper.bibexpo.messaging.campaign.model.enums.CampaignTriggerType;
import com.timekeeper.bibexpo.messaging.campaign.repository.CampaignBaseRepository;
import com.timekeeper.bibexpo.model.entity.Event;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.model.enums.EventOperation;
import com.timekeeper.bibexpo.repository.EventRepository;
import com.timekeeper.bibexpo.service.validator.EventAccessValidator;
import com.timekeeper.bibexpo.service.validator.EventOperationGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Channel-agnostic campaign lifecycle: create-as-draft, optional arm on create/update,
 * draft-only edits, disarm with the scheduled-send cutoff, draft-only deletion, and the
 * single-active AUTO_BIB_COLLECTED rule. Channel subclasses supply their repository plus the
 * hooks below and keep the public API methods so {@code @Auditable}/{@code @Transactional}
 * stay on the Spring proxy boundary.
 *
 * @param <C> campaign entity of the channel
 * @param <CREATE> create request DTO of the channel
 * @param <UPDATE> update request DTO of the channel
 * @param <R> response DTO of the channel
 */
public abstract class AbstractCampaignService<
        C extends CampaignEntity,
        CREATE extends CampaignWriteRequest,
        UPDATE extends CampaignWriteRequest,
        R> {

    private static final int DISARM_CUTOFF_SECONDS = 30;
    private static final int MIN_SCHEDULE_AHEAD_MINUTES = 3;

    protected final Logger log = LoggerFactory.getLogger(getClass());

    // Channel name as it appears in log lines ("SMS", "WhatsApp"); constant per subclass
    private final String channelLabel;

    protected final CampaignBaseRepository<C> campaignRepository;
    private final EventRepository eventRepository;
    private final EventAccessValidator eventAccessValidator;
    private final EventOperationGuard eventOperationGuard;

    protected AbstractCampaignService(String channelLabel,
                                      CampaignBaseRepository<C> campaignRepository,
                                      EventRepository eventRepository,
                                      EventAccessValidator eventAccessValidator,
                                      EventOperationGuard eventOperationGuard) {
        this.channelLabel = channelLabel;
        this.campaignRepository = campaignRepository;
        this.eventRepository = eventRepository;
        this.eventAccessValidator = eventAccessValidator;
        this.eventOperationGuard = eventOperationGuard;
    }

    protected final R doCreate(Long eventId, CREATE request, User currentUser) {
        log.info("Creating {} campaign for event ID: {} by user: {}",
                channelLabel, eventId, currentUser.getUsername());

        Event event = validateEventAccess(eventId, currentUser);
        eventOperationGuard.requireAllowed(event, EventOperation.CAMPAIGN_WRITE);

        enforceCreateLimit(eventId);

        C campaign = newDraft(request, event);
        campaign.setName(request.getName().trim());
        campaign.setStatus(CampaignStatus.DRAFT);

        if (request.getTriggerType() != null) {
            applyArm(campaign, request.getTriggerType(), request.getTargetFilter(),
                    request.getScheduledDate(), request.getScheduledTime(), event);
        }

        C saved = campaignRepository.save(campaign);
        log.info("Successfully created {} campaign ID: {} status: {} for event ID: {} by user: {}",
                channelLabel, saved.getId(), saved.getStatus(), eventId, currentUser.getUsername());

        return toResponse(saved, event);
    }

    protected final R doUpdate(Long eventId, Long campaignId, UPDATE request, User currentUser) {
        log.info("Updating {} campaign ID: {} for event ID: {} by user: {}",
                channelLabel, campaignId, eventId, currentUser.getUsername());

        Event event = validateEventAccess(eventId, currentUser);
        eventOperationGuard.requireAllowed(event, EventOperation.CAMPAIGN_WRITE);

        C campaign = findCampaignOrThrow(campaignId, eventId);

        if (campaign.getStatus() != CampaignStatus.DRAFT) {
            throw invalidCampaign("Only draft campaigns can be modified. Disarm the campaign first.");
        }

        if (request.getName() != null && !request.getName().isBlank()) {
            campaign.setName(request.getName().trim());
        }

        applyTemplateChange(campaign, request, eventId);

        if (request.getTriggerType() != null) {
            applyArm(campaign, request.getTriggerType(), request.getTargetFilter(),
                    request.getScheduledDate(), request.getScheduledTime(), event);
        }

        C updated = campaignRepository.saveAndFlush(campaign);
        log.info("Successfully updated {} campaign ID: {} status: {} by user: {}",
                channelLabel, updated.getId(), updated.getStatus(), currentUser.getUsername());

        return toResponse(updated, event);
    }

    protected final List<R> doList(Long eventId, User currentUser) {
        log.info("Fetching {} campaigns for event ID: {} by user: {}",
                channelLabel, eventId, currentUser.getUsername());

        Event event = validateEventAccess(eventId, currentUser);

        return campaignRepository.findAllByEventId(eventId)
                .stream().map(campaign -> toResponse(campaign, event)).toList();
    }

    protected final R doGet(Long eventId, Long campaignId, User currentUser) {
        log.info("Fetching {} campaign ID: {} for event ID: {} by user: {}",
                channelLabel, campaignId, eventId, currentUser.getUsername());

        Event event = validateEventAccess(eventId, currentUser);

        return toResponse(findCampaignOrThrow(campaignId, eventId), event);
    }

    protected final R doDisarm(Long eventId, Long campaignId, User currentUser) {
        log.info("Disarming {} campaign ID: {} for event ID: {} by user: {}",
                channelLabel, campaignId, eventId, currentUser.getUsername());

        Event event = validateEventAccess(eventId, currentUser);
        eventOperationGuard.requireAllowed(event, EventOperation.CAMPAIGN_WRITE);

        C campaign = findCampaignOrThrow(campaignId, eventId);

        if (campaign.getStatus() != CampaignStatus.ACTIVE) {
            throw invalidCampaign("Only active campaigns can be disarmed.");
        }

        if (campaign.getTriggerType() == CampaignTriggerType.SCHEDULED && campaign.getScheduledAt() != null) {
            Instant cutoff = campaign.getScheduledAt().minusSeconds(DISARM_CUTOFF_SECONDS);
            if (Instant.now().isAfter(cutoff)) {
                throw invalidCampaign(
                        "Scheduled campaigns cannot be disarmed within " + DISARM_CUTOFF_SECONDS + " seconds of the scheduled time.");
            }
        }

        campaign.setStatus(CampaignStatus.DRAFT);
        campaign.setTriggerType(null);
        campaign.setTargetFilter(null);
        campaign.setScheduledAt(null);

        C updated = campaignRepository.saveAndFlush(campaign);
        log.info("Successfully disarmed {} campaign ID: {} by user: {}",
                channelLabel, campaignId, currentUser.getUsername());

        return toResponse(updated, event);
    }

    protected final void doDelete(Long eventId, Long campaignId, User currentUser) {
        log.info("Deleting {} campaign ID: {} for event ID: {} by user: {}",
                channelLabel, campaignId, eventId, currentUser.getUsername());

        Event event = validateEventAccess(eventId, currentUser);
        eventOperationGuard.requireAllowed(event, EventOperation.CAMPAIGN_WRITE);

        C campaign = findCampaignOrThrow(campaignId, eventId);

        if (campaign.getStatus() != CampaignStatus.DRAFT) {
            throw invalidCampaign("Only draft campaigns can be deleted.");
        }

        AuditContextHolder.setEntityLabel(campaign.getName());
        AuditContextHolder.setOrganizationId(
                event.getOrganization() != null ? event.getOrganization().getId() : null);

        campaignRepository.delete(campaign);
        log.info("Successfully deleted {} campaign ID: {} by user: {}",
                channelLabel, campaignId, currentUser.getUsername());
    }

    private void applyArm(C campaign, CampaignTriggerType triggerType,
                          CampaignTargetFilter targetFilter, String scheduledDate, String scheduledTime,
                          Event event) {
        if (targetFilter == null) {
            throw invalidCampaign("Target filter is required when arming a campaign.");
        }

        Instant scheduledAt = null;
        if (triggerType == CampaignTriggerType.SCHEDULED) {
            if (scheduledDate == null || scheduledTime == null) {
                throw invalidCampaign("Scheduled date and time are required for a scheduled campaign.");
            }
            if (event.getTimezone() == null) {
                throw invalidCampaign("The event does not have a timezone configured.");
            }
            try {
                scheduledAt = ZonedDateTime.of(
                        LocalDate.parse(scheduledDate),
                        LocalTime.parse(scheduledTime),
                        ZoneId.of(event.getTimezone())
                ).toInstant();
            } catch (DateTimeParseException e) {
                throw invalidCampaign("Invalid scheduled date or time format. Use yyyy-MM-dd and HH:mm.");
            }
            Instant minAllowed = Instant.now().plus(MIN_SCHEDULE_AHEAD_MINUTES, ChronoUnit.MINUTES);
            if (!scheduledAt.isAfter(minAllowed)) {
                throw invalidCampaign(
                        "Scheduled time must be at least " + MIN_SCHEDULE_AHEAD_MINUTES + " minutes in the future.");
            }
        }

        if (triggerType == CampaignTriggerType.AUTO_BIB_COLLECTED
                && campaignRepository.existsByEventIdAndTriggerTypeAndStatus(
                        event.getId(), CampaignTriggerType.AUTO_BIB_COLLECTED, CampaignStatus.ACTIVE)) {
            throw campaignAlreadyActive(
                    "An active bib collection campaign already exists for this event. Please disarm it before arming a new one.");
        }

        campaign.setTriggerType(triggerType);
        campaign.setTargetFilter(targetFilter);
        campaign.setScheduledAt(scheduledAt);
        campaign.setStatus(CampaignStatus.ACTIVE);
    }

    protected final C findCampaignOrThrow(Long campaignId, Long eventId) {
        return campaignRepository.findByIdAndEventId(campaignId, eventId)
                .orElseThrow(this::campaignNotFound);
    }

    protected final Event validateEventAccess(Long eventId, User currentUser) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(EventNotFoundException::new);

        eventAccessValidator.validateUserAuthorizationForEvent(currentUser, event);

        return event;
    }

    /**
     * Channel's invalid-campaign exception carrying the given message.
     */
    protected abstract RuntimeException invalidCampaign(String message);

    /**
     * Channel's already-active exception for the duplicate AUTO_BIB_COLLECTED rule.
     */
    protected abstract RuntimeException campaignAlreadyActive(String message);

    /**
     * Channel's campaign-not-found exception.
     */
    protected abstract RuntimeException campaignNotFound();

    /**
     * Reject creation when the event has reached the channel's campaign limit.
     */
    protected abstract void enforceCreateLimit(Long eventId);

    /**
     * Build a new campaign wired to the event and its resolved template. Name and DRAFT status
     * are set by the base class afterwards.
     */
    protected abstract C newDraft(CREATE request, Event event);

    /**
     * Re-resolve and set the template when the update request carries a template ID.
     */
    protected abstract void applyTemplateChange(C campaign, UPDATE request, Long eventId);

    /**
     * Map the campaign to the channel's response DTO.
     */
    protected abstract R toResponse(C campaign, Event event);
}
