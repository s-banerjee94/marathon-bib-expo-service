package com.timekeeper.bibexpo.messaging.provider.service.impl;

import com.timekeeper.bibexpo.messaging.delivery.OutboundMessage;
import com.timekeeper.bibexpo.messaging.provider.exception.MessagingProviderException;
import com.timekeeper.bibexpo.messaging.provider.model.ProviderParam;
import com.timekeeper.bibexpo.messaging.provider.model.entity.MessagingProvider;
import com.timekeeper.bibexpo.messaging.provider.model.enums.HttpMethodType;
import com.timekeeper.bibexpo.messaging.provider.model.enums.MessageContentType;
import com.timekeeper.bibexpo.messaging.provider.service.impl.RequestTokenResolver.Escape;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns a provider row plus one outbound message into the exact HTTP call that would be made, and
 * into a secret-free copy of it for logging. Shared by the real client, which builds then fires, and
 * the stub, which builds then only logs — so what dev sees locally is the same payload production
 * sends, rather than the raw message fields the mapping may not even reference.
 */
@Component
@RequiredArgsConstructor
class ProviderRequestBuilder {

    private final RequestTokenResolver tokenResolver;

    /** One fully-assembled provider call. */
    record ProviderRequest(HttpMethodType method, String url, Map<String, String> headers, String body) {
    }

    /**
     * Assembles the call: every value — URL, header/query params, body template — is token-resolved
     * and escaped for its context.
     *
     * @throws MessagingProviderException if the provider has no endpoint configured
     */
    ProviderRequest build(MessagingProvider provider, OutboundMessage message) {
        if (provider.getBaseUrl() == null || provider.getBaseUrl().isBlank()) {
            throw new MessagingProviderException(
                    "The " + provider.getChannel() + " provider has no endpoint configured.");
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

        String body = carriesBody(provider.getHttpMethod())
                ? tokenResolver.resolve(provider.getBodyTemplate(), escapeFor(provider.getContentType()), provider, message)
                : null;

        return new ProviderRequest(provider.getHttpMethod(), uri.build().encode().toUriString(), headers, body);
    }

    /** Whether the verb sends a request body at all. */
    static boolean carriesBody(HttpMethodType method) {
        return method == HttpMethodType.POST || method == HttpMethodType.PUT || method == HttpMethodType.PATCH;
    }

    /** How a token value must be escaped to be safe inside a body of this encoding. */
    private static Escape escapeFor(MessageContentType contentType) {
        if (contentType == null) {
            return Escape.JSON;
        }
        return switch (contentType) {
            case JSON -> Escape.JSON;
            case FORM -> Escape.FORM;
            case XML -> Escape.XML;
            case TEXT -> Escape.NONE;
        };
    }

    /**
     * Whether the provider's mapping can carry the message at all. False for a row with neither
     * request params nor a body template — the placeholder the campaign resolver hands back when no
     * provider is configured — where an assembled request would show nothing about the message.
     */
    boolean carriesMessage(MessagingProvider provider) {
        boolean hasParams = !safeParams(provider).isEmpty();
        boolean hasBody = provider.getBodyTemplate() != null && !provider.getBodyTemplate().isBlank();
        return hasParams || hasBody;
    }

    /** Replaces the provider's own secrets with a mask, for a request that is about to be logged. */
    ProviderRequest redacted(ProviderRequest request, MessagingProvider provider) {
        return new ProviderRequest(request.method(), redact(request.url(), provider),
                redactMap(request.headers(), provider), redact(request.body(), provider));
    }

    String redact(String text, MessagingProvider provider) {
        String result = replaceSecret(text, provider.getAuthToken());
        result = replaceSecret(result, provider.getPassword());
        return replaceSecret(result, basicAuthSecret(provider));
    }

    private Map<String, String> redactMap(Map<String, String> source, MessagingProvider provider) {
        Map<String, String> out = new LinkedHashMap<>();
        source.forEach((key, value) -> out.put(key, redact(value, provider)));
        return out;
    }

    private String basicAuthSecret(MessagingProvider provider) {
        if (provider.getPassword() == null || provider.getPassword().isBlank()) {
            return null;
        }
        String username = provider.getUsername() == null ? "" : provider.getUsername();
        return Base64.getEncoder()
                .encodeToString((username + ":" + provider.getPassword()).getBytes(StandardCharsets.UTF_8));
    }

    // Below this, a "secret" is short enough that blanking every occurrence would corrupt the rest of
    // the line — a two-character token would mask half the payload and hide what was actually sent.
    private static final int MIN_REDACTABLE_SECRET_LENGTH = 6;

    private String replaceSecret(String text, String secret) {
        if (text == null || secret == null || secret.length() < MIN_REDACTABLE_SECRET_LENGTH) {
            return text;
        }
        return text.replace(secret, "****");
    }

    private List<ProviderParam> safeParams(MessagingProvider provider) {
        return provider.getRequestParams() == null ? new ArrayList<>() : provider.getRequestParams();
    }
}
