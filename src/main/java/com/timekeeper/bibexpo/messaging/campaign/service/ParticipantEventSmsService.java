package com.timekeeper.bibexpo.messaging.campaign.service;

import com.timekeeper.bibexpo.model.dynamodb.ParticipantDDB;
import com.timekeeper.bibexpo.model.entity.Event;

public interface ParticipantEventSmsService {

    /**
     * Auto-triggered SMS sent to a participant immediately after their bib is collected.
     * Uses the active AUTO_BIB_COLLECTED campaign for the event.
     * Silently no-ops if no active campaign exists or the participant has no phone number.
     * Never throws — bib collection must not fail due to SMS.
     *
     * @param event       event the bib was collected in
     * @param participant participant whose bib was just collected
     */
    void sendBibCollectedSms(Event event, ParticipantDDB participant);
}
