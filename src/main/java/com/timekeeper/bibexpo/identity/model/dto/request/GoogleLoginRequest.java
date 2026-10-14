package com.timekeeper.bibexpo.identity.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Sign in with Google: the ID token Google issued to the frontend")
public class GoogleLoginRequest {

    @NotBlank(message = "Google ID token is required")
    @Schema(description = "The ID token (the credential JWT) returned by Google Identity Services", example = "eyJhbGciOiJSUzI1NiIsImtpZCI6...")
    private String idToken;
}
