package com.timekeeper.bibexpo.messaging.campaign.util;

import com.timekeeper.bibexpo.messaging.shared.template.MessageTemplateContext;
import com.timekeeper.bibexpo.messaging.shared.template.MessageTemplateParser;

import java.util.Arrays;
import java.util.List;

/**
 * Renders a campaign template's stored body variables (newline-joined placeholder
 * expressions) into the ordered values a provider-rendered gateway expects — entry n
 * becomes positional variable {@code {{VAR:n}}} (Twilio ContentVariables for WhatsApp,
 * DLT variables for SMS).
 */
public final class CampaignVariableRenderer {

    private CampaignVariableRenderer() {}

    public static List<String> render(String joinedBodyVariables, MessageTemplateContext context) {
        if (joinedBodyVariables == null || joinedBodyVariables.isBlank()) {
            return List.of();
        }
        return Arrays.stream(joinedBodyVariables.split("\n"))
                .map(expression -> MessageTemplateParser.parse(expression, context))
                .toList();
    }
}
