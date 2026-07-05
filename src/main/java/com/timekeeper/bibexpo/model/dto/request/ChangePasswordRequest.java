package com.timekeeper.bibexpo.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for a signed-in user changing their own password. The current password is required and
 * verified before the new one is set, so a hijacked session cannot silently change the password.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to change the signed-in user's own password")
public class ChangePasswordRequest {

    @NotBlank(message = "Current password is required")
    @Schema(description = "The user's existing password, re-entered to authorize the change", example = "OldSecurePass123!")
    private String currentPassword;

    @NotBlank(message = "New password is required")
    @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
    @Schema(description = "New password (will be encrypted)", example = "NewSecurePass123!")
    private String newPassword;
}
