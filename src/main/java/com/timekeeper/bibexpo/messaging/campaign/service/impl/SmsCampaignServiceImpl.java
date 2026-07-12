package com.timekeeper.bibexpo.messaging.campaign.service.impl;

import com.timekeeper.bibexpo.annotation.Auditable;
import com.timekeeper.bibexpo.exception.EventLimitExceededException;
import com.timekeeper.bibexpo.messaging.campaign.exception.InvalidSmsCampaignException;
import com.timekeeper.bibexpo.messaging.campaign.exception.SmsCampaignAlreadyActiveException;
import com.timekeeper.bibexpo.messaging.campaign.exception.SmsCampaignNotFoundException;
import com.timekeeper.bibexpo.messaging.campaign.exception.SmsTemplateNotFoundException;
import com.timekeeper.bibexpo.messaging.campaign.model.dto.request.CreateSmsCampaignRequest;
import com.timekeeper.bibexpo.messaging.campaign.model.dto.request.UpdateSmsCampaignRequest;
import com.timekeeper.bibexpo.messaging.campaign.model.dto.response.SmsCampaignResponse;
import com.timekeeper.bibexpo.messaging.campaign.model.entity.SmsCampaign;
import com.timekeeper.bibexpo.messaging.campaign.model.entity.SmsTemplate;
import com.timekeeper.bibexpo.messaging.campaign.repository.SmsCampaignRepository;
import com.timekeeper.bibexpo.messaging.campaign.repository.SmsTemplateRepository;
import com.timekeeper.bibexpo.messaging.campaign.service.SmsCampaignService;
import com.timekeeper.bibexpo.model.entity.Event;
import com.timekeeper.bibexpo.model.entity.EventLimit;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.model.enums.AuditAction;
import com.timekeeper.bibexpo.model.enums.AuditEntityType;
import com.timekeeper.bibexpo.repository.EventLimitRepository;
import com.timekeeper.bibexpo.repository.EventRepository;
import com.timekeeper.bibexpo.service.validator.EventAccessValidator;
import com.timekeeper.bibexpo.service.validator.EventOperationGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SmsCampaignServiceImpl
        extends AbstractCampaignService<SmsCampaign, CreateSmsCampaignRequest, UpdateSmsCampaignRequest, SmsCampaignResponse>
        implements SmsCampaignService {

    private final SmsTemplateRepository smsTemplateRepository;
    private final EventLimitRepository eventLimitRepository;

    public SmsCampaignServiceImpl(SmsCampaignRepository smsCampaignRepository,
                                  SmsTemplateRepository smsTemplateRepository,
                                  EventRepository eventRepository,
                                  EventAccessValidator eventAccessValidator,
                                  EventLimitRepository eventLimitRepository,
                                  EventOperationGuard eventOperationGuard) {
        super("SMS", smsCampaignRepository, eventRepository, eventAccessValidator, eventOperationGuard);
        this.smsTemplateRepository = smsTemplateRepository;
        this.eventLimitRepository = eventLimitRepository;
    }

    @Auditable(entityType = AuditEntityType.SMS_CAMPAIGN, action = AuditAction.CREATE)
    @Override
    @Transactional
    public SmsCampaignResponse createCampaign(Long eventId, CreateSmsCampaignRequest request, User currentUser) {
        return doCreate(eventId, request, currentUser);
    }

    @Auditable(entityType = AuditEntityType.SMS_CAMPAIGN, action = AuditAction.UPDATE)
    @Override
    @Transactional
    public SmsCampaignResponse updateCampaign(Long eventId, Long campaignId, UpdateSmsCampaignRequest request, User currentUser) {
        return doUpdate(eventId, campaignId, request, currentUser);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SmsCampaignResponse> getCampaignsByEvent(Long eventId, User currentUser) {
        return doList(eventId, currentUser);
    }

    @Override
    @Transactional(readOnly = true)
    public SmsCampaignResponse getCampaignById(Long eventId, Long campaignId, User currentUser) {
        return doGet(eventId, campaignId, currentUser);
    }

    @Auditable(entityType = AuditEntityType.SMS_CAMPAIGN, action = AuditAction.STATUS_CHANGE)
    @Override
    @Transactional
    public SmsCampaignResponse disarmCampaign(Long eventId, Long campaignId, User currentUser) {
        return doDisarm(eventId, campaignId, currentUser);
    }

    @Auditable(entityType = AuditEntityType.SMS_CAMPAIGN, action = AuditAction.DELETE)
    @Override
    @Transactional
    public void deleteCampaign(Long eventId, Long campaignId, User currentUser) {
        doDelete(eventId, campaignId, currentUser);
    }

    @Override
    protected RuntimeException invalidCampaign(String message) {
        return new InvalidSmsCampaignException(message);
    }

    @Override
    protected RuntimeException campaignAlreadyActive(String message) {
        return new SmsCampaignAlreadyActiveException(message);
    }

    @Override
    protected RuntimeException campaignNotFound() {
        return new SmsCampaignNotFoundException();
    }

    @Override
    protected void enforceCreateLimit(Long eventId) {
        EventLimit limits = eventLimitRepository.findByEventId(eventId)
                .orElseGet(() -> EventLimit.builder().build());
        if (campaignRepository.countByEventId(eventId) >= limits.getMaxSmsCampaigns()) {
            throw new EventLimitExceededException("You have reached the maximum number of SMS campaigns allowed for this event.");
        }
    }

    @Override
    protected SmsCampaign newDraft(CreateSmsCampaignRequest request, Event event) {
        SmsTemplate template = smsTemplateRepository.findByIdAndEventId(request.getSmsTemplateId(), event.getId())
                .orElseThrow(SmsTemplateNotFoundException::new);

        return SmsCampaign.builder()
                .event(event)
                .smsTemplate(template)
                .organizationId(event.getOrganization() != null ? event.getOrganization().getId() : null)
                .build();
    }

    @Override
    protected void applyTemplateChange(SmsCampaign campaign, UpdateSmsCampaignRequest request, Long eventId) {
        if (request.getSmsTemplateId() != null) {
            SmsTemplate template = smsTemplateRepository.findByIdAndEventId(request.getSmsTemplateId(), eventId)
                    .orElseThrow(SmsTemplateNotFoundException::new);
            campaign.setSmsTemplate(template);
        }
    }

    @Override
    protected SmsCampaignResponse toResponse(SmsCampaign campaign, Event event) {
        return SmsCampaignResponse.fromEntity(campaign);
    }
}
