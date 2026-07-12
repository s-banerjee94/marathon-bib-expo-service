package com.timekeeper.bibexpo.demo.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.demo")
@Data
public class DemoProperties {

    /** Lifetime of a demo session (and its QR) in minutes. */
    private long sessionTtlMinutes = 3;

    /** Global cap on concurrently live (uncollected, unexpired) sessions; creation past it returns 429. */
    private long maxLiveSessions = 500;

    /** Session creations allowed per IP per minute. */
    private int createLimitPerMinute = 10;

    /** Collect attempts allowed per IP per minute. */
    private int collectLimitPerMinute = 30;
}
