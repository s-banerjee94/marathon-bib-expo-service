package com.timekeeper.bibexpo.service;

import com.timekeeper.bibexpo.exception.SmsCampaignAlreadyActiveException;
import com.timekeeper.bibexpo.exception.SmsCampaignNotFoundException;
import com.timekeeper.bibexpo.model.dto.request.CreateSmsCampaignRequest;
import com.timekeeper.bibexpo.model.dto.request.UpdateSmsCampaignRequest;
import com.timekeeper.bibexpo.model.dto.response.SmsCampaignResponse;
import com.timekeeper.bibexpo.model.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface SmsCampaignService {

    /**
     * Create a new SMS campaign for an event.
     * AUTO_BIB_COLLECTED starts as ACTIVE automatically. SCHEDULED and MANUAL start as DRAFT.
     * Only one AUTO_BIB_COLLECTED campaign in DRAFT or ACTIVE status is allowed per event.
     *
     * @throws SmsCampaignAlreadyActiveException if an active AUTO_BIB_COLLECTED campaign already exists
     */
    SmsCampaignResponse createCampaign(Long eventId, CreateSmsCampaignRequest request, User currentUser);

    /**
     * Update a campaign. SENT campaigns cannot be updated.
     * Changing triggerType automatically adjusts status:
     * TO AUTO_BIB_COLLECTED → ACTIVE, to SCHEDULED/MANUAL → DRAFT.
     *
     * @throws SmsCampaignNotFoundException if campaign does not exist
     */
    SmsCampaignResponse updateCampaign(Long eventId, Long campaignId, UpdateSmsCampaignRequest request, User currentUser);

    /**
     * Get all campaigns for an event (paginated).
     */
    Page<SmsCampaignResponse> getCampaignsByEvent(Long eventId, Pageable pageable, User currentUser);

    /**
     * Get a single campaign by ID.
     *
     * @throws SmsCampaignNotFoundException if campaign does not exist
     */
    SmsCampaignResponse getCampaignById(Long eventId, Long campaignId, User currentUser);

    /**
     * Deactivate an ACTIVE campaign, moving it back to DRAFT.
     *
     * @throws SmsCampaignNotFoundException if campaign does not exist
     */
    SmsCampaignResponse deactivateCampaign(Long eventId, Long campaignId, User currentUser);

    /**
     * Delete a DRAFT campaign permanently.
     *
     * @throws SmsCampaignNotFoundException if campaign does not exist
     */
    void deleteCampaign(Long eventId, Long campaignId, User currentUser);
}
