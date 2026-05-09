package com.timekeeper.bibexpo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.client.RestClient;

@Configuration
@Profile("prod")
public class RestClientConfig {

    @Bean
    public RestClient restClient() {
        return RestClient.create();
    }
}
