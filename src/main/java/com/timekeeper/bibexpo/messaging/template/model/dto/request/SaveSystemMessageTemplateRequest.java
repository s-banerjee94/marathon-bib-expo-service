package com.timekeeper.bibexpo.messaging.template.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * Root request to create or replace a system message template. The purpose and channel come from
 * the path. Use {@code body} for client-rendered channels (SMS) and {@code variables} for
 * provider-rendered channels (WhatsApp).
 */
@Data
@Schema(description = "System message template content for one purpose × channel")
public class SaveSystemMessageTemplateRequest {

    @Schema(description = "Message text with #{...} placeholders, for client-rendered channels (SMS)",
            example = "BibExpo: You're invited as #{role}. Create your account: #{inviteUrl}")
    private String body;

    @Schema(description = "Newline-separated #{...} expressions mapped to positional variables, for provider-rendered channels (WhatsApp)",
            example = "#{role}\n#{organizationName}\n#{inviteUrl}")
    private String variables;

    @Schema(description = "Registered template id: DLT template id (SMS) or provider Content SID (WhatsApp)", example = "1707171234567890123")
    private String dltTemplateId;

    @Schema(description = "Registered DLT header / sender id", example = "BIBEXP")
    private String senderId;

    @Schema(description = "Whether this template is active for sending", example = "true")
    private boolean enabled;
}
