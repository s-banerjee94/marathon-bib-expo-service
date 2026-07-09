package com.timekeeper.bibexpo.invitation.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.invite")
@Data
public class InviteProperties {

    /** Frontend origin the invite link points at; the invitee opens the account-creation form there. */
    private String baseUrl = "https://localhost:8080";

    /** Frontend route that consumes the invite token. */
    private String acceptPath = "/accept-invite";

    /** Invite link lifetime in minutes; also the cache expiry. */
    private long ttlMinutes = 5;
}
