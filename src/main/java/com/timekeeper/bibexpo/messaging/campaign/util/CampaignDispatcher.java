package com.timekeeper.bibexpo.messaging.campaign.util;

import com.timekeeper.bibexpo.messaging.campaign.exception.MessageSendException;
import com.timekeeper.bibexpo.model.dynamodb.ParticipantDDB;
import com.timekeeper.bibexpo.repository.dynamodb.ParticipantDDBRepository;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;

import java.time.Instant;
import java.util.Map;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.Predicate;

/**
 * Channel-neutral campaign dispatch loop shared by SMS and WhatsApp: page-iterates an
 * event's participants, applies the target filter, skips already-sent participants via a
 * per-channel dedup map, paces sends, and aborts after too many consecutive gateway
 * failures. Resume-safe — the dedup map is written immediately after each successful send,
 * so a scheduler retry continues where the previous run stopped.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CampaignDispatcher {

    private static final int PAGE_SIZE = 50;

    private final ParticipantDDBRepository participantDDBRepository;

    /** Sends one message to one participant; returns the provider message ID (nullable). */
    @FunctionalInterface
    public interface ParticipantSender {
        String send(ParticipantDDB participant) throws MessageSendException;
    }

    @Getter
    @Builder
    public static class DispatchRequest {
        private final Long eventId;
        /** Key written into the participant's per-channel sends map; the campaign ID as string. */
        private final String campaignKey;
        /** Channel label for log lines, e.g. "SMS" or "WhatsApp". */
        private final String channelName;
        private final int initialSentCount;
        private final long sendDelayMs;
        @Builder.Default
        private final int consecutiveFailureThreshold = 5;
        private final Predicate<ParticipantDDB> targetFilter;
        /** Accessor for the channel's dedup map on the participant (smsCampaignSends / whatsAppCampaignSends). */
        private final Function<ParticipantDDB, Map<String, String>> sendsMapAccessor;
        private final ParticipantSender sender;
        /** Persists the running sent count when the loop stops early (interrupt or abort). */
        private final IntConsumer checkpoint;
        /** Builds the channel-specific exception thrown when the consecutive-failure threshold is hit. */
        private final IntFunction<RuntimeException> abortExceptionFactory;
    }

    @Getter
    @AllArgsConstructor
    public static class DispatchOutcome {
        private final int sentCount;
        /** False when the loop was interrupted; the caller must not mark the campaign SENT. */
        private final boolean completed;
    }

    // Running counters carried through the per-participant loop.
    private static final class DispatchState {
        private int sentCount;
        private int consecutiveFailures;

        private DispatchState(int initialSentCount) {
            this.sentCount = initialSentCount;
        }
    }

    public DispatchOutcome dispatch(DispatchRequest request) {
        DispatchState state = new DispatchState(request.getInitialSentCount());

        for (Page<ParticipantDDB> page : participantDDBRepository.findPagesByEventId(request.getEventId(), PAGE_SIZE)) {
            for (ParticipantDDB participant : page.items()) {
                if (!isSendable(request, participant)) {
                    continue;
                }
                boolean interrupted = sendToParticipant(request, participant, state);
                if (interrupted) {
                    return new DispatchOutcome(state.sentCount, false);
                }
            }
        }

        return new DispatchOutcome(state.sentCount, true);
    }

    /** True when the participant passes the target filter, has not already been sent, and has a phone number. */
    private boolean isSendable(DispatchRequest request, ParticipantDDB participant) {
        String campaignKey = request.getCampaignKey();
        if (!request.getTargetFilter().test(participant)) {
            return false;
        }
        if (request.getSendsMapAccessor().apply(participant).containsKey(campaignKey)) {
            log.debug("Skipping already-sent participant bib {} for campaign {}", participant.getBibNumber(), campaignKey);
            return false;
        }
        String phone = participant.getPhoneNumber();
        if (phone == null || phone.isBlank()) {
            log.warn("Skipping bib {} for campaign {}: no phone number", participant.getBibNumber(), campaignKey);
            return false;
        }
        return true;
    }

    /**
     * Sends to one participant and updates {@code state}. Returns true only when the send was
     * interrupted (the caller must stop the dispatch); throws the abort exception once the
     * consecutive-failure threshold is reached.
     */
    private boolean sendToParticipant(DispatchRequest request, ParticipantDDB participant, DispatchState state) {
        String campaignKey = request.getCampaignKey();
        try {
            Thread.sleep(request.getSendDelayMs());

            request.getSender().send(participant);

            request.getSendsMapAccessor().apply(participant).put(campaignKey, Instant.now().toString());
            participantDDBRepository.save(participant);
            state.sentCount++;
            state.consecutiveFailures = 0;
            log.debug("{} sent to bib {} for campaign {}", request.getChannelName(), participant.getBibNumber(), campaignKey);
            return false;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            request.getCheckpoint().accept(state.sentCount);
            log.warn("Campaign ID: {} send interrupted at bib {}", campaignKey, participant.getBibNumber());
            return true;

        } catch (MessageSendException | RuntimeException e) {
            handleSendFailure(request, participant, state, e);
            return false;
        }
    }

    private void handleSendFailure(DispatchRequest request, ParticipantDDB participant, DispatchState state, Exception e) {
        state.consecutiveFailures++;
        log.warn("{} failed for bib {} in campaign {}: {} (consecutive: {})",
                request.getChannelName(), participant.getBibNumber(), request.getCampaignKey(), e.getMessage(), state.consecutiveFailures);

        if (state.consecutiveFailures >= request.getConsecutiveFailureThreshold()) {
            request.getCheckpoint().accept(state.sentCount);
            log.error("Campaign ID: {} aborted after {} consecutive gateway failures — scheduler will retry",
                    request.getCampaignKey(), state.consecutiveFailures);
            throw request.getAbortExceptionFactory().apply(state.consecutiveFailures);
        }
    }
}
