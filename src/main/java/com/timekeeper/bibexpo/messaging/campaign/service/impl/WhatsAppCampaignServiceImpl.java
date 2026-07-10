package com.timekeeper.bibexpo.messaging.campaign.service.impl;

import com.timekeeper.bibexpo.annotation.Auditable;
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
import com.timekeeper.bibexpo.model.entity.Event;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.model.enums.AuditAction;
import com.timekeeper.bibexpo.model.enums.AuditEntityType;
import com.timekeeper.bibexpo.repository.EventRepository;
import com.timekeeper.bibexpo.service.validator.EventAccessValidator;
import com.timekeeper.bibexpo.service.validator.EventOperationGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class WhatsAppCampaignServiceImpl
        extends AbstractCampaignService<WhatsAppCampaign, CreateWhatsAppCampaignRequest, UpdateWhatsAppCampaignRequest, WhatsAppCampaignResponse>
        implements WhatsAppCampaignService {

    private static final int MAX_CAMPAIGNS_PER_EVENT = 20;

    private final WhatsAppTemplateRepository templateRepository;

    public WhatsAppCampaignServiceImpl(WhatsAppCampaignRepository campaignRepository,
                                       WhatsAppTemplateRepository templateRepository,
                                       EventRepository eventRepository,
                                       EventAccessValidator eventAccessValidator,
                                       EventOperationGuard eventOperationGuard) {
        super("WhatsApp", campaignRepository, eventRepository, eventAccessValidator, eventOperationGuard);
        this.templateRepository = templateRepository;
    }

    @Auditable(entityType = AuditEntityType.WHATSAPP_CAMPAIGN, action = AuditAction.CREATE)
    @Override
    @Transactional
    public WhatsAppCampaignResponse createCampaign(Long eventId, CreateWhatsAppCampaignRequest request, User currentUser) {
        return doCreate(eventId, request, currentUser);
    }

    @Auditable(entityType = AuditEntityType.WHATSAPP_CAMPAIGN, action = AuditAction.UPDATE)
    @Override
    @Transactional
    public WhatsAppCampaignResponse updateCampaign(Long eventId, Long campaignId, UpdateWhatsAppCampaignRequest request, User currentUser) {
        return doUpdate(eventId, campaignId, request, currentUser);
    }

    @Override
    @Transactional(readOnly = true)
    public List<WhatsAppCampaignResponse> getCampaignsByEvent(Long eventId, User currentUser) {
        return doList(eventId, currentUser);
    }

    @Auditable(entityType = AuditEntityType.WHATSAPP_CAMPAIGN, action = AuditAction.STATUS_CHANGE)
    @Override
    @Transactional
    public WhatsAppCampaignResponse disarmCampaign(Long eventId, Long campaignId, User currentUser) {
        return doDisarm(eventId, campaignId, currentUser);
    }

    @Auditable(entityType = AuditEntityType.WHATSAPP_CAMPAIGN, action = AuditAction.DELETE)
    @Override
    @Transactional
    public void deleteCampaign(Long eventId, Long campaignId, User currentUser) {
        doDelete(eventId, campaignId, currentUser);
    }

    @Override
    protected RuntimeException invalidCampaign(String message) {
        return new InvalidWhatsAppCampaignException(message);
    }

    @Override
    protected RuntimeException campaignAlreadyActive(String message) {
        return new WhatsAppCampaignAlreadyActiveException(message);
    }

    @Override
    protected RuntimeException campaignNotFound() {
        return new WhatsAppCampaignNotFoundException();
    }

    @Override
    protected void enforceCreateLimit(Long eventId) {
        if (campaignRepository.countByEventId(eventId) >= MAX_CAMPAIGNS_PER_EVENT) {
            throw new InvalidWhatsAppCampaignException("An event can have a maximum of 20 WhatsApp campaigns.");
        }
    }

    @Override
    protected WhatsAppCampaign newDraft(CreateWhatsAppCampaignRequest request, Event event) {
        WhatsAppTemplate template = templateRepository.findByIdAndEventId(request.getWhatsAppTemplateId(), event.getId())
                .orElseThrow(WhatsAppTemplateNotFoundException::new);

        return WhatsAppCampaign.builder()
                .eventId(event.getId())
                .organizationId(event.getOrganization() != null ? event.getOrganization().getId() : null)
                .whatsAppTemplate(template)
                .build();
    }

    @Override
    protected void applyTemplateChange(WhatsAppCampaign campaign, UpdateWhatsAppCampaignRequest request, Long eventId) {
        if (request.getWhatsAppTemplateId() != null) {
            WhatsAppTemplate template = templateRepository.findByIdAndEventId(request.getWhatsAppTemplateId(), eventId)
                    .orElseThrow(WhatsAppTemplateNotFoundException::new);
            campaign.setWhatsAppTemplate(template);
        }
    }

    @Override
    protected WhatsAppCampaignResponse toResponse(WhatsAppCampaign campaign, Event event) {
        return WhatsAppCampaignResponse.fromEntity(campaign, event);
    }
}
