package com.timekeeper.bibexpo.messaging.delivery;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * The per-send payload handed to {@code MessagingProviderClient}. Carries the recipient, the
 * registered template id, and the content in both forms — the already-rendered text
 * (client-rendered providers) and the ordered variables (provider-rendered providers). The client
 * picks whichever its param mapping references.
 */
@Getter
@Builder
public class OutboundMessage {

    private final String recipientPhone;

    /**
     * Registered template id. Required for every SMS (India DLT compliance) and used as the
     * template reference for provider-rendered channels such as Twilio WhatsApp.
     */
    private final String templateId;

    /** Registered DLT header / sender id, carried per message-type. */
    private final String senderId;

    /** Finished message text — for client-rendered providers that send the body themselves. */
    private final String message;

    /** Ordered variable values — for provider-rendered providers that hold the template. */
    private final List<String> variables;
}
