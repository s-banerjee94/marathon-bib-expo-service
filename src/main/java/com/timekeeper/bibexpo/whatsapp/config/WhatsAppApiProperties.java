package com.timekeeper.bibexpo.whatsapp.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "whatsapp.api")
@Data
public class WhatsAppApiProperties {

    private String url;
    private String accountSid;
    private String authToken;
    private String fromNumber;
}
