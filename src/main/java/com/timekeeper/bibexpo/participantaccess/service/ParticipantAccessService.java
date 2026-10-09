package com.timekeeper.bibexpo.participantaccess.service;

import com.timekeeper.bibexpo.participant.model.dto.response.ParticipantDistributionResponse;
import com.timekeeper.bibexpo.participantaccess.model.dto.response.ParticipantVerificationResponse;
import com.timekeeper.bibexpo.user.model.entity.User;

public interface ParticipantAccessService {

    /**
     * Ensures every participant of an event has a verification short URL. The event and the caller's
     * access are checked before this returns; the codes are then generated in the background. The first
     * call generates a code for all participants; later calls only fill in participants that do not yet
     * have one, and a repeated or concurrent call yields the same codes. Each short URL expires three
     * days after the event end date.
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
