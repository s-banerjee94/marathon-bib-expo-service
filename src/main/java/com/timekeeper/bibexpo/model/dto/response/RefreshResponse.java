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
@Schema(description = "Refresh response with new short-lived access token. New refresh token is rotated as an HttpOnly cookie.")
public class RefreshResponse {

    @Schema(description = "New JWT access token (Bearer)")
    private String accessToken;

    @Schema(description = "Access token expiration in milliseconds", example = "900000")
    private Long expiresIn;
}
