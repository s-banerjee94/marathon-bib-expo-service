package com.timekeeper.bibexpo.messaging.provider.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * A one-off test message used to verify a provider configuration before relying on it. The fields
 * map to the same {@code OutboundMessage} tokens a real send uses: SMS supplies {@code message} +
 * {@code templateId} (DLT id); WhatsApp supplies {@code templateId} (Content SID) + {@code variables}.
 */
@Data
@Schema(description = "Test message for verifying a provider configuration")
public class ProviderTestSendRequest {

    @NotBlank
    @Schema(description = "Recipient phone number, country-coded", example = "919876543210")
    private String recipientPhone;

    @Schema(description = "Registered template id: DLT template id (SMS) or Content SID (WhatsApp)", example = "1707160000000000000", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String templateId;

    @Schema(description = "Registered DLT header / sender id", example = "BIBEXP", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String senderId;

    @Schema(description = "Finished message text, for client-rendered channels (SMS)", example = "Test message from your campaign provider.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String message;

    @Schema(description = "Ordered variable values, for provider-rendered channels (WhatsApp)", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private List<String> variables;
}
