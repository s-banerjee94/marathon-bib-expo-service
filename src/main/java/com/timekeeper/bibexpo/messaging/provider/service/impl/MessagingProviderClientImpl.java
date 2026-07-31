package com.timekeeper.bibexpo.messaging.provider.service.impl;

import com.timekeeper.bibexpo.messaging.shared.enums.MessageChannel;
import com.timekeeper.bibexpo.messaging.provider.exception.MessagingProviderException;
import com.timekeeper.bibexpo.messaging.delivery.OutboundMessage;
import com.timekeeper.bibexpo.messaging.provider.model.entity.MessagingProvider;
import com.timekeeper.bibexpo.messaging.provider.model.enums.HttpMethodType;
import com.timekeeper.bibexpo.messaging.provider.model.enums.MessageContentType;
import com.timekeeper.bibexpo.messaging.provider.service.MessagingProviderCache;
import com.timekeeper.bibexpo.messaging.provider.service.MessagingProviderClient;
import com.timekeeper.bibexpo.messaging.provider.service.impl.ProviderRequestBuilder.ProviderRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.URI;

/**
 * Reads the channel's provider row and replays it into one HTTP call. Every value — the URL, each
 * header/query field, and the body template — is run through {@link RequestTokenResolver}, so a
 * provider's entire request shape (GET query, POST JSON, POST form, Basic auth, account-id in the
 * URL) is expressed as data. Switching or adding a provider is a data change, not a code change.
 */
@Service
@ConditionalOnProperty(name = "messaging.stub-enabled", havingValue = "false", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class MessagingProviderClientImpl implements MessagingProviderClient {

    private final MessagingProviderCache providerCache;
    private final RestClient restClient;
    private final ProviderRequestBuilder requestBuilder;

    @Override
    public void send(MessageChannel channel, OutboundMessage message) {
        MessagingProvider provider = providerCache.findSystem(channel)
                .orElseThrow(() -> new MessagingProviderException(
                        "No " + channel + " provider is configured."));
        if (!provider.isEnabled()) {
            throw new MessagingProviderException("The " + channel + " provider is disabled.");
        }
        send(provider, message);
    }

    // No enabled-gate here: the SYSTEM path checks above, the campaign resolver only returns enabled
    // rows, and a test-send must be able to exercise a provider before it is switched on.
    @Override
    public void send(MessagingProvider provider, OutboundMessage message) {
        MessageChannel channel = provider.getChannel();
        ProviderRequest request = requestBuilder.build(provider, message);

        logRequest(channel, provider, request);
        fire(provider, request, channel);
        log.info("Message dispatched over {} to {}", channel, message.getRecipientPhone());
    }

    /**
     * Logs the fully-assembled provider request (method, URL, headers, body) before it is sent, with
     * the provider's own secrets redacted. Lets a configured request be inspected even when the
     * actual delivery cannot complete yet (e.g. no registered DLT template).
     */
    private void logRequest(MessageChannel channel, MessagingProvider provider, ProviderRequest request) {
        ProviderRequest safe = requestBuilder.redacted(request, provider);
        log.info("[SYS-MSG] {} request: {} {}", channel, safe.method(), safe.url());
        if (!safe.headers().isEmpty()) {
            log.info("[SYS-MSG] {} headers: {}", channel, safe.headers());
        }
        if (safe.method() == HttpMethodType.POST && safe.body() != null && !safe.body().isBlank()) {
            log.info("[SYS-MSG] {} body: {}", channel, safe.body());
        }
    }

    private void fire(MessagingProvider provider, ProviderRequest request, MessageChannel channel) {
        try {
            // url is already fully percent-encoded (uri.build().encode()); pass a URI so RestClient's
            // default UriBuilderFactory does not encode it a second time (double-encoding query values).
            String response;
            if (ProviderRequestBuilder.carriesBody(request.method())) {
                RestClient.RequestBodySpec spec = restClient
                        .method(HttpMethod.valueOf(request.method().name())).uri(URI.create(request.url()));
                request.headers().forEach(spec::header);
                spec.contentType(mediaTypeFor(provider.getContentType()));
                if (request.body() != null && !request.body().isBlank()) {
                    spec.body(request.body());
                }
                response = spec.retrieve().body(String.class);
            } else {
                RestClient.RequestHeadersSpec<?> spec = restClient.get().uri(URI.create(request.url()));
                request.headers().forEach(spec::header);
                response = spec.retrieve().body(String.class);
            }
            requireSuccessBody(provider, channel, response);
        } catch (RestClientResponseException e) {
            log.warn("[SYS-MSG] {} provider returned {}: {}", channel, e.getStatusCode(),
                    requestBuilder.redact(e.getResponseBodyAsString(), provider));
            throw new MessagingProviderException("The " + channel + " provider call failed.", e);
        } catch (MessagingProviderException e) {
            throw e;
        } catch (Exception e) {
            // No HTTP response (bad URL, connection refused, timeout) — surface the cause so it is diagnosable.
            log.warn("[SYS-MSG] {} call could not be completed: {}", channel, e.toString());
            throw new MessagingProviderException("The " + channel + " provider call failed.", e);
        }
    }

    /**
     * Treats a 2xx response as a failure when the provider is configured to prove success in the body.
     * Plenty of gateways answer 200 with {@code {"status":"error"}} and bill for it; without this the
     * send is recorded as delivered and nobody finds out.
     */
    private void requireSuccessBody(MessagingProvider provider, MessageChannel channel, String response) {
        String expected = provider.getSuccessContains();
        // Logged at info alongside the request: it is how an administrator finds the text that proves a
        // send worked, which is the value the success marker has to be set to.
        log.info("[SYS-MSG] {} provider response: {}", channel,
                response == null ? "<empty>" : requestBuilder.redact(response, provider));

        if (expected == null || expected.isBlank()) {
            return;
        }
        if (response == null || !response.contains(expected)) {
            log.warn("[SYS-MSG] {} provider accepted the request but did not confirm success: {}", channel,
                    response == null ? "<empty>" : requestBuilder.redact(response, provider));
            throw new MessagingProviderException("The " + channel + " provider did not confirm the message was sent.");
        }
    }

    private MediaType mediaTypeFor(MessageContentType contentType) {
        if (contentType == null) {
            return MediaType.APPLICATION_JSON;
        }
        return switch (contentType) {
            case JSON -> MediaType.APPLICATION_JSON;
            case FORM -> MediaType.APPLICATION_FORM_URLENCODED;
            case XML -> MediaType.APPLICATION_XML;
            case TEXT -> MediaType.TEXT_PLAIN;
        };
    }
}
