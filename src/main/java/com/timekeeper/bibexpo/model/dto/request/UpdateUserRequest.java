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
    @Schema(description = "New password for the user (will be encrypted)", example = "NewSecurePass123!")
    private String password;

    @Email(message = "Email must be valid")
    @Size(max = 100, message = "Email must not exceed 100 characters")
    @Schema(description = "Email address", example = "john.doe@example.com")
    private String email;

    @Size(max = 100, message = "Full name must not exceed 100 characters")
    @Schema(description = "Full name of the user", example = "John Doe")
    private String fullName;

    @Pattern(regexp = "^[+]?[0-9]{10,15}$", message = "Phone number must be valid (10-15 digits, optional + prefix)")
    @Schema(description = "Phone number", example = "+1234567890")
    private String phoneNumber;
}
