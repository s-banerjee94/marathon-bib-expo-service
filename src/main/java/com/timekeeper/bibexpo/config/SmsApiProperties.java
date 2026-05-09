package com.timekeeper.bibexpo.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "sms.api")
@Data
public class SmsApiProperties {

    private String url;
    private String username;
    private String password;
    private String sender;
}
