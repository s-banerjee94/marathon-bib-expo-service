package com.timekeeper.bibexpo.whatsapp.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "whatsapp.scheduler")
@Data
public class WhatsAppSchedulerProperties {

    private long sendDelayMs = 250;
    private int maxRetryCount = 2;
    private int stuckThresholdMinutes = 10;
}
