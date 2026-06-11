package com.timekeeper.bibexpo.service.util;

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
        String send(ParticipantDDB participant) throws Exception;
    }

    /**
     * Invoked after each successful send (dedup map already persisted). Failures here are
     * logged and swallowed — they never affect the campaign. Seam for usage accounting and
     * future delivery reports.
     */
    @FunctionalInterface
    public interface PostSendListener {
        void afterSend(ParticipantDDB participant, String messageSid);
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
        private final PostSendListener postSendListener;
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

    public DispatchOutcome dispatch(DispatchRequest request) {
        String campaignKey = request.getCampaignKey();
        String channel = request.getChannelName();
        int sentCount = request.getInitialSentCount();
        int consecutiveFailures = 0;

        for (Page<ParticipantDDB> page : participantDDBRepository.findPagesByEventId(request.getEventId(), PAGE_SIZE)) {
            for (ParticipantDDB participant : page.items()) {
                if (!request.getTargetFilter().test(participant)) {
                    continue;
                }
                if (request.getSendsMapAccessor().apply(participant).containsKey(campaignKey)) {
                    log.debug("Skipping already-sent participant bib {} for campaign {}", participant.getBibNumber(), campaignKey);
                    continue;
                }
                String phone = participant.getPhoneNumber();
                if (phone == null || phone.isBlank()) {
                    log.warn("Skipping bib {} for campaign {}: no phone number", participant.getBibNumber(), campaignKey);
                    continue;
                }

                try {
                    Thread.sleep(request.getSendDelayMs());

                    String messageSid = request.getSender().send(participant);

                    request.getSendsMapAccessor().apply(participant).put(campaignKey, Instant.now().toString());
                    participantDDBRepository.save(participant);
                    sentCount++;
                    consecutiveFailures = 0;
                    notifyPostSend(request, participant, messageSid);
                    log.debug("{} sent to bib {} for campaign {}", channel, participant.getBibNumber(), campaignKey);

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    request.getCheckpoint().accept(sentCount);
                    log.warn("Campaign ID: {} send interrupted at bib {}", campaignKey, participant.getBibNumber());
                    return new DispatchOutcome(sentCount, false);

                } catch (Exception e) {
                    consecutiveFailures++;
                    log.warn("{} failed for bib {} in campaign {}: {} (consecutive: {})",
                            channel, participant.getBibNumber(), campaignKey, e.getMessage(), consecutiveFailures);

                    if (consecutiveFailures >= request.getConsecutiveFailureThreshold()) {
                        request.getCheckpoint().accept(sentCount);
                        log.error("Campaign ID: {} aborted after {} consecutive gateway failures — scheduler will retry",
                                campaignKey, consecutiveFailures);
                        throw request.getAbortExceptionFactory().apply(consecutiveFailures);
                    }
                }
            }
        }

        return new DispatchOutcome(sentCount, true);
    }

    private void notifyPostSend(DispatchRequest request, ParticipantDDB participant, String messageSid) {
        if (request.getPostSendListener() == null) {
            return;
        }
        try {
            request.getPostSendListener().afterSend(participant, messageSid);
        } catch (Exception e) {
            log.warn("Post-send listener failed for bib {} in campaign {}: {}",
                    participant.getBibNumber(), request.getCampaignKey(), e.getMessage());
        }
    }
}
