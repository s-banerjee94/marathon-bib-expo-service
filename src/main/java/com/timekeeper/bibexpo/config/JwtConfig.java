package com.timekeeper.bibexpo.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "jwt")
@Data
public class JwtConfig {
    private String secret;
    private String issuer;

    private Long accessTokenExpiration;
    private Long refreshTokenExpiration;

    private String cookieDomain;
    private String refreshCookieName;
    private String csrfCookieName;
    private Boolean cookieSecure;
}
