package com.timekeeper.bibexpo.model.dto.request;

import com.timekeeper.bibexpo.model.entity.UserRole;
import com.timekeeper.bibexpo.validator.ValidEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for creating a new user.
 * Used by root user to create admin, org admin, org user, and distributor accounts.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request for creating a new user")
public class CreateUserRequest {

    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "Username can only contain letters, numbers, underscores, and hyphens")
    @Schema(description = "Unique username for the user", example = "john_doe")
    private String username;

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
    @Schema(description = "Password for the user (will be encrypted)", example = "SecurePass123!")
    private String password;

    @Email(message = "Email must be valid")
    @Size(max = 100, message = "Email must not exceed 100 characters")
    @Schema(description = "Email address (required for ADMIN, ORG_ADMIN, ORG_USER; optional for DISTRIBUTOR)", example = "john.doe@example.com")
    private String email;

    @NotBlank(message = "Full name is required")
    @Size(max = 100, message = "Full name must not exceed 100 characters")
    @Schema(description = "Full name of the user", example = "John Doe")
    private String fullName;

    @Pattern(regexp = "^\\d{10}$", message = "must be a 10-digit number")
    @Schema(description = "Phone number (required for ADMIN, ORG_ADMIN, ORG_USER; optional for DISTRIBUTOR), 10-digit Indian format", example = "9876543210")
    private String phoneNumber;

    @NotNull(message = "Role is required")
    @ValidEnum(enumClass = UserRole.class, excludes = {"ROOT"})
    @Schema(description = "Role to assign to the user", example = "ADMIN",
            implementation = String.class,
            allowableValues = {"ADMIN", "ORGANIZER_ADMIN", "ORGANIZER_USER", "DISTRIBUTOR"})
    private String role;

    @Schema(description = "Organization ID (required for ORGANIZER_ADMIN, ORGANIZER_USER, DISTRIBUTOR)", example = "1")
    private Long organizationId;

    @Builder.Default
    @Schema(description = "Whether the account is enabled", example = "true", defaultValue = "true")
    private Boolean enabled = true;

    @Builder.Default
    @Schema(description = "Whether the account is non-expired", example = "true", defaultValue = "true")
    private Boolean accountNonExpired = true;

    @Builder.Default
    @Schema(description = "Whether the account is non-locked", example = "true", defaultValue = "true")
    private Boolean accountNonLocked = true;

    @Builder.Default
    @Schema(description = "Whether the credentials are non-expired", example = "true", defaultValue = "true")
    private Boolean credentialsNonExpired = true;
}
