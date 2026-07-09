package com.timekeeper.bibexpo.messaging.campaign.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "sms.scheduler")
@Data
public class SmsSchedulerProperties {

    private long sendDelayMs = 10;
    private int maxRetryCount = 2;
    private int stuckThresholdMinutes = 20;
}
