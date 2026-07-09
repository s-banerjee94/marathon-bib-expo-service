package com.timekeeper.bibexpo.passwordreset.model.dto.request;

import com.timekeeper.bibexpo.messaging.shared.enums.MessageChannel;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

/**
 * Administrator request to issue a reset link for a user. The link is always returned in the
 * response so the administrator can share it; the optional channels additionally deliver it to the
 * user's own registered phone as a convenience.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Issue a password-reset link for a user")
public class IssueResetLinkRequest {

    @Schema(description = "Channels to also deliver the link on, to the user's own registered phone. "
            + "Omit to just return the link for manual sharing.",
            example = "[\"WHATSAPP\"]",
            allowableValues = {"WHATSAPP", "SMS"}, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Set<MessageChannel> deliveryChannels;
}
