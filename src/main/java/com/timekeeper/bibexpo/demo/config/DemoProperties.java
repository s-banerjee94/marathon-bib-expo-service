package com.timekeeper.bibexpo.demo.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.demo")
@Data
public class DemoProperties {

    /** Lifetime of a demo session (and its QR) in minutes: how long the QR stays valid. */
    private long sessionTtlMinutes = 10;

    /**
     * Total minutes a demo code is remembered (drives cache eviction); must be &ge; sessionTtlMinutes.
     * The gap between the two is the grace window in which an expired code still answers 410 instead
     * of 404, so a late scan gets a friendly "grab a fresh QR" rather than "not found".
     */
    private long sessionRetentionMinutes = 15;

    /** Global cap on concurrently live (uncollected, unexpired) sessions; creation past it returns 429. */
    private long maxLiveSessions = 500;

    /** Session creations allowed per IP per minute. */
    private int createLimitPerMinute = 10;

    /** Collect attempts allowed per IP per minute. */
    private int collectLimitPerMinute = 30;
}
