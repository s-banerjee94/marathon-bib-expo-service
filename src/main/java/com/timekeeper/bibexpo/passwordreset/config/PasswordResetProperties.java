package com.timekeeper.bibexpo.passwordreset.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.password-reset")
@Data
public class PasswordResetProperties {

    /** Frontend origin the reset link points at; the user opens the set-new-password form there. */
    private String baseUrl = "https://localhost:8080";

    /** Frontend route that consumes the reset token. */
    private String resetPath = "/reset-password";

    /** Reset link lifetime in minutes; also the cache expiry. */
    private long ttlMinutes = 10;
}
