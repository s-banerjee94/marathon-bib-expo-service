package com.timekeeper.bibexpo.participantaccess.service;

import com.timekeeper.bibexpo.model.dto.response.ParticipantDistributionResponse;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.participantaccess.model.dto.response.ParticipantVerificationResponse;

public interface ParticipantAccessService {

    /**
     * Ensures every participant of an event has a verification short URL.
     * Runs asynchronously. The first call generates a code for all participants;
     * later calls only generate codes for participants that do not yet have one.
     * Each short URL expires three days after the event end date.
     * TODO: add per-event call-rate limit to prevent concurrent duplicate runs.
     * TODO: add idempotency key support so clients can safely retry without double-processing.
     */
    void generateShortUrls(Long eventId, User currentUser);

    /**
     * Resolves a short code to the participant's verification details.
     * Public — no authentication required.
     */
    ParticipantVerificationResponse resolveShortUrl(String shortCode);

    /**
     * Returns the encrypted QR token PNG image for a participant.
     * The token is opaque to generic QR scanners; only this app can decode it.
     */
    byte[] getParticipantQr(Long eventId, String bibNumber, User currentUser);

    /**
     * Decrypts a QR token scanned at the distribution counter and returns the
     * participant's current distribution status.
     * Verifies the token's embedded event ID matches the path event ID.
     */
    ParticipantDistributionResponse scanQr(Long eventId, String token, User currentUser);
}
