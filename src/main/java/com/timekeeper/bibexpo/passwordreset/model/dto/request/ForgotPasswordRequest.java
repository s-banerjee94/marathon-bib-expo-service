package com.timekeeper.bibexpo.passwordreset.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Public forgot-password request. The identifier is matched against a username, email, or phone
 * number. The response is always the same generic acknowledgement, so this never discloses whether
 * an account exists.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request a password-reset link for your own account")
public class ForgotPasswordRequest {

    @NotBlank(message = "An account username, email, or phone number is required")
    @Schema(description = "The account's username, email, or 10-digit phone number", example = "john.doe@example.com")
    private String identifier;
}
