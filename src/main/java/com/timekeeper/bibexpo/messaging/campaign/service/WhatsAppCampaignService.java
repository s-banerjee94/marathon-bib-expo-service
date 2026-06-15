package com.timekeeper.bibexpo.messaging.campaign.service;

import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.messaging.campaign.model.dto.request.CreateWhatsAppCampaignRequest;
import com.timekeeper.bibexpo.messaging.campaign.model.dto.request.UpdateWhatsAppCampaignRequest;
import com.timekeeper.bibexpo.messaging.campaign.model.dto.response.WhatsAppCampaignResponse;

import java.util.List;

public interface WhatsAppCampaignService {

    /**
     * Create a WhatsApp campaign. Without a trigger type it is saved as DRAFT; with one it is
     * armed immediately (moves to ACTIVE). Arming validates the schedule, the one-active
     * AUTO_BIB_COLLECTED rule, and that the template's sender scope matches the organization's
     * currently resolved sender.
     *
     * @param eventId     event the campaign belongs to
     * @param request     name, template, optional trigger/filter/schedule
     * @param currentUser caller; organizer roles may only access their organization's events
     * @return the created campaign
     */
    WhatsAppCampaignResponse createCampaign(Long eventId, CreateWhatsAppCampaignRequest request, User currentUser);

    /**
     * Update a DRAFT campaign; arming rules are the same as on create.
     *
     * @param eventId     event the campaign belongs to
     * @param campaignId  campaign to update
     * @param request     fields to change
     * @param currentUser caller; organizer roles may only access their organization's events
     * @return the updated campaign
     */
    WhatsAppCampaignResponse updateCampaign(Long eventId, Long campaignId, UpdateWhatsAppCampaignRequest request, User currentUser);

    /**
     * List an event's WhatsApp campaigns.
     *
     * @param eventId     event whose campaigns are listed
     * @param currentUser caller; organizer roles may only access their organization's events
     * @return all campaigns of the event
     */
    List<WhatsAppCampaignResponse> getCampaignsByEvent(Long eventId, User currentUser);

    /**
     * Fetch a single campaign by ID within an event.
     *
     * @param eventId     event the campaign belongs to
     * @param campaignId  campaign to fetch
     * @param currentUser caller; organizer roles may only access their organization's events
     * @return the campaign
     */
    WhatsAppCampaignResponse getCampaignById(Long eventId, Long campaignId, User currentUser);

    /**
     * Disarm an ACTIVE campaign back to DRAFT (clears trigger, filter and schedule).
     * Scheduled campaigns cannot be disarmed within 30 seconds of their scheduled time.
     *
     * @param eventId     event the campaign belongs to
     * @param campaignId  campaign to disarm
     * @param currentUser caller; organizer roles may only access their organization's events
     * @return the disarmed campaign
     */
    WhatsAppCampaignResponse disarmCampaign(Long eventId, Long campaignId, User currentUser);

    /**
     * Delete a DRAFT campaign.
     *
     * @param eventId     event the campaign belongs to
     * @param campaignId  campaign to delete
     * @param currentUser caller; organizer roles may only access their organization's events
     */
    void deleteCampaign(Long eventId, Long campaignId, User currentUser);
}
