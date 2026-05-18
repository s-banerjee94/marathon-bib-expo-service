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
@Schema(description = "Login response with short-lived access token and user details. Refresh token is set as an HttpOnly cookie.")
public class LoginResponse {

    @Schema(description = "JWT access token (Bearer)", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
    private String accessToken;

    @Schema(description = "Access token expiration in milliseconds", example = "900000")
    private Long expiresIn;

    @Schema(description = "User ID", example = "1")
    private Long userId;

    @Schema(description = "Username", example = "admin")
    private String username;

    @Schema(description = "User role", example = "ORGANIZER_ADMIN")
    private String role;

    @Schema(description = "Organization ID (nullable)", example = "123")
    private Long organizationId;
}
