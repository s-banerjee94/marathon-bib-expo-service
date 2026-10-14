package com.timekeeper.bibexpo.identity.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Login request with username or email, and password")
public class LoginRequest {

    @NotBlank(message = "Username or email is required")
    @Schema(description = "Username or email address; a value containing '@' is matched as an email, case-insensitively", example = "root")
    private String username;

    @NotBlank(message = "Password is required")
    @Schema(description = "Password", example = "root")
    private String password;
}
