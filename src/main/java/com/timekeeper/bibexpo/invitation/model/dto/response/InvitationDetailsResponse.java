package com.timekeeper.bibexpo.invitation.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Public view of a pending invite so the accept form can show the fixed role and
 * organization. Carries no token and no sensitive data.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Fixed details of a pending invite")
public class InvitationDetailsResponse {

    @Schema(description = "Role the account will be created with", example = "DISTRIBUTOR")
    private String role;

    @Schema(description = "Organization ID (null for system-level roles)", example = "7")
    private Long organizationId;

    @Schema(description = "Organization name (null for system-level roles)", example = "Mumbai Runners")
    private String organizationName;

    @Schema(description = "Event ID the distributor will be assigned to (null for non-distributor roles)", example = "10")
    private Long eventId;

    @Schema(description = "Event name the distributor will be assigned to (null for non-distributor roles)", example = "Mumbai Marathon 2026")
    private String eventName;

    @Schema(description = "Phone number the invite was sent to, to pre-fill the accept form (null if none)", example = "9876543210")
    private String recipientPhone;

    @Schema(description = "When the invite expires", example = "2026-06-11T14:35:00Z")
    private Instant expiresAt;
}
