package com.timekeeper.bibexpo.whatsapp.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.timekeeper.bibexpo.whatsapp.config.WhatsAppApiProperties;
import com.timekeeper.bibexpo.whatsapp.exception.WhatsAppSendException;
import com.timekeeper.bibexpo.whatsapp.model.WhatsAppSender;
import com.timekeeper.bibexpo.whatsapp.service.WhatsAppGatewayService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Profile("prod")
@Slf4j
public class WhatsAppGatewayServiceImpl implements WhatsAppGatewayService {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;

    public WhatsAppGatewayServiceImpl(RestClient restClient, ObjectMapper objectMapper,
                                      WhatsAppApiProperties properties) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.baseUrl = properties.getUrl();

        try {
            new URI(baseUrl);
        } catch (Exception e) {
            throw new IllegalStateException("Invalid WhatsApp gateway URL: " + baseUrl);
        }

        log.info("WhatsApp gateway initialised — url: {}", baseUrl);
    }

    @Override
    public String sendTemplate(WhatsAppSender sender, String to, String contentSid, List<String> variables) {
        String messagesUrl = baseUrl + "/2010-04-01/Accounts/" + sender.getAccountSid() + "/Messages.json";

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("To", normalizeWhatsAppNumber(to));
        form.add("From", normalizeWhatsAppNumber(sender.getFromNumber()));
        form.add("ContentSid", contentSid);
        form.add("ContentVariables", buildContentVariables(variables));

        try {
            JsonNode response = restClient.post()
                    .uri(messagesUrl)
                    .header(HttpHeaders.AUTHORIZATION, basicAuth(sender.getAccountSid(), sender.getAuthToken()))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class);

            String messageSid = response != null ? response.path("sid").asText(null) : null;
            log.info("WhatsApp message accepted for {} (contentSid={}, sid={})", to, contentSid, messageSid);
            return messageSid;
        } catch (Exception e) {
            log.error("WhatsApp gateway call failed for {}: {}", to, e.getMessage());
            throw new WhatsAppSendException("The WhatsApp message could not be sent, please try again later.");
        }
    }

    private String buildContentVariables(List<String> variables) {
        if (variables == null || variables.isEmpty()) {
            return "{}";
        }
        Map<String, String> positional = new LinkedHashMap<>();
        for (int i = 0; i < variables.size(); i++) {
            positional.put(String.valueOf(i + 1), variables.get(i));
        }
        try {
            return objectMapper.writeValueAsString(positional);
        } catch (JsonProcessingException e) {
            throw new WhatsAppSendException("The WhatsApp message could not be sent, please try again later.");
        }
    }

    private String basicAuth(String accountSid, String authToken) {
        String credentials = accountSid + ":" + authToken;
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    private String normalizeWhatsAppNumber(String number) {
        String trimmed = number.trim();
        if (trimmed.startsWith("whatsapp:")) {
            return trimmed;
        }
        if (!trimmed.startsWith("+")) {
            trimmed = "+" + trimmed;
        }
        return "whatsapp:" + trimmed;
    }
}
