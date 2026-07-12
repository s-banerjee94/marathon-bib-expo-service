package com.timekeeper.bibexpo.messaging.campaign.util;

import com.timekeeper.bibexpo.messaging.shared.template.MessageTemplateContext;
import com.timekeeper.bibexpo.messaging.shared.template.MessageTemplateParser;

import java.util.Arrays;
import java.util.List;

/**
 * Renders a WhatsApp template's stored body variables (newline-joined placeholder
 * expressions) into the ordered values for Twilio's ContentVariables — entry n becomes
 * template variable {{n}}.
 */
public final class WhatsAppVariableRenderer {

    private WhatsAppVariableRenderer() {}

    public static List<String> render(String joinedBodyVariables, MessageTemplateContext context) {
        if (joinedBodyVariables == null || joinedBodyVariables.isBlank()) {
            return List.of();
        }
        return Arrays.stream(joinedBodyVariables.split("\n"))
                .map(expression -> MessageTemplateParser.parse(expression, context))
                .toList();
    }
}
