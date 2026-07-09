package com.timekeeper.bibexpo.messaging.campaign.service;

public interface WhatsAppCampaignSendService {

    /**
     * Asynchronously dispatch WhatsApp messages to all matching participants for the campaign.
     * Re-validates the template's sender scope against the organization's currently resolved
     * sender before sending (the organization may have switched modes since arming) — a
     * mismatch marks the campaign FAILED without sending. Marks the campaign SENT on full
     * completion, or leaves it SENDING for scheduler retry on gateway failure.
     */
    void sendCampaignAsync(Long campaignId);
}
