package com.timekeeper.bibexpo.passwordreset.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Detail returned when a reset token is opened, so the set-new-password form can render and confirm
 * which account is being reset. The token is only delivered to the account's own phone, so the owner
 * is the only party that can reach this.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Details of a valid password-reset token")
public class PasswordResetTokenStatusResponse {

    @Schema(description = "Username of the account being reset", example = "john.doe")
    private String username;

    @Schema(description = "Full name of the account being reset", example = "John Doe")
    private String fullName;

    @Schema(description = "When the reset link expires")
    private Instant expiresAt;
}
