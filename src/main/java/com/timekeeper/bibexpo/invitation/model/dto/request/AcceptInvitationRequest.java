package com.timekeeper.bibexpo.invitation.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Personal details the invitee supplies when accepting an invite. The role and organization
 * are taken from the stored invitation, never from this payload.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Details supplied by the invitee to complete their account")
public class AcceptInvitationRequest {

    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "Username can only contain letters, numbers, underscores, and hyphens")
    @Schema(description = "Unique username for the new account", example = "ravi_d")
    private String username;

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
    @Schema(description = "Password for the new account", example = "SecurePass123!")
    private String password;

    @Email(message = "Email must be valid")
    @Size(max = 100, message = "Email must not exceed 100 characters")
    @Schema(description = "Email address (required unless the invited role is DISTRIBUTOR)", example = "ravi.das@example.com")
    private String email;

    @NotBlank(message = "Full name is required")
    @Size(max = 100, message = "Full name must not exceed 100 characters")
    @Schema(description = "Full name of the new user", example = "Ravi Das")
    private String fullName;

    @Pattern(regexp = "^\\d{10}$", message = "must be a 10-digit number")
    @Schema(description = "Phone number (required unless the invited role is DISTRIBUTOR), 10-digit Indian format", example = "9876543210")
    private String phoneNumber;
}
