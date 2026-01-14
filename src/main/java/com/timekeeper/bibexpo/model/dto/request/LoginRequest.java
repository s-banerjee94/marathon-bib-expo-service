package com.timekeeper.bibexpo.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Login request with username and password")
public class LoginRequest {

    @NotBlank(message = "Username is required")
    @Schema(description = "Username", example = "root")
    private String username;

    @NotBlank(message = "Password is required")
    @Schema(description = "Password", example = "root")
    private String password;
}
