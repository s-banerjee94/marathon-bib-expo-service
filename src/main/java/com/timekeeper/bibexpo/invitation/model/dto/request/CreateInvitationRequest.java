package com.timekeeper.bibexpo.invitation.model.dto.request;

import com.timekeeper.bibexpo.messaging.shared.enums.MessageChannel;
import com.timekeeper.bibexpo.model.entity.UserRole;
import com.timekeeper.bibexpo.validation.ValidEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

/**
 * Request to issue an invite link. The role and organization are fixed here and become the
 * trusted target when the invitee accepts; the invitee cannot change them.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to issue a user-invite link")
public class CreateInvitationRequest {

    @NotNull(message = "Role is required")
    @ValidEnum(enumClass = UserRole.class, excludes = {"ROOT"})
    @Schema(description = "Role the invited user will be created with", example = "DISTRIBUTOR",
            implementation = String.class,
            allowableValues = {"ADMIN", "ORGANIZER_ADMIN", "ORGANIZER_USER", "DISTRIBUTOR"})
    private String role;

    @Schema(description = "Organization ID (required for ORGANIZER_ADMIN, ORGANIZER_USER, DISTRIBUTOR)", example = "7", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Long organizationId;

    @Schema(description = "Event ID the distributor is assigned to (required for DISTRIBUTOR; ignored for other roles)", example = "10", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Long eventId;

    @Schema(description = "Channels to deliver the invite link on. Omit for manual (the URL is just returned).",
            example = "[\"WHATSAPP\"]",
            allowableValues = {"WHATSAPP", "SMS"}, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Set<MessageChannel> deliveryChannels;

    @Pattern(regexp = "^\\d{10}$", message = "must be a 10-digit number")
    @Schema(description = "Invitee phone number, 10 digits without country code; the provider configuration decides whether the +91 country code is added at send time. "
            + "Required when a phone channel (WHATSAPP/SMS) is selected. Also pre-fills the accept form.",
            example = "9876543210", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String recipientPhone;
}
