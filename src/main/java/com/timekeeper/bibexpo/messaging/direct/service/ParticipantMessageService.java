package com.timekeeper.bibexpo.messaging.direct.service;

import com.timekeeper.bibexpo.messaging.direct.model.dto.request.SendParticipantMessagesRequest;
import com.timekeeper.bibexpo.messaging.direct.model.dto.response.ParticipantMessagesResponse;
import com.timekeeper.bibexpo.model.entity.User;

/**
 * Targeted counterpart to the campaign senders: delivers one template to named participants
 * instead of the whole event. Meant for the "I never got the confirmation" desk request.
 */
public interface ParticipantMessageService {

    /**
     * Sends one message template to the requested bib numbers, in the calling thread, through the
     * same campaign provider a campaign on this channel would use.
     *
     * <p>A participant-level problem (unknown bib, missing phone number, provider rejection) fails
     * only that entry and is reported in its result; the remaining bib numbers are still attempted.
     * Problems that apply to the whole request — no reachable event, a template that cannot be
     * resolved, no configured provider — throw instead.
     *
     * <p>Campaign send history is left untouched: this send neither reads nor writes the
     * per-campaign dedup map, so it is never suppressed as "already sent" and never marks a
     * participant as covered by a campaign.
     *
     * @param eventId     event the participants belong to
     * @param request     channel, template (optional), and the bib numbers to message
     * @param currentUser actor, checked for access to the event
     * @return the template used plus one result entry per requested bib number
     */
    ParticipantMessagesResponse sendToParticipants(Long eventId, SendParticipantMessagesRequest request, User currentUser);
}
