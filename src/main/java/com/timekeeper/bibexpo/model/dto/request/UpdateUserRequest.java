package com.timekeeper.bibexpo.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for updating an existing user's profile.
 * All fields are optional - only provided fields will be updated.
 * Only basic profile fields can be updated (password, email, fullName, phoneNumber).
 * Administrative fields like role, organization, and account status require separate admin operations.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request for updating a user's profile")
public class UpdateUserRequest {

    @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
    @Schema(description = "New password for the user (will be encrypted)", example = "NewSecurePass123!", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String password;

    @Email(message = "Email must be valid")
    @Size(max = 100, message = "Email must not exceed 100 characters")
    @Schema(description = "Email address", example = "john.doe@example.com", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String email;

    @Size(max = 100, message = "Full name must not exceed 100 characters")
    @Schema(description = "Full name of the user", example = "John Doe", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String fullName;

    @Pattern(regexp = "^\\d{10}$", message = "must be a 10-digit number")
    @Schema(description = "Phone number, 10-digit Indian format", example = "9876543210", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String phoneNumber;
}
