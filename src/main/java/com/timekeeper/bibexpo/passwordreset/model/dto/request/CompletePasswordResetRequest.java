package com.timekeeper.bibexpo.passwordreset.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Public request to complete a password reset. The token itself is the credential (supplied in the
 * path), so only the new password is carried here.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Set a new password using a valid reset token")
public class CompletePasswordResetRequest {

    @NotBlank(message = "New password is required")
    @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
    @Schema(description = "New password (will be encrypted)", example = "NewSecurePass123!")
    private String newPassword;
}
