package com.timekeeper.bibexpo.messaging.campaign.service;

import com.timekeeper.bibexpo.model.dynamodb.ParticipantDDB;
import com.timekeeper.bibexpo.model.entity.Event;

public interface ParticipantEventWhatsAppService {

    /**
     * Auto-triggered WhatsApp message sent to a participant immediately after their bib is
     * collected. Uses the active AUTO_BIB_COLLECTED WhatsApp campaign for the event.
     * Silently no-ops if no active campaign exists, the participant has no phone number, or
     * the template's sender scope no longer matches the organization's resolved sender.
     * Never throws — bib collection must not fail due to WhatsApp.
     *
     * @param event       event the bib was collected in
     * @param participant participant whose bib was just collected
     */
    void sendBibCollectedWhatsApp(Event event, ParticipantDDB participant);
}
