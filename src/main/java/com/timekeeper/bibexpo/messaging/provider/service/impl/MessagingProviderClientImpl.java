package com.timekeeper.bibexpo.messaging.provider.service.impl;

import com.timekeeper.bibexpo.messaging.shared.enums.MessageChannel;
import com.timekeeper.bibexpo.messaging.provider.exception.MessagingProviderException;
import com.timekeeper.bibexpo.messaging.delivery.OutboundMessage;
import com.timekeeper.bibexpo.messaging.provider.model.ProviderParam;
import com.timekeeper.bibexpo.messaging.provider.model.entity.MessagingProvider;
import com.timekeeper.bibexpo.messaging.provider.model.enums.HttpMethodType;
import com.timekeeper.bibexpo.messaging.provider.model.enums.MessageContentType;
import com.timekeeper.bibexpo.messaging.provider.repository.MessagingProviderRepository;
import com.timekeeper.bibexpo.messaging.provider.service.MessagingProviderClient;
import com.timekeeper.bibexpo.messaging.provider.service.impl.RequestTokenResolver.Escape;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads the channel's provider row and replays it into one HTTP call. Every value — the URL, each
 * header/query field, and the body template — is run through {@link RequestTokenResolver}, so a
 * provider's entire request shape (GET query, POST JSON, POST form, Basic auth, account-id in the
 * URL) is expressed as data. Switching or adding a provider is a data change, not a code change.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MessagingProviderClientImpl implements MessagingProviderClient {

    private final MessagingProviderRepository providerRepository;
    private final RestClient restClient;
    private final RequestTokenResolver tokenResolver;

    @Override
    public void send(MessageChannel channel, OutboundMessage message) {
        MessagingProvider provider = providerRepository.findByChannel(channel)
                .orElseThrow(() -> new MessagingProviderException(
                        "No " + channel + " provider is configured."));
        if (!provider.isEnabled()) {
            throw new MessagingProviderException("The " + channel + " provider is disabled.");
        }
        if (provider.getBaseUrl() == null || provider.getBaseUrl().isBlank()) {
            throw new MessagingProviderException("The " + channel + " provider has no endpoint configured.");
        }

        Map<String, String> headers = new LinkedHashMap<>();
        UriComponentsBuilder uri = UriComponentsBuilder.fromUriString(
                tokenResolver.resolve(provider.getBaseUrl(), Escape.NONE, provider, message));

        for (ProviderParam param : safeParams(provider)) {
            if (param.getName() == null || param.getName().isBlank() || param.getLocation() == null) {
                continue;
            }
            String value = tokenResolver.resolve(param.getValue(), Escape.NONE, provider, message);
            switch (param.getLocation()) {
                case HEADER -> headers.put(param.getName(), value);
                case QUERY -> uri.queryParam(param.getName(), value);
            }
        }

        boolean post = provider.getHttpMethod() == HttpMethodType.POST;
        boolean form = provider.getContentType() == MessageContentType.FORM;
        String body = post
                ? tokenResolver.resolve(provider.getBodyTemplate(), form ? Escape.FORM : Escape.JSON, provider, message)
                : null;

        String url = uri.build().encode().toUriString();
        logRequest(channel, provider, url, headers, body);
        fire(provider, url, headers, body, channel);
        log.info("System message dispatched over {} to {}", channel, message.getRecipientPhone());
    }

    /**
     * Logs the fully-assembled provider request (method, URL, headers, body) before it is sent, with
     * the provider's own secrets redacted. Lets a configured request be inspected even when the
     * actual delivery cannot complete yet (e.g. no registered DLT template).
     */
    private void logRequest(MessageChannel channel, MessagingProvider provider, String url,
                            Map<String, String> headers, String body) {
        log.info("[SYS-MSG] {} request: {} {}", channel, provider.getHttpMethod(), redact(url, provider));
        if (!headers.isEmpty()) {
            log.info("[SYS-MSG] {} headers: {}", channel, redactMap(headers, provider));
        }
        if (provider.getHttpMethod() == HttpMethodType.POST && body != null && !body.isBlank()) {
            log.info("[SYS-MSG] {} body: {}", channel, redact(body, provider));
        }
    }

    private Map<String, String> redactMap(Map<String, String> source, MessagingProvider provider) {
        Map<String, String> out = new LinkedHashMap<>();
        source.forEach((key, value) -> out.put(key, redact(value, provider)));
        return out;
    }

    private String redact(String text, MessagingProvider provider) {
        String result = replaceSecret(text, provider.getAuthToken());
        result = replaceSecret(result, provider.getPassword());
        return replaceSecret(result, basicAuthSecret(provider));
    }

    private String basicAuthSecret(MessagingProvider provider) {
        if (provider.getPassword() == null || provider.getPassword().isBlank()) {
            return null;
        }
        String username = provider.getUsername() == null ? "" : provider.getUsername();
        return Base64.getEncoder()
                .encodeToString((username + ":" + provider.getPassword()).getBytes(StandardCharsets.UTF_8));
    }

    private String replaceSecret(String text, String secret) {
        if (text == null || secret == null || secret.isBlank()) {
            return text;
        }
        return text.replace(secret, "****");
    }

    private void fire(MessagingProvider provider, String url, Map<String, String> headers,
                      String body, MessageChannel channel) {
        try {
            if (provider.getHttpMethod() == HttpMethodType.GET) {
                RestClient.RequestHeadersSpec<?> spec = restClient.get().uri(url);
                headers.forEach(spec::header);
                spec.retrieve().toBodilessEntity();
                return;
            }
            RestClient.RequestBodySpec spec = restClient.post().uri(url);
            headers.forEach(spec::header);
            spec.contentType(provider.getContentType() == MessageContentType.FORM
                    ? MediaType.APPLICATION_FORM_URLENCODED
                    : MediaType.APPLICATION_JSON);
            if (body != null && !body.isBlank()) {
                spec.body(body);
            }
            spec.retrieve().toBodilessEntity();
        } catch (RestClientResponseException e) {
            log.warn("[SYS-MSG] {} provider returned {}: {}", channel, e.getStatusCode(),
                    redact(e.getResponseBodyAsString(), provider));
            throw new MessagingProviderException("The " + channel + " provider call failed.", e);
        } catch (Exception e) {
            // No HTTP response (bad URL, connection refused, timeout) — surface the cause so it is diagnosable.
            log.warn("[SYS-MSG] {} call could not be completed: {}", channel, e.toString());
            throw new MessagingProviderException("The " + channel + " provider call failed.", e);
        }
    }

    private List<ProviderParam> safeParams(MessagingProvider provider) {
        return provider.getRequestParams() == null ? new ArrayList<>() : provider.getRequestParams();
    }
}
