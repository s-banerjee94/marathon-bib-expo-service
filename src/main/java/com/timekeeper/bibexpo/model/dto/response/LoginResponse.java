package com.timekeeper.bibexpo.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Login response with JWT token and user details")
public class LoginResponse {

    @Schema(description = "JWT access token", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
    private String token;

    @Schema(description = "Token type", example = "Bearer")
    private String tokenType;

    @Schema(description = "Token expiration time in milliseconds", example = "604800000")
    private Long expiresIn;

    @Schema(description = "Username", example = "admin")
    private String username;

    @Schema(description = "User role", example = "ROLE_ADMIN")
    private String role;

    @Schema(description = "Organization ID (nullable)", example = "123")
    private Long organizationId;
}
