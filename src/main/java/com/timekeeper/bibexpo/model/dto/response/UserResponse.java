package com.timekeeper.bibexpo.model.dto.response;

import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.model.entity.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * DTO for user response.
 * Returns user information without sensitive data like password.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "User information response")
public class UserResponse {

    @Schema(description = "User ID", example = "1")
    private Long id;

    @Schema(description = "Username", example = "john_doe")
    private String username;

    @Schema(description = "Email address", example = "john.doe@example.com")
    private String email;

    @Schema(description = "Full name", example = "John Doe")
    private String fullName;

    @Schema(description = "Phone number", example = "+1234567890")
    private String phoneNumber;

    @Schema(description = "User role", example = "ADMIN")
    private UserRole role;

    @Schema(description = "Short-lived presigned URL for the profile picture, null if none set",
            example = "https://bucket.s3.ap-south-1.amazonaws.com/users/1/profile/uuid.png?X-Amz-...")
    private String profilePictureUrl;

    @Schema(description = "Organization ID (null for system-level roles)", example = "1")
    private Long organizationId;

    @Schema(description = "Organization name (null for system-level roles)", example = "Marathon Organizers Inc")
    private String organizationName;

    @Schema(description = "Whether the account is enabled", example = "true")
    private Boolean enabled;

    @Schema(description = "Whether the account is non-expired", example = "true")
    private Boolean accountNonExpired;

    @Schema(description = "Whether the account is non-locked", example = "true")
    private Boolean accountNonLocked;

    @Schema(description = "Whether the credentials are non-expired", example = "true")
    private Boolean credentialsNonExpired;

    @Schema(description = "When the user was created", example = "2026-01-15T10:30:00Z")
    private Instant createdAt;

    @Schema(description = "When the user was last updated", example = "2026-01-15T10:30:00Z")
    private Instant updatedAt;

    @Schema(description = "Username who created this user", example = "root")
    private String createdBy;

    @Schema(description = "Username who last modified this user", example = "admin")
    private String lastModifiedBy;

    /**
     * Factory method to create UserResponse from User entity
     */
    public static UserResponse fromEntity(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .phoneNumber(user.getPhoneNumber())
                .role(user.getRole())
                .organizationId(user.getOrganization() != null ? user.getOrganization().getId() : null)
                .organizationName(user.getOrganization() != null ? user.getOrganization().getOrganizerName() : null)
                .enabled(user.getEnabled())
                .accountNonExpired(user.getAccountNonExpired())
                .accountNonLocked(user.getAccountNonLocked())
                .credentialsNonExpired(user.getCredentialsNonExpired())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .createdBy(user.getCreatedBy())
                .lastModifiedBy(user.getLastModifiedBy())
                .build();
    }
}
