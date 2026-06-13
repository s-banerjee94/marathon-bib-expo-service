package com.timekeeper.bibexpo.messaging.delivery;

import com.timekeeper.bibexpo.messaging.shared.enums.MessageChannel;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * Outcome of attempting to send a system message over one channel.
 */
@Getter
@Builder
@AllArgsConstructor
@Schema(description = "Outcome of sending a system message over one channel")
public class DeliveryResult {

    @Schema(description = "The channel", example = "WHATSAPP")
    private final MessageChannel channel;

    @Schema(description = "Whether the send succeeded", example = "true")
    private final boolean sent;

    @Schema(description = "Failure detail when not sent; null on success", example = "The WhatsApp template for INVITE has no Content SID configured.")
    private final String detail;
}
