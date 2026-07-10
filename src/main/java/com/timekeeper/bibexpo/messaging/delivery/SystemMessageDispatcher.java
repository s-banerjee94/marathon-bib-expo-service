package com.timekeeper.bibexpo.messaging.delivery;

import com.timekeeper.bibexpo.messaging.provider.service.MessagingProviderClient;
import com.timekeeper.bibexpo.messaging.shared.enums.MessageChannel;
import com.timekeeper.bibexpo.messaging.shared.enums.SystemTemplatePurpose;
import com.timekeeper.bibexpo.messaging.system.model.entity.SystemMessageTemplate;
import com.timekeeper.bibexpo.messaging.system.service.SystemMessageTemplateService;
import com.timekeeper.bibexpo.messaging.shared.template.MessageTemplateParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Sends an app-default (system) message over one or more channels for a given purpose. Each channel
 * is attempted independently and best-effort: one channel failing never blocks the others or the
 * caller, and every outcome is reported back as a {@link DeliveryResult}. The per-channel payload is
 * rendered from that channel's {@link SystemMessageTemplate} using the supplied context object, whose
 * getters back the {@code #{field}} placeholders.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SystemMessageDispatcher {

    private final MessagingProviderClient messagingProviderClient;
    private final SystemMessageTemplateService systemMessageTemplateService;

    /**
     * Delivers the message for {@code purpose} to {@code recipientPhone} over each requested channel.
     *
     * @param purpose        the system flow whose template to render (e.g. INVITE, PASSWORD_RESET)
     * @param channels       the channels to send over; an empty set delivers nothing and returns an empty list
     * @param recipientPhone the destination phone number
     * @param context        the variable source whose getters back the template's {@code #{field}} placeholders
     * @return one {@link DeliveryResult} per channel, in iteration order
     */
    public List<DeliveryResult> deliver(SystemTemplatePurpose purpose, Set<MessageChannel> channels,
                                        String recipientPhone, Object context) {
        List<DeliveryResult> results = new ArrayList<>();
        for (MessageChannel channel : channels) {
            try {
                messagingProviderClient.send(channel, buildMessage(purpose, channel, recipientPhone, context));
                results.add(DeliveryResult.builder().channel(channel).sent(true).build());
            } catch (Exception e) {
                log.warn("{} delivery over {} failed: {}", purpose, channel, e.getMessage());
                results.add(DeliveryResult.builder().channel(channel).sent(false).detail(e.getMessage()).build());
            }
        }
        return results;
    }

    /**
     * Builds the per-channel payload from that channel's template: the body is rendered for
     * client-rendered providers, the ordered variables for provider-rendered ones, and both carry the
     * registered template and sender id.
     */
    private OutboundMessage buildMessage(SystemTemplatePurpose purpose, MessageChannel channel,
                                         String recipientPhone, Object context) {
        SystemMessageTemplate template = systemMessageTemplateService.resolve(purpose, channel);
        return OutboundMessage.builder()
                .recipientPhone(recipientPhone)
                .templateId(template.getDltTemplateId())
                .senderId(template.getSenderId())
                .message(renderBody(template.getBody(), context))
                .variables(renderVariables(template.getVariables(), context))
                .build();
    }

    private String renderBody(String body, Object context) {
        return (body == null || body.isBlank()) ? null : MessageTemplateParser.parse(body, context);
    }

    /** Renders the newline-separated {@code #{field}} expressions into ordered positional values. */
    private List<String> renderVariables(String variables, Object context) {
        if (variables == null || variables.isBlank()) {
            return List.of();
        }
        return Arrays.stream(variables.split("\n"))
                .map(expression -> MessageTemplateParser.parse(expression, context))
                .toList();
    }
}
