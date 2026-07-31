package com.timekeeper.bibexpo.messaging.provider.service.impl;

import com.timekeeper.bibexpo.messaging.provider.exception.InvalidMessagingProviderException;
import com.timekeeper.bibexpo.messaging.provider.model.ProviderParam;
import com.timekeeper.bibexpo.messaging.provider.model.entity.MessagingProvider;
import com.timekeeper.bibexpo.messaging.provider.model.enums.TemplateMode;
import com.timekeeper.bibexpo.messaging.shared.enums.MessageChannel;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Holds a provider row to its own word: the mode it declares must match the tokens its request
 * actually reads.
 *
 * <p>The mode and the request mapping are two separate fields filled in by the same person, and
 * nothing ties them together. A row that claims to want the finished text while its body only reads
 * {@code {{VAR:n}}} renders the message, drops it, and sends the vendor empty variables — delivered,
 * reported as sent, and blank. Comparing a template's mode against the provider's mode cannot catch
 * it, because both agree; only reading the mapping can.
 */
@Component
public class ProviderMappingValidator {

    private static final String MESSAGE_TOKEN = "MESSAGE";
    private static final String TEMPLATE_ID_TOKEN = "TEMPLATE_ID";
    private static final String RECIPIENT_EMAIL_TOKEN = "RECIPIENT_EMAIL";
    private static final String SENDER_ID_TOKEN = "SENDER_ID";
    private static final Set<String> COUNTRY_CODE_TOKENS = Set.of("RECIPIENT_E164", "RECIPIENT_CC");
    private static final Set<String> VARIABLE_TOKENS = Set.of("VAR", "VARIABLES_JSON");
    private static final Set<String> PHONE_TOKENS = Set.of("RECIPIENT", "RECIPIENT_E164", "RECIPIENT_CC");

    /**
     * Rejects a provider whose declared mode contradicts its mapping.
     *
     * @param provider the row about to be saved
     * @throws InvalidMessagingProviderException if the mode and the mapping disagree
     */
    public void requireConsistent(MessagingProvider provider) {
        inconsistency(provider).ifPresent(problem -> {
            throw new InvalidMessagingProviderException(problem);
        });
    }

    /**
     * What is wrong with the row, phrased for whoever is configuring it.
     *
     * @param provider the row to inspect
     * @return the problem, or empty when the mode and the mapping agree
     */
    public Optional<String> inconsistency(MessagingProvider provider) {
        TemplateMode mode = provider.getTemplateMode();
        if (mode == null) {
            return Optional.empty();
        }

        Set<String> tokens = mappedTokens(provider);

        if (provider.getChannel() == MessageChannel.EMAIL && !tokens.contains(RECIPIENT_EMAIL_TOKEN)) {
            return Optional.of("This provider sends email but its request never uses {{RECIPIENT_EMAIL}}, so it has "
                    + "no address to send to. Add it.");
        }
        if (provider.getChannel() != MessageChannel.EMAIL
                && tokens.stream().noneMatch(PHONE_TOKENS::contains)) {
            return Optional.of("This provider's request never uses {{RECIPIENT}} or {{RECIPIENT_E164}}, so it has no "
                    + "number to send to. Add one.");
        }

        if (tokens.stream().anyMatch(COUNTRY_CODE_TOKENS::contains)
                && (provider.getDefaultCountryCode() == null || provider.getDefaultCountryCode().isBlank())) {
            return Optional.of("This provider's request builds an international number, so it needs a country code. "
                    + "Set one, or use {{RECIPIENT}} to send the number exactly as it is stored.");
        }

        if (mode == TemplateMode.CLIENT_RENDERED && !tokens.contains(MESSAGE_TOKEN)) {
            return Optional.of("This provider is set to send the finished message text, but its request never uses "
                    + "{{MESSAGE}}. Add it, or set the provider to render the message itself.");
        }
        if (mode == TemplateMode.PROVIDER_RENDERED && tokens.stream().noneMatch(VARIABLE_TOKENS::contains)) {
            return Optional.of("This provider is set to render the message from its own registered template, but its "
                    + "request never uses {{VAR:n}} or {{VARIABLES_JSON}}. Add one, or set the provider to send the "
                    + "finished message text.");
        }
        return Optional.empty();
    }

    /** What a template can hand to a send, as the mapping's requirements are judged against it. */
    public record TemplateContent(boolean hasMessageText, int variableCount, boolean hasTemplateId,
                                  boolean hasSenderId) {
    }

    /**
     * Whether a template can satisfy what the provider's request asks for. Judged from the mapping
     * rather than the declared mode, so a registered template that needs no variables at all stays
     * valid — the request simply never asks for any.
     *
     * @param provider the provider the message would go through
     * @param content  what the template supplies
     * @return the problem, or empty when the template covers every token the request reads
     */
    public Optional<String> unmetRequirement(MessagingProvider provider, TemplateContent content) {
        Set<String> tokens = mappedTokens(provider);
        String channel = String.valueOf(provider.getChannel());

        if (tokens.contains(MESSAGE_TOKEN) && !content.hasMessageText()) {
            return Optional.of(provider.getChannel() == MessageChannel.WHATSAPP
                    ? "Your WhatsApp sender expects the finished message text, but WhatsApp campaigns send the "
                            + "approved template instead. Set the sender to use the template variables."
                    : "Your " + channel + " sender expects the finished message text, but this template does not have any.");
        }

        int expected = maxVariableIndex(provider) + 1;
        if (expected > content.variableCount()) {
            return Optional.of("Your " + channel + " sender fills " + expected + " variable(s), but this template "
                    + "supplies " + content.variableCount() + ". Add the missing variables before sending.");
        }

        if (tokens.contains(SENDER_ID_TOKEN) && !content.hasSenderId()) {
            return Optional.of("Your " + channel + " sender puts a sender id on every message, which this template "
                    + "does not have.");
        }

        if (tokens.contains(TEMPLATE_ID_TOKEN) && !content.hasTemplateId()) {
            return Optional.of("Your " + channel + " sender needs a registered template id, which this template "
                    + "does not have.");
        }
        return Optional.empty();
    }

    /** Highest positional variable index the row reads across its whole mapping, or -1 for none. */
    private int maxVariableIndex(MessagingProvider provider) {
        int highest = Math.max(RequestTokenResolver.maxVariableIndex(provider.getBaseUrl()),
                RequestTokenResolver.maxVariableIndex(provider.getBodyTemplate()));

        List<ProviderParam> params = provider.getRequestParams();
        if (params != null) {
            for (ProviderParam param : params) {
                highest = Math.max(highest, RequestTokenResolver.maxVariableIndex(param.getValue()));
            }
        }
        return highest;
    }

    /** Every token the row reads, across the URL, the header/query params, and the body template. */
    private Set<String> mappedTokens(MessagingProvider provider) {
        Set<String> tokens = new HashSet<>(RequestTokenResolver.tokenNames(provider.getBaseUrl()));
        tokens.addAll(RequestTokenResolver.tokenNames(provider.getBodyTemplate()));

        List<ProviderParam> params = provider.getRequestParams();
        if (params != null) {
            params.forEach(param -> tokens.addAll(RequestTokenResolver.tokenNames(param.getValue())));
        }
        return tokens;
    }
}
