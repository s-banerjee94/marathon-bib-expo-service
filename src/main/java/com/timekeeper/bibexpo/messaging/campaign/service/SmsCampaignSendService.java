package com.timekeeper.bibexpo.messaging.campaign.service;

public interface SmsCampaignSendService {

    /**
     * Asynchronously dispatch SMS to all matching participants for the campaign.
     * Marks campaign SENT on full completion, or leaves it SENDING for scheduler retry on gateway failure.
     */
    void sendCampaignAsync(Long campaignId);
}
