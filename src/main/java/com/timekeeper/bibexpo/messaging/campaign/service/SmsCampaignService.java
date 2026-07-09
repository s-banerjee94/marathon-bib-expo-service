package com.timekeeper.bibexpo.messaging.campaign.service;

import com.timekeeper.bibexpo.messaging.campaign.exception.SmsCampaignNotFoundException;
import com.timekeeper.bibexpo.messaging.campaign.model.dto.request.CreateSmsCampaignRequest;
import com.timekeeper.bibexpo.messaging.campaign.model.dto.request.UpdateSmsCampaignRequest;
import com.timekeeper.bibexpo.messaging.campaign.model.dto.response.SmsCampaignResponse;
import com.timekeeper.bibexpo.model.entity.User;
import java.util.List;

public interface SmsCampaignService {

    /**
     * Create a new DRAFT campaign with name and template only.
     * Use the update endpoint with a triggerType to arm it.
     */
    SmsCampaignResponse createCampaign(Long eventId, CreateSmsCampaignRequest request, User currentUser);

    /**
     * Update a DRAFT campaign. Only DRAFT campaigns can be modified.
     * If triggerType is present in the request the campaign is armed and moves to ACTIVE.
     * If triggerType is null only name and template are updated and the campaign stays DRAFT.
     *
     * @throws SmsCampaignNotFoundException if campaign does not exist
     */
    SmsCampaignResponse updateCampaign(Long eventId, Long campaignId, UpdateSmsCampaignRequest request, User currentUser);

    /**
     * Get all campaigns for an event.
     */
    List<SmsCampaignResponse> getCampaignsByEvent(Long eventId, User currentUser);

    /**
     * Get a single campaign by ID.
     *
     * @throws SmsCampaignNotFoundException if campaign does not exist
     */
    SmsCampaignResponse getCampaignById(Long eventId, Long campaignId, User currentUser);

    /**
     * Disarm an ACTIVE campaign, clearing trigger config and moving back to DRAFT.
     * SCHEDULED campaigns cannot be disarmed within 30 seconds of scheduledAt.
     *
     * @throws SmsCampaignNotFoundException if campaign does not exist
     */
    SmsCampaignResponse disarmCampaign(Long eventId, Long campaignId, User currentUser);

    /**
     * Delete a DRAFT campaign permanently.
     *
     * @throws SmsCampaignNotFoundException if campaign does not exist
     */
    void deleteCampaign(Long eventId, Long campaignId, User currentUser);
}
