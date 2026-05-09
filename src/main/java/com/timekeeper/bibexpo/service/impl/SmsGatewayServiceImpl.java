package com.timekeeper.bibexpo.service.impl;

import com.timekeeper.bibexpo.config.SmsApiProperties;
import com.timekeeper.bibexpo.exception.SmsSendException;
import com.timekeeper.bibexpo.service.SmsGatewayService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

@Service
@Profile("prod")
@Slf4j
public class SmsGatewayServiceImpl implements SmsGatewayService {

    private final RestClient restClient;
    private final String baseUrl;
    private final String username;
    private final String password;
    private final String sender;

    public SmsGatewayServiceImpl(RestClient restClient, SmsApiProperties properties) {
        this.restClient = restClient;
        this.baseUrl = properties.getUrl();
        this.username = properties.getUsername();
        this.password = properties.getPassword();
        this.sender = properties.getSender();

        try {
            new URI(baseUrl);
        } catch (Exception e) {
            throw new IllegalStateException("Invalid SMS gateway URL: " + baseUrl);
        }

        log.info("SMS gateway initialised — url: {}, sender: {}", baseUrl, sender);
    }

    @Override
    public void send(String phoneNumber, String message, String dltTemplateId) {
        try {
            URI uri = UriComponentsBuilder.fromUriString(baseUrl)
                    .queryParam("username", username)
                    .queryParam("password", password)
                    .queryParam("sender", sender)
                    .queryParam("to", phoneNumber)
                    .queryParam("message", message)
                    .queryParam("DLT_TE_ID", dltTemplateId)
                    .build()
                    .toUri();

            restClient.get()
                    .uri(uri)
                    .retrieve()
                    .toBodilessEntity();

            log.info("SMS dispatched to {} via gateway", phoneNumber);
        } catch (Exception e) {
            log.error("SMS gateway call failed for {}: {}", phoneNumber, e.getMessage());
            throw new SmsSendException("Gateway error for " + phoneNumber + ": " + e.getMessage());
        }
    }
}
