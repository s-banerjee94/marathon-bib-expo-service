package com.timekeeper.bibexpo.messaging.provider.service.impl;

import com.timekeeper.bibexpo.messaging.shared.enums.MessageChannel;
import com.timekeeper.bibexpo.messaging.provider.exception.MessagingProviderException;
import com.timekeeper.bibexpo.messaging.delivery.OutboundMessage;
import com.timekeeper.bibexpo.messaging.provider.model.entity.MessagingProvider;
import com.timekeeper.bibexpo.messaging.provider.model.enums.MessageContentType;
import com.timekeeper.bibexpo.messaging.provider.service.MessagingProviderCache;
import com.timekeeper.bibexpo.messaging.provider.service.MessagingProviderClient;
import com.timekeeper.bibexpo.messaging.provider.service.impl.ProviderRequestBuilder.ProviderRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

/**
 * Reads the channel's provider row and replays it into one HTTP call. Every value — the URL, each
 * header/query field, and the body template — is run through {@link RequestTokenResolver}, so a
 * provider's entire request shape (GET query, POST JSON, POST form, Basic auth, account-id in the
 * URL) is expressed as data. Switching or adding a provider is a data change, not a code change.
 *
 * <p>The request carries the recipient's number and the message, and a GET request its credentials too,
 * so the assembled request is logged at DEBUG only, and anything the provider sends back is scrubbed of
 * the number, the text and the secrets before it is logged.
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
        ProviderRequest request = requestBuilder.build(provider, message);
        if (log.isDebugEnabled()) {
            ProviderRequest safe = requestBuilder.redacted(request, provider);
            log.debug("{} {} request: {} {} headers={} body={}", provider.getUsage(), provider.getChannel(),
                    safe.method(), safe.url(), safe.headers(), safe.body());
        }
        fire(provider, request, message);
    }

    private void fire(MessagingProvider provider, ProviderRequest request, OutboundMessage message) {
        MessageChannel channel = provider.getChannel();
        try {
            // url is already fully percent-encoded (uri.build().encode()); pass a URI so RestClient's
            // default UriBuilderFactory does not encode it a second time (double-encoding query values).
            URI uri = URI.create(request.url());
            if (provider.getOrganizationId() != null) {
                requirePublicHost(provider, uri);
            }
            String response;
            if (ProviderRequestBuilder.carriesBody(request.method())) {
                RestClient.RequestBodySpec spec = restClient
                        .method(HttpMethod.valueOf(request.method().name())).uri(uri);
                request.headers().forEach(spec::header);
                spec.contentType(mediaTypeFor(provider.getContentType()));
                if (request.body() != null && !request.body().isBlank()) {
                    spec.body(request.body());
                }
                response = spec.retrieve().body(String.class);
            } else {
                RestClient.RequestHeadersSpec<?> spec = restClient.get().uri(uri);
                request.headers().forEach(spec::header);
                response = spec.retrieve().body(String.class);
            }
            requireSuccessBody(provider, message, response);
        } catch (RestClientResponseException e) {
            log.atWarn()
                    .addKeyValue("event", "message.provider_rejected")
                    .addKeyValue("channel", channel)
                    .addKeyValue("usage", provider.getUsage())
                    .addKeyValue("organizationId", provider.getOrganizationId())
                    .addKeyValue("status", e.getStatusCode().value())
                    .addKeyValue("response", requestBuilder.scrub(e.getResponseBodyAsString(), provider, message))
                    .log("Messaging provider rejected the request");
            // Not chained: the client exception's message carries the response, and the URL for some failures.
            throw new MessagingProviderException("The " + channel + " provider call failed.");
        } catch (MessagingProviderException e) {
            throw e;
        } catch (Exception e) {
            // No HTTP response (bad URL, connection refused, timeout). The outer exception's message repeats the
            // full URL, query string and all, so only the most specific cause is logged, scrubbed.
            Throwable cause = NestedExceptionUtils.getMostSpecificCause(e);
            log.atWarn()
                    .addKeyValue("event", "message.provider_unreachable")
                    .addKeyValue("channel", channel)
                    .addKeyValue("usage", provider.getUsage())
                    .addKeyValue("organizationId", provider.getOrganizationId())
                    .addKeyValue("exception", cause.getClass().getSimpleName())
                    .addKeyValue("reason", requestBuilder.scrub(cause.getMessage(), provider, message))
                    .log("Messaging provider could not be reached");
            throw new MessagingProviderException("The " + channel + " provider call failed.");
        }
    }

    /**
     * An organization's admin chooses this address and our server makes the call, so it must not reach our
     * own network or the cloud metadata service. Root-managed rows are trusted and not checked. The lookup
     * lands in the JVM's DNS cache, so the client connects to the address checked here rather than
     * resolving again to a different one.
     */
    private void requirePublicHost(MessagingProvider provider, URI uri) throws UnknownHostException {
        for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
            if (isInternal(address)) {
                log.atWarn()
                        .addKeyValue("event", "message.provider_blocked")
                        .addKeyValue("channel", provider.getChannel())
                        .addKeyValue("organizationId", provider.getOrganizationId())
                        .addKeyValue("host", uri.getHost())
                        .log("Messaging provider address points to a private network");
                throw new MessagingProviderException("The " + provider.getChannel()
                        + " provider's address points to a private network, which is not allowed.");
            }
        }
    }

    // Unique-local IPv6 (fc00::/7) has no InetAddress check of its own and holds AWS's IPv6 metadata address.
    private static boolean isInternal(InetAddress address) {
        return address.isLoopbackAddress() || address.isAnyLocalAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()
                || (address instanceof Inet6Address && (address.getAddress()[0] & 0xfe) == 0xfc);
    }

    /**
     * Treats a 2xx response as a failure when the provider is configured to prove success in the body.
     * Plenty of gateways answer 200 with {@code {"status":"error"}} and bill for it; without this the
     * send is recorded as delivered and nobody finds out.
     */
    private void requireSuccessBody(MessagingProvider provider, OutboundMessage message, String response) {
        String expected = provider.getSuccessContains();
        if (expected == null || expected.isBlank()) {
            // Logged at info until a success marker is set: it is how an administrator finds the text that
            // proves a send worked, which is the value the marker has to be set to.
            log.atInfo()
                    .addKeyValue("event", "message.provider_response")
                    .addKeyValue("channel", provider.getChannel())
                    .addKeyValue("usage", provider.getUsage())
                    .addKeyValue("organizationId", provider.getOrganizationId())
                    .addKeyValue("response", response == null ? "<empty>" : requestBuilder.scrub(response, provider, message))
                    .log("Messaging provider response, no success marker set");
            return;
        }
        if (response == null || !response.contains(expected)) {
            log.atWarn()
                    .addKeyValue("event", "message.provider_unconfirmed")
                    .addKeyValue("channel", provider.getChannel())
                    .addKeyValue("usage", provider.getUsage())
                    .addKeyValue("organizationId", provider.getOrganizationId())
                    .addKeyValue("response", response == null ? "<empty>" : requestBuilder.scrub(response, provider, message))
                    .log("Messaging provider accepted the request but did not confirm success");
            throw new MessagingProviderException(
                    "The " + provider.getChannel() + " provider did not confirm the message was sent.");
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
